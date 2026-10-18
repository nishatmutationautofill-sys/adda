package com.adda.chat.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class Me(
    val uid: String,
    val username: String,
    val friends: List<String>,
    val requests: List<String>,
    val discoverable: Boolean = false,
    val profilePhoto: String? = null,
    /** আমি যাদের ব্লক করেছি */
    val blocked: List<String> = emptyList(),
    /** সক্রিয়তা ব্যাচ: স্তর (০-৪), ৩০ দিনের স্কোর, পরের স্তরের স্কোর (০ = সর্বোচ্চ স্তর) */
    val badgeLevel: Int = 0,
    val badgeScore: Int = 0,
    val badgeNext: Int = 0
)

data class DiscoverableUser(val uid: String, val username: String, val profilePhoto: String?)

data class ChatMessage(
    val id: String,
    val from: String,
    val to: String,
    val type: String,
    val text: String,
    val fileUrl: String?,
    val fileName: String?,
    val size: Long,
    val date: Long,
    val encrypted: Boolean = false,
    val envelope: String? = null,
    val secure: Boolean = false,
    val ttlSeconds: Int = 0,
    val expiresAt: Long = 0L,
    /** চ্যাট খোলার সময় সার্ভার থেকে আসা আগের মেসেজ (লাইভ নয়) — ছবি/ভিডিও ব্লার থাকবে, ট্যাপে লোড হবে */
    val history: Boolean = false,
    /** রিপ্লাই: যে মেসেজের উত্তর দেওয়া হয়েছে তার আইডি, প্রেরক ও (সার্ভার-বানানো) স্নিপেট */
    val replyToId: String? = null,
    val replyText: String = "",
    val replyFrom: String = "",
    val replyKind: String = "",
    /** ০ পাঠানো, ১ পৌঁছেছে, ২ দেখেছে */
    val status: Int = 0,
    /** রিঅ্যাকশন: ইউজার আইডি → ইমোজি */
    val reactions: Map<String, String> = emptyMap(),
    /** এডিট করা হলে এডিটের সময় (না হলে ০) */
    val editedAt: Long = 0L,
    val pinned: Boolean = false,
    val forwarded: Boolean = false
) {
    val isFile: Boolean get() = type == "file" && !fileUrl.isNullOrEmpty()
    val isImage: Boolean
        get() = isFile && IMAGE_REGEX.containsMatchIn(fileName?.takeIf { it.isNotEmpty() } ?: fileUrl.orEmpty())

    val isVideo: Boolean
        get() = isFile && VIDEO_REGEX.containsMatchIn(fileName?.takeIf { it.isNotEmpty() } ?: fileUrl.orEmpty())
    val isVisualMedia: Boolean get() = isImage || isVideo

    companion object {
        private val IMAGE_REGEX = Regex("\\.(jpe?g|png|gif|webp|bmp|avif)$", RegexOption.IGNORE_CASE)
        private val VIDEO_REGEX = Regex("\\.(mp4|webm|3gp|mov|mkv|m4v)$", RegexOption.IGNORE_CASE)
    }
}

data class ChatPreview(val text: String, val date: Long, val mine: Boolean)

data class ToastMsg(val id: Int, val text: String)

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val item = opt(i)
        when (item) {
            is String -> out.add(item)
            is JSONObject -> item.optString("uid").takeIf { it.isNotEmpty() }?.let { out.add(it) }
        }
    }
    return out
}

private fun JSONObject.strOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)

fun parseMe(o: JSONObject): Me = Me(
    uid = o.optString("uid"),
    username = o.optString("username"),
    friends = o.optJSONArray("friends").toStringList(),
    requests = o.optJSONArray("friendRequests").toStringList(),
    discoverable = o.optBoolean("discoverable", false),
    profilePhoto = if (o.isNull("profilePhoto")) null else o.optString("profilePhoto").takeIf { it.isNotBlank() },
    blocked = o.optJSONArray("blocked").toStringList(),
    badgeLevel = o.optJSONObject("badge")?.optInt("level", 0) ?: 0,
    badgeScore = o.optJSONObject("badge")?.optInt("score", 0) ?: 0,
    badgeNext = o.optJSONObject("badge")?.optInt("next", 0) ?: 0
)

fun parseMessage(o: JSONObject): ChatMessage = ChatMessage(
    id = o.optString("id"),
    from = o.optString("from"),
    to = o.optString("to"),
    type = o.strOrNull("type") ?: "text",
    text = o.strOrNull("text").orEmpty(),
    fileUrl = o.strOrNull("fileUrl"),
    fileName = o.strOrNull("fileName"),
    size = o.optLong("size", 0L),
    date = parseDate(o.opt("date")),
    encrypted = o.optBoolean("encrypted", false),
    envelope = if (o.isNull("envelope")) null else o.optString("envelope").takeIf { it.isNotBlank() && it != "null" },
    secure = o.optBoolean("secure", false),
    ttlSeconds = o.optInt("ttlSeconds", 0),
    expiresAt = if (o.isNull("expiresAt")) 0L else parseDate(o.opt("expiresAt")),
    replyToId = o.optJSONObject("replyTo")?.optString("id")?.takeIf { it.isNotEmpty() },
    replyText = o.optJSONObject("replyTo")?.optString("text").orEmpty(),
    replyFrom = o.optJSONObject("replyTo")?.optString("from").orEmpty(),
    replyKind = o.optJSONObject("replyTo")?.optString("kind").orEmpty(),
    status = when {
        o.has("seenAt") && !o.isNull("seenAt") -> 2
        o.has("deliveredAt") && !o.isNull("deliveredAt") -> 1
        else -> 0
    },
    reactions = parseReactions(o.optJSONObject("reactions")),
    editedAt = if (o.isNull("editedAt") || !o.has("editedAt")) 0L else parseDate(o.opt("editedAt")),
    pinned = o.optBoolean("pinned", false),
    forwarded = o.optBoolean("forwarded", false)
)

/** সার্ভারের গ্রুপ আইডি G-XXXXXXXX আকারের */
private val GROUP_ID_REGEX = Regex("^G-[0-9A-F]{8}$")
fun isGroupId(id: String?): Boolean = id != null && GROUP_ID_REGEX.matches(id)

fun parseReactions(o: JSONObject?): Map<String, String> {
    if (o == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    val it = o.keys()
    while (it.hasNext()) { val k = it.next(); val v = o.optString(k); if (v.isNotBlank()) out[k] = v }
    return out
}

private fun parseDate(v: Any?): Long {
    return when (v) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: parseIso(v)
        else -> System.currentTimeMillis()
    }
}

private fun parseIso(s: String): Long {
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")) {
        try {
            val f = SimpleDateFormat(pattern, Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            return f.parse(s)?.time ?: continue
        } catch (_: Exception) {
        }
    }
    return System.currentTimeMillis()
}
