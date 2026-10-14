package com.adda.chat.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    BackHandler { Session.closeConversation() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) Session.sendFile(uri)
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
                Avatar(Session.displayName(uid), 40.dp, online = Session.online[uid] == true)
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
                    MessageRow(m, mine = m.from == Session.me?.uid, onOpenImage = { viewerUrl = it })
                }
            }
        }

        // কম্পোজার
        Surface(color = Color.White) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { picker.launch("*/*") }) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "ফাইল পাঠান", tint = Accent)
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("মেসেজ লিখুন…") },
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
                        if (text.isNotEmpty()) Session.sendText(text) { ok -> if (ok) input = "" }
                    },
                    enabled = input.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "পাঠান",
                        tint = if (input.isNotBlank()) Accent else Muted
                    )
                }
            }
        }
    }

    viewerUrl?.let { url ->
        ImageViewer(url = url, onClose = { viewerUrl = null }, onDownload = {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
    }
}

private val timeFormat = SimpleDateFormat("hh:mm a", Locale("bn", "BD"))

@Composable
private fun MessageRow(m: ChatMessage, mine: Boolean, onOpenImage: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth(),
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
            Column(
                bubbleModifier
                    .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("📎 ${m.fileName ?: "ফাইল ডাউনলোড"}", color = if (mine) Color.White else Accent, fontWeight = FontWeight.SemiBold)
                if (m.size > 0) {
                    Text(
                        "%.2f MB".format(m.size / 1024.0 / 1024.0),
                        color = if (mine) Color.White.copy(alpha = 0.8f) else Muted,
                        fontSize = 12.sp
                    )
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
private fun ImageViewer(url: String, onClose: () -> Unit, onDownload: () -> Unit) {
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
            Button(
                onClick = onDownload,
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
            ) { Text("ডাউনলোড") }
        }
    }
}
