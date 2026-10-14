package com.adda.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
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

// ---- Gen-Z রঙের প্যালেট: ইলেকট্রিক ভায়োলেট → হট পিংক ----
val Accent = Color(0xFF7B3FF2)
val AccentLight = Color(0xFFFF4F9A)
val Canvas = Color(0xFFF8F4FF)
val Ink = Color(0xFF1B1633)
val Muted = Color(0xFF8B84A8)
val Line = Color(0xFFECE5FB)
val Them = Color(0xFFF1E9FF)
val Danger = Color(0xFFFF3B6B)
val Success = Color(0xFF19C37D)
val Sunny = Color(0xFFFFB02E)
val BubbleBrush = Brush.linearGradient(listOf(Color(0xFF7B3FF2), Color(0xFFB04DF0), Color(0xFFFF4F9A)))
/** স্টোরি-স্টাইল রিং (অনলাইন বন্ধুদের জন্য) */
val StoryRing = Brush.sweepGradient(listOf(Color(0xFFFF4F9A), Color(0xFFFFB02E), Color(0xFF7B3FF2), Color(0xFFFF4F9A)))
/** চ্যাটের পেছনের হালকা রঙিন ব্যাকগ্রাউন্ড */
val ChatBackdrop = Brush.verticalGradient(listOf(Color(0xFFF8F4FF), Color(0xFFFFF0F7)))

/** প্রোফাইল ছবি না থাকলে নাম অনুযায়ী আলাদা আলাদা রঙিন গ্রেডিয়েন্ট */
private val avatarPalette = listOf(
    listOf(Color(0xFF7B3FF2), Color(0xFFFF4F9A)),
    listOf(Color(0xFF00B8D9), Color(0xFF7B3FF2)),
    listOf(Color(0xFFFF8A3D), Color(0xFFFF4F9A)),
    listOf(Color(0xFF19C37D), Color(0xFF00B8D9)),
    listOf(Color(0xFFFFB02E), Color(0xFFFF6B6B)),
    listOf(Color(0xFF4F7CFF), Color(0xFF9B5CFF))
)
fun avatarBrush(name: String): Brush =
    Brush.linearGradient(avatarPalette[(name.hashCode() and 0x7fffffff) % avatarPalette.size])

private val AddaTypography = Typography(
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
)

private val AddaShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun AddaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            primaryContainer = Them,
            onPrimaryContainer = Accent,
            secondary = AccentLight,
            background = Canvas,
            surface = Color.White,
            surfaceVariant = Them,
            onSurface = Ink,
            onSurfaceVariant = Muted,
            onBackground = Ink,
            outline = Line,
            error = Danger
        ),
        typography = AddaTypography,
        shapes = AddaShapes,
        content = content
    )
}

@Composable
fun Avatar(
    name: String, size: Dp = 44.dp, online: Boolean = false, modifier: Modifier = Modifier,
    photoUrl: String? = null, ring: Boolean = false
) {
    Box(modifier = modifier.size(size)) {
        val inner = if (ring) Modifier.fillMaxSize().border(2.5.dp, StoryRing, CircleShape).padding(4.dp) else Modifier.fillMaxSize()
        Box(modifier = inner.clip(CircleShape).background(avatarBrush(name)), contentAlignment = Alignment.Center) {
            if (!photoUrl.isNullOrBlank()) {
                val ctx = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(Api.absoluteUrl(photoUrl)).crossfade(true).build(),
                    contentDescription = "$name প্রোফাইল ছবি",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (size.value * 0.42f).sp
                )
            }
        }
        if (online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(Success)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}
