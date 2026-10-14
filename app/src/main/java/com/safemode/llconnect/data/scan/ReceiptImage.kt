package com.safemode.llconnect.data.scan

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File

/** How a scanned receipt is encoded before upload. */
enum class ReceiptQuality {
    /** Downscaled and JPEG-compressed — small uploads, the default. */
    COMPRESSED,

    /** Full resolution, lossless PNG — no quality loss, much larger files. */
    FULL,
}

/** The encoded bytes plus the file extension and MIME type that describe them. */
data class EncodedReceipt(val bytes: ByteArray, val extension: String, val mimeType: String)

/** Image downscaling, encoding, and temp-file handoff for scanned receipts. */
object ReceiptImage {

    /**
     * Encodes [bitmap] according to [quality]:
     * - [ReceiptQuality.COMPRESSED]: downscales so the longest edge is at most [maxEdge], then
     *   encodes JPEG at [jpegQuality] — legible receipts held to a few hundred KB so server storage
     *   isn't overloaded by full-resolution camera photos.
     * - [ReceiptQuality.FULL]: keeps full resolution and encodes lossless PNG — no quality loss, at
     *   the cost of much larger files.
     */
    fun encode(
        bitmap: Bitmap,
        quality: ReceiptQuality,
        maxEdge: Int = 2048,
        jpegQuality: Int = 75,
    ): EncodedReceipt {
        val out = ByteArrayOutputStream()
        return when (quality) {
            ReceiptQuality.COMPRESSED -> {
                val scaled = downscale(bitmap, maxEdge)
                scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                if (scaled !== bitmap) scaled.recycle()
                EncodedReceipt(out.toByteArray(), "jpg", "image/jpeg")
            }
            ReceiptQuality.FULL -> {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                EncodedReceipt(out.toByteArray(), "png", "image/png")
            }
        }
    }

    private fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longEdge
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** Writes [bytes] to a uniquely named file (with the given [extension]) in the cache; returns its path. */
    fun writeToCache(context: Context, bytes: ByteArray, extension: String): String {
        val dir = File(context.cacheDir, "receipt_scans").apply { mkdirs() }
        val file = File(dir, "receipt-${System.currentTimeMillis()}.$extension")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
