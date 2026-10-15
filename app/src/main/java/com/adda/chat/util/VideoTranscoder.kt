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
 * পাঠানোর আগে ভিডিও ছোট করে: MP4 / H.264, সর্বোচ্চ 480p, ~900 kbps।
 * Media3 Transformer ফোনের হার্ডওয়্যার এনকোডার ব্যবহার করে — দ্রুত, কম ব্যাটারি, APK ছোট।
 * কনভার্ট ব্যর্থ হলে null দেয়; তখন কলার আসল ফাইলই পাঠায়, অ্যাপ ক্র্যাশ করে না।
 */
object VideoTranscoder {
    private const val MAX_INPUT_DURATION_MS = 120L * 1000L
    private const val MAX_OUTPUT_BYTES = 50L * 1024L * 1024L
    private const val TIMEOUT_MS = 240_000L

    data class Result(val file: File, val durationMs: Long)

    suspend fun transcode(context: Context, sourceUri: Uri, durationMs: Long): Result? {
        require(durationMs in 1..MAX_INPUT_DURATION_MS) {
            if (durationMs > MAX_INPUT_DURATION_MS) "২ মিনিটের বেশি ভিডিও পাঠানো যাবে না" else "ভিডিওর দৈর্ঘ্য পড়া যায়নি"
        }
        val dir = File(context.cacheDir, "video_transcode").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val output = File(dir, "adda_${System.currentTimeMillis()}.mp4")
        return try {
            val height = withContext(Dispatchers.IO) { displayHeight(context, sourceUri) }
            val ok = withTimeoutOrNull(TIMEOUT_MS) { runTransformer(context, sourceUri, output, height) } ?: false
            if (ok && output.exists() && output.length() in 1..MAX_OUTPUT_BYTES) Result(output, durationMs)
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
                // ছোট ভিডিও বড় করা হবে না; শুধু ৪৮০p-র বেশি হলে নামানো হবে
                if (displayHeight > 480) videoEffects.add(Presentation.createForHeight(480))
                val edited = EditedMediaItem.Builder(MediaItem.fromUri(src))
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
                val encoder = DefaultEncoderFactory.Builder(ctx)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(900_000).build())
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
