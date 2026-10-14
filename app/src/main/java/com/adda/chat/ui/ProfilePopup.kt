package com.adda.chat.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adda.chat.data.Api

/** প্রোফাইল পপআপে দেখানোর তথ্য */
data class ProfileInfo(
    val uid: String,
    val name: String,
    val photoUrl: String?,
    val online: Boolean? = null,
    /** শুধু নিজের প্রোফাইলে true — "ছবি বদলান" বাটন দেখায় */
    val isMe: Boolean = false
)

/**
 * ভেসে ওঠা (popup) প্রোফাইল কার্ড: বড় ছবি + নাম।
 * বন্ধুর প্রোফাইল ছবিতে ট্যাপ করলে এটি খোলে; বাইরে ট্যাপ করলে বন্ধ হয়।
 */
@Composable
fun ProfilePopup(info: ProfileInfo, onClose: () -> Unit, onChangePhoto: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.82f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "profilePopupScale"
    )
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, label = "profilePopupAlpha")

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f * alpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color.White,
                shadowElevation = 28.dp,
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 340.dp)
                    .scale(scale)
                    // কার্ডের ভেতরে ট্যাপ করলে বন্ধ হবে না
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            ) {
                Box {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(264.dp).clip(RoundedCornerShape(26.dp)).background(BubbleBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!info.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx)
                                        .data(Api.absoluteUrl(info.photoUrl))
                                        .addHeader("ngrok-skip-browser-warning", "true")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "${info.name} প্রোফাইল ছবি",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    info.name.take(1).uppercase(),
                                    color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            info.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(info.uid, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (info.online != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (info.online) "● অনলাইন" else "অফলাইন",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = if (info.online) Success else Muted
                            )
                        }
                        if (info.isMe && onChangePhoto != null) {
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = onChangePhoto, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("ছবি বদলান")
                            }
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
