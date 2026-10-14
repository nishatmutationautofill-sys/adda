package com.adda.chat.util

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.IOException

/**
 * পাঠানোর আগে ভিডিও ছোট করে: MP4 / H.264, সর্বোচ্চ 480p, 30 fps।
 * H.265 (libx265) ফোনে অনেক ভারী ও অস্থির ছিল, এটাই অ্যাপ ক্র্যাশের কারণ — তাই H.264।
 * কনভার্ট ব্যর্থ হলে null দেয়; তখন কলার আসল ফাইলই পাঠায়, অ্যাপ ক্র্যাশ করে না।
 */
object VideoTranscoder {
    private const val MAX_INPUT_DURATION_MS = 60L * 1000L
    private const val MAX_OUTPUT_BYTES = 50L * 1024L * 1024L

    data class Result(val file: File, val durationMs: Long)

    fun transcode(context: Context, sourceUri: Uri, durationMs: Long): Result? {
        require(durationMs in 1..MAX_INPUT_DURATION_MS) {
            if (durationMs > MAX_INPUT_DURATION_MS) "১ মিনিটের বেশি ভিডিও পাঠানো যাবে না" else "ভিডিওর দৈর্ঘ্য পড়া যায়নি"
        }
        val dir = File(context.cacheDir, "video_transcode").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val input = File.createTempFile("input_", ".video", dir)
        val output = File.createTempFile("adda_", ".mp4", dir)
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { src ->
                input.outputStream().use { dst -> src.copyTo(dst) }
            } ?: throw IOException("ভিডিও ফাইল পড়া যাচ্ছে না")

            val args = arrayOf(
                "-y", "-i", input.absolutePath,
                "-t", "60",
                "-vf", "scale=w='min(854,iw)':h='min(480,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,fps=30",
                "-c:v", "libx264", "-preset", "ultrafast", "-crf", "28",
                "-maxrate", "900k", "-bufsize", "1800k",
                "-threads", "2",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "64k", "-ac", "2",
                "-movflags", "+faststart",
                output.absolutePath
            )
            val session = FFmpegKit.executeWithArguments(args)
            if (!ReturnCode.isSuccess(session.returnCode) || !output.exists() || output.length() <= 0L ||
                output.length() > MAX_OUTPUT_BYTES) {
                output.delete()
                null
            } else Result(output, durationMs)
        } catch (e: IllegalArgumentException) {
            output.delete()
            throw e
        } catch (_: Throwable) {
            // UnsatisfiedLinkError / OutOfMemoryError সহ সব — ক্র্যাশ নয়, আসল ফাইলে ফিরে যাওয়া
            runCatching { output.delete() }
            null
        } finally {
            runCatching { input.delete() }
        }
    }
}
