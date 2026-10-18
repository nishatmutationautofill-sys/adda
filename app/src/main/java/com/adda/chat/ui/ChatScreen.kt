package com.adda.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.style.TextOverflow

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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
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
import com.adda.chat.data.Social
import com.adda.chat.data.isGroupId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(requestCallPermission: (kind: String, action: () -> Unit) -> Unit) {
    val uid = Session.activeFriend ?: return
    val ctx = LocalContext.current
    var input by remember(uid) { mutableStateOf(Session.drafts[uid].orEmpty()) }
    var secureMode by remember { mutableStateOf(false) }
    var ttlSeconds by remember { mutableStateOf(3) }
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    var viewerSecure by remember { mutableStateOf(false) }
    var viewerMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    var headerMenu by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var attachMenu by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var showEmojiPack by remember { mutableStateOf(false) }
    val isGroup = isGroupId(uid)
    var searchOn by remember(uid) { mutableStateOf(false) }
    var searchQuery by remember(uid) { mutableStateOf("") }
    var editTarget by remember(uid) { mutableStateOf<ChatMessage?>(null) }
    var forwardTarget by remember(uid) { mutableStateOf<ChatMessage?>(null) }
    var showGallery by remember(uid) { mutableStateOf(false) }
    var showDisappear by remember(uid) { mutableStateOf(false) }
    var showGroupInfo by remember(uid) { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var customEmojis by remember { mutableStateOf(loadEmojiPack(ctx)) }
    val listState = rememberLazyListState()
    val shownMessages = if (searchQuery.isBlank()) Session.messages.toList() else Session.messages.filter {
        it.text.contains(searchQuery, ignoreCase = true) || (it.fileName ?: "").contains(searchQuery, ignoreCase = true)
    }
    // অসমাপ্ত লেখা সংরক্ষণ + "টাইপ করছে" সিগন্যাল
    LaunchedEffect(input, uid) {
        Session.drafts[uid] = input
        if (input.isNotBlank()) Session.notifyTyping(uid)
    }
    val uiScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val composerColor by animateColorAsState(if (secureMode) Ink else Color.White, label = "composerColor")
    val secureBg by animateColorAsState(if (secureMode) Color(0xFFDDE8F6) else Color.Transparent, label = "secureBg")
    val barTint = if (secureMode) Color(0xFF8DBBFF) else Accent
    val softTint = if (secureMode) Color(0xFF6B8CB8) else Muted

    // চ্যাট খোলা থাকলে এবং নতুন মেসেজ এলে "দেখেছে" জানানো
    LaunchedEffect(uid, Session.startTick, Session.messages.size, Session.socketConnected) { Session.markSeen(uid) }

    BackHandler { Session.closeConversation() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { Session.sendFile(uri, secureMode, ttlSeconds, replyTarget?.id); replyTarget = null }
    }
    val videoCapture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { Session.sendFile(it, secureMode, ttlSeconds, replyTarget?.id); replyTarget = null }
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

    Column(Modifier.fillMaxSize().background(ChatBackdrop).imePadding()) {
        // হেডার
        Surface(color = Color.White, shadowElevation = 4.dp, shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { Session.closeConversation() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান", tint = Navy)
                }
                Avatar(
                    Session.displayName(uid), 42.dp, online = Session.online[uid] == true, photoUrl = Session.profilePhotos[uid],
                    modifier = Modifier.clip(CircleShape).clickable { if (isGroup) showGroupInfo = true else showProfile = true }
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Session.displayName(uid), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Navy, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        val level = Session.badges[uid] ?: 0
                        if (level > 0) {
                            Spacer(Modifier.size(6.dp))
                            BadgeIcon(level, 16.dp)
                        }
                    }
                    val typer = Session.typing[uid]
                    Text(
                        when {
                            typer != null -> if (isGroup) "$typer টাইপ করছে…" else "টাইপ করছে…"
                            isGroup -> "${Social.groupById(uid)?.members?.size ?: 0} জন সদস্য"
                            Session.online[uid] == true -> "অনলাইন"
                            else -> "অফলাইন"
                        },
                        fontSize = 12.sp,
                        color = if (typer != null || (!isGroup && Session.online[uid] == true)) Color(0xFF168653) else Muted
                    )
                }
                if (!isGroup) {
                    IconButton(onClick = { requestCallPermission("audio") { CallManager.startCall("audio", uid) } }) {
                        Icon(Icons.Filled.Call, contentDescription = "অডিও কল", tint = Navy)
                    }
                    IconButton(onClick = { requestCallPermission("video") { CallManager.startCall("video", uid) } }) {
                        Icon(Icons.Filled.Videocam, contentDescription = "ভিডিও কল", tint = Navy)
                    }
                }
                Box {
                    IconButton(onClick = { headerMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "আরও", tint = Navy) }
                    DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }) {
                        DropdownMenuItem(text = { Text("🔍 চ্যাটে খুঁজুন") }, onClick = { headerMenu = false; searchOn = true })
                        DropdownMenuItem(text = { Text("🖼️ মিডিয়া ও ফাইল") }, onClick = { headerMenu = false; showGallery = true })
                        DropdownMenuItem(
                            text = { Text("⏳ অদৃশ্য মেসেজ (" + ((Session.disappear[uid] ?: 0).let { if (it == 0) "বন্ধ" else Session.disappearLabel(it) }) + ")") },
                            onClick = { headerMenu = false; showDisappear = true }
                        )
                        if (isGroup) {
                            DropdownMenuItem(text = { Text("👥 গ্রুপের তথ্য") }, onClick = { headerMenu = false; showGroupInfo = true })
                        } else {
                            DropdownMenuItem(text = { Text("আনফ্রেন্ড করুন") }, onClick = { headerMenu = false; confirmAction = "unfriend" })
                            DropdownMenuItem(text = { Text("ব্লক করুন", color = Danger) }, onClick = { headerMenu = false; confirmAction = "block" })
                        }
                    }
                }
            }
        }

        if (searchOn) ChatSearchBar(searchQuery, { searchQuery = it }, shownMessages.size) { searchOn = false; searchQuery = "" }
        val pinnedList = Session.messages.filter { it.pinned }.sortedBy { it.date }
        if (pinnedList.isNotEmpty()) {
            PinnedBanner(pinnedList) { id ->
                val idx = shownMessages.indexOfFirst { it.id == id }
                if (idx >= 0) uiScope.launch { listState.animateScrollToItem(idx) }
            }
        }
        val disappearSecs = Session.disappear[uid] ?: 0
        if (disappearSecs > 0) {
            Text(
                "⏳ অদৃশ্য মেসেজ চালু · নতুন মেসেজ ${Session.disappearLabel(disappearSecs)} পর মুছে যাবে",
                fontSize = 12.sp, color = Muted,
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }

        // Secure Me চালু থাকলে স্পষ্ট ব্যানার
        if (secureMode) {
            Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF8DBBFF), modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(8.dp))
                Text("সিকিউর মোড চালু · দেখার ${ttlSeconds} সেকেন্ড পর মেসেজ মুছে যাবে", color = Color.White, fontSize = 12.sp)
            }
        }

        // মেসেজ
        Box(Modifier.weight(1f).fillMaxWidth().background(secureBg)) {
            if (Session.messages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Them), contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Icons.Filled.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(36.dp)) }
                    Spacer(Modifier.size(12.dp))
                    Text("কথা বলা শুরু করুন", fontWeight = FontWeight.Bold, color = Ink)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(shownMessages, key = { it.id }) { m ->
                    MessageRow(
                        m,
                        mine = m.from == Session.me?.uid,
                        onOpenImage = { viewerUrl = it; viewerSecure = m.secure; viewerMsg = m },
                        onDownload = { downloadFile(ctx, it, m.fileName) },
                        onMenu = { selectedDelete = m },
                        onReply = {
                            if (m.secure) Toast.makeText(ctx, "সিকিউর মেসেজে রিপ্লাই করা যায় না", Toast.LENGTH_SHORT).show()
                            else replyTarget = m
                        },
                        onJump = { id ->
                            val idx = Session.messages.indexOfFirst { it.id == id }
                            if (idx >= 0) uiScope.launch { listState.animateScrollToItem(idx) }
                        }
                    )
                }
            }
        }

        val toggleRecord: () -> Unit = {
                        if (recording) {
                            try { recorder?.stop() } catch (_: Exception) { }
                            try { recorder?.release() } catch (_: Exception) { }
                            recorder = null
                            recording = false
                            recordingFile?.let { Session.sendFile(Uri.fromFile(it), secureMode, ttlSeconds, replyTarget?.id); replyTarget = null }
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
        }

        // কম্পোজার
        Surface(color = composerColor, shadowElevation = 14.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
                editTarget?.let { _ ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp)).background(Them).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✏️ মেসেজ এডিট করছেন", fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editTarget = null; input = "" }) { Icon(Icons.Filled.Close, contentDescription = "এডিট বাতিল", tint = Muted) }
                    }
                }
                replyTarget?.let { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (secureMode) Color(0xFF17375F) else Them).height(52.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(if (secureMode) Color(0xFF8DBBFF) else Accent))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(
                                if (r.from == Session.me?.uid) "নিজেকে রিপ্লাই" else "${Session.displayName(r.from)} কে রিপ্লাই",
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = barTint, maxLines = 1
                            )
                            Text(
                                if (r.isFile) (r.fileName ?: "ফাইল") else r.text,
                                fontSize = 13.sp, color = if (secureMode) Color.White else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { replyTarget = null }) { Icon(Icons.Filled.Close, contentDescription = "রিপ্লাই বাতিল", tint = softTint) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (secureMode) Color(0xFF17375F) else Canvas)
                            .border(1.dp, if (secureMode) Color(0xFF2B568C) else Line, RoundedCornerShape(50))
                            .clickable {
                                if (isGroup) Toast.makeText(ctx, "গ্রুপে Secure Me চলে না", Toast.LENGTH_SHORT).show()
                                else { secureMode = !secureMode; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(15.dp),
                            tint = if (secureMode) Color(0xFF8DBBFF) else Muted)
                        Spacer(Modifier.size(6.dp))
                        Text(if (secureMode) "সিকিউর মোড চালু" else "Secure Me", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = if (secureMode) Color.White else Muted)
                    }
                    Spacer(Modifier.weight(1f))
                }
                if (secureMode) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${ttlSeconds} সে.", color = Color(0xFF8DBBFF), fontWeight = FontWeight.Bold)
                        Slider(
                            value = ttlSeconds.toFloat(),
                            onValueChange = { ttlSeconds = it.toInt().coerceIn(1, 60) },
                            valueRange = 1f..60f,
                            steps = 58,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6FA8FF), activeTrackColor = Color(0xFF6FA8FF),
                                inactiveTrackColor = Color(0xFF2B568C), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text("60", color = Color(0xFF6B8CB8), fontSize = 11.sp)
                    }
                }
                if (Session.mediaUploadProgress != null) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp)).background(Them).padding(12.dp)
                    ) {
                        Text(Session.mediaUploadLabel, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.size(6.dp))
                        LinearProgressIndicator(
                            progress = { Session.mediaUploadProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                            color = Accent, trackColor = Color.White
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("${((Session.mediaUploadProgress ?: 0f) * 100).toInt()}%", fontSize = 11.sp, color = Muted)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { attachMenu = true }) { Icon(Icons.Filled.AttachFile, contentDescription = "সংযুক্ত করুন", tint = barTint) }
                        DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            DropdownMenuItem(text = { Text("ছবি") }, onClick = { attachMenu = false; picker.launch("image/*") })
                            DropdownMenuItem(text = { Text("ভিডিও") }, onClick = { attachMenu = false; picker.launch("video/*") })
                            DropdownMenuItem(text = { Text("অন্য ফাইল") }, onClick = { attachMenu = false; picker.launch("*/*") })
                        }
                    }
                    IconButton(onClick = {
                        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                        try { videoCapture.launch(intent) } catch (_: Exception) { Toast.makeText(ctx, "ক্যামেরা অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Filled.Videocam, contentDescription = "ভিডিও", tint = barTint) }
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(if (recording) "ভয়েস রেকর্ড হচ্ছে…" else if (secureMode) "সিকিউর মেসেজ লিখুন…" else "মেসেজ লিখুন…", color = softTint) },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            IconButton(onClick = { showEmojiPack = true }) {
                                Icon(Icons.Filled.EmojiEmotions, contentDescription = "ইমোজি", tint = softTint)
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = if (secureMode) Color(0xFF17375F) else Canvas,
                            unfocusedContainerColor = if (secureMode) Color(0xFF17375F) else Canvas,
                            focusedTextColor = if (secureMode) Color.White else Ink,
                            unfocusedTextColor = if (secureMode) Color.White else Ink,
                            cursorColor = if (secureMode) Color(0xFF8DBBFF) else Accent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    val hasText = input.isNotBlank()
                    Box(
                        Modifier.size(46.dp).clip(CircleShape)
                            .let { if (recording) it.background(Color(0xFFE54868)) else if (secureMode) it.background(Color(0xFF6FA8FF)) else it.background(BubbleBrush) }
                            .clickable {
                                val text = input.trim()
                                if (text.isNotEmpty()) {
                                    val et = editTarget
                                    if (et != null) Session.editMessage(et.id, text) { ok -> if (ok) { input = ""; editTarget = null } }
                                    else Session.sendText(text, secureMode, ttlSeconds, replyTarget?.id) { ok -> if (ok) { input = ""; replyTarget = null; Session.notifyTyping(uid, false) } }
                                }
                                else toggleRecord()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (hasText) Icons.AutoMirrored.Filled.Send else if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (hasText) "পাঠান" else if (recording) "রেকর্ডিং বন্ধ করে পাঠান" else "ভয়েস মেসেজ রেকর্ড",
                            tint = if (secureMode && !recording) Ink else Color.White
                        )
                    }
                }
            }
        }
    }

    selectedDelete?.let { target ->
        MessageActionSheet(
            message = target,
            mine = target.from == Session.me?.uid,
            onDismiss = { selectedDelete = null },
            onReact = { e -> selectedDelete = null; Session.react(target.id, e) },
            onEdit = { selectedDelete = null; editTarget = target; input = target.text },
            onPin = { selectedDelete = null; Session.pinMessage(target.id, !target.pinned) },
            onForward = { selectedDelete = null; forwardTarget = target },
            onDeleteForMe = { selectedDelete = null; Session.deleteMessage(target, everyone = false) },
            onDeleteForEveryone = { selectedDelete = null; Session.deleteMessage(target, everyone = true) },
            onDownload = { url -> selectedDelete = null; downloadFile(ctx, url, target.fileName) },
            onOpenExternal = { url ->
                selectedDelete = null
                val name = target.fileName.orEmpty()
                val mime = if (isAudioFile(name)) "audio/*" else if (isVideoFile(name)) "video/*" else "*/*"
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) { downloadFile(ctx, url, target.fileName) }
            }
        )
    }

    confirmAction?.let { act ->
        val friendName = Session.displayName(uid)
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(if (act == "block") "ব্লক করবেন?" else "আনফ্রেন্ড করবেন?") },
            text = {
                Text(
                    if (act == "block") "$friendName কে ব্লক করলে সে আপনাকে মেসেজ বা কল করতে পারবে না এবং বন্ধু তালিকা থেকে সরে যাবে। পরে আনব্লক করা যাবে।"
                    else "$friendName আপনার বন্ধু তালিকা থেকে সরে যাবে। চাইলে পরে আবার রিকুয়েস্ট পাঠানো যাবে।"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAction = null
                    if (act == "block") Session.blockUser(uid) else Session.unfriend(uid)
                }) { Text(if (act == "block") "ব্লক করুন" else "আনফ্রেন্ড", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("বাতিল") } }
        )
    }

    forwardTarget?.let { t ->
        ForwardDialog(
            onPick = { dest -> forwardTarget = null; Session.forwardMessage(t.id, dest) { } },
            onDismiss = { forwardTarget = null }
        )
    }
    if (showGallery) {
        MediaGalleryDialog(
            onPick = { m ->
                showGallery = false
                if (m.isImage) { viewerUrl = Api.absoluteUrl(m.fileUrl.orEmpty()); viewerSecure = m.secure; viewerMsg = m }
                else {
                    val idx = Session.messages.indexOfFirst { it.id == m.id }
                    if (idx >= 0) uiScope.launch { listState.animateScrollToItem(idx) }
                }
            },
            onClose = { showGallery = false }
        )
    }
    if (showDisappear) {
        DisappearDialog(
            chat = uid,
            canChange = !isGroup || (Social.groupById(uid)?.admins?.contains(Session.me?.uid) == true),
            onDismiss = { showDisappear = false }
        )
    }
    if (showGroupInfo) GroupInfoDialog(uid) { showGroupInfo = false }

    if (showProfile) {
        ProfilePopup(
            info = ProfileInfo(
                uid, Session.displayName(uid), Session.profilePhotos[uid],
                online = Session.online[uid] == true, badge = Session.badges[uid] ?: 0
            ),
            onClose = { showProfile = false }
        )
    }

    if (showEmojiPack) {
        AlertDialog(
            onDismissRequest = { showEmojiPack = false },
            title = { Text("ইমোজি প্যাক") },
            text = {
                Column {
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
        ImageViewer(
            url = url,
            canDownload = !viewerSecure,
            onDownload = { downloadFile(ctx, url, viewerMsg?.fileName) },
            onDelete = { val t = viewerMsg; viewerUrl = null; viewerMsg = null; selectedDelete = t },
            onClose = { viewerUrl = null; viewerMsg = null }
        )
    }
}

private val timeFormat = SimpleDateFormat("hh:mm a", Locale("bn", "BD"))

/** শুধু ইমোজি দিয়ে তৈরি ছোট মেসেজ (১–৩টি ইমোজি) হলে সেটি বড় করে দেখানো হয় */
private fun emojiOnlyCount(text: String): Int {
    val t = text.trim()
    if (t.isEmpty() || t.length > 60) return 0
    val iter = android.icu.text.BreakIterator.getCharacterInstance()
    iter.setText(t)
    var count = 0
    var start = iter.first()
    var end = iter.next()
    while (end != android.icu.text.BreakIterator.DONE) {
        if (!isEmojiCluster(t.substring(start, end))) return 0
        count++
        if (count > 3) return 0
        start = end
        end = iter.next()
    }
    return count
}

private fun isEmojiCluster(c: String): Boolean {
    if (c.isBlank()) return false
    val cp = c.codePointAt(0)
    // সাধারণ অক্ষর, সংখ্যা ও বিরামচিহ্ন ইমোজি নয়
    if (cp < 0x80 || Character.isLetterOrDigit(cp)) return false
    return cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF ||
        cp in 0x2B00..0x2BFF || cp in 0x1F1E6..0x1F1FF ||
        cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 ||
        cp == 0x2139 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    m: ChatMessage, mine: Boolean, onOpenImage: (String) -> Unit, onDownload: (String) -> Unit,
    onMenu: () -> Unit, onReply: () -> Unit, onJump: (String) -> Unit
) {
    val ctx = LocalContext.current
    // ডানে টানলে রিপ্লাই (সোয়াইপ-রিপ্লাই)
    var dragX by remember(m.id) { mutableStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val shownX by animateFloatAsState(dragX, label = "swipeReply")
    Box(
        Modifier.fillMaxWidth().pointerInput(m.id) {
            detectHorizontalDragGestures(
                onDragEnd = { if (dragX >= thresholdPx) onReply(); dragX = 0f },
                onDragCancel = { dragX = 0f },
                onHorizontalDrag = { change, delta ->
                    val next = dragX + delta
                    if (next >= 0f) { dragX = next.coerceAtMost(thresholdPx * 1.4f); change.consume() } else dragX = 0f
                }
            )
        }
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = Accent,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(22.dp).alpha((shownX / thresholdPx).coerceIn(0f, 1f))
        )
    Column(
        Modifier.fillMaxWidth().offset { IntOffset(shownX.roundToInt(), 0) },
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        if (m.replyToId != null) ReplyQuote(m, onJump)
        val shape = RoundedCornerShape(
            topStart = 24.dp, topEnd = 24.dp,
            bottomEnd = if (mine) 6.dp else 24.dp,
            bottomStart = if (mine) 24.dp else 6.dp
        )
        // মেসেজ/ফাইলে ট্যাপ বা লং-প্রেস করলে ডিলেট মেনু খোলে
        val tapMenu = Modifier.combinedClickable(onClick = onMenu, onLongClick = onMenu)
        val bubbleModifier = Modifier
            .widthIn(max = 290.dp)
            .clip(shape)
            .let { if (mine) it.background(BubbleBrush) else it.background(Color.White).border(1.dp, Line, shape) }

        // আগের (হিস্টরি) ছবি/ভিডিও: প্রথমে লোড হয় না, ব্লার কার্ড দেখায়; ট্যাপ করলে লোড হবে
        val deferred = m.history && m.isVisualMedia && !m.secure && Session.loadedMedia[m.id] != true

        if (deferred) {
            DeferredMediaCard(m, onLoad = { Session.loadMedia(m.id) }, onMenu = onMenu)
        } else if (m.isImage) {
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
                    .clip(RoundedCornerShape(18.dp))
                    .background(Them)
                    .border(1.dp, Line, RoundedCornerShape(18.dp))
                    .combinedClickable(onClick = { onOpenImage(url) }, onLongClick = onMenu)
            )
        } else if (m.isFile && isAudioFile(m.fileName)) {
            Box(bubbleModifier.then(tapMenu).padding(horizontal = 12.dp, vertical = 10.dp)) {
                VoiceMessagePlayer(Api.absoluteUrl(m.fileUrl.orEmpty()), mine)
            }
        } else if (m.isFile && isVideoFile(m.fileName)) {
            VideoMessageCard(
                Api.absoluteUrl(m.fileUrl.orEmpty()), onLongClick = onMenu,
                onDownload = if (m.secure) null else ({ onDownload(Api.absoluteUrl(m.fileUrl.orEmpty())) }),
                onDelete = onMenu
            )
        } else if (m.isFile) {
            val url = Api.absoluteUrl(m.fileUrl.orEmpty())
            val fileName = m.fileName ?: "ফাইল"
            val isAudio = fileName.endsWith(".m4a", true) || fileName.endsWith(".mp3", true) || fileName.endsWith(".aac", true) || fileName.endsWith(".wav", true) || fileName.endsWith(".ogg", true)
            val isVideo = fileName.endsWith(".mp4", true) || fileName.endsWith(".webm", true) || fileName.endsWith(".3gp", true) || fileName.endsWith(".mov", true)
            Row(bubbleModifier.then(tapMenu).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (mine) Color.White.copy(alpha = 0.22f) else Them),
                    contentAlignment = Alignment.Center
                ) { Text(if (isAudio) "🎙️" else if (isVideo) "🎬" else "📎", fontSize = 20.sp) }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                    Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (mine) Color.White else Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
            val emojiCount = emojiOnlyCount(m.text)
            if (emojiCount > 0) {
                // ইমোজি একা পাঠালে বাবল ছাড়া বড় করে দেখানো হয়
                Text(
                    m.text.trim(),
                    fontSize = when (emojiCount) { 1 -> 64.sp; 2 -> 48.sp; else -> 40.sp },
                    lineHeight = when (emojiCount) { 1 -> 76.sp; 2 -> 58.sp; else -> 48.sp },
                    modifier = tapMenu.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            } else {
                Box(bubbleModifier.then(tapMenu).padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(m.text, color = if (mine) Color.White else Ink, fontSize = 16.sp, lineHeight = 22.sp)
                }
            }
        }
        MessageMeta(m)
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(timeFormat.format(Date(m.date)), fontSize = 11.sp, color = Muted)
            if (mine) {
                Spacer(Modifier.size(4.dp))
                Icon(
                    if (m.status >= 1) Icons.Filled.DoneAll else Icons.Filled.Done,
                    contentDescription = when (m.status) { 2 -> "দেখেছে"; 1 -> "পৌঁছেছে"; else -> "পাঠানো হয়েছে" },
                    tint = if (m.status >= 2) Accent else Muted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
    }
}

/** রিপ্লাই করা মেসেজের ছোট উদ্ধৃতি — ট্যাপ করলে মূল মেসেজে যায় */
@Composable
private fun ReplyQuote(m: ChatMessage, onJump: (String) -> Unit) {
    val who = if (m.replyFrom == Session.me?.uid) "আপনি" else Session.displayName(m.replyFrom)
    val snippet = m.replyText.ifEmpty { if (m.replyKind == "file") "ফাইল" else "মেসেজ" }
    Row(
        Modifier.widthIn(max = 260.dp).padding(bottom = 3.dp).clip(RoundedCornerShape(14.dp)).background(Them)
            .clickable { m.replyToId?.let(onJump) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(38.dp).background(Accent))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(who, fontSize = 11.sp, color = Accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(snippet, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** আগের ছবি/ভিডিওর ব্লার প্লেসহোল্ডার — আসল ফাইল ডাউনলোড হয় না; ট্যাপ করলে লোড হবে */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeferredMediaCard(m: ChatMessage, onLoad: () -> Unit, onMenu: () -> Unit) {
    val isVideo = m.isVideo
    Box(
        Modifier.width(220.dp).height(160.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFC7DAF3), Color(0xFF9DB8DE))))
            .combinedClickable(onClick = onLoad, onLongClick = onMenu),
        contentAlignment = Alignment.Center
    ) {
        // ব্লার করা রঙের ছোপ (শুধু সাজসজ্জা — আসল ছবি নয়)
        Box(Modifier.size(110.dp).offset(x = (-50).dp, y = (-30).dp).blur(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
        Box(Modifier.size(90.dp).offset(x = 55.dp, y = 35.dp).blur(24.dp).clip(CircleShape).background(Color(0xFF6654F1).copy(alpha = 0.35f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isVideo) Icons.Filled.Videocam else Icons.Filled.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (isVideo) "ভিডিও" else "ছবি",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.size(5.dp))
                Text(
                    if (m.size > 0) "লোড করুন · %.1f MB".format(m.size / 1024.0 / 1024.0) else "ট্যাপ করে লোড করুন",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** মেসেজ/ফাইলে ট্যাপ করলে নিচ থেকে ওঠা ডিলেট মেনু */
@Composable
private fun MessageActionSheet(
    message: ChatMessage,
    mine: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onForward: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onDownload: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { }
            ) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 14.dp)) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp)
                            .clip(RoundedCornerShape(50)).background(Line)
                    )
                    Spacer(Modifier.height(10.dp))
                    val label = when {
                        message.isImage -> "ছবি"
                        message.isVideo -> "ভিডিও"
                        message.isFile -> "ফাইল"
                        else -> "মেসেজ"
                    }
                    Text(
                        label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    ExtraActionRows(message, mine, onReact, onEdit, onPin, onForward)
                    if (!message.isFile && message.text.isNotBlank()) {
                        SheetRow(Icons.Filled.ContentCopy, "কপি করুন", Ink) {
                            clipboard.setText(AnnotatedString(message.text))
                            onDismiss()
                        }
                    }
                    if (message.isFile && !message.secure && !message.isImage && !message.isVideo) {
                        SheetRow(Icons.Filled.AttachFile, "খুলুন", Ink) { onOpenExternal(Api.absoluteUrl(message.fileUrl.orEmpty())) }
                    }
                    if (message.isFile && !message.secure) {
                        SheetRow(Icons.Filled.Download, "ডাউনলোড", Ink) { onDownload(Api.absoluteUrl(message.fileUrl.orEmpty())) }
                    }
                    SheetRow(Icons.Filled.Delete, "শুধু আমার থেকে ডিলেট", Danger) { onDeleteForMe() }
                    if (mine) {
                        SheetRow(Icons.Filled.DeleteSweep, "উভয় দিক থেকে ডিলেট", Danger) { onDeleteForEveryone() }
                    }
                    Text(
                        if (mine) "“উভয় দিক” বেছে নিলে বন্ধুর চ্যাট ও সার্ভার থেকেও মুছে যাবে।"
                        else "এটি শুধু আপনার চ্যাট থেকে মুছবে; বন্ধুর কাছে থাকবে।",
                        color = Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("বাতিল") }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.size(14.dp))
        Text(text, color = tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ImageViewer(url: String, canDownload: Boolean, onDownload: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { box = it }) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).addHeader("ngrok-skip-browser-warning", "true").build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    // ডবল ট্যাপ: জুম ইন / জুম আউট
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    }
                    // দুই আঙুলে পিঞ্চ জুম ইন/আউট এবং টেনে সরানো
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val ns = (scale * zoom).coerceIn(1f, 6f)
                            val maxX = box.width * (ns - 1f) / 2f
                            val maxY = box.height * (ns - 1f) / 2f
                            offset = if (ns <= 1f) Offset.Zero
                            else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            scale = ns
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            )
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canDownload) IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = "ডাউনলোড", tint = Color.White)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "ডিলেট", tint = Color.White)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন", tint = Color.White)
                }
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
                    put(MediaStore.MediaColumns.MIME_TYPE,
                        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(cleanName.substringAfterLast('.', "").lowercase())
                            ?: "application/octet-stream")
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
