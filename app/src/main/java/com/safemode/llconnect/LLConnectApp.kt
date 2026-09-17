package com.safemode.llconnect

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okio.Path.Companion.toOkioPath

class LLConnectApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }

    /**
     * Coil uses this loader app-wide. Requests go through an OkHttp client carrying the
     * LubeLogger auth headers, and images are cached in memory and on disk so each one is
     * fetched from the server only once. `respectCacheHeaders(false)` means we reuse the
     * cached copy even though the image route sends no cache-control headers — the image
     * filename is a UUID that changes when the photo is replaced, so the URL stays a safe key.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { Graph.apiProvider.authenticatedImageClient() }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .crossfade(true)
            .build()
}
