package com.adda.chat.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adda.chat.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Reads apkUrl from Firestore appConfig/runtime and renders its QR code. */
@Composable
fun DownloadAppScreen() {
    var downloadUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var refreshedAtLeastOnce by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun makeQr(value: String) {
        qrBitmap = null
        error = ""
        val url = value.trim()
        if (url.isBlank()) {
            error = "Firestore-এর appConfig/runtime ডকুমেন্টে apkUrl ফিল্ডে APK লিংক দিন।"
            return
        }
        if (!url.startsWith("https://")) {
            error = "নিরাপদ ডাউনলোডের জন্য HTTPS লিংক ব্যবহার করুন।"
            return
        }
        try {
            val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 640, 640)
            val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
            for (x in 0 until 640) for (y in 0 until 640) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
            qrBitmap = bitmap
        } catch (_: Exception) {
            error = "এই লিংকের QR কোড তৈরি করা যায়নি।"
        }
    }

    suspend fun loadFirebaseUrl() {
        loading = true
        error = ""
        val value = withContext(Dispatchers.IO) { Api.fetchApkUrl() }.orEmpty()
        loading = false
        refreshedAtLeastOnce = true
        downloadUrl = value
        if (value.isNotBlank()) {
            makeQr(value)
        } else {
            qrBitmap = null
            error = "Firestore থেকে apkUrl পাওয়া যায়নি। appConfig/runtime ডকুমেন্টে apkUrl ফিল্ড ও ইন্টারনেট পরীক্ষা করুন।"
        }
    }

    LaunchedEffect(Unit) { loadFirebaseUrl() }

    Column(
        Modifier.fillMaxSize().background(Canvas).statusBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Download, null, tint = Color(0xFF6957D5), modifier = Modifier.size(42.dp))
        Text("Adda অ্যাপ ডাউনলোড", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(top = 8.dp))
        Text("অন্য ডিভাইসের ক্যামেরা দিয়ে QR কোড স্ক্যান করে অ্যাপ ডাউনলোড করুন", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)

        Card(Modifier.fillMaxWidth().padding(top = 20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.QrCode2, null, tint = Color(0xFF6957D5), modifier = Modifier.size(28.dp))
                Text("Firestore থেকে ডাউনলোড লিংক", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 8.dp))
                when {
                    loading -> CircularProgressIndicator(Modifier.padding(top = 18.dp))
                    qrBitmap != null -> {
                        Image(qrBitmap!!.asImageBitmap(), contentDescription = "Adda app download QR code", modifier = Modifier.size(240.dp).padding(top = 18.dp))
                        Text("QR স্ক্যান করলে APK-এর পাবলিক HTTPS লিংক খুলবে", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(downloadUrl, color = Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    else -> Text("Firestore-এ সঠিক ডাউনলোড URL যোগ করুন।", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp), fontSize = 13.sp)
                Button(onClick = { scope.launch { loadFirebaseUrl() } }, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(if (refreshedAtLeastOnce) "লিংক আবার লোড করুন" else "Firebase থেকে লিংক আনুন")
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEDEAFF)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("একবার Firestore-এ সেট করলেই হবে", fontWeight = FontWeight.Bold, color = Ink)
                Text("Firebase Console → Firestore → appConfig → runtime ডকুমেন্টে apkUrl নামে String field রাখুন এবং Google Drive-এর APK-এর শেয়ার লিংক দিন। APK ফাইলটি ‘Anyone with the link’ দিয়ে শেয়ারযোগ্য হতে হবে।", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                Text("Google Drive-এর লিংক স্ক্যান করলে Drive-এর ডাউনলোড/প্রিভিউ পেজ খুলতে পারে; ব্যবহারকারীকে সেখান থেকে Download চাপতে হতে পারে।", color = Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

