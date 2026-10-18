package com.adda.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adda.chat.data.Api
import com.adda.chat.data.ChatMessage
import com.adda.chat.data.Session
import com.adda.chat.data.Social
import com.adda.chat.data.isGroupId

/** মেসেজ মেনুর দ্রুত রিঅ্যাকশন */
val QuickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/** মেসেজ মেনুর নতুন অংশ: রিঅ্যাকশন, এডিট, পিন, ফরওয়ার্ড */
@Composable
fun ExtraActionRows(
    message: ChatMessage, mine: Boolean,
    onReact: (String) -> Unit, onEdit: () -> Unit, onPin: () -> Unit, onForward: () -> Unit
) {
    val myUid = Session.me?.uid
    if (!message.secure) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (e in QuickReactions) {
                val selected = message.reactions[myUid] == e
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (selected) Them else Color.Transparent)
                        .clickable { onReact(if (selected) "" else e) },
                    contentAlignment = Alignment.Center
                ) { Text(e, fontSize = 24.sp) }
            }
        }
    }
    val canEdit = mine && message.type == "text" && !message.secure && !message.encrypted &&
        System.currentTimeMillis() - message.date < 60 * 60 * 1000L
    if (canEdit) ExtraRow("✏️", "এডিট করুন", Ink, onEdit)
    if (!message.secure) {
        ExtraRow("📌", if (message.pinned) "আনপিন করুন" else "পিন করুন", Ink, onPin)
        ExtraRow("↪️", "ফরওয়ার্ড করুন", Ink, onForward)
    }
}

@Composable
private fun ExtraRow(emoji: String, text: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(14.dp))
        Text(text, color = tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** বাবলের নিচে: রিঅ্যাকশন চিপ, ফরওয়ার্ড/পিন/সম্পাদিত লেবেল */
@Composable
fun MessageMeta(m: ChatMessage) {
    if (m.forwarded || m.pinned || m.editedAt > 0L) {
        Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.forwarded) Text("↪ ফরওয়ার্ড করা", fontSize = 11.sp, color = Muted)
            if (m.pinned) Text("📌", fontSize = 11.sp)
            if (m.editedAt > 0L) Text("সম্পাদিত", fontSize = 11.sp, color = Muted)
        }
    }
    if (m.reactions.isNotEmpty()) {
        val counts = m.reactions.values.groupingBy { it }.eachCount()
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((emoji, count) in counts) {
                val mineReacted = m.reactions[Session.me?.uid] == emoji
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (mineReacted) Them else Color.White)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) { Text(if (count > 1) "$emoji $count" else emoji, fontSize = 13.sp) }
            }
        }
    }
}

/** পিন করা মেসেজের ব্যানার */
@Composable
fun PinnedBanner(pins: List<ChatMessage>, onJump: (String) -> Unit) {
    val top = pins.lastOrNull() ?: return
    Row(
        Modifier.fillMaxWidth().background(Them).clickable { onJump(top.id) }.padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📌", fontSize = 14.sp)
        Spacer(Modifier.size(8.dp))
        Text(
            if (top.isFile) (top.fileName ?: "ফাইল") else top.text,
            fontSize = 13.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (pins.size > 1) Text("+${pins.size - 1}", fontSize = 12.sp, color = Muted)
    }
}

/** চ্যাটের ভেতরে সার্চ বার */
@Composable
fun ChatSearchBar(query: String, onChange: (String) -> Unit, resultCount: Int, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Muted)
        OutlinedTextField(
            value = query, onValueChange = onChange, singleLine = true,
            placeholder = { Text("এই চ্যাটে খুঁজুন") },
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
        )
        if (query.isNotBlank()) Text("$resultCount", color = Muted, fontSize = 12.sp)
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "সার্চ বন্ধ", tint = Muted) }
    }
}

/** ফরওয়ার্ডের গন্তব্য বাছাই: বন্ধু বা গ্রুপ */
@Composable
fun ForwardDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val friends = Session.me?.friends.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("কাকে পাঠাবেন?") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(Social.groups.toList(), key = { "fg_${it.id}" }) { g ->
                    PickRow(g.name, Session.profilePhotos[g.id], "গ্রুপ") { onPick(g.id) }
                }
                items(friends, key = { "ff_$it" }) { uid ->
                    PickRow(Session.displayName(uid), Session.profilePhotos[uid], "") { onPick(uid) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
private fun PickRow(name: String, photo: String?, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, 38.dp, photoUrl = photo)
        Spacer(Modifier.size(12.dp))
        Text(name, fontSize = 16.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (tag.isNotEmpty()) Text(tag, fontSize = 12.sp, color = Muted)
    }
}

/** চ্যাট-ভিত্তিক অদৃশ্য মেসেজ টাইমার */
@Composable
fun DisappearDialog(chat: String, canChange: Boolean, onDismiss: () -> Unit) {
    val current = Session.disappear[chat] ?: 0
    val options = listOf(0, 3600, 86400, 604800)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("অদৃশ্য মেসেজ") },
        text = {
            Column {
                Text("চালু করলে এই চ্যাটের নতুন মেসেজ ও ফাইল পাঠানোর পর নির্ধারিত সময়ে দুই পক্ষ ও সার্ভার থেকে মুছে যাবে।", fontSize = 13.sp, color = Muted)
                Spacer(Modifier.size(10.dp))
                for (opt in options) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = canChange) { Session.setDisappear(chat, opt); onDismiss() }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = current == opt, onCheckedChange = null)
                        Spacer(Modifier.size(10.dp))
                        Text(if (opt == 0) "বন্ধ" else Session.disappearLabel(opt), fontSize = 16.sp, color = Ink)
                    }
                }
                if (!canChange) Text("গ্রুপে শুধু অ্যাডমিন বদলাতে পারে।", fontSize = 12.sp, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ঠিক আছে") } }
    )
}

/** শেয়ার করা ছবি/ভিডিও/ফাইলের গ্যালারি (লোড হওয়া মেসেজ থেকে; সিকিউর মেসেজ বাদ) */
@Composable
fun MediaGalleryDialog(onPick: (ChatMessage) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val items = Session.messages.filter { it.isFile && !it.secure }.sortedByDescending { it.date }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "বন্ধ", tint = Navy) }
                Text("মিডিয়া ও ফাইল (${items.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Navy)
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("এখনো কিছু শেয়ার হয়নি", color = Muted) }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    gridItems(items, key = { it.id }) { m ->
                        Box(Modifier.aspectRatio(1f).background(Them).clickable { onPick(m) }, contentAlignment = Alignment.Center) {
                            if (m.isImage) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx).data(Api.absoluteUrl(m.fileUrl.orEmpty())).crossfade(true).build(),
                                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                            } else if (m.isVideo) {
                                Icon(Icons.Filled.Videocam, contentDescription = "ভিডিও", tint = Accent, modifier = Modifier.size(34.dp))
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                                    Text("📎", fontSize = 26.sp)
                                    Text(m.fileName ?: "ফাইল", fontSize = 11.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** নতুন গ্রুপ তৈরি */
@Composable
fun CreateGroupDialog(onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val picked = remember { mutableStateOf(setOf<String>()) }
    val friends = Session.me?.friends.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন গ্রুপ") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { if (it.length <= 40) name = it }, singleLine = true, placeholder = { Text("গ্রুপের নাম") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(8.dp))
                Text("সদস্য বাছুন (${picked.value.size})", fontSize = 13.sp, color = Muted)
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(friends, key = { "cg_$it" }) { uid ->
                        Row(
                            Modifier.fillMaxWidth().clickable { picked.value = if (uid in picked.value) picked.value - uid else picked.value + uid }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = uid in picked.value, onCheckedChange = null)
                            Spacer(Modifier.size(8.dp))
                            Avatar(Session.displayName(uid), 34.dp, photoUrl = Session.profilePhotos[uid])
                            Spacer(Modifier.size(10.dp))
                            Text(Session.displayName(uid), fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && picked.value.isNotEmpty(),
                onClick = { Social.createGroup(name.trim(), picked.value.toList()) { id -> if (id != null) { onCreated(id) } } }
            ) { Text("তৈরি করুন") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

/** গ্রুপের তথ্য, সদস্য, যোগ/বাদ/ছাড়া */
@Composable
fun GroupInfoDialog(gid: String, onClose: () -> Unit) {
    val g = Social.groupById(gid)
    val myUid = Session.me?.uid
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    if (g == null) return
    val isAdmin = myUid != null && myUid in g.admins

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(g.name) },
        text = {
            Column {
                Text("${g.members.size} জন সদস্য", fontSize = 13.sp, color = Muted)
                Spacer(Modifier.size(6.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(g.members, key = { "gm_${it.uid}" }) { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(m.username, 36.dp, photoUrl = m.photo)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (m.uid == myUid) "${m.username} (আপনি)" else m.username, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (m.uid in g.admins) Text("অ্যাডমিন", fontSize = 11.sp, color = Accent)
                            }
                            if (isAdmin && m.uid != myUid && m.uid != g.owner) {
                                TextButton(onClick = { Social.removeMember(g.id, m.uid) }) { Text("বাদ", color = Danger) }
                            }
                        }
                    }
                }
                if (isAdmin) {
                    TextButton(onClick = { adding = true }) { Text("➕ সদস্য যোগ করুন") }
                    TextButton(onClick = { renaming = true }) { Text("✏️ নাম বদলান") }
                }
                TextButton(onClick = { confirmLeave = true }) { Text("🚪 গ্রুপ ছাড়ুন", color = Danger) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("বন্ধ") } }
    )

    if (adding) {
        val candidates = Session.me?.friends.orEmpty().filter { f -> g.members.none { it.uid == f } }
        val picked = remember { mutableStateOf(setOf<String>()) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("সদস্য যোগ করুন") },
            text = {
                if (candidates.isEmpty()) Text("যোগ করার মতো আর কোনো বন্ধু নেই", color = Muted)
                else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(candidates, key = { "ga_$it" }) { uid ->
                        Row(
                            Modifier.fillMaxWidth().clickable { picked.value = if (uid in picked.value) picked.value - uid else picked.value + uid }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = uid in picked.value, onCheckedChange = null)
                            Spacer(Modifier.size(8.dp))
                            Text(Session.displayName(uid), fontSize = 15.sp, color = Ink)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = picked.value.isNotEmpty(), onClick = { Social.addMembers(g.id, picked.value.toList()); adding = false }) { Text("যোগ করুন") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("বাতিল") } }
        )
    }
    if (renaming) {
        var newName by remember { mutableStateOf(g.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("গ্রুপের নাম") },
            text = { OutlinedTextField(value = newName, onValueChange = { if (it.length <= 40) newName = it }, singleLine = true) },
            confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = { Social.renameGroup(g.id, newName.trim()); renaming = false }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("বাতিল") } }
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("গ্রুপ ছাড়বেন?") },
            text = { Text("ছাড়লে এই গ্রুপের মেসেজ আর দেখতে পাবেন না।") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onClose(); Social.leaveGroup(g.id) }) { Text("ছাড়ুন", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("বাতিল") } }
        )
    }
}

/** এই চ্যাট কি গ্রুপ? */
fun isGroupChat(id: String?): Boolean = isGroupId(id)
