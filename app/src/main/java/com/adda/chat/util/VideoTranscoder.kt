package com.adda.chat.util

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.IOException

/** Converts picked videos to MP4/HEVC, 480p maximum, 30 fps, with mobile-friendly bitrate before encryption/upload. */
object VideoTranscoder {
    private const val MAX_INPUT_DURATION_MS = 60L * 1000L
    private const val MAX_OUTPUT_BYTES = 50L * 1024L * 1024L

    data class Result(val file: File, val durationMs: Long)

    fun transcode(context: Context, sourceUri: Uri, durationMs: Long): Result {
        require(durationMs in 1..MAX_INPUT_DURATION_MS) {
            if (durationMs > MAX_INPUT_DURATION_MS) "১ মিনিটের বেশি ভিডিও পাঠানো যাবে না" else "ভিডিওর দৈর্ঘ্য পড়া যায়নি"
        }
        val dir = File(context.cacheDir, "video_transcode").apply { mkdirs() }
        val input = File.createTempFile("input_", ".video", dir)
        val output = File.createTempFile("adda_", ".mp4", dir)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { src ->
                input.outputStream().use { dst -> src.copyTo(dst) }
            } ?: throw IOException("ভিডিও ফাইল পড়া যাচ্ছে না")

            // MP4 is intentional: WebM does not generally support HEVC/H.265 as a standard codec.
            val args = arrayOf(
                "-y", "-i", input.absolutePath,
                "-t", "60",
                "-vf", "scale=w='min(854,iw)':h='min(480,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,fps=30",
                "-c:v", "libx265", "-tag:v", "hvc1", "-preset", "veryfast", "-b:v", "600k", "-maxrate", "700k", "-bufsize", "1200k",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "64k", "-ac", "2",
                "-movflags", "+faststart",
                output.absolutePath
            )
            val session = FFmpegKit.executeWithArguments(args)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                throw IOException("ভিডিও কনভার্ট করা যায়নি: ${session.failStackTrace ?: session.returnCode}")
            }
            if (!output.exists() || output.length() <= 0L) throw IOException("কনভার্ট করা ভিডিও পাওয়া যায়নি")
            if (output.length() > MAX_OUTPUT_BYTES) throw IOException("কমপ্রেস করার পরও ভিডিও ৫০ MB-এর বেশি। ছোট ভিডিও নির্বাচন করুন")
            return Result(output, durationMs)
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            input.delete()
        }
    }
}
