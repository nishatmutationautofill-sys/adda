package com.adda.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.adda.chat.data.Api

val Accent = Color(0xFF4D4FE8)
val AccentLight = Color(0xFF3F8CFF)
val Canvas = Color(0xFFF3F4F8)
val Ink = Color(0xFF14161F)
val Muted = Color(0xFF7A8094)
val Line = Color(0xFFE8EAF1)
val Them = Color(0xFFECEEF5)
val Danger = Color(0xFFE5484D)
val Success = Color(0xFF22B573)
val BubbleBrush = Brush.linearGradient(listOf(Color(0xFF5A5CF0), Color(0xFF3F8CFF)))

@Composable
fun AddaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = Canvas,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink
        ),
        content = content
    )
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp, online: Boolean = false, modifier: Modifier = Modifier, photoUrl: String? = null) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(BubbleBrush),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank()) {
                val ctx = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(Api.absoluteUrl(photoUrl)).crossfade(true).build(),
                    contentDescription = "$name প্রোফাইল ছবি",
                    modifier = Modifier.size(size).clip(CircleShape)
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.4f).sp
                )
            }
        }
        if (online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.3f)
                    .clip(CircleShape)
                    .background(Success)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}
