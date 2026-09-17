package com.safemode.llconnect.ui.common

import android.content.Context
import coil.imageLoader

/**
 * Clears Coil's in-memory and on-disk image caches so the next image request
 * re-downloads from the server. Used by pull-to-refresh.
 */
fun clearImageCache(context: Context) {
    val loader = context.imageLoader
    loader.memoryCache?.clear()
    loader.diskCache?.clear()
}
