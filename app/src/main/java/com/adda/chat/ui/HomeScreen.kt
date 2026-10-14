package com.adda.chat.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.foundation.Image
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
fun HomeScreen() {
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
                    .setPrompt("অন্য ব্যক্তির আড্ডা QR কোড স্ক্যান করুন")
                    .setBeepEnabled(false).setOrientationLocked(true)
            )
        } else Session.toast("QR স্ক্যান করতে ক্যামেরার অনুমতি দিন")
    }
    fun startQrScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == AndroidPackageManager.PERMISSION_GRANTED) {
            scannerLauncher.launch(
                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("অন্য ব্যক্তির আড্ডা QR কোড স্ক্যান করুন")
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
        Surface(color = Accent, shadowElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth().background(BubbleBrush).statusBarsPadding().padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 14.dp),
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // প্রোফাইল কার্ড
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clickable { photoPicker.launch("image/*") }, contentAlignment = Alignment.Center) {
                            Avatar(me.username, 58.dp, photoUrl = me.profilePhoto)
                            Box(Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(me.username, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                            Text("আইডি: ${me.uid}", color = Muted, fontSize = 13.sp)
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(me.uid))
                            Session.toast("আপনার আইডি কপি হয়েছে")
                        }) { Icon(Icons.Filled.ContentCopy, contentDescription = "আইডি কপি", tint = Accent) }
                    }
                }
            }

            // QR দিয়ে সরাসরি বন্ধু যোগ
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("QR কোড দিয়ে বন্ধু যোগ", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("নিজের QR দেখান অথবা অন্যজনের QR স্ক্যান করুন। স্ক্যান হলেই দুজন স্বয়ংক্রিয়ভাবে বন্ধু হবেন।", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { showMyQr = true }, modifier = Modifier.weight(1f)) {
                                Text("আমার QR দেখান")
                            }
                            Button(onClick = { startQrScan() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) {
                                Text("QR স্ক্যান করুন")
                            }
                        }
                    }
                }
            }

            // প্রোফাইল, ছবি এবং পাবলিক বন্ধু-চাই সেটিং
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("প্রোফাইল ও বন্ধু খোঁজা", fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("বন্ধু চাই", fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("পাবলিক তালিকায় দেখাতে সুইচ চালু করুন", color = Muted, fontSize = 12.sp)
                            }
                            Switch(checked = me.discoverable, onCheckedChange = { Session.setDiscoverable(it) })
                        }
                        Text(
                            if (me.discoverable) "আপনার আইডি ও নাম পাবলিক তালিকায় দেখা যাচ্ছে।" else "‘বন্ধু চাই’ চালু করলে অন্যরা আপনার আইডি দেখতে ও রিকুয়েস্ট পাঠাতে পারবে।",
                            color = Muted, fontSize = 12.sp
                        )
                    }
                }
            }

            // বন্ধু যোগ
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("বন্ধু যোগ করুন", fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = friendUid,
                                onValueChange = { friendUid = it },
                                placeholder = { Text("বন্ধুর আইডি") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.size(8.dp))
                            Button(
                                onClick = { Session.sendFriendRequest(friendUid) { friendUid = "" } },
                                enabled = friendUid.isNotBlank(),
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // রিকুয়েস্ট
            if (me.requests.isNotEmpty()) {
                item { Text("ফ্রেন্ড রিকুয়েস্ট (${me.requests.size})", fontWeight = FontWeight.Bold, color = Ink) }
                items(me.requests, key = { "req_$it" }) { uid ->
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(Session.displayName(uid), 44.dp)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Session.displayName(uid), fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(uid, fontSize = 12.sp, color = Muted)
                            }
                            Button(onClick = { Session.respondRequest(uid, true) }, shape = RoundedCornerShape(50)) { Text("গ্রহণ") }
                            Spacer(Modifier.size(6.dp))
                            OutlinedButton(onClick = { Session.respondRequest(uid, false) }, shape = RoundedCornerShape(50)) { Text("বাতিল") }
                        }
                    }
                }
            }

            // বন্ধু এবং বন্ধু হতে আগ্রহী ইউজার — দুটি ট্যাব
            item {
                TabRow(selectedTabIndex = peopleTab, containerColor = Color.White, contentColor = Accent) {
                    Tab(selected = peopleTab == 0, onClick = { peopleTab = 0 }, text = { Text("বন্ধুরা (${me.friends.size})") })
                    Tab(selected = peopleTab == 1, onClick = { peopleTab = 1; Session.refreshDiscoverableUsers() }, text = { Text("বন্ধু চাই (${Session.discoverableUsers.size})") })
                }
            }
            if (peopleTab == 0) {
                item {
                    OutlinedTextField(value = friendSearch, onValueChange = { friendSearch = it },
                        placeholder = { Text("নাম বা আইডি দিয়ে বন্ধু খুঁজুন") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                }
                if (me.friends.isEmpty()) item { Text("এখনো কাউকে যোগ করেননি", color = Muted) }
                else if (filteredFriends.isEmpty()) item { Text("এই নামে বা আইডিতে কোনো বন্ধু পাওয়া যায়নি", color = Muted) }
                items(filteredFriends, key = { "fr_$it" }) { uid ->
                    val unread = Session.unread[uid] ?: 0
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { Session.openConversation(uid) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(Session.displayName(uid), 48.dp, online = Session.online[uid] == true, photoUrl = Session.profilePhotos[uid])
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Session.displayName(uid), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                                Text(if (Session.online[uid] == true) "অনলাইন" else uid, fontSize = 12.sp, color = if (Session.online[uid] == true) Success else Muted)
                            }
                            if (unread > 0) Box(Modifier.clip(CircleShape).background(Accent).padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) { Text(unread.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            } else {
                item { Text("যারা বন্ধু হতে আগ্রহী", fontWeight = FontWeight.SemiBold, color = Ink) }
                if (Session.discoverableUsers.isEmpty()) item { Text("এখনো কেউ ‘বন্ধু চাই’ চালু করেননি।", color = Muted) }
                items(Session.discoverableUsers.filter { it.uid !in me.friends && it.uid !in me.requests && it.uid != me.uid }, key = { "discover_${it.uid}" }) { person ->
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(person.username, 46.dp, photoUrl = person.profilePhoto)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) { Text(person.username, fontWeight = FontWeight.SemiBold, color = Ink); Text(person.uid, color = Muted, fontSize = 12.sp) }
                            Button(onClick = { Session.sendFriendRequest(person.uid) {} }, shape = RoundedCornerShape(50)) { Text("বন্ধু হন") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
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
                    Text("শুধু যাকে বন্ধু করতে চান, তাকেই এই QR দেখান।", color = Muted, fontSize = 12.sp)
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
