package com.adda.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object Api {
    // ওয়েব ভার্সনের Firebase কনফিগ — সার্ভারের লিংক Firestore এর appConfig/runtime ডকুমেন্টে থাকে
    private const val FIREBASE_PROJECT = "ghostmessenger-1bcaf"
    private const val FIREBASE_API_KEY = "AIzaSyCf07K2cPgDRDqvp-XQivIAC1KWc7674Eg"
    private val URL_FIELDS = listOf("serverUrl", "serverURL", "url", "link", "publicUrl")

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile var serverUrl: String = ""
    @Volatile var token: String = ""

    class ApiException(message: String, val status: Int) : Exception(message)

    private val JSON = "application/json".toMediaType()

    fun normalizeUrl(raw: String?): String {
        var u = raw?.trim().orEmpty()
        if (u.isEmpty()) return ""
        if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(u)) u = "https://$u"
        return u.trimEnd('/')
    }

    fun isNgrok(url: String) = url.contains("ngrok", ignoreCase = true)

    /** সম্পূর্ণ ফাইল URL (সার্ভার-রিলেটিভ হলে সার্ভারের ঠিকানা জুড়ে দেয়) */
    fun absoluteUrl(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file://")) path else serverUrl + path

    private fun Request.Builder.withCommonHeaders(): Request.Builder {
        if (token.isNotEmpty()) header("Authorization", "Bearer $token")
        if (isNgrok(serverUrl)) header("ngrok-skip-browser-warning", "true")
        return this
    }

    suspend fun request(
        method: String,
        endpoint: String,
        json: JSONObject? = null,
        body: RequestBody? = null
    ): String = withContext(Dispatchers.IO) {
        if (serverUrl.isEmpty()) throw ApiException("সার্ভার লিংক এখনো পাওয়া যায়নি", 0)
        val requestBody: RequestBody? = when {
            body != null -> body
            json != null -> json.toString().toRequestBody(JSON)
            method == "POST" -> "{}".toRequestBody(JSON)
            else -> null
        }
        try {
            val req = Request.Builder().url(serverUrl + endpoint).withCommonHeaders()
                .method(method, requestBody).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = try { JSONObject(text).optString("error") } catch (_: Exception) { "" }
                    throw ApiException(msg.ifEmpty { "অনুরোধ ব্যর্থ (${resp.code})" }, resp.code)
                }
                text
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("সার্ভারে পৌঁছানো যাচ্ছে না — সার্ভার চালু আছে কিনা দেখুন", 0)
        } catch (e: IllegalArgumentException) {
            throw ApiException("সার্ভারের লিংকটি সঠিক নয়: $serverUrl", 0)
        }
    }


    suspend fun downloadBytes(pathOrUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val url = if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) pathOrUrl else serverUrl + pathOrUrl
        val builder = Request.Builder().url(url).withCommonHeaders().get()
        try {
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw ApiException("ফাইল ডাউনলোড ব্যর্থ (${resp.code})", resp.code)
                resp.body?.bytes() ?: throw ApiException("ফাইল খালি", 0)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("ফাইল ডাউনলোড করা যায়নি", 0)
        }
    }

    suspend fun getObj(endpoint: String) = JSONObject(request("GET", endpoint))
    suspend fun getArr(endpoint: String) = JSONArray(request("GET", endpoint))
    suspend fun postObj(endpoint: String, json: JSONObject) = JSONObject(request("POST", endpoint, json = json))

    /** Firestore appConfig/runtime document থেকে APK download URL আনে. */
    suspend fun fetchApkUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT/databases/(default)/documents/appConfig/runtime?key=$FIREBASE_API_KEY"
            client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val fields = JSONObject(r.body?.string().orEmpty()).optJSONObject("fields") ?: return@use null
                val value = fields.optJSONObject("apkUrl")?.optString("stringValue").orEmpty().trim()
                if (value.startsWith("https://")) value else null
            }
        } catch (_: Exception) { null }
    }

    /** Firestore REST দিয়ে সার্ভারের বর্তমান লিংক আনে; না পেলে null */
    suspend fun fetchServerUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT/databases/(default)/documents/appConfig/runtime?key=$FIREBASE_API_KEY"
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val fields = JSONObject(r.body?.string().orEmpty()).optJSONObject("fields") ?: return@use null
                for (k in URL_FIELDS) {
                    val v = fields.optJSONObject(k)?.optString("stringValue")
                    val n = normalizeUrl(v)
                    if (n.isNotEmpty()) return@use n
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
