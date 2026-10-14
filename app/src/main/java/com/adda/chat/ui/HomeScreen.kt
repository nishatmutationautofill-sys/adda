package com.adda.chat.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var menuOpen by remember { mutableStateOf(false) }
    var friendUid by remember { mutableStateOf("") }
    var friendSearch by remember { mutableStateOf("") }
    val filteredFriends = me.friends.filter { uid ->
        Session.displayName(uid).contains(friendSearch.trim(), ignoreCase = true) ||
            uid.contains(friendSearch.trim(), ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().background(Canvas)) {
        // হেডার
        Surface(color = Color.White, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("আড্ডা", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Text(
                        Session.connectionLabel,
                        fontSize = 12.sp,
                        color = if (Session.socketConnected) Success else Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "মেনু") }
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
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(me.username, 52.dp)
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

            // বন্ধু যোগ
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
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

            // বন্ধুদের তালিকা ও সার্চ
            item {
                Text("বন্ধুরা (${me.friends.size})", fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = friendSearch,
                    onValueChange = { friendSearch = it },
                    placeholder = { Text("নাম বা আইডি দিয়ে বন্ধু খুঁজুন") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }
            if (me.friends.isEmpty()) {
                item { Text("এখনো কাউকে যোগ করেননি", color = Muted) }
            } else if (filteredFriends.isEmpty()) {
                item { Text("এই নামে বা আইডিতে কোনো বন্ধু পাওয়া যায়নি", color = Muted) }
            }
            items(filteredFriends, key = { "fr_$it" }) { uid ->
                val unread = Session.unread[uid] ?: 0
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { Session.openConversation(uid) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Session.displayName(uid), 48.dp, online = Session.online[uid] == true)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Session.displayName(uid), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                            Text(
                                if (Session.online[uid] == true) "অনলাইন" else uid,
                                fontSize = 12.sp,
                                color = if (Session.online[uid] == true) Success else Muted
                            )
                        }
                        if (unread > 0) {
                            Box(
                                Modifier.clip(CircleShape).background(Accent).padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(unread.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
        }
    }
}
