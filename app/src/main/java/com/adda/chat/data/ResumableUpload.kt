package com.adda.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * রেজিউমেবল (চাঙ্ক) আপলোড: ফাইল ১ MiB করে টুকরো টুকরো পাঠানো হয়।
 * নেটওয়ার্ক কেটে গেলে সার্ভারকে জিজ্ঞেস করা হয় কতটুকু পৌঁছেছে, তারপর সেখান থেকেই আবার শুরু —
 * শুরু থেকে নয়। ধারাবাহিকভাবে ব্যর্থ হতে থাকলে কিছুক্ষণ অপেক্ষা করে সীমিতবার চেষ্টা করে।
 */
object ResumableUpload {
    private const val MAX_FAILS_IN_ROW = 8
    private val OCTET = "application/octet-stream".toMediaType()

    suspend fun upload(
        source: File,
        to: String,
        name: String,
        mime: String,
        secure: Boolean,
        encrypted: Boolean,
        envelope: String?,
        ttlSeconds: Int,
        replyTo: String?,
        onProgress: (Float) -> Unit
    ): JSONObject {
        val size = source.length()
        require(size > 0) { "ফাইলটি খালি" }

        val init = JSONObject()
            .put("to", to).put("name", name).put("mime", mime).put("size", size)
            .put("secure", secure).put("encrypted", encrypted).put("ttlSeconds", ttlSeconds)
        if (encrypted && envelope != null) init.put("envelope", envelope)
        if (replyTo != null) init.put("replyTo", replyTo)

        val started = Api.postObj("/api/uploads/init", init)
        val uploadId = started.getString("uploadId")
        val chunkSize = started.optInt("chunkSize", 1024 * 1024).coerceIn(64 * 1024, 4 * 1024 * 1024)
        var offset = started.optLong("received", 0L)
        var fails = 0

        try {
            RandomAccessFile(source, "r").use { raf ->
                while (offset < size) {
                    val len = minOf(chunkSize.toLong(), size - offset).toInt()
                    val buf = ByteArray(len)
                    withContext(Dispatchers.IO) { raf.seek(offset); raf.readFully(buf) }
                    try {
                        val resp = JSONObject(
                            Api.request("PUT", "/api/uploads/$uploadId?offset=$offset", body = buf.toRequestBody(OCTET))
                        )
                        offset = resp.optLong("received", offset + len)
                        fails = 0
                        onProgress((offset.toFloat() / size).coerceIn(0f, 1f))
                    } catch (e: Api.ApiException) {
                        // ৪xx (৪০৯ বাদে) মানে অনুরোধই ভুল — আবার চেষ্টা করে লাভ নেই
                        if (e.status in 400..499 && e.status != 409 && e.status != 408 && e.status != 429) throw e
                        fails++
                        if (fails > MAX_FAILS_IN_ROW) throw Api.ApiException("নেটওয়ার্ক সমস্যা — আপলোড সম্পূর্ণ হয়নি। পরে আবার চেষ্টা করুন", 0)
                        delay(minOf(1000L shl minOf(fails, 4), 15_000L))
                        // সার্ভার কতটুকু পেয়েছে জেনে সেখান থেকে চালিয়ে যাওয়া
                        try {
                            offset = Api.getObj("/api/uploads/$uploadId").optLong("received", offset)
                            onProgress((offset.toFloat() / size).coerceIn(0f, 1f))
                        } catch (_: Api.ApiException) { /* পরের লুপে আবার চেষ্টা */ }
                    }
                }
            }
            return Api.postObj("/api/uploads/$uploadId/complete", JSONObject())
        } catch (e: Exception) {
            // সেশন সার্ভারে থাকে (২৪ ঘণ্টা); স্থায়ী ব্যর্থতায় নিজে থেকে মুছে ফেলার চেষ্টা
            try { Api.request("DELETE", "/api/uploads/$uploadId") } catch (_: Exception) { }
            throw e
        }
    }
}
