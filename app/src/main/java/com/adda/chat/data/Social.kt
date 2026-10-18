package com.adda.chat.data

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import com.adda.chat.util.FileUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import org.json.JSONArray
import org.json.JSONObject

/** গ্রুপ, স্টোরি ও কল হিস্ট্রির অবস্থা ও সার্ভার-কল */
object Social {
    data class GroupMember(val uid: String, val username: String, val photo: String?)
    data class Group(val id: String, val name: String, val owner: String, val admins: List<String>, val members: List<GroupMember>)
    data class StoryItem(
        val id: String, val kind: String, val text: String, val bg: String, val fileUrl: String?,
        val createdAt: Long, val viewed: Boolean, val viewCount: Int
    )
    data class StoryUser(val uid: String, val username: String, val photo: String?, val items: List<StoryItem>) {
        val allViewed: Boolean get() = items.all { it.viewed }
    }
    data class CallLog(
        val id: String, val incoming: Boolean, val kind: String, val status: String,
        val at: Long, val durationSec: Int, val uid: String, val name: String, val photo: String?
    )

    val groups = mutableStateListOf<Group>()
    val stories = mutableStateListOf<StoryUser>()
    val calls = mutableStateListOf<CallLog>()

    fun reset() { groups.clear(); stories.clear(); calls.clear() }

    private fun photoOf(o: JSONObject): String? =
        o.optString("profilePhoto").takeIf { it.isNotBlank() && it != "null" }

    fun groupById(id: String?): Group? = groups.firstOrNull { it.id == id }

    /** সবকিছু একসাথে রিফ্রেশ (লগইনের পর) */
    suspend fun refreshAll() {
        refreshGroups(); refreshStories(); refreshCalls()
    }

    // ---------------- গ্রুপ ----------------

    private fun parseGroup(o: JSONObject): Group {
        val arr = o.optJSONArray("members") ?: JSONArray()
        val members = (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            GroupMember(m.optString("uid"), m.optString("username").ifEmpty { m.optString("uid") }, photoOf(m))
        }
        val adminsArr = o.optJSONArray("admins") ?: JSONArray()
        return Group(
            id = o.optString("id"), name = o.optString("name"), owner = o.optString("owner"),
            admins = (0 until adminsArr.length()).map { adminsArr.optString(it) }, members = members
        )
    }

    suspend fun refreshGroups() {
        try {
            val arr = Api.getArr("/api/groups")
            val list = (0 until arr.length()).map { parseGroup(arr.getJSONObject(it)) }
            groups.clear(); groups.addAll(list)
            for (g in list) {
                Session.names[g.id] = g.name
                for (m in g.members) {
                    if (Session.names[m.uid] == null) Session.names[m.uid] = m.username
                    if (m.photo != null && Session.profilePhotos[m.uid] == null) Session.profilePhotos[m.uid] = m.photo
                }
            }
        } catch (_: Exception) { }
    }

    fun createGroup(name: String, members: List<String>, onDone: (String?) -> Unit) {
        Session.scope.launch {
            try {
                val body = JSONObject().put("name", name).put("members", JSONArray(members))
                val g = parseGroup(Api.postObj("/api/groups", body))
                refreshGroups()
                onDone(g.id)
            } catch (e: Api.ApiException) { Session.toast(e.message); onDone(null) }
        }
    }

    fun addMembers(gid: String, uids: List<String>) {
        Session.scope.launch {
            try {
                Api.postObj("/api/groups/${Uri.encode(gid)}/members", JSONObject().put("add", JSONArray(uids)))
                refreshGroups()
            } catch (e: Api.ApiException) { Session.toast(e.message) }
        }
    }

    fun removeMember(gid: String, uid: String) {
        Session.scope.launch {
            try {
                Api.postObj("/api/groups/${Uri.encode(gid)}/remove", JSONObject().put("uid", uid))
                refreshGroups()
            } catch (e: Api.ApiException) { Session.toast(e.message) }
        }
    }

    fun leaveGroup(gid: String) {
        Session.scope.launch {
            try {
                Api.postObj("/api/groups/${Uri.encode(gid)}/remove", JSONObject())
                if (Session.activeFriend == gid) Session.closeConversation()
                Session.lastPreview.remove(gid); Session.unread.remove(gid)
                refreshGroups()
            } catch (e: Api.ApiException) { Session.toast(e.message) }
        }
    }

    fun renameGroup(gid: String, name: String) {
        Session.scope.launch {
            try {
                Api.postObj("/api/groups/${Uri.encode(gid)}/rename", JSONObject().put("name", name))
                refreshGroups()
            } catch (e: Api.ApiException) { Session.toast(e.message) }
        }
    }

    // ---------------- স্টোরি ----------------

    suspend fun refreshStories() {
        try {
            val arr = Api.getArr("/api/stories")
            val list = (0 until arr.length()).map { i ->
                val u = arr.getJSONObject(i)
                val itemsArr = u.optJSONArray("items") ?: JSONArray()
                StoryUser(
                    uid = u.optString("uid"), username = u.optString("username").ifEmpty { u.optString("uid") }, photo = photoOf(u),
                    items = (0 until itemsArr.length()).map { j ->
                        val it = itemsArr.getJSONObject(j)
                        StoryItem(
                            id = it.optString("id"), kind = it.optString("kind", "text"), text = it.optString("text"),
                            bg = it.optString("bg", "#6654F1"), fileUrl = it.optString("fileUrl").takeIf { f -> f.isNotBlank() && f != "null" },
                            createdAt = it.optLong("createdAt"), viewed = it.optBoolean("viewed", false), viewCount = it.optInt("viewCount", 0)
                        )
                    }
                )
            }
            stories.clear(); stories.addAll(list)
        } catch (_: Exception) { }
    }

    fun postStoryText(text: String, bg: String, onDone: (Boolean) -> Unit) {
        Session.scope.launch {
            try {
                val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("text", text).addFormDataPart("bg", bg).build()
                Api.request("POST", "/api/stories", body = form)
                refreshStories(); Session.toast("স্টোরি দেওয়া হয়েছে"); onDone(true)
            } catch (e: Api.ApiException) { Session.toast(e.message); onDone(false) }
        }
    }

    fun postStoryImage(uri: Uri, caption: String, onDone: (Boolean) -> Unit) {
        Session.scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) { FileUpload.prepare(Session.appContext, uri) }
                val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("text", caption)
                    .addFormDataPart("file", prepared.name, prepared.body).build()
                Api.request("POST", "/api/stories", body = form)
                refreshStories(); Session.toast("স্টোরি দেওয়া হয়েছে"); onDone(true)
            } catch (e: Api.ApiException) { Session.toast(e.message); onDone(false) }
            catch (e: Exception) { Session.toast("স্টোরি দেওয়া যায়নি: ${e.message}"); onDone(false) }
        }
    }

    fun markStoryViewed(id: String) {
        Session.scope.launch { try { Api.request("POST", "/api/stories/${Uri.encode(id)}/view") } catch (_: Exception) { } }
    }

    fun deleteStory(id: String) {
        Session.scope.launch {
            try { Api.request("DELETE", "/api/stories/${Uri.encode(id)}"); refreshStories() }
            catch (e: Api.ApiException) { Session.toast(e.message) }
        }
    }

    // ---------------- কল হিস্ট্রি ----------------

    suspend fun refreshCalls() {
        try {
            val arr = Api.getArr("/api/calls")
            val list = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                CallLog(
                    id = c.optString("id"), incoming = c.optString("direction") == "in", kind = c.optString("kind", "audio"),
                    status = c.optString("status"), at = parseIsoMs(c.optString("startedAt")), durationSec = c.optInt("durationSec", 0),
                    uid = c.optString("uid"), name = c.optString("username").ifEmpty { c.optString("uid") }, photo = photoOf(c)
                )
            }
            calls.clear(); calls.addAll(list)
        } catch (_: Exception) { }
    }

    private fun parseIsoMs(s: String): Long = try {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        f.parse(s)?.time ?: 0L
    } catch (_: Exception) { 0L }
}
