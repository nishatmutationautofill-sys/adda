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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adda.chat.data.Session

/** মূল স্ক্রিন: বন্ধু তালিকা / আগ্রহী তালিকা — নিচে ভাসমান পিল নেভিগেশন */
@Composable
fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(0) }
    val totalUnread = Session.unread.values.sum()
    val requestCount = Session.me?.requests?.size ?: 0

    Column(Modifier.fillMaxSize().background(Canvas)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                2 -> GamesScreen()
                3 -> DownloadAppScreen()
                4 -> CallsScreen()
                else -> HomeScreen(tab = tab, onGoPeople = { tab = 1 })
            }
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp)) {
            Surface(
                shape = RoundedCornerShape(34.dp),
                color = Color.White,
                shadowElevation = 20.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NavPill(Icons.Filled.People, "বন্ধু", tab == 0, totalUnread, Modifier.weight(1f)) { tab = 0 }
                    NavPill(Icons.Filled.PersonAdd, "আগ্রহী", tab == 1, requestCount, Modifier.weight(1f)) { tab = 1 }
                    NavPill(Icons.Filled.SportsEsports, "গেম", tab == 2, 0, Modifier.weight(1f)) { tab = 2 }
                    NavPill(Icons.Filled.Download, "ডাউনলোড", tab == 3, 0, Modifier.weight(1f)) { tab = 3 }
                    NavPill(Icons.Filled.Call, "কল", tab == 4, 0, Modifier.weight(1f)) { tab = 4 }
                }
            }
        }
    }
}

@Composable
private fun NavPill(icon: ImageVector, label: String, selected: Boolean, badge: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(if (selected) BubbleBrush else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgedBox(badge = {
            if (badge > 0) Badge(containerColor = if (selected) Color.White else Danger, contentColor = if (selected) Danger else Color.White) {
                Text(if (badge > 99) "99+" else badge.toString())
            }
        }) {
            Icon(icon, contentDescription = label, tint = if (selected) Color.White else Muted, modifier = Modifier.size(24.dp))
        }
        if (selected) {
            Spacer(Modifier.size(8.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}
