package com.karakept.app.ui.utils

import com.fleeksoft.ksoup.nodes.Document

/**
 * LRU cache for parsed Ksoup Documents, keyed by bookmark ID.
 * Scoped to BookmarkViewerScreenModel lifetime -- cleaned up when the
 * ScreenModel is disposed.
 *
 * Uses LinkedHashMap with accessOrder=true for O(1) LRU eviction.
 */
class ParsedDocumentCache(private val maxSize: Int = 5) {
    private val cache = LinkedHashMap<Long, Document>(maxSize + 1, 0.75f, true)

    fun get(bookmarkId: Long): Document? = cache[bookmarkId]

    fun put(bookmarkId: Long, document: Document) {
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
