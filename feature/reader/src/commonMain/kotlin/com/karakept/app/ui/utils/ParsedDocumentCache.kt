package com.karakept.app.ui.utils

import com.karakept.app.ui.components.reader.PreparedReaderDocument

/**
 * LRU cache for prepared reader documents, keyed by bookmark ID.
 * Only read and written from the main thread: preparing happens in the background,
 * the cache is consulted before and filled after.
 * Scoped to BookmarkViewerScreenModel lifetime -- cleaned up when the
 * ScreenModel is disposed.
 *
 * Uses LinkedHashMap with accessOrder=true for O(1) LRU eviction.
 */
class ParsedDocumentCache(private val maxSize: Int = 5) {
    private val cache = LinkedHashMap<Long, PreparedReaderDocument>(maxSize + 1, 0.75f, true)

    fun get(bookmarkId: Long): PreparedReaderDocument? = cache[bookmarkId]

    /** The entry for [bookmarkId] only if it was prepared from [html]; a re-crawl or a refresh misses. */
    fun get(bookmarkId: Long, html: String): PreparedReaderDocument? =
        cache[bookmarkId]?.takeIf { it.html == html }

    fun put(bookmarkId: Long, document: PreparedReaderDocument) {
        cache[bookmarkId] = document
        if (cache.size > maxSize) {
            val oldest = cache.keys.iterator().next()
            cache.remove(oldest)
        }
    }

    fun remove(bookmarkId: Long) { cache.remove(bookmarkId) }

    fun clear() { cache.clear() }

    val size: Int get() = cache.size
}
