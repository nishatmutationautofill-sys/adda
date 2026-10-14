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
    val requests: List<String>
)

data class ChatMessage(
    val id: String,
    val from: String,
    val to: String,
    val type: String,
    val text: String,
    val fileUrl: String?,
    val fileName: String?,
    val size: Long,
    val date: Long
) {
    val isFile: Boolean get() = type == "file" && !fileUrl.isNullOrEmpty()
    val isImage: Boolean
        get() = isFile && IMAGE_REGEX.containsMatchIn(fileName?.takeIf { it.isNotEmpty() } ?: fileUrl.orEmpty())

    companion object {
        private val IMAGE_REGEX = Regex("\\.(jpe?g|png|gif|webp|bmp|avif)$", RegexOption.IGNORE_CASE)
    }
}

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
    requests = o.optJSONArray("friendRequests").toStringList()
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
    date = parseDate(o.opt("date"))
)

private fun parseDate(v: Any?): Long {
    return when (v) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: parseIso(v)
        else -> System.currentTimeMillis()
    }
}

private fun parseIso(s: String): Long {
    for (pattern in listOf("yyyy-MM-ddTHH:mm:ss.SSSXXX", "yyyy-MM-ddTHH:mm:ssXXX")) {
        try {
            val f = SimpleDateFormat(pattern, Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            return f.parse(s)?.time ?: continue
        } catch (_: Exception) {
        }
    }
    return System.currentTimeMillis()
}
