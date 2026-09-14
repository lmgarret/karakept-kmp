package com.karakept.app.ui.screens

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [advancePagesUntilItemsFound].
 *
 * These guard two regressions at once: `loadNextPage` setting `_hasMoreItems = false` as soon as
 * a single DB page returned 0 items after client-side filtering, when later pages still held
 * matches; and the walk addressing rows by counting, which let a row committed above it shift
 * every page below out from under it (#333).
 *
 * The cursor here is a row index — the walk never interprets it, it only carries it back.
 */
class PaginationUtilsTest {

    /** A DB page returning [rawCount] raw rows, none of which survive the filter. */
    private fun emptyPage(rawCount: Int, next: Int? = 0) =
        PageRead<String, Int>(emptyList(), rawCount, next)

    /**
     * A DB page returning [items] after client-side filtering. rawCount defaults to pageSize
     * (20) to represent a full page that still has more behind it — only [items] passed the
     * filter. Pass an explicit rawCount < pageSize to represent the last page.
     */
    private fun itemPage(vararg items: String, rawCount: Int = 20, next: Int? = 0) =
        PageRead(items.toList(), rawCount, next)

    /** The simplest happy path: the first read already has matching items. */
    @Test
    fun returnsFirstPageItemsWhenAvailable() = runTest {
        val calls = mutableListOf<Int?>()
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 0, startCursor = null, pageSize = 20) { after ->
            calls += after
            itemPage("a", "b", "c")
        }
        assertEquals(listOf("a", "b", "c"), result.items)
        assertEquals(0, result.lastPage)
        assertFalse(result.dbExhausted)
        assertEquals(listOf<Int?>(null), calls)
    }

    /**
     * Core regression test: the first read is entirely filtered out (20 raw, 0 visible), the
     * next has matches. The walk must not stop at the empty one.
     */
    @Test
    fun skipsFullyFilteredPagesAndContinuesToNextPage() = runTest {
        var reads = 0
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 1, startCursor = 20, pageSize = 20) { _ ->
            when (reads++) {
                0 -> emptyPage(20, next = 40)
                else -> itemPage("x", "y", next = 60)
            }
        }
        assertEquals(listOf("x", "y"), result.items)
        assertEquals(2, result.lastPage)
        assertFalse(result.dbExhausted)
    }

    /** Four consecutive fully-filtered reads; the fifth contains the first matching items. */
    @Test
    fun skipsMultipleConsecutiveFilteredPages() = runTest {
        var reads = 0
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 1, startCursor = 20, pageSize = 20) { _ ->
            if (reads++ < 4) emptyPage(20) else itemPage("found")
        }
        assertEquals(listOf("found"), result.items)
        assertEquals(5, result.lastPage)
        assertFalse(result.dbExhausted)
    }

    /** The source is exhausted on the first read, so the walk returns at once. */
    @Test
    fun stopsWhenFirstPageIsEmpty() = runTest {
        val calls = mutableListOf<Int?>()
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 3, startCursor = 60, pageSize = 20) { after ->
            calls += after
            emptyPage(0, next = null)
        }
        assertEquals(emptyList(), result.items)
        assertEquals(3, result.lastPage)
        assertTrue(result.dbExhausted)
        assertEquals(listOf<Int?>(60), calls)
    }

    /**
     * Exhausted on a partial page after an earlier page filtered out entirely. The walk must
     * stop and signal exhaustion even though the partial page yielded nothing either.
     */
    @Test
    fun stopsOnPartialPageEvenWhenFiltered() = runTest {
        var reads = 0
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 0, startCursor = null, pageSize = 20) { _ ->
            if (reads++ == 0) emptyPage(20) else emptyPage(15)
        }
        assertEquals(emptyList(), result.items)
        assertEquals(1, result.lastPage)
        assertTrue(result.dbExhausted)
    }

    /** Items found on the last partial page: show them *and* stop paging. */
    @Test
    fun returnsItemsFromLastPageAndSignalsExhaustion() = runTest {
        var reads = 0
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 0, startCursor = null, pageSize = 20) { _ ->
            if (reads++ == 0) emptyPage(20) else itemPage("last", rawCount = 7)
        }
        assertEquals(listOf("last"), result.items)
        assertEquals(1, result.lastPage)
        assertTrue(result.dbExhausted)
    }

    /** A full first page returns its items and does not claim the source has ended. */
    @Test
    fun doesNotSignalExhaustionWhenPageIsFull() = runTest {
        val items = (1..20).map { "item$it" }
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 0, startCursor = null, pageSize = 20) { _ ->
            PageRead(items, 20, 20)
        }
        assertEquals(items, result.items)
        assertFalse(result.dbExhausted)
    }

    /** The page counter begins at [startPage], which `loadNextPage` sets to `currentPage + 1`. */
    @Test
    fun respectsStartPage() = runTest {
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 5, startCursor = 100, pageSize = 10) { _ ->
            itemPage("item")
        }
        assertEquals(5, result.lastPage)
    }

    /**
     * Every read resumes strictly after the previous read's own last row. Counting rows instead
     * is what let a sync shift the walk: rows committed above the read position pushed an equal
     * number past it, into a range a forward-only walk never asks for again (#333).
     */
    @Test
    fun eachReadResumesAfterThePreviousReadsLastRow() = runTest {
        val asked = mutableListOf<Int?>()
        var reads = 0
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 0, startCursor = null, pageSize = 20) { after ->
            asked += after
            when (reads++) {
                0 -> emptyPage(20, next = 117)
                1 -> emptyPage(20, next = 342)
                else -> itemPage("x", next = 590)
            }
        }
        assertEquals(listOf<Int?>(null, 117, 342), asked)
        assertEquals(590, result.nextCursor, "the walk hands back where the next one resumes")
    }

    /**
     * A page whose rows the filter discarded entirely still moves the cursor on. Holding the
     * previous cursor would ask for the very same rows again, forever.
     */
    @Test
    fun aFullyFilteredPageStillAdvancesTheCursor() = runTest {
        val asked = mutableListOf<Int?>()
        var reads = 0
        advancePagesUntilItemsFound<String, Int>(startPage = 0, startCursor = null, pageSize = 20) { after ->
            asked += after
            if (reads++ == 0) emptyPage(20, next = 20) else itemPage("x")
        }
        assertEquals(listOf<Int?>(null, 20), asked)
    }

    /**
     * An empty page reports no last row, so the walk keeps the cursor it already had rather
     * than resetting to the top of the table.
     */
    @Test
    fun anEmptyPageLeavesThePreviousCursorStanding() = runTest {
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 2, startCursor = 40, pageSize = 20) { _ ->
            PageRead<String, Int>(emptyList(), 0, null)
        }
        assertEquals(40, result.nextCursor)
        assertTrue(result.dbExhausted)
    }

    /** A walk that starts at the top and finds nothing has no position to hand on. */
    @Test
    fun anEmptyFirstReadHandsBackNoCursor() = runTest {
        val result = advancePagesUntilItemsFound<String, Int>(startPage = 0, startCursor = null, pageSize = 20) { _ ->
            PageRead<String, Int>(emptyList(), 0, null)
        }
        assertNull(result.nextCursor)
    }

    // -------------------------------------------------------------------------
    // seekWindowPage — how far a fast-scroll jump has to read (#273)
    // -------------------------------------------------------------------------

    /** Nothing is filtered out, so the window needs the target's own page plus the slack one. */
    @Test
    fun seekReachesTheTargetsPageWhenNothingIsFilteredOut() {
        // One page loaded, every row surviving: row 800 sits on page 16.
        val page = seekWindowPage(targetIndex = 800, loadedRows = 50, loadedPage = 0, pageSize = 50)
        assertEquals(17, page)
        assertTrue((page + 1) * 50 > 800)
    }

    /** Half the rows discarded by the filter, so the window has to reach twice as far. */
    @Test
    fun seekReadsFurtherWhenTheFilterDiscardsRows() {
        // 50 rows survived out of the 100 raw rows read for them.
        val page = seekWindowPage(targetIndex = 400, loadedRows = 50, loadedPage = 1, pageSize = 50)
        assertEquals(17, page)
    }

    @Test
    fun seekNeverShrinksTheWindow() {
        assertEquals(
            9,
            seekWindowPage(targetIndex = 3, loadedRows = 400, loadedPage = 9, pageSize = 50)
        )
    }

    /** A window that yielded nothing says nothing about density — reach one page further. */
    @Test
    fun seekExtendsByOnePageWhenNothingSurvivedTheFilter() {
        assertEquals(
            3,
            seekWindowPage(targetIndex = 900, loadedRows = 0, loadedPage = 2, pageSize = 50)
        )
    }

    /** A seek to the end of a large list is one read, not a page-at-a-time walk. */
    @Test
    fun seekToTheEndOfALargeListIsOneRead() {
        val pageSize = 50
        val total = 20_000
        val page = seekWindowPage(
            targetIndex = total - 1,
            loadedRows = pageSize,
            loadedPage = 0,
            pageSize = pageSize
        )
        assertTrue((page + 1) * pageSize >= total)
    }

    /** The arithmetic runs in Long: a huge target must not wrap into a tiny window. */
    @Test
    fun seekDoesNotOverflowOnAHugeTarget() {
        val page = seekWindowPage(
            targetIndex = 5_000_000,
            loadedRows = 1,
            loadedPage = 100,
            pageSize = 50
        )
        assertTrue(page > 100)
    }
}
