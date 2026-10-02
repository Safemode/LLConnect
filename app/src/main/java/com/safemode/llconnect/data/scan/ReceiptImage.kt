package com.safemode.llconnect.data.scan

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File

/** Image downscaling, JPEG encoding, and temp-file handoff for scanned receipts. */
object ReceiptImage {

    /**
     * Downscales [bitmap] so its longest edge is at most [maxEdge], then encodes JPEG at
     * [quality]. Keeps receipts legible while holding uploads to a few hundred KB so server
     * storage isn't overloaded by full-resolution camera photos.
     */
    fun compress(bitmap: Bitmap, maxEdge: Int = 2048, quality: Int = 75): ByteArray {
        val scaled = downscale(bitmap, maxEdge)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bitmap) scaled.recycle()
        return out.toByteArray()
    }

    private fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longEdge
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** Writes [bytes] to a uniquely named JPEG in the cache and returns its path. */
    fun writeToCache(context: Context, bytes: ByteArray): String {
        val dir = File(context.cacheDir, "receipt_scans").apply { mkdirs() }
        val file = File(dir, "receipt-${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
