package com.karakept.app.utils

import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import imageLoaderContext

/**
 * The image loader's disk cache: list thumbnails and any other image fetched from the network.
 * Not an offline copy — anything in it is downloaded again on demand — but on a large device it
 * grows to 250 MB, so storage shows it apart from the rest of the app and "Clean up now" empties it.
 */
interface ThumbnailCache {
    fun sizeBytes(): Long
    fun clear()
}

object NoThumbnailCache : ThumbnailCache {
    override fun sizeBytes() = 0L
    override fun clear() {}
}

@OptIn(ExperimentalCoilApi::class)
object CoilThumbnailCache : ThumbnailCache {
    private fun diskCache() = SingletonImageLoader.get(imageLoaderContext()).diskCache

    override fun sizeBytes(): Long = diskCache()?.size ?: 0L

    override fun clear() {
        diskCache()?.clear()
    }
}
