package com.karakept.app.data.cache

import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import imageLoaderContext

// Lives with the app rather than in :core:data: the image loader is set up here.
@OptIn(ExperimentalCoilApi::class)
object CoilThumbnailCache : ThumbnailCache {
    private fun diskCache() = SingletonImageLoader.get(imageLoaderContext()).diskCache

    override fun sizeBytes(): Long = diskCache()?.size ?: 0L

    override fun clear() {
        diskCache()?.clear()
    }
}
