package com.adda.chat.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adda.chat.call.CallManager
import com.google.firebase.messaging.FirebaseMessaging
import com.adda.chat.service.ConnectionService
import com.adda.chat.util.FileUpload
import com.adda.chat.util.Notifier
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import org.json.JSONArray
import org.json.JSONObject

/** অ্যাপের সব অবস্থা (state), লগইন, সকেট ও বার্তা এখানে */
object Session {
    lateinit var appContext: Context
        private set
    private lateinit var prefs: SharedPreferences

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var serverUrl by mutableStateOf("")
    var connectionLabel by mutableStateOf("সার্ভার লিংক খোঁজা হচ্ছে…")
    var socketConnected by mutableStateOf(false)
    var hasToken by mutableStateOf(false)
    var me by mutableStateOf<Me?>(null)
    var busy by mutableStateOf(false)
    var authError by mutableStateOf("")
    var activeFriend by mutableStateOf<String?>(null)
    var toastMsg by mutableStateOf<ToastMsg?>(null)
    /** MainActivity.onStart এ বাড়ে — অনুমতির ডায়ালগ আবার যাচাই করতে */
    var startTick by mutableStateOf(0)

    val names = mutableStateMapOf<String, String>()
    val online = mutableStateMapOf<String, Boolean>()
    val unread = mutableStateMapOf<String, Int>()
    val messages = mutableStateListOf<ChatMessage>()

    @Volatile var appInForeground = false

    private val seenIds = HashSet<String>()
    private var socket: Socket? = null
    private var restoring = false
    private var toastCounter = 0

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("adda", Context.MODE_PRIVATE)
        Api.token = prefs.getString("messengerToken", "").orEmpty()
        hasToken = Api.token.isNotEmpty()
        Notifier.init(appContext)
        startServerUrlWatcher()
    }

    fun toast(text: String?) {
        if (text.isNullOrBlank()) return
        toastMsg = ToastMsg(++toastCounter, text)
    }

    fun displayName(uid: String): String = names[uid] ?: uid

    // ---------- সার্ভার লিংক (Firestore) ----------

    private fun startServerUrlWatcher() {
        scope.launch {
            while (true) {
                val url = Api.fetchServerUrl()
                if (url != null && url != Api.serverUrl) {
                    Api.serverUrl = url
                    serverUrl = url
                    connectionLabel = "● সার্ভার লিংক পাওয়া গেছে"
                    onServerUrlChanged()
                } else if (url == null && Api.serverUrl.isEmpty()) {
                    connectionLabel = "সার্ভার কনফিগারেশন পাওয়া যায়নি"
                } else if (Api.token.isNotEmpty() && me == null) {
                    restoreSession()
                }
                delay(15_000)
            }
        }
    }

    private fun onServerUrlChanged() {
        if (Api.token.isEmpty()) return
        if (me != null) {
            connectSocket()
            scope.launch { refreshPeople() }
        } else {
            restoreSession()
        }
    }

    private fun restoreSession() {
        if (restoring || Api.serverUrl.isEmpty()) return
        restoring = true
        scope.launch {
            try {
                me = parseMe(Api.getObj("/api/me"))
                enterMessenger()
            } catch (e: Api.ApiException) {
                if (e.status == 401 || e.status == 403) clearToken()
                else connectionLabel = "সার্ভারে পৌঁছানো যাচ্ছে না"
            } catch (_: Exception) {
                connectionLabel = "সার্ভারে পৌঁছানো যাচ্ছে না"
            } finally {
                restoring = false
            }
        }
    }

    private suspend fun enterMessenger() {
        ConnectionService.start(appContext)
        refreshPeople()
        connectSocket()
        refreshFcmToken()
    }

    /** Firebase token refresh/register. The server stores it against the authenticated account. */
    fun refreshFcmToken() {
        if (Api.token.isEmpty() || Api.serverUrl.isEmpty()) return
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> registerPushToken(token) }
        } catch (_: Exception) {
            // A matching google-services.json is required for FCM.
        }
    }

    fun registerPushToken(token: String) {
        if (token.isBlank() || Api.token.isEmpty() || Api.serverUrl.isEmpty()) return
        scope.launch {
            try {
                Api.postObj("/api/push-token", JSONObject().put("token", token))
            } catch (_: Exception) {
                // Next login/token refresh retries registration.
            }
        }
    }

    /** নেট ফিরলে বা সার্ভিস জাগলে: সকেট বিচ্ছিন্ন থাকলে সাথে সাথে আবার যুক্ত করে */
    fun ensureConnected() {
        if (Api.token.isEmpty() || Api.serverUrl.isEmpty()) return
        if (me == null) {
            restoreSession()
            return
        }
        val s = socket
        if (s == null) {
            connectSocket()
        } else if (!s.connected()) {
            // UI state এবং Socket.IO engine state কখনও সাময়িকভাবে আলাদা হতে পারে।
            // প্রকৃত socket সংযোগ নেই হলেই পুনরায় connect() করি।
            try { s.connect() } catch (_: Exception) { }
        } else {
            socketConnected = true
            connectionLabel = "● সংযুক্ত"
        }
    }

    private fun clearToken() {
        prefs.edit().remove("messengerToken").apply()
        Api.token = ""
        hasToken = false
    }

    // ---------- লগইন / সাইন আপ / লগআউট ----------

    fun authenticate(username: String, password: String, signup: Boolean) {
        if (Api.serverUrl.isEmpty()) {
            toast("সার্ভার ইউআরএল লোড হচ্ছে, একটু অপেক্ষা করুন...")
            return
        }
        busy = true
        authError = ""
        scope.launch {
            try {
                val r = Api.postObj(
                    if (signup) "/api/signup" else "/api/login",
                    JSONObject().put("username", username.trim()).put("password", password)
                )
                Api.token = r.getString("token")
                prefs.edit().putString("messengerToken", Api.token).apply()
                hasToken = true
                me = parseMe(r.getJSONObject("user"))
                enterMessenger()
                toast(if (signup) "অ্যাকাউন্ট তৈরি হয়েছে। আপনার আইডি: ${me?.uid}" else "লগইন সফল হয়েছে")
            } catch (e: Api.ApiException) {
                authError = e.message ?: "ত্রুটি হয়েছে"
            } catch (e: Exception) {
                authError = e.message ?: "ত্রুটি হয়েছে"
            } finally {
                busy = false
            }
        }
    }

    fun logout() {
        clearToken() // আগে টোকেন মুছি, যাতে কল শেষ হওয়ার সময় সার্ভিস আবার শুরু না হয়
        CallManager.endCall(true)
        ConnectionService.stop(appContext)
        socket?.let { it.off(); it.disconnect(); it.close() }
        socket = null
        socketConnected = false
        clearToken()
        me = null
        activeFriend = null
        messages.clear()
        seenIds.clear()
        names.clear()
        online.clear()
        unread.clear()
    }

    // ---------- বন্ধু ----------

    suspend fun refreshPeople() {
        try {
            me = parseMe(Api.getObj("/api/me"))
        } catch (e: Api.ApiException) {
            toast(e.message)
            return
        }
        val current = me ?: return
        val ids = (current.friends + current.requests).distinct()
        coroutineScope {
            ids.map { uid ->
                async {
                    try {
                        val p = Api.getObj("/api/users/${Uri.encode(uid)}")
                        names[uid] = p.optString("username", uid).ifEmpty { uid }
                    } catch (_: Exception) {
                    }
                }
            }.awaitAll()
        }
    }

    fun sendFriendRequest(rawUid: String, onSuccess: () -> Unit) {
        val uid = rawUid.trim().uppercase()
        if (uid.isEmpty()) return
        scope.launch {
            try {
                val r = Api.postObj("/api/friends/request", JSONObject().put("uid", uid))
                onSuccess()
                toast(r.optString("message").ifEmpty { "ফ্রেন্ড রিকুয়েস্ট পাঠানো হয়েছে" })
            } catch (e: Api.ApiException) {
                toast(e.message)
            }
        }
    }

    fun respondRequest(uid: String, accept: Boolean) {
        scope.launch {
            try {
                Api.postObj(if (accept) "/api/friends/accept" else "/api/friends/reject", JSONObject().put("uid", uid))
                refreshPeople()
                toast(if (accept) "ফ্রেন্ড রিকুয়েস্ট গ্রহণ করা হয়েছে" else "রিকুয়েস্ট বাতিল করা হয়েছে")
            } catch (e: Api.ApiException) {
                toast(e.message)
            }
        }
    }

    // ---------- চ্যাট ----------

    fun openConversation(uid: String) {
        activeFriend = uid
        unread.remove(uid)
        messages.clear()
        seenIds.clear()
        scope.launch {
            try {
                val arr: JSONArray = Api.getArr("/api/messages/${Uri.encode(uid)}")
                if (activeFriend != uid) return@launch
                for (i in 0 until arr.length()) addMessage(parseMessage(arr.getJSONObject(i)))
                messages.sortBy { it.date }
            } catch (e: Api.ApiException) {
                toast(e.message)
            } catch (_: Exception) {
            }
        }
    }

    fun closeConversation() {
        activeFriend = null
        messages.clear()
        seenIds.clear()
    }

    private fun addMessage(m: ChatMessage) {
        val myUid = me?.uid ?: return
        val active = activeFriend ?: return
        if (m.id.isEmpty() || seenIds.contains(m.id)) return
        if (m.from != myUid && m.from != active) return
        if (m.to != myUid && m.to != active) return
        seenIds.add(m.id)
        messages.add(m)
    }

    private fun onChatMessage(m: ChatMessage) {
        val myUid = me?.uid ?: return
        val active = activeFriend
        val preview = if (m.isFile) "📎 ${m.fileName ?: "ফাইল"}" else m.text
        if (active != null && (m.from == active || m.to == active)) {
            addMessage(m)
            if (m.from != myUid && !appInForeground) {
                Notifier.message(appContext, displayName(m.from), preview, m.from.hashCode())
            }
        } else if (m.from != myUid) {
            unread[m.from] = (unread[m.from] ?: 0) + 1
            if (appInForeground) toast("নতুন মেসেজ এসেছে: ${displayName(m.from)}")
            else Notifier.message(appContext, displayName(m.from), preview, m.from.hashCode())
        }
    }

    fun sendText(text: String, onDone: (success: Boolean) -> Unit) {
        val to = activeFriend ?: return
        val s = socket
        if (s == null || !socketConnected) {
            toast("সার্ভারের সঙ্গে সংযোগ নেই")
            onDone(false)
            return
        }
        s.emit("chat:send", JSONObject().put("to", to).put("text", text), Ack { args ->
            val err = (args.firstOrNull() as? JSONObject)?.optString("error").orEmpty()
            scope.launch {
                if (err.isNotEmpty()) toast(err)
                onDone(err.isEmpty())
            }
        })
    }

    fun sendFile(uri: Uri) {
        val to = activeFriend ?: run {
            toast("ফাইল পাঠাতে আগে একজন বন্ধুকে নির্বাচন করুন")
            return
        }
        scope.launch {
            try {
                toast("ফাইল প্রস্তুত হচ্ছে…")
                val prepared = withContext(Dispatchers.IO) { FileUpload.prepare(appContext, uri) }
                val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", prepared.name, prepared.body)
                    .addFormDataPart("to", to)
                    .build()
                val resp = Api.request("POST", "/api/messages/file", body = form)
                addMessage(parseMessage(JSONObject(resp)))
                toast(if (prepared.compressed) "ছবি কমপ্রেস করে পাঠানো হয়েছে" else "ফাইল পাঠানো হয়েছে")
            } catch (e: Api.ApiException) {
                toast(e.message)
            } catch (e: IllegalArgumentException) {
                toast(e.message)
            } catch (e: Exception) {
                toast("ফাইল পাঠানো যায়নি: ${e.message}")
            }
        }
    }

    // ---------- সকেট ----------

    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    private fun connectSocket() {
        socket?.let { it.off(); it.disconnect(); it.close() }
        socket = null
        if (Api.serverUrl.isEmpty() || Api.token.isEmpty()) return

        val opts = IO.Options().apply {
            forceNew = true
            reconnection = true
            transports = arrayOf("websocket", "polling")
            auth = mapOf("token" to Api.token)
            if (Api.isNgrok(Api.serverUrl)) extraHeaders = mapOf("ngrok-skip-browser-warning" to listOf("true"))
        }
        val s = try {
            IO.socket(Api.serverUrl, opts)
        } catch (e: Exception) {
            connectionLabel = "সার্ভারের লিংক সঠিক নয়"
            return
        }
        socket = s

        s.on(Socket.EVENT_CONNECT) {
            scope.launch {
                socketConnected = true
                connectionLabel = "● সংযুক্ত"
                ConnectionService.refreshNotification(appContext, CallManager.phase != CallManager.Phase.None)
            }
        }
        s.on(Socket.EVENT_DISCONNECT) {
            scope.launch {
                socketConnected = false
                connectionLabel = "সংযোগ বিচ্ছিন্ন"
                ConnectionService.refreshNotification(appContext, CallManager.phase != CallManager.Phase.None)
            }
        }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val reason = args.firstOrNull()?.toString().orEmpty()
            scope.launch {
                socketConnected = false
                connectionLabel = "সংযোগ সমস্যা: $reason"
            }
        }
        s.on("chat:message") { args ->
            (args.firstOrNull() as? JSONObject)?.let { o -> scope.launch { onChatMessage(parseMessage(o)) } }
        }
        s.on("friend:request") { args ->
            val o = args.firstOrNull() as? JSONObject
            scope.launch {
                refreshPeople()
                toast("${o?.optString("username")?.ifEmpty { null } ?: o?.optString("uid").orEmpty()} আপনাকে ফ্রেন্ড রিকুয়েস্ট পাঠিয়েছেন")
            }
        }
        s.on("friend:accepted") { args ->
            val o = args.firstOrNull() as? JSONObject
            scope.launch {
                refreshPeople()
                toast("${o?.optString("username")?.ifEmpty { null } ?: o?.optString("uid").orEmpty()} আপনার রিকুয়েস্ট গ্রহণ করেছেন")
            }
        }
        s.on("friend:rejected") {
            scope.launch { toast("আপনার ফ্রেন্ড রিকুয়েস্ট গ্রহণ করা হয়নি") }
        }
        s.on("presence:update") { args ->
            (args.firstOrNull() as? JSONObject)?.let { o ->
                scope.launch { online[o.optString("uid")] = o.optBoolean("online") }
            }
        }
        s.on("presence:list") { args ->
            val arr = args.firstOrNull() as? JSONArray
            if (arr != null) {
                scope.launch {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        online[item.optString("uid")] = item.optBoolean("online")
                    }
                }
            }
        }
        for (ev in listOf("offer", "answer", "candidate", "hangup")) {
            s.on("webrtc:$ev") { args ->
                (args.firstOrNull() as? JSONObject)?.let { o -> scope.launch { CallManager.onSignal(ev, o) } }
            }
        }
        s.connect()
    }
}
