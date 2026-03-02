package com.karakept.app.ui.screens

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [advancePagesUntilItemsFound].
 *
 * These tests guard against the regression where `loadNextPage` would set
 * `_hasMoreItems = false` as soon as a single DB page returned 0 items after
 * client-side filtering, even though later pages might contain matching items.
 */
class PaginationUtilsTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Simulates a DB page that always returns [rawCount] raw rows, 0 filtered. */
    private fun emptyPage(rawCount: Int): Pair<List<String>, Int> = Pair(emptyList(), rawCount)

    /** Simulates a DB page that returns [items] after filtering (same as raw for these tests). */
    private fun itemPage(vararg items: String): Pair<List<String>, Int> =
        Pair(items.toList(), items.size)

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * The simplest happy path: page 0 already has matching items.
     * The loop should return immediately without advancing further.
     */
    @Test
    fun returnsFirstPageItemsWhenAvailable() = runTest {
        val calls = mutableListOf<Int>()
        val result = advancePagesUntilItemsFound(startPage = 0, pageSize = 20) { page ->
            calls += page
            itemPage("a", "b", "c")
        }
        assertEquals(listOf("a", "b", "c"), result.first)
        assertEquals(0, result.second)          // lastPage
        assertFalse(result.third)               // not exhausted (full page)
        assertEquals(listOf(0), calls)          // only page 0 fetched
    }

    /**
     * Core regression test: page 1 is entirely filtered out (20 raw, 0 visible),
     * page 2 has matches. The paginator must NOT stop at page 1.
     */
    @Test
    fun skipsFullyFilteredPagesAndContinuesToNextPage() = runTest {
        val result = advancePagesUntilItemsFound(startPage = 1, pageSize = 20) { page ->
            when (page) {
                1 -> emptyPage(20)              // full DB page, 0 after client-side filter
                2 -> itemPage("x", "y")         // items found on page 2
                else -> emptyPage(0)
            }
        }
        assertEquals(listOf("x", "y"), result.first)
        assertEquals(2, result.second)
        assertFalse(result.third)
    }

    /**
     * Multiple consecutive filtered-out pages: pages 1–4 are all filtered,
     * page 5 contains the first matching items.
     */
    @Test
    fun skipsMultipleConsecutiveFilteredPages() = runTest {
        val result = advancePagesUntilItemsFound(startPage = 1, pageSize = 20) { page ->
            when (page) {
                in 1..4 -> emptyPage(20)        // pages 1-4: full but filtered out
                5 -> itemPage("found")           // page 5: match
                else -> emptyPage(0)
            }
        }
        assertEquals(listOf("found"), result.first)
        assertEquals(5, result.second)
        assertFalse(result.third)
    }

    /**
     * DB is truly exhausted on the first page (rawCount = 0).
     * The loop must return immediately with an empty list and dbExhausted = true.
     */
    @Test
    fun stopsWhenFirstPageIsEmpty() = runTest {
        val calls = mutableListOf<Int>()
        val result = advancePagesUntilItemsFound(startPage = 3, pageSize = 20) { page ->
            calls += page
            emptyPage(0)
        }
        assertEquals(emptyList(), result.first)
        assertEquals(3, result.second)
        assertTrue(result.third)               // dbExhausted
        assertEquals(listOf(3), calls)         // only one fetch
    }

    /**
     * DB exhausted on a partial page (fewer rows than pageSize) after filtering out earlier pages.
     * The loop must stop and signal exhaustion even if the partial page itself yielded no items.
     */
    @Test
    fun stopsOnPartialPageEvenWhenFiltered() = runTest {
        val result = advancePagesUntilItemsFound(startPage = 0, pageSize = 20) { page ->
            when (page) {
                0 -> emptyPage(20)              // full page, all filtered
                1 -> emptyPage(15)              // last partial page, also all filtered
                else -> emptyPage(0)
            }
        }
        assertEquals(emptyList(), result.first)
        assertEquals(1, result.second)
        assertTrue(result.third)               // dbExhausted (15 < 20)
    }

    /**
     * Items found on the last partial page (both items found AND db exhausted).
     * The caller should show the items AND set hasMoreItems = false.
     */
    @Test
    fun returnsItemsFromLastPageAndSignalsExhaustion() = runTest {
        val result = advancePagesUntilItemsFound(startPage = 0, pageSize = 20) { page ->
            when (page) {
                0 -> emptyPage(20)              // full page, all filtered
                1 -> Pair(listOf("last"), 7)    // 7 raw rows, 1 visible → last page
                else -> emptyPage(0)
            }
        }
        assertEquals(listOf("last"), result.first)
        assertEquals(1, result.second)
        assertTrue(result.third)               // dbExhausted (7 < 20)
    }

    /**
     * Exactly pageSize items on the first page with no client-side filtering.
     * Should return items and NOT signal exhaustion (there might be more pages).
     */
    @Test
    fun doesNotSignalExhaustionWhenPageIsFull() = runTest {
        val items = (1..20).map { "item$it" }
        val result = advancePagesUntilItemsFound(startPage = 0, pageSize = 20) { _ ->
            Pair(items, 20)
        }
        assertEquals(items, result.first)
        assertFalse(result.third)              // not exhausted
    }

    /**
     * Ensures startPage is respected: the loop should begin at [startPage] and not page 0.
     * This matters for `loadNextPage` which starts from `currentPage + 1`.
     */
    @Test
    fun respectsStartPage() = runTest {
        val calls = mutableListOf<Int>()
        advancePagesUntilItemsFound(startPage = 5, pageSize = 10) { page ->
            calls += page
            itemPage("item")
        }
        assertEquals(5, calls.first())         // first call is startPage, not 0
    }
}
