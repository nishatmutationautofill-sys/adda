package com.adda.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adda.chat.data.Session

/** নিচের নেভিগেশনসহ মূল স্ক্রিন: চ্যাট / মানুষ / প্রোফাইল */
@Composable
fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(0) }
    val totalUnread = Session.unread.values.sum()
    val requestCount = Session.me?.requests?.size ?: 0

    Column(Modifier.fillMaxSize().background(Canvas)) {
        Box(Modifier.weight(1f)) {
            HomeScreen(tab = tab, onGoPeople = { tab = 1 })
        }
        Surface(color = Color.White, shadowElevation = 12.dp) {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Them,
                    unselectedIconColor = Muted, unselectedTextColor = Muted
                )
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                    icon = {
                        BadgedBox(badge = { if (totalUnread > 0) Badge { Text(if (totalUnread > 99) "99+" else totalUnread.toString()) } }) {
                            Icon(Icons.Filled.ChatBubble, contentDescription = "চ্যাট")
                        }
                    },
                    label = { Text("চ্যাট") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                    icon = {
                        BadgedBox(badge = { if (requestCount > 0) Badge { Text(requestCount.toString()) } }) {
                            Icon(Icons.Filled.People, contentDescription = "মানুষ")
                        }
                    },
                    label = { Text("মানুষ") }
                )
            }
        }
    }
}
