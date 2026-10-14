package com.adda.chat.ui

import android.app.DownloadManager
import android.content.Intent
import android.content.ContentValues
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adda.chat.call.CallManager
import com.adda.chat.data.Api
import com.adda.chat.data.ChatMessage
import com.adda.chat.data.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(requestCallPermission: (kind: String, action: () -> Unit) -> Unit) {
    val uid = Session.activeFriend ?: return
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var secureMode by remember { mutableStateOf(false) }
    var ttlSeconds by remember { mutableStateOf(3) }
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    var viewerSecure by remember { mutableStateOf(false) }
    var selectedDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var showEmojiPack by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var customEmojis by remember { mutableStateOf(loadEmojiPack(ctx)) }
    val listState = rememberLazyListState()

    BackHandler { Session.closeConversation() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) Session.sendFile(uri, secureMode, ttlSeconds)
    }
    val videoCapture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { Session.sendFile(it, secureMode, ttlSeconds) }
                ?: Toast.makeText(ctx, "ভিডিও পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }
    val emojiPackPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val raw = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val obj = JSONObject(raw)
                val arr = obj.optJSONArray("emojis") ?: JSONArray()
                val list = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.take(300)
                if (list.isEmpty()) throw IllegalArgumentException("প্যাকে কোনো ইমোজি নেই")
                saveEmojiPack(ctx, list)
                customEmojis = list
                Toast.makeText(ctx, "ইমোজি প্যাক ইম্পোর্ট হয়েছে (${list.size})", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "ইমোজি প্যাক ইম্পোর্ট করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Session.messages.size) {
        if (Session.messages.isNotEmpty()) listState.animateScrollToItem(Session.messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(Canvas).imePadding()) {
        // হেডার
        Surface(color = Color.White, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { Session.closeConversation() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                }
                Avatar(Session.displayName(uid), 40.dp, online = Session.online[uid] == true, photoUrl = Session.profilePhotos[uid])
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(Session.displayName(uid), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                    Text(
                        if (Session.online[uid] == true) "অনলাইন" else "অফলাইন",
                        fontSize = 12.sp,
                        color = if (Session.online[uid] == true) Success else Muted
                    )
                }
                IconButton(onClick = { requestCallPermission("audio") { CallManager.startCall("audio", uid) } }) {
                    Icon(Icons.Filled.Call, contentDescription = "অডিও কল", tint = Accent)
                }
                IconButton(onClick = { requestCallPermission("video") { CallManager.startCall("video", uid) } }) {
                    Icon(Icons.Filled.Videocam, contentDescription = "ভিডিও কল", tint = Accent)
                }
            }
        }

        // মেসেজ
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (Session.messages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("👋", fontSize = 40.sp)
                    Text("কথা বলা শুরু করুন", fontWeight = FontWeight.Bold, color = Ink)
                    Text("আপনার মেসেজ এখানে দেখা যাবে।", color = Muted, fontSize = 13.sp)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(Session.messages, key = { it.id }) { m ->
                    MessageRow(
                        m,
                        mine = m.from == Session.me?.uid,
                        onOpenImage = { viewerUrl = it; viewerSecure = m.secure },
                        onDownload = { downloadFile(ctx, it, m.fileName) },
                        onDelete = { selectedDelete = m }
                    )
                }
            }
        }

        // কম্পোজার ও Secure Me
        Surface(color = Color.White) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = secureMode, onCheckedChange = { secureMode = it })
                    Text("Secure Me", fontWeight = FontWeight.SemiBold, color = if (secureMode) Color(0xFFB3261E) else Ink)
                    Spacer(Modifier.weight(1f))
                    if (secureMode) Text("স্বয়ংক্রিয় ডিলেট", color = Color(0xFFB3261E), fontSize = 12.sp)
                }
                if (secureMode) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${ttlSeconds}s", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
                        Slider(
                            value = ttlSeconds.toFloat(),
                            onValueChange = { ttlSeconds = it.toInt().coerceIn(1, 60) },
                            valueRange = 1f..60f,
                            steps = 58,
                            modifier = Modifier.weight(1f)
                        )
                        Text("60s", color = Muted, fontSize = 11.sp)
                    }
                }
                if (Session.mediaUploadProgress != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(Session.mediaUploadLabel, fontSize = 12.sp, color = Muted)
                        LinearProgressIndicator(progress = { Session.mediaUploadProgress ?: 0f }, modifier = Modifier.fillMaxWidth())
                        Text("${((Session.mediaUploadProgress ?: 0f) * 100).toInt()}%", fontSize = 11.sp, color = Muted)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { picker.launch("*/*") }) { Icon(Icons.Filled.AttachFile, contentDescription = "ফাইল", tint = Accent) }
                    IconButton(onClick = { showEmojiPack = true }) { Icon(Icons.Filled.EmojiEmotions, contentDescription = "ইমোজি", tint = Accent) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60).putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                        try { videoCapture.launch(intent) } catch (_: Exception) { Toast.makeText(ctx, "ক্যামেরা অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Filled.Videocam, contentDescription = "১ মিনিট পর্যন্ত ভিডিও", tint = Accent) }
                    IconButton(onClick = {
                        if (recording) {
                            try { recorder?.stop() } catch (_: Exception) { }
                            try { recorder?.release() } catch (_: Exception) { }
                            recorder = null
                            recording = false
                            recordingFile?.let { Session.sendFile(Uri.fromFile(it), secureMode, ttlSeconds) }
                            recordingFile = null
                        } else {
                            requestCallPermission("audio") {
                                try {
                                    val file = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                                    val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else {
                                        @Suppress("DEPRECATION")
                                        MediaRecorder()
                                    }
                                    r.setAudioSource(MediaRecorder.AudioSource.MIC)
                                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    r.setAudioEncodingBitRate(96_000)
                                    r.setAudioSamplingRate(44_100)
                                    r.setMaxDuration(5 * 60 * 1000)
                                    r.setOutputFile(file.absolutePath)
                                    r.prepare()
                                    r.start()
                                    recorder = r
                                    recordingFile = file
                                    recording = true
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "ভয়েস রেকর্ড শুরু করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }) {
                        Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (recording) "রেকর্ডিং বন্ধ করে পাঠান" else "ভয়েস মেসেজ রেকর্ড",
                            tint = if (recording) Color.Red else Accent)
                    }
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(if (recording) "ভয়েস রেকর্ড হচ্ছে…" else "মেসেজ লিখুন…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Canvas,
                        unfocusedContainerColor = Canvas,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isNotEmpty()) Session.sendText(text, secureMode, ttlSeconds) { ok -> if (ok) input = "" }
                    },
                    enabled = input.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "পাঠান",
                        tint = if (input.isNotBlank()) Accent else Muted)
                }
                }
            }
        }
    }

    selectedDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { selectedDelete = null },
            title = { Text("মেসেজ/ফাইল ডিলেট করবেন?") },
            text = { Text(if (target.from == Session.me?.uid) "এটি আপনার এবং অপর পক্ষের চ্যাট থেকে মুছে যাবে।" else "এটি শুধু আপনার চ্যাট থেকে মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = { selectedDelete = null; Session.deleteMessage(target) }) { Text("ডিলেট") }
            },
            dismissButton = { TextButton(onClick = { selectedDelete = null }) { Text("বাতিল") } }
        )
    }

    if (showEmojiPack) {
        AlertDialog(
            onDismissRequest = { showEmojiPack = false },
            title = { Text("ইমোজি প্যাক") },
            text = {
                Column {
                    Text("ইমোজি ট্যাপ করলে মেসেজে যোগ হবে। JSON ফাইল থেকে নিজের প্যাক ইম্পোর্ট করতে পারবেন।")
                    Spacer(Modifier.size(8.dp))
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(6),
                        modifier = Modifier.fillMaxWidth().sizeIn(maxHeight = 220.dp)
                    ) {
                        items(customEmojis) { emoji ->
                            Text(emoji, fontSize = 26.sp, modifier = Modifier.clickable {
                                input += emoji
                                showEmojiPack = false
                            }.padding(5.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEmojiPack = false; emojiPackPicker.launch("application/json") }) {
                    Text("প্যাক ইম্পোর্ট")
                }
            },
            dismissButton = { TextButton(onClick = { showEmojiPack = false }) { Text("বন্ধ") } }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            if (recording) {
                try { recorder?.stop() } catch (_: Exception) { }
                try { recorder?.release() } catch (_: Exception) { }
            }
        }
    }

    viewerUrl?.let { url ->
        ImageViewer(url = url, onClose = { viewerUrl = null })
    }
}

private val timeFormat = SimpleDateFormat("hh:mm a", Locale("bn", "BD"))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(m: ChatMessage, mine: Boolean, onOpenImage: (String) -> Unit, onDownload: (String) -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = {},
            onLongClick = { if (mine) onDelete() }
        ),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        val shape = RoundedCornerShape(18.dp)
        val bubbleModifier = Modifier
            .widthIn(max = 290.dp)
            .clip(shape)
            .let { if (mine) it.background(BubbleBrush) else it.background(Them) }

        if (m.isImage) {
            val url = Api.absoluteUrl(m.fileUrl.orEmpty())
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(url)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .crossfade(true)
                    .build(),
                contentDescription = m.fileName ?: "ছবি",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .sizeIn(maxWidth = 240.dp, maxHeight = 300.dp)
                    .clip(shape)
                    .background(Them)
                    .clickable { onOpenImage(url) }
            )
        } else if (m.isFile) {
            val url = Api.absoluteUrl(m.fileUrl.orEmpty())
            val fileName = m.fileName ?: "ফাইল"
            val isAudio = fileName.endsWith(".m4a", true) || fileName.endsWith(".mp3", true) || fileName.endsWith(".aac", true) || fileName.endsWith(".wav", true) || fileName.endsWith(".ogg", true)
            val isVideo = fileName.endsWith(".mp4", true) || fileName.endsWith(".webm", true) || fileName.endsWith(".3gp", true) || fileName.endsWith(".mov", true)
            Row(bubbleModifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable {
                    if (m.secure) {
                        Toast.makeText(ctx, "সিকিউর ফাইল অ্যাপের বাইরে খোলা বা ডাউনলোড করা যাবে না", Toast.LENGTH_SHORT).show()
                    } else {
                        val mime = if (isAudio) "audio/*" else if (isVideo) "video/*" else "*/*"
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { onDownload(url) }
                    }
                }.padding(end = 8.dp)) {
                    Text("${if (isAudio) "🎙️" else if (isVideo) "🎬" else "📎"} $fileName",
                        color = if (mine) Color.White else Accent, fontWeight = FontWeight.SemiBold)
                    if (m.size > 0) Text("%.2f MB".format(m.size / 1024.0 / 1024.0),
                        color = if (mine) Color.White.copy(alpha = 0.8f) else Muted, fontSize = 12.sp)
                }
                if (!m.secure) {
                    IconButton(onClick = { onDownload(url) }) {
                        Icon(Icons.Filled.Download, contentDescription = "ডাউনলোড", tint = if (mine) Color.White else Accent)
                    }
                }
            }
        } else {
            Box(bubbleModifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(m.text, color = if (mine) Color.White else Ink, fontSize = 16.sp)
            }
        }
        Text(
            "${if (mine) "আপনি" else Session.displayName(m.from)} · ${timeFormat.format(Date(m.date))}",
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ImageViewer(url: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).addHeader("ngrok-skip-browser-warning", "true").build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন", tint = Color.White)
            }
        }
    }
}


private fun downloadFile(ctx: android.content.Context, url: String, suggestedName: String?) {
    try {
        val uri = Uri.parse(url)
        val cleanName = (suggestedName ?: uri.lastPathSegment ?: "adda_file").replace(Regex("[^A-Za-z0-9._ -]"), "_")
        if (uri.scheme == "file") {
            val source = File(uri.path ?: throw IllegalArgumentException("ফাইল পাওয়া যায়নি"))
            if (!source.exists()) throw IllegalArgumentException("ফাইলের মেয়াদ শেষ হয়েছে")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val dest = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Downloads ফোল্ডার খোলা যায়নি")
                ctx.contentResolver.openOutputStream(dest)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                    ?: throw IllegalStateException("ফাইল সংরক্ষণ করা যায়নি")
            } else {
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.cacheDir
                val dest = File(dir, cleanName)
                source.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
            Toast.makeText(ctx, "Downloads-এ ফাইল সংরক্ষণ হয়েছে", Toast.LENGTH_LONG).show()
            return
        }
        val request = DownloadManager.Request(uri)
            .setTitle(suggestedName ?: "Adda ফাইল")
            .setDescription("Adda থেকে ডাউনলোড হচ্ছে")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        if (Api.token.isNotBlank()) request.addRequestHeader("Authorization", "Bearer ${Api.token}")
        if (Api.isNgrok(Api.serverUrl)) request.addRequestHeader("ngrok-skip-browser-warning", "true")
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, cleanName)
        val manager = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(ctx, "ডাউনলোড শুরু হয়েছে — Downloads ফোল্ডারে পাবেন", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "ডাউনলোড শুরু করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun loadEmojiPack(ctx: android.content.Context): List<String> {
    return try {
        val raw = ctx.getSharedPreferences("adda_emoji_pack", android.content.Context.MODE_PRIVATE)
            .getString("emojis", null) ?: return defaultEmojiPack
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.take(300).ifEmpty { defaultEmojiPack }
    } catch (_: Exception) { defaultEmojiPack }
}

private fun saveEmojiPack(ctx: android.content.Context, emojis: List<String>) {
    ctx.getSharedPreferences("adda_emoji_pack", android.content.Context.MODE_PRIVATE)
        .edit().putString("emojis", JSONArray(emojis).toString()).apply()
}

private val defaultEmojiPack = listOf(
    "😀","😃","😄","😁","😆","😅","😂","🤣","🥹","😊","😍","🥰",
    "😘","😎","🤔","🙄","😴","😭","😡","🤗","🤝","👍","👎","👏",
    "🙏","❤️","🧡","💛","💚","💙","💜","🖤","💔","💯","🔥","✨",
    "🎉","🎂","🌹","🌸","☕","🍕","⚽","🎮","🚀","🐱","🐼","🦋"
)
