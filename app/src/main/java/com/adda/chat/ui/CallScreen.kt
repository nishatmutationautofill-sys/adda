package com.adda.chat.ui

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.adda.chat.call.CallManager
import com.adda.chat.call.CallManager.Phase
import com.adda.chat.data.Session
import kotlinx.coroutines.delay

@Composable
fun CallScreen(requestCallPermission: (kind: String, action: () -> Unit) -> Unit) {
    BackHandler(enabled = true) { /* কল চলাকালে ব্যাক বাটন বন্ধ */ }
    val context = LocalContext.current
    val screenShareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            try {
                // FLAG_SECURE blocks Android MediaProjection, so clear it only after explicit user consent.
                (context as? Activity)?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                com.adda.chat.service.ScreenShareService.start(context, result.resultCode, data)
            } catch (_: Exception) {
                Session.toast("স্ক্রিন শেয়ার সার্ভিস চালু করা যায়নি")
            }
        } else {
            Session.toast("স্ক্রিন শেয়ারের অনুমতি দেওয়া হয়নি")
        }
    }

    // কলের সময় স্ট্যাটাস বারের আইকন সাদা
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = true
            // Screen capture ends with the call screen; restore screenshot protection.
            window?.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val phase = CallManager.phase
    val name = Session.displayName(CallManager.peerUid)
    val isVideo = CallManager.callKind == "video"

    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(phase, CallManager.connectedAt) {
        while (phase == Phase.Connected) {
            seconds = (SystemClock.elapsedRealtime() - CallManager.connectedAt) / 1000
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0F2A4A))) {
        if (isVideo && phase != Phase.Incoming) {
            VideoView(track = CallManager.remoteVideo, mirror = false, overlay = false, modifier = Modifier.fillMaxSize())
            if (CallManager.localVideo != null) {
                VideoView(
                    track = CallManager.localVideo,
                    mirror = true,
                    overlay = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 72.dp, end = 14.dp)
                        .size(width = 108.dp, height = 144.dp)
                        .border(2.dp, Color.White)
                )
            }
        }

        // উপরে নাম ও অবস্থা
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isVideo || phase == Phase.Incoming) {
                Spacer(Modifier.height(48.dp))
                Avatar(name, 112.dp)
                Spacer(Modifier.height(16.dp))
            }
            Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (phase == Phase.Connected) "%02d:%02d".format(seconds / 60, seconds % 60) else CallManager.status,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp
            )
        }

        // নিচের বাটন
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 32.dp)) {
            if (phase == Phase.Incoming) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(Icons.Filled.CallEnd, Color(0xFFE5484D), "প্রত্যাখ্যান", 68.dp) { CallManager.declineCall() }
                    RoundButton(
                        if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                        Color(0xFF22B573), "গ্রহণ", 68.dp
                    ) { requestCallPermission(CallManager.callKind) { CallManager.acceptCall() } }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(
                        if (CallManager.micMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        if (CallManager.micMuted) Color.White else Color(0x33FFFFFF),
                        "মাইক", 56.dp,
                        tint = if (CallManager.micMuted) Color.Black else Color.White
                    ) { CallManager.toggleMute() }
                    RoundButton(
                        Icons.Filled.VolumeUp,
                        if (CallManager.speakerOn) Color.White else Color(0x33FFFFFF),
                        "স্পিকার", 56.dp,
                        tint = if (CallManager.speakerOn) Color.Black else Color.White
                    ) { CallManager.toggleSpeaker() }
                    if (isVideo) {
                        if (phase == Phase.Connected && !CallManager.screenSharing) {
                            RoundButton(Icons.Filled.Cast, Color(0xFF1677FF), "স্ক্রিন শেয়ার", 56.dp) {
                                val manager = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenShareLauncher.launch(manager.createScreenCaptureIntent())
                            }
                        }
                        RoundButton(
                            if (CallManager.camOff) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                            if (CallManager.camOff) Color.White else Color(0x33FFFFFF),
                            "ক্যামেরা", 56.dp,
                            tint = if (CallManager.camOff) Color.Black else Color.White
                        ) { CallManager.toggleCamera() }
                        RoundButton(Icons.Filled.FlipCameraAndroid, Color(0x33FFFFFF), "ক্যামেরা বদল", 56.dp) { CallManager.switchCamera() }
                    }
                    RoundButton(Icons.Filled.CallEnd, Color(0xFFE5484D), "কল কাটুন", 64.dp) {
                        CallManager.endCall(true)
                        Session.toast("কল কেটে দেওয়া হয়েছে")
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    background: Color,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(size)) {
            Icon(icon, contentDescription = description, tint = tint)
        }
    }
}
