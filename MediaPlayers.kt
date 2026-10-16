package com.adda.chat.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.adda.chat.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal fun isAudioFile(name: String?): Boolean {
    val n = name.orEmpty().lowercase()
    return listOf(".m4a", ".mp3", ".aac", ".wav", ".ogg", ".opus").any { n.endsWith(it) }
}

internal fun isVideoFile(name: String?): Boolean {
    val n = name.orEmpty().lowercase()
    return listOf(".mp4", ".webm", ".3gp", ".mov", ".mkv", ".m4v").any { n.endsWith(it) }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** একসাথে একটাই ভয়েস/ভিডিও চলবে */
private object Playback {
    var current: ExoPlayer? = null
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun buildPlayer(ctx: Context): ExoPlayer {
    val headers = HashMap<String, String>()
    headers["ngrok-skip-browser-warning"] = "true"
    if (Api.token.isNotBlank()) headers["Authorization"] = "Bearer ${Api.token}"
    val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
    return ExoPlayer.Builder(ctx)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http)))
        .build()
}

private fun openRetriever(r: MediaMetadataRetriever, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme == "file") r.setDataSource(uri.path) else r.setDataSource(url, HashMap<String, String>())
}

private fun readDuration(url: String): Long = try {
    val r = MediaMetadataRetriever()
    try {
        openRetriever(r, url)
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } finally {
        r.release()
    }
} catch (_: Throwable) {
    0L
}

/** চ্যাট বাবলের ভেতরে ভয়েস মেসেজ প্লেয়ার: প্লে/পজ, প্রগ্রেস, সময় */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VoiceMessagePlayer(url: String, mine: Boolean) {
    val ctx = LocalContext.current
    var player by remember(url) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember(url) { mutableStateOf(false) }
    var position by remember(url) { mutableStateOf(0L) }
    var duration by remember(url) { mutableStateOf(0L) }

    LaunchedEffect(url) { duration = withContext(Dispatchers.IO) { readDuration(url) } }
    LaunchedEffect(playing, player) {
        while (playing) {
            position = player?.currentPosition ?: 0L
            delay(150)
        }
    }
    DisposableEffect(url) {
        onDispose {
            player?.let {
                if (Playback.current === it) Playback.current = null
                it.release()
            }
        }
    }

    fun toggle() {
        val p = player ?: buildPlayer(ctx).also { np ->
            np.setMediaItem(MediaItem.fromUri(url))
            np.prepare()
            np.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && np.duration > 0) duration = np.duration
                    if (state == Player.STATE_ENDED) {
                        np.pause()
                        np.seekTo(0)
                        position = 0L
                        playing = false
                    }
                }
            })
            player = np
        }
        if (p.isPlaying) p.pause() else {
            Playback.current?.takeIf { it !== p }?.pause()
            Playback.current = p
            p.play()
        }
    }

    val sub = if (mine) Color.White.copy(alpha = 0.85f) else Muted
    Row(Modifier.width(210.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (mine) Color.White.copy(alpha = 0.25f) else Accent)
                .clickable { toggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "পজ" else "প্লে",
                tint = Color.White
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            val frac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                color = if (mine) Color.White else Accent,
                trackColor = if (mine) Color.White.copy(alpha = 0.3f) else Line
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (playing || position > 0) "${formatMs(position)} / ${formatMs(duration)}" else formatMs(duration),
                fontSize = 11.sp, color = sub
            )
        }
    }
}

/** ভিডিও মেসেজ কার্ড: থাম্বনেইল + প্লে বাটন; ট্যাপ করলে অ্যাপের ভেতরেই ফুল-স্ক্রিন প্লেয়ার */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideoMessageCard(url: String, onLongClick: () -> Unit = {}, onDownload: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    var show by remember { mutableStateOf(false) }
    var thumb by remember(url) { mutableStateOf<Bitmap?>(null) }
    var duration by remember(url) { mutableStateOf(0L) }

    LaunchedEffect(url) {
        val result = withContext(Dispatchers.IO) {
            try {
                val r = MediaMetadataRetriever()
                try {
                    openRetriever(r, url)
                    val frame = r.getFrameAtTime(0)
                    val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    Pair(frame, d)
                } finally {
                    r.release()
                }
            } catch (_: Throwable) {
                Pair<Bitmap?, Long>(null, 0L)
            }
        }
        thumb = result.first
        duration = result.second
    }

    val bmp = thumb
    val ratio = if (bmp != null && bmp.height > 0) (bmp.width.toFloat() / bmp.height).coerceIn(0.6f, 1.8f) else 1.4f
    Box(
        Modifier.width(220.dp).aspectRatio(ratio)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0F2A4A))
            .combinedClickable(onClick = { show = true }, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bitmap = bmp.asImageBitmap(), contentDescription = "ভিডিও", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, contentDescription = "প্লে", tint = Color.White, modifier = Modifier.size(32.dp)) }
        if (duration > 0) {
            Text(
                formatMs(duration), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
    if (show) VideoViewerDialog(
        url = url,
        onDownload = onDownload,
        onDelete = onDelete?.let { del -> { show = false; del() } },
        onClose = { show = false }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoViewerDialog(url: String, onDownload: (() -> Unit)?, onDelete: (() -> Unit)?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val exo = remember(url) {
        buildPlayer(ctx).apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exo) { onDispose { exo.release() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { c -> PlayerView(c).apply { player = exo; useController = true } },
                modifier = Modifier.fillMaxSize()
            )
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDownload != null) IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = "ডাউনলোড", tint = Color.White)
                }
                if (onDelete != null) IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "ডিলেট", tint = Color.White)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন", tint = Color.White) }
            }
        }
    }
}
