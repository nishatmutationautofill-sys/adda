package com.adda.chat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/** আপলোডের জন্য ফাইল তৈরি করে; ছবি হলে imo-র মতো কমপ্রেস করে ছোট JPEG বানায় */
object FileUpload {
    const val MAX_BYTES = 50L * 1024 * 1024
    private const val MAX_SIDE = 1280
    private const val JPEG_QUALITY = 70

    class Prepared(val body: RequestBody, val name: String, val size: Long, val compressed: Boolean)

    fun prepare(ctx: Context, uri: Uri): Prepared {
        val cr = ctx.contentResolver
        var name = "file"
        var size = -1L
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        val mime = cr.getType(uri) ?: "application/octet-stream"
        if (size > MAX_BYTES) throw IllegalArgumentException("সর্বোচ্চ ৫০ MB ফাইল পাঠানো যাবে")

        if (mime.startsWith("image/") && !mime.contains("gif") && !mime.contains("svg")) {
            val bytes = compressImage(ctx, uri, size)
            if (bytes != null) {
                val base = name.substringBeforeLast('.', name)
                return Prepared(bytes.toRequestBody("image/jpeg".toMediaType()), "$base.jpg", bytes.size.toLong(), true)
            }
        }

        val type: MediaType = mime.toMediaType()
        val body = object : RequestBody() {
            override fun contentType(): MediaType = type
            override fun contentLength(): Long = size
            override fun writeTo(sink: BufferedSink) {
                val input: InputStream = cr.openInputStream(uri) ?: throw java.io.IOException("ফাইল পড়া যাচ্ছে না")
                input.source().use { sink.writeAll(it) }
            }
        }
        return Prepared(body, name, size, false)
    }

    /** ছোট না হলে বা কমপ্রেস ব্যর্থ হলে null (তখন আসল ফাইলই যাবে) */
    private fun compressImage(ctx: Context, uri: Uri, originalSize: Long): ByteArray? {
        val cr = ctx.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null

            var sample = 1
            while (max(w, h) / sample > MAX_SIDE * 2) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) } ?: return null

            val rotation = cr.openInputStream(uri)?.use { exifRotation(it) } ?: 0
            val scale = min(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))

            val matrix = Matrix()
            if (rotation != 0) matrix.postRotate(rotation.toFloat())
            if (scale < 1f) matrix.postScale(scale, scale)
            val transformed = if (rotation != 0 || scale < 1f)
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) else decoded

            // স্বচ্ছ PNG এর ব্যাকগ্রাউন্ড সাদা করা
            val flat = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flat)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(transformed, 0f, 0f, null)

            val out = ByteArrayOutputStream()
            flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (transformed !== decoded) transformed.recycle()
            decoded.recycle()
            flat.recycle()

            val bytes = out.toByteArray()
            val untouched = sample == 1 && scale >= 1f && rotation == 0
            if (originalSize > 0 && bytes.size >= originalSize && untouched) null else bytes
        } catch (_: Throwable) {
            null
        }
    }

    private fun exifRotation(input: InputStream): Int {
        return try {
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }
}
