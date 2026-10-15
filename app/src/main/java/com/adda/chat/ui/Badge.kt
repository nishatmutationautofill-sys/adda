package com.adda.chat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** সক্রিয়তার সম্মানসূচক ব্যাচ: ১ সক্রিয়, ২ নিয়মিত, ৩ সম্মানিত, ৪ আড্ডা রত্ন */
private val BADGE_NAMES = listOf("", "সক্রিয়", "নিয়মিত", "সম্মানিত", "আড্ডা রত্ন")

fun badgeName(level: Int): String = BADGE_NAMES.getOrElse(level) { "" }

/** ছোট শিল্ড আইকন — স্তর অনুযায়ী রং বদলায় (লেভেল ০ হলে কিছুই আঁকে না) */
@Composable
fun BadgeIcon(level: Int, iconSize: Dp = 16.dp, modifier: Modifier = Modifier) {
    if (level <= 0) return
    val fill = when (level) { 1 -> Color.White; 2 -> Accent; else -> Navy }
    val stroke = if (level >= 3) Navy else Accent
    val star = when { level >= 4 -> Color(0xFFFFC83D); level == 3 -> Color.White; else -> Color.Transparent }
    Canvas(modifier.size(iconSize)) {
        val w = this.size.width
        val h = this.size.height
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            lineTo(w * 0.92f, h * 0.2f)
            lineTo(w * 0.92f, h * 0.52f)
            cubicTo(w * 0.92f, h * 0.78f, w * 0.7f, h * 0.92f, w * 0.5f, h * 0.98f)
            cubicTo(w * 0.3f, h * 0.92f, w * 0.08f, h * 0.78f, w * 0.08f, h * 0.52f)
            lineTo(w * 0.08f, h * 0.2f)
            close()
        }
        drawPath(shield, fill)
        drawPath(shield, stroke, style = Stroke(width = w * 0.1f))
        if (level >= 3) {
            val cx = w * 0.5f
            val cy = h * 0.5f
            val outer = w * 0.24f
            val inner = outer * 0.45f
            val starPath = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outer else inner
                val a = Math.toRadians(-90.0 + i * 36.0)
                val x = cx + (r * cos(a)).toFloat()
                val y = cy + (r * sin(a)).toFloat()
                if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
            }
            starPath.close()
            drawPath(starPath, star)
        }
    }
}

/** আইকন + স্তরের নাম, প্রোফাইল পপআপের জন্য */
@Composable
fun BadgeLabel(level: Int) {
    if (level <= 0) return
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Them).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BadgeIcon(level, 16.dp)
        Text(badgeName(level), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Navy)
    }
}
