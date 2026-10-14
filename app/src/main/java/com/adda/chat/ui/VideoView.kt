package com.adda.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adda.chat.call.CallManager
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/** WebRTC ভিডিও ট্র্যাক দেখানোর Compose ওয়াপার */
@Composable
fun VideoView(track: VideoTrack?, mirror: Boolean, overlay: Boolean, modifier: Modifier = Modifier) {
    val rendererState = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(CallManager.eglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                setZOrderMediaOverlay(overlay)
                rendererState.value = this
            }
        }
    )

    val renderer = rendererState.value
    DisposableEffect(track, renderer) {
        if (track != null && renderer != null) {
            try { track.addSink(renderer) } catch (_: Exception) {}
        }
        onDispose {
            if (track != null && renderer != null) {
                try { track.removeSink(renderer) } catch (_: Exception) {}
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try { rendererState.value?.release() } catch (_: Exception) {}
        }
    }
}
