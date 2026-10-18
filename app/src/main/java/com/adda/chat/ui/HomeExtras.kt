package com.adda.chat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTapGestures
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adda.chat.data.Api
import com.adda.chat.data.Session
import com.adda.chat.data.Social
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val STORY_SECONDS = 5000

private fun parseBg(hex: String): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Accent }

// ======================================================================= স্টোরি

@Composable
fun StoriesRow() {
    var viewingUser by remember { mutableStateOf<Int?>(null) }
    var composing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Social.refreshStories(); Social.refreshGroups() }

    val myUid = Session.me?.uid
    val ordered = Social.stories.toList()
    val mine = ordered.firstOrNull { it.uid == myUid }

    Column {
        Text("স্টোরি", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "my_story") {
                Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Avatar(
                            Session.displayName(myUid.orEmpty()), 64.dp, photoUrl = Session.profilePhotos[myUid.orEmpty()],
                            ring = mine != null && !mine.allViewed,
                            modifier = Modifier.clip(CircleShape).clickable {
                                if (mine != null) viewingUser = ordered.indexOf(mine) else composing = true
                            }
                        )
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(Accent).clickable { composing = true },
                            contentAlignment = Alignment.Center
                        ) { Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text("আপনার স্টোরি", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            items(ordered.filter { it.uid != myUid }, key = { "story_${it.uid}" }) { u ->
                Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(
                        u.username, 64.dp, photoUrl = u.photo, ring = !u.allViewed,
                        modifier = Modifier.clip(CircleShape).clickable { viewingUser = ordered.indexOf(u) }
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(u.username, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    viewingUser?.let { start -> if (start in ordered.indices) StoryViewer(ordered, start) { viewingUser = null } }
    if (composing) StoryComposerDialog { composing = false }
}

@Composable
private fun StoryViewer(users: List<Social.StoryUser>, startUser: Int, onClose: () -> Unit) {
    var userIdx by remember { mutableStateOf(startUser) }
    var itemIdx by remember { mutableStateOf(0) }
    val myUid = Session.me?.uid
    val user = users.getOrNull(userIdx) ?: run { onClose(); return }
    val item = user.items.getOrNull(itemIdx) ?: run { onClose(); return }
    val progress = remember(userIdx, itemIdx) { Animatable(0f) }
    val ctx = LocalContext.current

    fun next() {
        if (itemIdx + 1 < user.items.size) itemIdx++
        else if (userIdx + 1 < users.size) { userIdx++; itemIdx = 0 }
        else onClose()
    }
    fun prev() {
        if (itemIdx > 0) itemIdx--
        else if (userIdx > 0) { userIdx--; itemIdx = (users[userIdx].items.size - 1).coerceAtLeast(0) }
    }

    LaunchedEffect(userIdx, itemIdx) {
        if (user.uid != myUid) Social.markStoryViewed(item.id)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(STORY_SECONDS, easing = LinearEasing))
        next()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).pointerInput(userIdx, itemIdx) {
                detectTapGestures { offset -> if (offset.x < size.width / 3f) prev() else next() }
            }
        ) {
            if (item.kind == "image" && item.fileUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(Api.absoluteUrl(item.fileUrl)).addHeader("ngrok-skip-browser-warning", "true").build(),
                    contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                )
                if (item.text.isNotBlank()) {
                    Text(
                        item.text, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(16.dp)
                    )
                }
            } else {
                Box(Modifier.fillMaxSize().background(parseBg(item.bg)), contentAlignment = Alignment.Center) {
                    Text(item.text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(28.dp))
                }
            }
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    user.items.forEachIndexed { i, _ ->
                        val frac = when { i < itemIdx -> 1f; i == itemIdx -> progress.value; else -> 0f }
                        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Color.White))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.username, 36.dp, photoUrl = user.photo)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (user.uid == myUid) "আপনার স্টোরি" else user.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            SimpleDateFormat("hh:mm a", Locale("bn", "BD")).format(Date(item.createdAt)) +
                                if (user.uid == myUid) " · ${item.viewCount} জন দেখেছেন" else "",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp
                        )
                    }
                    if (user.uid == myUid) {
                        IconButton(onClick = { Social.deleteStory(item.id); onClose() }) { Icon(Icons.Filled.Delete, contentDescription = "স্টোরি মুছুন", tint = Color.White) }
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "বন্ধ", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun StoryComposerDialog(onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var bgIndex by remember { mutableStateOf(0) }
    var posting by remember { mutableStateOf(false) }
    val colors = listOf("#6654F1", "#E54868", "#1FA86A", "#FFB02E", "#30256F", "#0F8FD8")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            posting = true
            Social.postStoryImage(uri, text.trim()) { ok -> posting = false; if (ok) onClose() }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!posting) onClose() },
        title = { Text("নতুন স্টোরি (২৪ ঘণ্টা)") },
        text = {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(parseBg(colors[bgIndex])),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (text.isBlank()) "কিছু লিখুন…" else text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(14.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(if (i == bgIndex) 30.dp else 24.dp).clip(CircleShape).background(parseBg(c)).clickable { bgIndex = i }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { if (it.length <= 300) text = it }, placeholder = { Text("লেখা বা ছবির ক্যাপশন") }, modifier = Modifier.fillMaxWidth())
                TextButton(enabled = !posting, onClick = { picker.launch("image/*") }) { Text("🖼️ ছবি দিয়ে স্টোরি") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !posting && text.isNotBlank(),
                onClick = { posting = true; Social.postStoryText(text.trim(), colors[bgIndex]) { ok -> posting = false; if (ok) onClose() } }
            ) { Text(if (posting) "পোস্ট হচ্ছে…" else "পোস্ট করুন") }
        },
        dismissButton = { TextButton(enabled = !posting, onClick = onClose) { Text("বাতিল") } }
    )
}

// ======================================================================= গ্রুপ

@Composable
fun NewGroupButton() {
    var show by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp), color = Them,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { show = true }
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("👥", fontSize = 20.sp)
            Spacer(Modifier.size(12.dp))
            Text("নতুন গ্রুপ তৈরি করুন", fontWeight = FontWeight.Bold, color = Accent, fontSize = 15.sp)
        }
    }
    if (show) CreateGroupDialog(onDismiss = { show = false }, onCreated = { id -> show = false; Session.openConversation(id) })
}

@Composable
fun GroupListRow(g: Social.Group) {
    val unread = Session.unread[g.id] ?: 0
    val last = Session.lastPreview[g.id]
    Surface(
        shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { Session.openConversation(g.id) }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(g.name, 60.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("👥 ${g.name}", fontWeight = if (unread > 0) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 16.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    last?.let { (if (it.mine) "আপনি: " else "") + it.text } ?: "${g.members.size} জন সদস্য",
                    fontSize = 13.sp, color = if (unread > 0) Ink else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (unread > 0) {
                Box(Modifier.clip(CircleShape).background(BubbleBrush).padding(horizontal = 9.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
                    Text(unread.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ======================================================================= কল হিস্ট্রি

@Composable
fun CallsScreen() {
    LaunchedEffect(Unit) { Social.refreshCalls() }
    val fmt = remember { SimpleDateFormat("d MMM, hh:mm a", Locale("bn", "BD")) }
    Column(Modifier.fillMaxSize().background(Canvas).statusBarsPadding()) {
        Text("কল হিস্ট্রি", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Navy, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
        if (Social.calls.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("এখনো কোনো কল হয়নি", color = Muted) }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(Social.calls.toList(), key = { it.id }) { c ->
                    val missed = c.status == "missed"
                    val label = when (c.status) {
                        "missed" -> if (c.incoming) "মিসড কল" else "রিসিভ হয়নি"
                        "declined" -> if (c.incoming) "প্রত্যাখ্যান করেছেন" else "প্রত্যাখ্যাত"
                        "completed" -> "${if (c.incoming) "ইনকামিং" else "আউটগোয়িং"} · ${formatDuration(c.durationSec)}"
                        else -> "চলছে"
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { Session.openConversation(c.uid) }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(c.name, 48.dp, photoUrl = c.photo)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (missed && c.incoming) Danger else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text((if (c.kind == "video") "🎥 " else "📞 ") + (if (c.incoming) "↙ " else "↗ ") + label, fontSize = 13.sp, color = if (missed && c.incoming) Danger else Muted)
                            }
                            Text(fmt.format(Date(c.at)), fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(sec: Int): String =
    if (sec < 60) "${sec} সেকেন্ড" else "${sec / 60} মিনিট ${sec % 60} সেকেন্ড"
