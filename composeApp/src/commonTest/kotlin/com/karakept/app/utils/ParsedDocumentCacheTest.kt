package com.karakept.app.utils

import com.fleeksoft.ksoup.Ksoup
import com.karakept.app.ui.utils.ParsedDocumentCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [ParsedDocumentCache] LRU eviction behavior.
 */
class ParsedDocumentCacheTest {

    private fun createCache(maxSize: Int = 5) = ParsedDocumentCache(maxSize)

    private fun parseDoc(index: Int) = Ksoup.parse("<html><body>test $index</body></html>")

    @Test
    fun getMissingKeyReturnsNull() {
        val cache = createCache()
        assertNull(cache.get(999L))
    }

    @Test
    fun putThenGetReturnsCachedDocument() {
        val cache = createCache()
        val doc = parseDoc(1)
        cache.put(1L, doc)
        val result = cache.get(1L)
        assertNotNull(result)
        assertEquals(doc, result)
    }

    @Test
    fun inserting6thItemEvictsLeastRecentlyUsed() {
        val cache = createCache(maxSize = 5)
        // Insert 5 entries: ids 1..5
        for (i in 1..5) {
            cache.put(i.toLong(), parseDoc(i))
        }
        assertEquals(5, cache.size)

        // Insert 6th entry -- should evict id 1 (least recently used)
        cache.put(6L, parseDoc(6))
        assertEquals(5, cache.size)
        assertNull(cache.get(1L), "Entry 1 should have been evicted")
        assertNotNull(cache.get(6L), "Entry 6 should be present")
    }

    @Test
    fun accessingEntryMakesItMostRecentlyUsed() {
        val cache = createCache(maxSize = 5)
        // Insert 5 entries: ids 1..5
        for (i in 1..5) {
            cache.put(i.toLong(), parseDoc(i))
        }

        // Access entry 1, making it most-recently-used
        cache.get(1L)

        // Insert 6th entry -- should evict id 2 (now the LRU), not id 1
        cache.put(6L, parseDoc(6))
        assertNotNull(cache.get(1L), "Entry 1 should survive (was accessed)")
        assertNull(cache.get(2L), "Entry 2 should have been evicted (LRU)")
    }

    @Test
    fun clearRemovesAllEntries() {
        val cache = createCache()
        for (i in 1..3) {
            cache.put(i.toLong(), parseDoc(i))
        }
        assertEquals(3, cache.size)

        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get(1L))
        assertNull(cache.get(2L))
        assertNull(cache.get(3L))
    }

    @Test
    fun removeDeletesSpecificEntry() {
        val cache = createCache()
        cache.put(1L, parseDoc(1))
        cache.put(2L, parseDoc(2))
        assertEquals(2, cache.size)

        cache.remove(1L)
        assertEquals(1, cache.size)
        assertNull(cache.get(1L))
        assertNotNull(cache.get(2L))
    }
}
