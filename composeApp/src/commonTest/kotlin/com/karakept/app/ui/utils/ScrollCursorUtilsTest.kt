package com.karakept.app.ui.utils

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkSlot
import com.karakept.app.data.model.BookmarkWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the fast-scroll cursor's mapping onto the whole list (#273).
 */
class ScrollCursorUtilsTest {

    // -------------------------------------------------------------------------
    // Thumb position
    // -------------------------------------------------------------------------

    @Test
    fun `fraction maps the first visible row over the total, not the loaded window`() {
        // Row 100 of 1000, whatever the window holding it happens to be.
        assertEquals(100f / 999f, scrollCursorFraction(100, 1000))
    }

    @Test
    fun `a denominator that grows with the window is what moved the thumb`() {
        // #273: measured against the loaded count, row 40 reports a smaller fraction every time
        // a page lands — the thumb walks back up the track. The total is what it maps over now,
        // and that does not grow as the list pages in.
        assertTrue(scrollCursorFraction(40, 100) < scrollCursorFraction(40, 50))
    }

    @Test
    fun `fraction is zero for a list too short to scroll`() {
        assertEquals(0f, scrollCursorFraction(0, 1))
        assertEquals(0f, scrollCursorFraction(3, 0))
    }

    @Test
    fun `fraction stays inside the track`() {
        assertEquals(1f, scrollCursorFraction(999, 100))
        assertEquals(0f, scrollCursorFraction(-5, 100))
    }

    // -------------------------------------------------------------------------
    // Pointed row
    // -------------------------------------------------------------------------

    @Test
    fun `index maps the thumb over the total`() {
        assertEquals(0, scrollCursorIndex(0f, 1000))
        assertEquals(999, scrollCursorIndex(1f, 1000))
        assertEquals(500, scrollCursorIndex(0.5f, 1001))
    }

    @Test
    fun `index clamps a fraction outside the track`() {
        assertEquals(0, scrollCursorIndex(-0.4f, 50))
        assertEquals(49, scrollCursorIndex(1.6f, 50))
    }

    @Test
    fun `index is zero for a list too short to scroll`() {
        assertEquals(0, scrollCursorIndex(0.7f, 1))
    }

    // -------------------------------------------------------------------------
    // Reaching a row that has not been read
    //
    // These replace the tests for the walk the cursor used to make. The list was indexed by the
    // rows read so far, so a thumb dropped past them named a row the list had no slot for, and
    // reaching it meant asking for everything in between: a target past the window pulled, a
    // pull already in flight was waited for, a read that came back short pulled again, and one
    // that brought nothing back landed on the last row it had. What every one of those asserted
    // is that a drag arrives somewhere sensible without crawling. Sizing the list by the view
    // makes that structural — the slot is already there — and these are the same guarantee in
    // the terms that now hold it.
    // -------------------------------------------------------------------------

    /** A large view with only its first page read — the state a drag starts from. */
    private fun sparseView(total: Int) = BookmarkWindow(
        viewTotal = total,
        pageSize = 50,
        pages = mapOf(0 to (1..50).map(::row))
    )

    private fun row(id: Int) = BookmarkEntity(
        localId = id.toLong(),
        remoteId = "remote-$id",
        serverId = "server-1",
        url = "https://example.com/$id",
        title = "Bookmark $id",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = id.toLong(),
        isArchived = false,
        isStarred = false
    )

    @Test
    fun `every row the thumb can name is a slot the list already holds`() {
        val window = sparseView(4397)

        for (step in 0..100) {
            val index = scrollCursorIndex(step / 100f, window.viewTotal)
            assertTrue(
                index in 0 until window.total,
                "a thumb at ${step}% names row $index, which the list must have a slot for"
            )
        }
    }

    @Test
    fun `a target past what has been read is addressable, not out of range`() {
        val window = sparseView(4397)
        val target = scrollCursorIndex(0.9f, window.viewTotal)

        assertTrue(target > 50, "the fixture must aim past the one page that is loaded")
        assertEquals(BookmarkSlot.Placeholder, window[target], "its row has not arrived")
        assertNotNull(window.pageOf(target), "but the list knows which page to read for it")
    }

    @Test
    fun `reaching the end of a large list reads one page, not every page on the way`() {
        // The crawl this replaces: a forward walk to the end of a 4397-row view at 50 rows a page
        // is 88 reads. A jump reads the page it lands on.
        val window = sparseView(4397)
        val last = scrollCursorIndex(1f, window.viewTotal)

        assertEquals(4396, last)
        assertEquals(
            listOf(87),
            window.pagesCovering(last..last),
            "landing on the last row asks for the page holding it and nothing before it"
        )
    }

    @Test
    fun `a drag across the track asks only for the pages it lands on`() {
        val window = sparseView(4397)

        // Three positions a finger might stop at, each one page.
        for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
            val index = scrollCursorIndex(fraction, window.viewTotal)
            assertEquals(
                1,
                window.pagesCovering(index..index).size,
                "a thumb resting at $fraction sits inside one page"
            )
        }
    }

    @Test
    fun `an empty view has nothing to point at`() {
        assertEquals(0, scrollCursorIndex(0.5f, 0))
        assertTrue(BookmarkWindow.EMPTY.pagesCovering(0..10).isEmpty())
    }
}
