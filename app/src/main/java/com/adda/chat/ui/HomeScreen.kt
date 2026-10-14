package com.adda.chat.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager as AndroidPackageManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adda.chat.data.Session

@Composable
fun HomeScreen(tab: Int = 0, onGoPeople: () -> Unit = {}) {
    val me = Session.me ?: return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showDiscoverable by remember { mutableStateOf(false) }
    var showMyQr by remember { mutableStateOf(false) }
    val qrBitmap = remember(me.uid) { createFriendQrBitmap(me.uid) }
    val scannerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { Session.addFriendByQr(it) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scannerLauncher.launch(
                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("")
                    .setBeepEnabled(false).setOrientationLocked(true)
            )
        } else Session.toast("QR স্ক্যান করতে ক্যামেরার অনুমতি দিন")
    }
    fun startQrScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == AndroidPackageManager.PERMISSION_GRANTED) {
            scannerLauncher.launch(
                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("")
                    .setBeepEnabled(false).setOrientationLocked(true)
            )
        } else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) Session.uploadProfilePhoto(uri)
        Session.requestInitialProfilePhoto = false
    }
    LaunchedEffect(me.uid, Session.requestInitialProfilePhoto) {
        if (Session.requestInitialProfilePhoto && me.profilePhoto.isNullOrBlank()) {
            photoPicker.launch("image/*")
        }
    }
    LaunchedEffect(tab) { if (tab == 1) Session.refreshDiscoverableUsers() }
    var showAddField by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var friendUid by remember { mutableStateOf("") }
    var friendSearch by remember { mutableStateOf("") }
    var peopleTab by remember { mutableStateOf(0) }
    val filteredFriends = me.friends.filter { uid ->
        Session.displayName(uid).contains(friendSearch.trim(), ignoreCase = true) ||
            uid.contains(friendSearch.trim(), ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().background(Canvas)) {
        // আধুনিক গ্রেডিয়েন্ট হেডার
        Surface(color = Accent, shadowElevation = 6.dp, shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)) {
            Row(
                Modifier.fillMaxWidth().background(BubbleBrush).statusBarsPadding().padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text("A", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("আড্ডা Messenger", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(
                        Session.connectionLabel,
                        fontSize = 12.sp,
                        color = if (Session.socketConnected) Color(0xFFB9FFE5) else Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "মেনু", tint = Color.White) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("আমার আইডি কপি") }, onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(me.uid))
                            Session.toast("আপনার আইডি কপি হয়েছে")
                        })
                        DropdownMenuItem(text = { Text("লগআউট") }, onClick = {
                            menuOpen = false
                            Session.logout()
                        })
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clickable { photoPicker.launch("image/*") }, contentAlignment = Alignment.Center) {
                                Avatar(me.username, 52.dp, photoUrl = me.profilePhoto)
                                Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(me.username, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(me.uid, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(me.uid))
                                Session.toast("কপি হয়েছে")
                            }) { Icon(Icons.Filled.ContentCopy, contentDescription = "আইডি কপি", tint = Accent) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CompactAction(Icons.Filled.QrCode, "আমার QR", Modifier.weight(1f)) { showMyQr = true }
                            CompactAction(Icons.Filled.QrCodeScanner, "স্ক্যান", Modifier.weight(1f)) { startQrScan() }
                            Row(
                                Modifier.weight(1.3f).height(36.dp).clip(RoundedCornerShape(50)).background(Canvas)
                                    .border(1.dp, Line, RoundedCornerShape(50)).padding(start = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("বন্ধু চাই", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
                                Switch(checked = me.discoverable, onCheckedChange = { Session.setDiscoverable(it) }, modifier = Modifier.scale(0.7f))
                            }
                            CompactAction(Icons.Filled.PersonAdd, "বন্ধু যোগ", Modifier.weight(1.1f), active = showAddField) { showAddField = !showAddField }
                        }
                        if (showAddField) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = friendUid,
                                    onValueChange = { friendUid = it },
                                    placeholder = { Text("বন্ধুর আইডি") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Line, focusedContainerColor = Color.White, unfocusedContainerColor = Color(0xFFFAFBFF)),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.size(8.dp))
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape)
                                        .background(if (friendUid.isNotBlank()) Accent else Line)
                                        .clickable(enabled = friendUid.isNotBlank()) {
                                            Session.sendFriendRequest(friendUid) { friendUid = ""; showAddField = false }
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.Add, contentDescription = "রিকুয়েস্ট পাঠান", tint = Color.White) }
                            }
                        }
                    }
                }
            }
            // ---- চ্যাট ট্যাব: সাম্প্রতিক চ্যাট লিস্ট ----
            item {
                OutlinedTextField(value = friendSearch, onValueChange = { friendSearch = it },
                    placeholder = { Text("নাম বা আইডি দিয়ে খুঁজুন") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Line, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
            }
            val sortedFriends = filteredFriends.sortedWith(
                compareByDescending<String> { Session.lastPreview[it]?.date ?: 0L }.thenBy { Session.displayName(it).lowercase() }
            )
            if (me.friends.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(84.dp).clip(CircleShape).background(Them), contentAlignment = Alignment.Center) { Text("💬", fontSize = 38.sp) }
                        Spacer(Modifier.height(12.dp))
                        Text("এখনো কোনো চ্যাট নেই", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                    }
                }
            } else if (sortedFriends.isEmpty()) {
                item { Text("এই নামে বা আইডিতে কোনো বন্ধু পাওয়া যায়নি", color = Muted) }
            }
            items(sortedFriends, key = { "chat_$it" }) { uid -> ChatListRow(uid) }
                }
                else -> {
            if (me.requests.isNotEmpty()) {
                item { Text("ফ্রেন্ড রিকুয়েস্ট (${me.requests.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink) }
                items(me.requests, key = { "req_$it" }) { uid ->
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(Session.displayName(uid), 44.dp)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Session.displayName(uid), fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(uid, fontSize = 12.sp, color = Muted)
                            }
                            Button(onClick = { Session.respondRequest(uid, true) }, shape = RoundedCornerShape(50)) { Text("গ্রহণ") }
                            Spacer(Modifier.size(6.dp))
                            OutlinedButton(onClick = { Session.respondRequest(uid, false) }, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Line)) { Text("বাতিল") }
                        }
                    }
                }
            }

            // ---- মানুষ ট্যাব: বন্ধু হতে আগ্রহীরা ----
            item { Text("যারা বন্ধু হতে আগ্রহী", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink) }
            val discoverList = Session.discoverableUsers.filter { it.uid !in me.friends && it.uid !in me.requests && it.uid != me.uid }
            if (discoverList.isEmpty()) item { Text("এখনো কেউ ‘বন্ধু চাই’ চালু করেননি।", color = Muted) }
            items(discoverList, key = { "discover_${it.uid}" }) { person ->
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(person.username, 46.dp, photoUrl = person.profilePhoto)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) { Text(person.username, fontWeight = FontWeight.SemiBold, color = Ink); Text(person.uid, color = Muted, fontSize = 12.sp) }
                        Button(onClick = { Session.sendFriendRequest(person.uid) {} }, shape = RoundedCornerShape(50)) { Text("বন্ধু হন") }
                    }
                }
            }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showMyQr) {
        AlertDialog(
            onDismissRequest = { showMyQr = false },
            title = { Text("আমার আড্ডা QR কোড") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "আমার বন্ধু যোগ করার QR কোড", modifier = Modifier.size(240.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(me.username, fontWeight = FontWeight.Bold, color = Ink)
                    Text(me.uid, color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showMyQr = false }) { Text("বন্ধ") }
            }
        )
    }

    if (showDiscoverable) {
        AlertDialog(
            onDismissRequest = { showDiscoverable = false },
            title = { Text("যারা বন্ধু চাইছেন") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (Session.discoverableUsers.isEmpty()) {
                        item { Text("এখনো কেউ ‘বন্ধু চাই’ চালু করেননি।", color = Muted) }
                    }
                    items(Session.discoverableUsers, key = { "discover_${it.uid}" }) { person ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(person.username, 42.dp, photoUrl = person.profilePhoto)
                            Spacer(Modifier.size(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.username, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(person.uid, fontSize = 12.sp, color = Muted)
                            }
                            Button(onClick = { Session.sendFriendRequest(person.uid) {}; }, shape = RoundedCornerShape(50)) { Text("রিকুয়েস্ট") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { Session.refreshDiscoverableUsers() }) { Text("রিফ্রেশ") } },
            dismissButton = { TextButton(onClick = { showDiscoverable = false }) { Text("বন্ধ") } }
        )
    }
}


private fun createFriendQrBitmap(uid: String): Bitmap {
    val content = "adda://friend/$uid"
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 640, 640)
    return Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until 640) for (y in 0 until 640) {
            bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
}


private val chatTimeFmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale("bn", "BD"))
private val chatDateFmt = java.text.SimpleDateFormat("dd MMM", java.util.Locale("bn", "BD"))

private fun formatChatTime(ms: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    return if (sameDay) chatTimeFmt.format(java.util.Date(ms)) else chatDateFmt.format(java.util.Date(ms))
}

@Composable
private fun ChatListRow(uid: String) {
    val unread = Session.unread[uid] ?: 0
    val last = Session.lastPreview[uid]
    val online = Session.online[uid] == true
    val subtitle = when {
        last != null -> (if (last.mine) "আপনি: " else "") + last.text
        online -> "অনলাইন"
        else -> ""
    }
    Surface(
        shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { Session.openConversation(uid) }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(Session.displayName(uid), 52.dp, online = online, photoUrl = Session.profilePhotos[uid])
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    Session.displayName(uid), fontWeight = if (unread > 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 16.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (unread > 0) Ink else if (last == null && online) Success else Muted,
                    fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (last != null) Text(formatChatTime(last.date), fontSize = 11.sp, color = if (unread > 0) Accent else Muted)
                if (unread > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.clip(CircleShape).background(Accent).padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                        Text(unread.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
private fun CompactAction(icon: ImageVector, label: String, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(50))
            .background(if (active) Accent else Them)
            .clickable(onClick = onClick).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Color.White else Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Accent, maxLines = 1)
    }
}
