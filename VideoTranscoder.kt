package com.adda.chat.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * পাঠানোর আগে ভিডিও কমপ্রেস করে: MP4 / H.264, সর্বোচ্চ 720p, স্ট্যান্ডার্ড বিটরেট ~2.5 Mbps।
 * Media3 Transformer ফোনের হার্ডওয়্যার এনকোডার ব্যবহার করে — দ্রুত, কম ব্যাটারি, APK ছোট।
 * কনভার্ট ব্যর্থ হলে null দেয়; তখন কলার আসল ফাইলই পাঠায়, অ্যাপ ক্র্যাশ করে না।
 */
object VideoTranscoder {
    private const val TARGET_HEIGHT = 720
    private const val VIDEO_BITRATE = 2_500_000 // 720p-এর জন্য স্ট্যান্ডার্ড
    private const val MIN_TIMEOUT_MS = 10L * 60L * 1000L

    data class Result(val file: File, val durationMs: Long)

    suspend fun transcode(context: Context, sourceUri: Uri, durationMs: Long): Result? {
        require(durationMs > 0) { "ভিডিওর দৈর্ঘ্য পড়া যায়নি" }
        // দৈর্ঘ্যের কোনো সীমা নেই; লম্বা ভিডিওর জন্য টাইমআউট দৈর্ঘ্য অনুযায়ী বাড়ে
        val timeoutMs = maxOf(MIN_TIMEOUT_MS, durationMs * 4)
        val dir = File(context.cacheDir, "video_transcode").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val output = File(dir, "adda_${System.currentTimeMillis()}.mp4")
        return try {
            val height = withContext(Dispatchers.IO) { displayHeight(context, sourceUri) }
            val ok = withTimeoutOrNull(timeoutMs) { runTransformer(context, sourceUri, output, height) } ?: false
            if (ok && output.exists() && output.length() > 0) Result(output, durationMs)
            else {
                runCatching { output.delete() }
                null
            }
        } catch (e: CancellationException) {
            runCatching { output.delete() }
            throw e
        } catch (_: Throwable) {
            runCatching { output.delete() }
            null
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun runTransformer(ctx: Context, src: Uri, out: File, displayHeight: Int): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val videoEffects = ArrayList<Effect>()
                // ভিডিও ৭২০p করা হয়; ছোট ভিডিও জোর করে বড় করা হয় না (মান নষ্ট হয়, সাইজ বাড়ে)
                if (displayHeight > TARGET_HEIGHT) videoEffects.add(Presentation.createForHeight(TARGET_HEIGHT))
                val edited = EditedMediaItem.Builder(MediaItem.fromUri(src))
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
                val encoder = DefaultEncoderFactory.Builder(ctx)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build())
                    .build()
                val transformer = Transformer.Builder(ctx)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoder)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()
                cont.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { runCatching { transformer.cancel() } }
                }
                try {
                    transformer.start(edited, out.absolutePath)
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(false)
                }
            }
        }

    /** ঘোরানো (rotation) হিসাব করে ভিডিওর আসল দেখানোর উচ্চতা */
    private fun displayHeight(ctx: Context, uri: Uri): Int = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) w else h
        } finally {
            r.release()
        }
    } catch (_: Throwable) {
        0
    }
}
