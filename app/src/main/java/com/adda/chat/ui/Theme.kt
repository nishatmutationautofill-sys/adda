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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.adda.chat.data.Api

// ---- আড্ডা নীল-সাদা থিম (হোম স্ক্রিনের হেডার থেকে নেওয়া) ----
val Accent = Color(0xFF6654F1)
val AccentLight = Color(0xFF8B7CFF)
val Navy = Color(0xFF30256F)
val Canvas = Color(0xFFF8F7FF)
val Ink = Color(0xFF211B43)
val Muted = Color(0xFF77718F)
val Line = Color(0xFFE7E2FA)
val Them = Color(0xFFF0EDFF)
val ChipBorder = Color(0xFFDED7FF)
val Danger = Color(0xFFE5484D)
val Success = Color(0xFF1FA86A)
val Sunny = Color(0xFFFFB02E)
val BubbleBrush: Brush = SolidColor(Accent)
/** অনলাইন বন্ধুদের রিং */
val StoryRing = Brush.sweepGradient(listOf(Accent, Color(0xFFB7AEFF), Accent))
/** চ্যাটের পেছনের হালকা নীল ব্যাকগ্রাউন্ড */
val ChatBackdrop = Brush.verticalGradient(listOf(Canvas, Color(0xFFF0EDFF)))

/** প্রোফাইল ছবি না থাকলে নাম অনুযায়ী নীল-ঘরানার আলাদা আলাদা রং */
private val avatarPalette = listOf(
    listOf(Color(0xFF6654F1), Color(0xFF9A8FFF)),
    listOf(Color(0xFF6D5CE7), Color(0xFF8E80FF)),
    listOf(Color(0xFF8A63F6), Color(0xFF6654F1)),
    listOf(Color(0xFF6E70D8), Color(0xFF6654F1)),
    listOf(Color(0xFF30256F), Color(0xFF8B7CFF)),
    listOf(Color(0xFF6654F1), Color(0xFFC0B8FF))
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
