package com.adda.chat.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adda.chat.data.Api
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
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun makeQr(value: String) {
        qrBitmap = null
        error = ""
        val url = value.trim()
        if (url.isBlank()) {
            error = "Firestore-এর appConfig/runtime ডকুমেন্টে apkUrl ফিল্ডে APK লিংক দিন।"
            return
        }
        if (!url.startsWith("https://", ignoreCase = true)) {
            error = "নিরাপদ ডাউনলোডের জন্য HTTPS লিংক ব্যবহার করুন।"
            return
        }
        try {
            val size = 640
            val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(
                        x, y,
                        if (matrix[x, y]) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    )
                }
            }
            qrBitmap = bitmap
        } catch (_: Exception) {
            error = "এই লিংকের QR কোড তৈরি করা যায়নি।"
        }
    }

    suspend fun loadFirebaseUrl() {
        loading = true
        error = ""
        try {
            val value: String = Api.fetchApkUrl().orEmpty()
            downloadUrl = value
            refreshedAtLeastOnce = true
            if (value.isNotBlank()) {
                makeQr(value)
            } else {
                qrBitmap = null
                error = "Firestore থেকে apkUrl পাওয়া যায়নি। appConfig/runtime ডকুমেন্টে apkUrl ফিল্ড, ইন্টারনেট এবং Firestore access পরীক্ষা করুন।"
            }
        } catch (_: Exception) {
            qrBitmap = null
            error = "ডাউনলোড লিংক আনা যায়নি। ইন্টারনেট সংযোগ ও Firestore access পরীক্ষা করুন।"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadFirebaseUrl() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Download,
            contentDescription = null,
            tint = Color(0xFF6957D5),
            modifier = Modifier.size(42.dp)
        )
        Text(
            "Adda অ্যাপ ডাউনলোড",
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Ink,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "অন্য ডিভাইসের ক্যামেরা দিয়ে QR কোড স্ক্যান করে অ্যাপ ডাউনলোড করুন",
            fontSize = 14.sp,
            color = Muted,
            modifier = Modifier.padding(top = 6.dp),
            textAlign = TextAlign.Center
        )

        Card(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = null,
                    tint = Color(0xFF6957D5),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    "Firestore থেকে ডাউনলোড লিংক",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.padding(top = 8.dp)
                )
                when {
                    loading -> CircularProgressIndicator(Modifier.padding(top = 18.dp))
                    qrBitmap != null -> {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Adda app download QR code",
                            modifier = Modifier.size(240.dp).padding(top = 18.dp)
                        )
                        Text(
                            "QR স্ক্যান করলে APK-এর পাবলিক HTTPS লিংক খুলবে",
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> Text(
                        "Firestore-এ সঠিক ডাউনলোড URL যোগ করুন।",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 18.dp),
                        textAlign = TextAlign.Center
                    )
                }
                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp),
                        fontSize = 13.sp
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (downloadUrl.isNotBlank()) {
                                clipboard.setText(AnnotatedString(downloadUrl))
                                Toast.makeText(context, "ডাউনলোড লিংক কপি হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !loading && downloadUrl.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("কপি লিংক")
                    }
                    Button(
                        onClick = {
                            if (downloadUrl.isNotBlank()) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, downloadUrl)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "ডাউনলোড লিংক শেয়ার করুন"))
                            }
                        },
                        enabled = !loading && downloadUrl.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("শেয়ার লিংক")
                    }
                }
                OutlinedButton(
                    onClick = { scope.launch { loadFirebaseUrl() } },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text(if (refreshedAtLeastOnce) "লিংক আবার লোড করুন" else "Firebase থেকে লিংক আনুন")
                }
            }
        }
    }
}
