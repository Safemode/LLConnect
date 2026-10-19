package com.safemode.llconnect.data.scan

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/** How a scanned receipt is encoded before upload. */
enum class ReceiptQuality {
    /** Downscaled and JPEG-compressed - small uploads, the default. */
    COMPRESSED,

    /** Full resolution, lossless PNG - no quality loss, much larger files. */
    FULL,
}

/** A scanned receipt written to the cache, plus the file extension and MIME type describing it. */
data class CachedReceipt(val path: String, val extension: String, val mimeType: String)

/** Image downscaling, encoding, and temp-file handoff for scanned receipts. */
object ReceiptImage {

    /** The file extension for an encoded receipt at the given quality. */
    fun extensionFor(quality: ReceiptQuality): String =
        if (quality == ReceiptQuality.FULL) "png" else "jpg"

    /** The MIME type for an encoded receipt at the given quality. */
    fun mimeTypeFor(quality: ReceiptQuality): String =
        if (quality == ReceiptQuality.FULL) "image/png" else "image/jpeg"

    /**
     * Encodes [bitmap] according to [quality] straight into a cache file, so the full encoded image
     * is never held in memory at once (a full-resolution PNG can be tens of MB):
     * - [ReceiptQuality.COMPRESSED]: downscales so the longest edge is at most [maxEdge], then
     *   encodes JPEG at [jpegQuality] - legible receipts held to a few hundred KB.
     * - [ReceiptQuality.FULL]: keeps full resolution and encodes lossless PNG - no quality loss, at
     *   the cost of much larger files.
     *
     * Returns the cache file path plus the extension and MIME type that describe it.
     */
    fun encodeToCache(
        context: Context,
        bitmap: Bitmap,
        quality: ReceiptQuality,
        maxEdge: Int = 2048,
        jpegQuality: Int = 75,
    ): CachedReceipt {
        val extension = extensionFor(quality)
        val dir = File(context.cacheDir, "receipt_scans").apply { mkdirs() }
        val file = File(dir, "receipt-${System.currentTimeMillis()}.$extension")
        val source = if (quality == ReceiptQuality.COMPRESSED) downscale(bitmap, maxEdge) else bitmap
        try {
            FileOutputStream(file).buffered().use { out ->
                when (quality) {
                    ReceiptQuality.COMPRESSED -> source.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                    ReceiptQuality.FULL -> source.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        } finally {
            if (source !== bitmap) source.recycle()
        }
        return CachedReceipt(file.absolutePath, extension, mimeTypeFor(quality))
    }

    private fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longEdge
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
