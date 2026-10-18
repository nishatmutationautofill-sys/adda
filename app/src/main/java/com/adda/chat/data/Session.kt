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
import com.adda.chat.security.E2ee
import com.adda.chat.util.FileUpload
import com.adda.chat.util.VideoTranscoder
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
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import okio.ForwardingSink
import okio.buffer
import okio.sink
import java.io.File
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
    var mediaUploadProgress by mutableStateOf<Float?>(null)
    var mediaUploadLabel by mutableStateOf("")
    var requestInitialProfilePhoto by mutableStateOf(false)
    var authError by mutableStateOf("")
    var activeFriend by mutableStateOf<String?>(null)
    var toastMsg by mutableStateOf<ToastMsg?>(null)
    /** MainActivity.onStart এ বাড়ে — অনুমতির ডায়ালগ আবার যাচাই করতে */
    var startTick by mutableStateOf(0)

    val names = mutableStateMapOf<String, String>()
    val profilePhotos = mutableStateMapOf<String, String>()
    /** বন্ধুদের সক্রিয়তা ব্যাচের স্তর (০-৪) */
    val badges = mutableStateMapOf<String, Int>()
    val online = mutableStateMapOf<String, Boolean>()
    val unread = mutableStateMapOf<String, Int>()
    /** চ্যাট লিস্টে শেষ মেসেজের প্রিভিউ (শুধু মেমরিতে থাকে — এনক্রিপ্টেড মেসেজ ডিস্কে সেভ হয় না) */
    val lastPreview = mutableStateMapOf<String, ChatPreview>()
    val messages = mutableStateListOf<ChatMessage>()
    val discoverableUsers = mutableStateListOf<DiscoverableUser>()
    /** ব্লার থাকা আগের ছবি/ভিডিও যেগুলো ইউজার ট্যাপ করে লোড করেছে (শুধু এই চ্যাট খোলা থাকা পর্যন্ত) */
    val loadedMedia = mutableStateMapOf<String, Boolean>()

    /** ৭ দিনের পুরনো ডাটা ডিভাইস থেকে মুছে যায় (সার্ভারেও একই নিয়ম — ফাইল সার্ভারে ৭ দিনের বেশি থাকে না) */
    const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

    /** Secure Me ফাইল পুরোটা মেমরিতে এনক্রিপ্ট/ডিক্রিপ্ট হয়, তাই এর সীমা (সাধারণ ফাইলের কোনো সীমা নেই) */
    const val SECURE_FILE_MAX_BYTES = 40L * 1024 * 1024

    @Volatile var appInForeground = false

    private val seenIds = HashSet<String>()
    private val pendingSecureExpiries = HashMap<String, Long>()
    private var socket: Socket? = null
    private var restoring = false
    private var toastCounter = 0

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("adda", Context.MODE_PRIVATE)
        // Remove decrypted media cache left by a prior process; ciphertext remains on server.
        appContext.cacheDir.listFiles()?.filter { it.name.startsWith("e2ee_") }?.forEach { it.delete() }
        Api.token = prefs.getString("messengerToken", "").orEmpty()
        hasToken = Api.token.isNotEmpty()
        Notifier.init(appContext)
        P2pChatManager.init(appContext)
        P2pChatManager.onText = { from, id, text, date -> scope.launch {
            val myUid = me?.uid ?: return@launch
            addMessage(ChatMessage(id, from, myUid, "text", text, null, null, 0L, date))
        } }
        startServerUrlWatcher()
        startRetentionSweeper()
    }

    // ---------- ৭ দিনের পুরনো ডাটা মোছা (ডিভাইস) ----------

    private fun startRetentionSweeper() {
        scope.launch {
            while (true) {
                try { purgeOldLocalData() } catch (_: Exception) { }
                delay(10 * 60 * 1000L)
            }
        }
    }

    private suspend fun purgeOldLocalData() {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        messages.filter { it.date < cutoff }.forEach { old ->
            old.fileUrl?.takeIf { it.startsWith("file:") }?.let { uri ->
                Uri.parse(uri).path?.let { p -> withContext(Dispatchers.IO) { File(p).delete() } }
            }
        }
        messages.removeAll { it.date < cutoff }
        lastPreview.filterValues { it.date < cutoff }.keys.toList().forEach { lastPreview.remove(it) }
        withContext(Dispatchers.IO) {
            // ডিক্রিপ্ট করা মিডিয়া, ভয়েস রেকর্ড ও ইমেজ ক্যাশের ৭ দিনের পুরনো ফাইল
            appContext.cacheDir.walkTopDown()
                .filter { it.isFile && it.lastModified() < cutoff }
                .filter { f ->
                    f.name.startsWith("e2ee_") || f.name.startsWith("voice_") ||
                        f.absolutePath.contains("image_cache")
                }
                .forEach { it.delete() }
        }
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
        // Register only the public key. The RSA private key remains in Android Keystore.
        try {
            Api.postObj("/api/e2ee/public-key", JSONObject().put("publicKey", E2ee.publicKeyBase64()))
        } catch (e: Exception) {
            toast("E2EE কী সার্ভারে নিবন্ধন হয়নি: ${e.message}")
        }
        ConnectionService.start(appContext)
        refreshPeople()
        Social.refreshAll()
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
                if (signup) requestInitialProfilePhoto = true
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
        P2pChatManager.closeAll()
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
        pendingSecureExpiries.clear()
        names.clear()
        badges.clear()
        profilePhotos.clear()
        online.clear()
        unread.clear()
        lastPreview.clear()
        loadedMedia.clear()
        drafts.clear()
        typing.clear()
        disappear.clear()
        Social.reset()
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
        val ids = (current.friends + current.requests + current.blocked).distinct()
        coroutineScope {
            ids.map { uid ->
                async {
                    try {
                        val p = Api.getObj("/api/users/${Uri.encode(uid)}")
                        names[uid] = p.optString("username", uid).ifEmpty { uid }
                        badges[uid] = p.optInt("badge", 0)
                        p.optString("profilePhoto").takeIf { it.isNotBlank() && it != "null" }?.let { profilePhotos[uid] = it }
                    } catch (_: Exception) {
                    }
                }
            }.awaitAll()
        }
    }

    // ---------- মেসেজের অবস্থা (পাঠানো / পৌঁছেছে / দেখেছে) ----------

    private fun applyStatus(ids: Set<String>, status: Int) {
        for (i in messages.indices) {
            val m = messages[i]
            if (m.id in ids && m.status < status) messages[i] = m.copy(status = status)
        }
    }

    /** চ্যাট খোলা ও অ্যাপ সামনে থাকলে সার্ভারকে জানায় যে বন্ধুর মেসেজ দেখা হয়েছে */
    fun markSeen(peer: String) {
        if (!appInForeground || !socketConnected || isGroupId(peer)) return
        socket?.emit("chat:seen", JSONObject().put("uid", peer))
    }

    // ---------- আনফ্রেন্ড / ব্লক ----------

    private suspend fun afterFriendshipRemoved(uid: String) {
        if (activeFriend == uid) closeConversation()
        unread.remove(uid)
        lastPreview.remove(uid)
        refreshPeople()
    }

    fun unfriend(uid: String) {
        scope.launch {
            try {
                Api.postObj("/api/friends/unfriend", JSONObject().put("uid", uid))
                afterFriendshipRemoved(uid)
                toast("বন্ধু তালিকা থেকে সরানো হয়েছে")
            } catch (e: Api.ApiException) { toast(e.message) }
        }
    }

    fun blockUser(uid: String) {
        scope.launch {
            try {
                Api.postObj("/api/friends/block", JSONObject().put("uid", uid))
                afterFriendshipRemoved(uid)
                toast("ব্লক করা হয়েছে")
            } catch (e: Api.ApiException) { toast(e.message) }
        }
    }

    fun unblockUser(uid: String) {
        scope.launch {
            try {
                Api.postObj("/api/friends/unblock", JSONObject().put("uid", uid))
                refreshPeople()
                toast("আনব্লক করা হয়েছে")
            } catch (e: Api.ApiException) { toast(e.message) }
        }
    }

    fun addFriendByQr(rawValue: String) {
        val scanned = rawValue.trim()
        val prefix = "adda://friend/"
        if (!scanned.startsWith(prefix, ignoreCase = true)) {
            toast("এটি আড্ডার বন্ধু যোগ করার QR কোড নয়")
            return
        }
        val uid = scanned.substring(prefix.length).trim().uppercase()
        if (!Regex("^MSG-[A-F0-9]{8}$").matches(uid)) {
            toast("QR কোডে সঠিক আড্ডা আইডি নেই")
            return
        }
        if (uid == me?.uid) {
            toast("নিজেকে বন্ধু হিসেবে যোগ করা যাবে না")
            return
        }
        scope.launch {
            try {
                val result = Api.postObj("/api/friends/qr-add", JSONObject().put("uid", uid))
                refreshPeople()
                toast(result.optString("message").ifEmpty { "QR স্ক্যান সফল — এখন আপনারা বন্ধু" })
            } catch (e: Api.ApiException) {
                toast(e.message)
            }
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

    fun setDiscoverable(enabled: Boolean) {
        scope.launch {
            try {
                Api.postObj("/api/discoverable", JSONObject().put("enabled", enabled))
                me = me?.copy(discoverable = enabled)
                if (enabled) refreshDiscoverableUsers()
                toast(if (enabled) "আপনার আইডি এখন সবাই দেখতে পারবে" else "আপনার আইডি আর পাবলিক তালিকায় নেই")
            } catch (e: Api.ApiException) { toast(e.message) }
        }
    }

    fun refreshDiscoverableUsers() {
        scope.launch {
            try {
                val arr = Api.getArr("/api/discoverable")
                val list = mutableListOf<DiscoverableUser>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list += DiscoverableUser(o.optString("uid"), o.optString("username"), o.optString("profilePhoto").takeIf { it.isNotBlank() && it != "null" })
                }
                discoverableUsers.clear(); discoverableUsers.addAll(list.filter { it.uid != me?.uid })
                list.forEach {
                    names[it.uid] = it.username
                    it.profilePhoto?.let { photo -> profilePhotos[it.uid] = photo }
                }
            } catch (e: Api.ApiException) { toast(e.message) }
        }
    }

    fun uploadProfilePhoto(uri: Uri) {
        scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) { FileUpload.prepare(appContext, uri) }
                if (!prepared.name.substringAfterLast('.', "").lowercase().let { it in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif") }) {
                    toast("একটি ছবি নির্বাচন করুন"); return@launch
                }
                if (prepared.size > 5L * 1024 * 1024) { toast("ছবির আকার ৫ MB-এর মধ্যে হতে হবে"); return@launch }
                val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", prepared.name, prepared.body).build()
                val result = JSONObject(Api.request("POST", "/api/profile-photo", body = form))
                me = me?.copy(profilePhoto = result.optString("profilePhoto"))
                me?.let { profilePhotos[it.uid] = result.optString("profilePhoto") }
                toast("প্রোফাইল ছবি আপডেট হয়েছে")
                refreshDiscoverableUsers()
            } catch (e: Exception) { toast(e.message ?: "প্রোফাইল ছবি আপলোড হয়নি") }
        }
    }

    /** everyone=true → উভয় দিক থেকে (শুধু নিজের পাঠানো মেসেজে); false → শুধু নিজের দিক থেকে */
    fun deleteMessage(message: ChatMessage, everyone: Boolean) {
        val forEveryone = everyone && message.from == me?.uid
        scope.launch {
            try {
                val localOnly = message.fileUrl?.startsWith("file:") == true
                try {
                    Api.request("DELETE", "/api/messages/${Uri.encode(message.id)}?scope=${if (forEveryone) "everyone" else "me"}")
                } catch (e: Api.ApiException) {
                    // লোকাল ফাইল (ভয়েস/Secure কপি) সার্ভারে নাও থাকতে পারে; লোকাল ডিলেট চালিয়ে যাবে
                    if (!localOnly) throw e
                }
                messages.firstOrNull { it.id == message.id }?.fileUrl?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path?.let { p -> withContext(Dispatchers.IO) { File(p).delete() } } }
                messages.removeAll { it.id == message.id }
                refreshPreviewFor(peerOf(message, me?.uid))
                toast(if (forEveryone) "উভয় দিক থেকে ডিলেট হয়েছে" else "আপনার দিক থেকে ডিলেট হয়েছে")
            } catch (e: Api.ApiException) { toast(e.message) }
        }
    }

    /** ডিলেটের পর চ্যাট লিস্টের শেষ মেসেজ প্রিভিউ ঠিক করা */
    private fun refreshPreviewFor(peer: String) {
        val myUid = me?.uid ?: return
        val last = messages.filter { (isGroupId(peer) && it.to == peer) || (it.from == peer && it.to == myUid) || (it.from == myUid && it.to == peer) }.maxByOrNull { it.date }
        if (last == null) { lastPreview.remove(peer); return }
        lastPreview.remove(peer)
        recordPreview(peer, last, last.from == myUid)
    }

    /** ব্লার থাকা আগের ছবি/ভিডিও ইউজার চাইলে লোড করে */
    fun loadMedia(id: String) { loadedMedia[id] = true }

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
        // টেক্সট, ছবি, ভিডিও ও ফাইল সবই এখন সার্ভারের মাধ্যমে যায়; P2P আর খোলা হয় না
        unread.remove(uid)
        typing.remove(uid)
        loadChatSettings(uid)
        messages.clear()
        seenIds.clear()
        loadedMedia.clear()
        scope.launch {
            try {
                val arr: JSONArray = Api.getArr("/api/messages/${Uri.encode(uid)}")
                if (activeFriend != uid) return@launch
                for (i in 0 until arr.length()) {
                    val raw = parseMessage(arr.getJSONObject(i)).copy(history = true)
                    if (System.currentTimeMillis() - raw.date >= RETENTION_MS) continue // ৭ দিনের পুরনো — দেখানো হবে না
                    try { addMessage(hydrateMessage(raw)) } catch (e: Exception) {
                        toast("এনক্রিপ্টেড মেসেজ খোলা যায়নি: ${e.message}")
                    }
                }
                messages.sortBy { it.date }
            } catch (e: Api.ApiException) {
                toast(e.message)
            } catch (_: Exception) {
            }
        }
    }

    fun closeConversation() {
        activeFriend?.let { P2pChatManager.close(it); if (socketConnected) emit("p2p:close", JSONObject().put("to", it)) }
        activeFriend = null
        messages.clear()
        seenIds.clear()
        loadedMedia.clear()
    }

    private suspend fun hydrateMessage(raw: ChatMessage): ChatMessage {
        val myUid = me?.uid ?: throw IllegalStateException("লগইন সেশন নেই")
        if (raw.isFile && raw.encrypted && envelopeVersion(raw.envelope) == 2) {
            // বড় ফাইল: ডিস্কে নামিয়ে টুকরো টুকরো করে ডিক্রিপ্ট (মেমরি ব্যবহার ফাইলের সাইজের ওপর নির্ভর করে না)
            val envelope = raw.envelope ?: throw IllegalStateException("ফাইলের এনক্রিপশন তথ্য নেই")
            val cipherFile = File(appContext.cacheDir, "dl_${raw.id}.enc")
            val plainFile = File(appContext.cacheDir, "e2ee_${raw.id}.dec")
            try {
                Api.downloadToFile(raw.fileUrl ?: throw IllegalStateException("ফাইল URL নেই"), cipherFile)
                val meta = withContext(Dispatchers.Default) { E2ee.decryptFileStreaming(cipherFile, plainFile, envelope, myUid) }
                val safeName = meta.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "adda_file" }
                val local = File(appContext.cacheDir, "e2ee_${raw.id}_$safeName")
                withContext(Dispatchers.IO) { local.delete(); plainFile.renameTo(local) }
                return raw.copy(fileUrl = "file://${local.absolutePath}", fileName = meta.fileName, size = local.length(), encrypted = false)
            } finally {
                cipherFile.delete()
                plainFile.delete()
            }
        }
        if (raw.isFile && raw.encrypted) {
            val envelope = raw.envelope ?: throw IllegalStateException("ফাইলের এনক্রিপশন তথ্য নেই")
            val cipherBytes = Api.downloadBytes(raw.fileUrl ?: throw IllegalStateException("ফাইল URL নেই"))
            val decoded = withContext(Dispatchers.Default) { E2ee.decryptFile(cipherBytes, envelope, myUid) }
            val safeName = decoded.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "adda_file" }
            val local = File(appContext.cacheDir, "e2ee_${raw.id}_$safeName")
            withContext(Dispatchers.IO) { local.writeBytes(decoded.bytes) }
            return raw.copy(
                fileUrl = "file://${local.absolutePath}",
                fileName = decoded.fileName,
                size = decoded.bytes.size.toLong(),
                encrypted = false
            )
        }
        if (raw.type == "text" && raw.text.startsWith("{") && raw.text.contains("\"_e2ee\"")) {
            val clear = E2ee.decryptText(raw.text, myUid)
            val payload = JSONObject(clear)
            return raw.copy(text = payload.optString("text"))
        }
        return raw
    }

    private fun envelopeVersion(envelope: String?): Int =
        try { JSONObject(envelope ?: "").optInt("_e2ee", 0) } catch (_: Exception) { 0 }

    /** চ্যাট লিস্টে যে কী-তে এই মেসেজ দেখাবে: গ্রুপ হলে গ্রুপ আইডি, নইলে অপর পক্ষ */
    private fun peerOf(m: ChatMessage, myUid: String?): String =
        if (isGroupId(m.to)) m.to else if (m.from == myUid) m.to else m.from

    private fun addMessage(m: ChatMessage) {
        val myUid = me?.uid ?: return
        val active = activeFriend ?: return
        if (m.id.isEmpty() || seenIds.contains(m.id)) return
        if (isGroupId(active)) {
            if (m.to != active) return
        } else {
            if (m.from != myUid && m.from != active) return
            if (m.to != myUid && m.to != active) return
        }
        if (m.secure && m.expiresAt > 0 && m.expiresAt <= System.currentTimeMillis()) {
            m.fileUrl?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path?.let { p -> File(p).delete() } }
            return
        }
        // The timer starts only when the recipient's Android client has decrypted and displayed it.
        val rememberedExpiry = pendingSecureExpiries.remove(m.id) ?: 0L
        val withServerExpiry = if (m.expiresAt == 0L && rememberedExpiry > 0L) m.copy(expiresAt = rememberedExpiry) else m
        val shown = if (withServerExpiry.secure && withServerExpiry.expiresAt == 0L && withServerExpiry.to == myUid) {
            withServerExpiry.copy(expiresAt = System.currentTimeMillis() + withServerExpiry.ttlSeconds.coerceIn(1, 60) * 1000L)
        } else withServerExpiry
        seenIds.add(shown.id)
        messages.add(shown)
        recordPreview(peerOf(shown, myUid), shown, shown.from == myUid)
        if (shown.secure && shown.expiresAt > 0) scheduleSecureExpiry(shown)
        if (shown.secure && shown.to == myUid && m.expiresAt == 0L) {
            socket?.emit("secure:opened", JSONObject().put("id", shown.id))
        }
    }

    private fun recordPreview(peer: String, m: ChatMessage, mine: Boolean) {
        val n = m.fileName.orEmpty().lowercase()
        val text = when {
            m.secure -> "🔒 সিকিউর মেসেজ"
            m.isImage -> "🖼️ ছবি"
            m.isFile && listOf(".m4a", ".mp3", ".aac", ".wav", ".ogg", ".opus").any { n.endsWith(it) } -> "🎙️ ভয়েস মেসেজ"
            m.isFile && listOf(".mp4", ".webm", ".3gp", ".mov", ".mkv", ".m4v").any { n.endsWith(it) } -> "🎬 ভিডিও"
            m.isFile -> "📎 ${m.fileName ?: "ফাইল"}"
            else -> m.text
        }
        val existing = lastPreview[peer]
        if (existing == null || m.date >= existing.date) lastPreview[peer] = ChatPreview(text, m.date, mine)
    }

    private fun scheduleSecureExpiry(m: ChatMessage) {
        scope.launch {
            delay((m.expiresAt - System.currentTimeMillis()).coerceAtLeast(0L))
            messages.removeAll { it.id == m.id }
            seenIds.remove(m.id)
            m.fileUrl?.takeIf { it.startsWith("file:") }?.let { uri ->
                Uri.parse(uri).path?.let { withContext(Dispatchers.IO) { File(it).delete() } }
            }
        }
    }

    private fun onChatMessage(m: ChatMessage) {
        val myUid = me?.uid ?: return
        val active = activeFriend
        val preview = when {
            m.secure -> "🔒 সিকিউর মেসেজ এসেছে"
            m.encrypted || m.text.contains("\"_e2ee\"") -> "🔐 নতুন এনক্রিপ্টেড মেসেজ"
            m.isFile && m.encrypted -> "🔐 এনক্রিপ্টেড ফাইল এসেছে"
            m.isFile -> "📎 ${m.fileName ?: "ফাইল"}"
            else -> m.text
        }
        val peer = peerOf(m, myUid)
        val title = if (isGroupId(m.to)) "${displayName(m.to)} · ${displayName(m.from)}" else displayName(m.from)
        if (active != null && (m.from == active || m.to == active)) {
            addMessage(m)
            if (m.from != myUid && !appInForeground) {
                Notifier.message(appContext, title, preview, peer.hashCode())
            }
        } else if (m.from != myUid) {
            unread[peer] = (unread[peer] ?: 0) + 1
            lastPreview[peer] = ChatPreview(preview, m.date, false)
            if (appInForeground) toast("নতুন মেসেজ এসেছে: $title")
            else Notifier.message(appContext, title, preview, peer.hashCode())
        }
    }

    private suspend fun peerE2eePublicKey(uid: String): String {
        val remote = Api.getObj("/api/e2ee/public-key/${Uri.encode(uid)}").getString("publicKey")
        val pinName = "e2ee_peer_key_$uid"
        val pinned = prefs.getString(pinName, null)
        if (pinned == null) {
            // Trust-on-first-use pin: later key substitution is rejected.
            prefs.edit().putString(pinName, remote).apply()
        } else if (pinned != remote) {
            throw SecurityException("এই বন্ধুর E2EE কী বদলে গেছে। নিরাপত্তার জন্য মেসেজ পাঠানো বন্ধ করা হয়েছে; পরিচয় যাচাই করুন।")
        }
        return remote
    }

    fun sendText(text: String, secure: Boolean = false, ttlSeconds: Int = 3, replyTo: String? = null, onDone: (success: Boolean) -> Unit) {
        val to = activeFriend ?: return
        val secureEff = secure && !isGroupId(to) // গ্রুপে Secure Me চলে না (E2EE শুধু দুজনের মধ্যে)
        val s = socket
        if (s == null || !socketConnected) {
            toast("সার্ভারের সঙ্গে সংযোগ নেই")
            onDone(false)
            return
        }
        scope.launch {
            try {
                val wireText = if (secureEff) {
                    // শুধু Secure Me চালু থাকলে এনক্রিপ্ট হবে
                    val peer = peerE2eePublicKey(to)
                    val payload = JSONObject().put("text", text).toString()
                    E2ee.encryptText(payload, me?.uid ?: throw IllegalStateException("লগইন নেই"), to, peer)
                } else text
                val data = JSONObject()
                    .put("to", to)
                    .put("text", wireText)
                    .put("secure", secureEff)
                    .put("ttlSeconds", ttlSeconds.coerceIn(1, 60))
                if (replyTo != null) data.put("replyTo", JSONObject().put("id", replyTo))
                s.emit("chat:send", data, Ack { args ->
                    val err = (args.firstOrNull() as? JSONObject)?.optString("error").orEmpty()
                    scope.launch {
                        if (err.isNotEmpty()) toast(err)
                        onDone(err.isEmpty())
                    }
                })
            } catch (e: Exception) {
                toast("এনক্রিপ্টেড মেসেজ পাঠানো যায়নি: ${e.message}")
                onDone(false)
            }
        }
    }

    /**
     * ফাইল/ছবি/ভিডিও সার্ভারের মাধ্যমে পাঠায় — রেজিউমেবল চাঙ্ক আপলোড (uploads API)।
     * - সাধারণ: নেটওয়ার্ক কেটে গেলে যেখানে থেমেছিল সেখান থেকে চলে; ফাইলের সাইজের সীমা সার্ভারের (ডিফল্ট ১ GB)।
     * - Secure Me: ফাইল ডিভাইসে টুকরো টুকরো করে AES-256-GCM-এ এনক্রিপ্ট হয়ে যায় (মেমরি-সীমা নেই);
     *   প্রাপক খুললে নির্ধারিত সময় পর দুই ফোন ও সার্ভার থেকে মুছে যায়। গ্রুপে Secure Me চলে না।
     * - সার্ভারে কোনো ফাইল ৭ দিনের বেশি থাকে না।
     */
    fun sendFile(uri: Uri, secure: Boolean = false, ttlSeconds: Int = 3, replyTo: String? = null) {
        val to = activeFriend ?: run { toast("ফাইল পাঠাতে আগে একজন বন্ধুকে নির্বাচন করুন"); return }
        val secureEff = secure && !isGroupId(to)
        if (secure && !secureEff) toast("গ্রুপে Secure Me চলে না — সাধারণভাবে পাঠানো হচ্ছে")
        if (mediaUploadProgress != null) { toast("আগের ফাইলটি আপলোড হচ্ছে"); return }
        scope.launch {
            var transcodedVideo: File? = null
            val temps = ArrayList<File>()
            try {
                mediaUploadProgress = 0f
                mediaUploadLabel = "ফাইল প্রস্তুত হচ্ছে…"
                val sender = me?.uid ?: throw IllegalStateException("লগইন নেই")
                val pickedName = withContext(Dispatchers.IO) {
                    if (uri.scheme == "file") uri.lastPathSegment.orEmpty() else {
                        appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) c.getString(idx).orEmpty() else ""
                            } else ""
                        } ?: ""
                    }
                }
                val isVideo = (appContext.contentResolver.getType(uri)?.startsWith("video/") == true) ||
                    pickedName.substringAfterLast('.', "").lowercase() in listOf("mp4", "webm", "mov", "mkv", "3gp", "m4v", "avi")
                val prepared = if (isVideo) {
                    val retriever = android.media.MediaMetadataRetriever()
                    val durationMs = try {
                        retriever.setDataSource(appContext, uri)
                        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally { retriever.release() }
                    if (durationMs <= 0L) throw IllegalArgumentException("ভিডিওর দৈর্ঘ্য পড়া যায়নি")
                    mediaUploadLabel = "ভিডিও কমপ্রেস হচ্ছে…"
                    val converted = VideoTranscoder.transcode(appContext, uri, durationMs)
                    if (converted != null) {
                        transcodedVideo = converted.file
                        withContext(Dispatchers.IO) { FileUpload.prepare(appContext, android.net.Uri.fromFile(converted.file)) }
                    } else {
                        withContext(Dispatchers.IO) { FileUpload.prepare(appContext, uri) }
                    }
                } else {
                    withContext(Dispatchers.IO) { FileUpload.prepare(appContext, uri) }
                }
                val mime = prepared.body.contentType()?.toString() ?: "application/octet-stream"
                val ttl = ttlSeconds.coerceIn(1, 60)

                // প্রস্তুত ফাইল ডিস্কে নামানো — চাঙ্ক ধরে পড়া ও আবার চেষ্টার জন্য
                val plain = File(appContext.cacheDir, "up_${java.util.UUID.randomUUID()}.bin")
                temps.add(plain)
                withContext(Dispatchers.IO) {
                    val sink = plain.sink().buffer()
                    prepared.body.writeTo(sink)
                    sink.flush()
                    sink.close()
                }

                val sentJson: JSONObject
                var localSecureCopy: File? = null
                if (secureEff) {
                    mediaUploadLabel = "এনক্রিপ্ট হচ্ছে…"
                    val peerKey = peerE2eePublicKey(to)
                    val encFile = File(appContext.cacheDir, "up_${java.util.UUID.randomUUID()}.enc")
                    temps.add(encFile)
                    val envelope = withContext(Dispatchers.Default) {
                        E2ee.encryptFileStreaming(plain, encFile, sender, to, peerKey, prepared.name, mime)
                    }
                    mediaUploadLabel = "সিকিউর ফাইল পাঠানো হচ্ছে…"
                    mediaUploadProgress = 0f
                    sentJson = ResumableUpload.upload(
                        encFile, to, "encrypted.bin", "application/octet-stream",
                        secure = true, encrypted = true, envelope = envelope, ttlSeconds = ttl, replyTo = replyTo
                    ) { mediaUploadProgress = it }
                    // প্রেরকের নিজের দিকের ডিক্রিপ্ট করা কপি (সার্ভারের chat:expire এলে মুছে যাবে)
                    val safeName = prepared.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "adda_file" }
                    val copy = File(appContext.cacheDir, "e2ee_sent_${java.util.UUID.randomUUID()}_$safeName")
                    withContext(Dispatchers.IO) { plain.copyTo(copy, overwrite = true) }
                    localSecureCopy = copy
                } else {
                    mediaUploadLabel = "ফাইল পাঠানো হচ্ছে…"
                    mediaUploadProgress = 0f
                    sentJson = ResumableUpload.upload(
                        plain, to, prepared.name, mime,
                        secure = false, encrypted = false, envelope = null, ttlSeconds = ttl, replyTo = replyTo
                    ) { mediaUploadProgress = it }
                }
                val sent = parseMessage(sentJson)
                val secureCopy: File? = localSecureCopy
                val shown = if (secureEff && secureCopy != null) {
                    sent.copy(fileUrl = Uri.fromFile(secureCopy).toString(), fileName = prepared.name, size = secureCopy.length(), encrypted = false, envelope = null)
                } else sent
                addMessage(shown)
                mediaUploadProgress = 1f
                toast(if (secureEff) "সিকিউর ফাইল পাঠানো হয়েছে" else "ফাইল পাঠানো হয়েছে")
            } catch (e: Api.ApiException) { toast(e.message) }
            catch (e: IllegalArgumentException) { toast(e.message) }
            catch (e: SecurityException) { toast(e.message) }
            catch (e: Exception) { toast("ফাইল পাঠানো যায়নি: ${e.message}") }
            catch (e: OutOfMemoryError) { toast("মেমরি কম পড়েছে, ছোট ফাইল পাঠান") }
            catch (e: Throwable) { toast("ফাইল পাঠানো যায়নি: ${e.javaClass.simpleName}") }
            finally {
                mediaUploadProgress = null
                mediaUploadLabel = ""
                temps.forEach { it.delete() }
                // শুধু কমপ্রেস করা কপি মোছা হয়; ব্যবহারকারীর আসল ভিডিও অক্ষত থাকে
                transcodedVideo?.delete()
            }
        }
    }

    // ---------- রিঅ্যাকশন / এডিট / পিন / ফরওয়ার্ড / টাইপিং / ড্রাফট / অদৃশ্য মেসেজ ----------

    /** চ্যাট আইডি → অসমাপ্ত লেখা (শুধু মেমরিতে; চ্যাট বদলালেও থাকে) */
    val drafts = HashMap<String, String>()
    /** চ্যাট আইডি → এখন যে টাইপ করছে তার নাম */
    val typing = mutableStateMapOf<String, String>()
    /** চ্যাট আইডি → অদৃশ্য মেসেজের সেকেন্ড (০ = বন্ধ) */
    val disappear = mutableStateMapOf<String, Int>()
    private var lastTypingSent = 0L
    private val typingJobs = HashMap<String, kotlinx.coroutines.Job>()

    fun notifyTyping(to: String, isTyping: Boolean = true) {
        if (!socketConnected) return
        val now = System.currentTimeMillis()
        if (isTyping && now - lastTypingSent < 3000) return
        lastTypingSent = if (isTyping) now else 0L
        emit("chat:typing", JSONObject().put("to", to).put("typing", isTyping))
    }

    private fun ackError(args: Array<Any>): String = (args.firstOrNull() as? JSONObject)?.optString("error").orEmpty()

    /** emoji খালি হলে রিঅ্যাকশন সরে যায় */
    fun react(id: String, emoji: String) {
        val s = socket ?: return
        s.emit("chat:react", JSONObject().put("id", id).put("emoji", emoji), Ack { args ->
            val err = ackError(args)
            if (err.isNotEmpty()) scope.launch { toast(err) }
        })
    }

    fun editMessage(id: String, text: String, onDone: (Boolean) -> Unit) {
        val s = socket
        if (s == null || !socketConnected) { toast("সার্ভারের সঙ্গে সংযোগ নেই"); onDone(false); return }
        s.emit("chat:edit", JSONObject().put("id", id).put("text", text), Ack { args ->
            val err = ackError(args)
            scope.launch { if (err.isNotEmpty()) toast(err); onDone(err.isEmpty()) }
        })
    }

    fun pinMessage(id: String, pinned: Boolean) {
        val s = socket ?: return
        s.emit("chat:pin", JSONObject().put("id", id).put("pinned", pinned), Ack { args ->
            val err = ackError(args)
            if (err.isNotEmpty()) scope.launch { toast(err) }
        })
    }

    fun forwardMessage(id: String, to: String, onDone: (Boolean) -> Unit) {
        scope.launch {
            try {
                Api.postObj("/api/messages/forward", JSONObject().put("id", id).put("to", to))
                toast("ফরওয়ার্ড করা হয়েছে")
                onDone(true)
            } catch (e: Api.ApiException) { toast(e.message); onDone(false) }
        }
    }

    fun loadChatSettings(chat: String) {
        scope.launch {
            try { disappear[chat] = Api.getObj("/api/chats/${Uri.encode(chat)}/settings").optInt("disappearSeconds", 0) }
            catch (_: Exception) { }
        }
    }

    fun setDisappear(chat: String, seconds: Int) {
        scope.launch {
            try {
                Api.postObj("/api/chats/${Uri.encode(chat)}/settings", JSONObject().put("disappearSeconds", seconds))
                disappear[chat] = seconds
                toast(if (seconds == 0) "অদৃশ্য মেসেজ বন্ধ" else "এখন থেকে নতুন মেসেজ ${disappearLabel(seconds)} পর মুছে যাবে")
            } catch (e: Api.ApiException) { toast(e.message) }
        }
    }

    fun disappearLabel(seconds: Int): String = when (seconds) {
        3600 -> "১ ঘণ্টা"; 86400 -> "১ দিন"; 604800 -> "৭ দিন"; else -> "বন্ধ"
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
            (args.firstOrNull() as? JSONObject)?.let { o -> scope.launch {
                val raw = parseMessage(o)
                try {
                    val active = activeFriend
                    val relevant = active != null && (raw.from == active || raw.to == active)
                    onChatMessage(if (relevant) hydrateMessage(raw) else raw)
                } catch (e: Exception) {
                    toast("এনক্রিপ্টেড মেসেজ খোলা যায়নি: ${e.message}")
                }
            } }
        }
        s.on("chat:deleted") { args ->
            val id = (args.firstOrNull() as? JSONObject)?.optString("id").orEmpty()
            scope.launch {
                messages.firstOrNull { it.id == id }?.fileUrl?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path?.let { p -> withContext(Dispatchers.IO) { File(p).delete() } } }
                messages.removeAll { it.id == id }
            }
        }
        s.on("chat:expire") { args ->
            val o = args.firstOrNull() as? JSONObject
            val id = o?.optString("id").orEmpty()
            val remainingMs = o?.optLong("remainingMs", 0L) ?: 0L
            // সার্ভারের দেওয়া অবশিষ্ট সময় ধরে ফোনের ঘড়ির পার্থক্য এড়ানো হয়
            val expiresAt = if (remainingMs > 0L) System.currentTimeMillis() + remainingMs else (o?.optLong("expiresAt", 0L) ?: 0L)
            scope.launch {
                val index = messages.indexOfFirst { it.id == id }
                if (expiresAt > 0L) {
                    if (index >= 0) {
                        val updated = messages[index].copy(expiresAt = expiresAt)
                        messages[index] = updated
                        scheduleSecureExpiry(updated)
                    } else {
                        pendingSecureExpiries[id] = expiresAt
                    }
                }
            }
        }
        s.on("chat:reactions") { args ->
            val o = args.firstOrNull() as? JSONObject
            if (o != null) {
                val id = o.optString("id"); val r = parseReactions(o.optJSONObject("reactions"))
                scope.launch { val i = messages.indexOfFirst { it.id == id }; if (i >= 0) messages[i] = messages[i].copy(reactions = r) }
            }
        }
        s.on("chat:edited") { args ->
            val o = args.firstOrNull() as? JSONObject
            if (o != null) {
                val id = o.optString("id"); val t = o.optString("text")
                scope.launch { val i = messages.indexOfFirst { it.id == id }; if (i >= 0) messages[i] = messages[i].copy(text = t, editedAt = System.currentTimeMillis()) }
            }
        }
        s.on("chat:pinned") { args ->
            val o = args.firstOrNull() as? JSONObject
            if (o != null) {
                val id = o.optString("id"); val p = o.optBoolean("pinned", false)
                scope.launch { val i = messages.indexOfFirst { it.id == id }; if (i >= 0) messages[i] = messages[i].copy(pinned = p) }
            }
        }
        s.on("chat:typing") { args ->
            val o = args.firstOrNull() as? JSONObject
            if (o != null) {
                val from = o.optString("from"); val chat = o.optString("to")
                val key = if (isGroupId(chat)) chat else from
                val isTyping = o.optBoolean("typing", false)
                scope.launch {
                    typingJobs.remove(key)?.cancel()
                    if (isTyping) {
                        typing[key] = displayName(from)
                        typingJobs[key] = scope.launch { delay(5000); typing.remove(key) }
                    } else typing.remove(key)
                }
            }
        }
        s.on("chat:settings") { args ->
            val o = args.firstOrNull() as? JSONObject
            if (o != null) {
                val chat = o.optString("chat"); val secs = o.optInt("disappearSeconds", 0)
                scope.launch { disappear[chat] = secs }
            }
        }
        s.on("group:update") { args ->
            val o = args.firstOrNull() as? JSONObject
            val gid = o?.optString("id").orEmpty()
            val action = o?.optString("action").orEmpty()
            scope.launch {
                if ((action == "left" || action == "removed") && o?.optString("uid") == me?.uid && activeFriend == gid) closeConversation()
                Social.refreshGroups()
            }
        }
        s.on("call:missed") { args ->
            val o = args.firstOrNull() as? JSONObject
            val from = o?.optString("from").orEmpty()
            val video = o?.optString("kind") == "video"
            scope.launch {
                Social.refreshCalls()
                if (appInForeground) toast("মিসড ${if (video) "ভিডিও" else "অডিও"} কল: ${displayName(from)}")
                else Notifier.missedCall(appContext, displayName(from), video)
            }
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
        s.on("chat:status") { args ->
            val o = args.firstOrNull() as? JSONObject
            val arr = o?.optJSONArray("ids")
            val st = o?.optInt("status", 0) ?: 0
            if (arr != null && st > 0) {
                val ids = HashSet<String>()
                for (i in 0 until arr.length()) ids.add(arr.optString(i))
                scope.launch { applyStatus(ids, st) }
            }
        }
        s.on("friend:removed") { args ->
            val uid = (args.firstOrNull() as? JSONObject)?.optString("uid").orEmpty()
            if (uid.isNotEmpty()) scope.launch { afterFriendshipRemoved(uid) }
        }
        s.on("friend:rejected") {
            scope.launch { toast("আপনার ফ্রেন্ড রিকুয়েস্ট গ্রহণ করা হয়নি") }
        }
        s.on("friend:qr-added") { args ->
            val o = args.firstOrNull() as? JSONObject
            scope.launch {
                refreshPeople()
                toast("${o?.optString("username")?.ifEmpty { null } ?: "নতুন বন্ধু"} আপনাকে QR দিয়ে বন্ধু করেছেন")
            }
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
        for (ev in listOf("offer", "answer", "candidate", "close")) {
            s.on("p2p:$ev") { args ->
                (args.firstOrNull() as? JSONObject)?.let { o -> scope.launch { P2pChatManager.onSignal(ev, o) } }
            }
        }
        s.connect()
    }
}
