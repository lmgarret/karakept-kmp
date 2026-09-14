package com.karakept.app.ui.utils

import kotlin.test.Test
import kotlin.test.assertEquals
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
    // Walking to a target past the loaded window
    // -------------------------------------------------------------------------

    @Test
    fun `a loaded target lands straight away`() {
        assertEquals(
            ScrollCursorStep.Land(42),
            scrollCursorStep(targetIndex = 42, loadedCount = 100, canLoadMore = true, isLoadingMore = false)
        )
    }

    @Test
    fun `a target past the window asks for the rows out to it`() {
        // Out to the target, not one page on: a pull the list can see is a crawl, not a jump.
        assertEquals(
            ScrollCursorStep.Pull(800),
            scrollCursorStep(targetIndex = 800, loadedCount = 100, canLoadMore = true, isLoadingMore = false)
        )
    }

    @Test
    fun `a page already in flight is waited for`() {
        assertEquals(
            ScrollCursorStep.Wait,
            scrollCursorStep(targetIndex = 800, loadedCount = 100, canLoadMore = true, isLoadingMore = true)
        )
    }

    @Test
    fun `a target past the end of the table lands on the last row`() {
        assertEquals(
            ScrollCursorStep.Land(99),
            scrollCursorStep(targetIndex = 800, loadedCount = 100, canLoadMore = false, isLoadingMore = false)
        )
    }

    @Test
    fun `a pull that brought nothing back is not asked again`() {
        assertEquals(
            ScrollCursorStep.Land(99),
            scrollCursorStep(
                targetIndex = 800,
                loadedCount = 100,
                canLoadMore = true,
                isLoadingMore = false,
                pulledAtCount = 100
            )
        )
    }

    @Test
    fun `a read that came back short of the target pulls again`() {
        assertEquals(
            ScrollCursorStep.Pull(800),
            scrollCursorStep(
                targetIndex = 800,
                loadedCount = 150,
                canLoadMore = true,
                isLoadingMore = false,
                pulledAtCount = 100
            )
        )
    }

    @Test
    fun `an empty window has nothing to land on`() {
        assertEquals(
            ScrollCursorStep.Wait,
            scrollCursorStep(targetIndex = 0, loadedCount = 0, canLoadMore = true, isLoadingMore = false)
        )
    }

    /** A drag to 75% of a 1000-row list, made on the first page: one read, then the jump. */
    @Test
    fun `a target far past the window is reached in one read`() {
        val total = 1000
        val target = scrollCursorIndex(0.75f, total)
        var loaded = 50
        var pulledAt: Int? = null

        val first = scrollCursorStep(target, loaded, canLoadMore = true, isLoadingMore = false, pulledAtCount = pulledAt)
        assertEquals(ScrollCursorStep.Pull(target), first)
        pulledAt = loaded
        // The read reaches the target, as seekWindowPage sizes it to.
        loaded = target + 1

        assertEquals(
            ScrollCursorStep.Land(target),
            scrollCursorStep(target, loaded, canLoadMore = true, isLoadingMore = false, pulledAtCount = pulledAt)
        )
    }

    /** A filter discarding more than the estimate allowed for: the next read resumes, larger. */
    @Test
    fun `a short read is followed by another, not by a crawl`() {
        val target = 749
        var loaded = 50
        var pulledAt: Int? = null
        var reads = 0
        var landedOn: Int? = null

        while (landedOn == null && reads < 30) {
            val step = scrollCursorStep(
                targetIndex = target,
                loadedCount = loaded,
                canLoadMore = true,
                isLoadingMore = false,
                pulledAtCount = pulledAt
            )
            when (step) {
                is ScrollCursorStep.Land -> landedOn = step.index
                is ScrollCursorStep.Pull -> {
                    reads++
                    pulledAt = loaded
                    // Each read covers half the remaining distance — a short one, by design.
                    loaded += (target + 1 - loaded) / 2 + 1
                }
                ScrollCursorStep.Wait -> break
            }
        }

        assertEquals(target, landedOn)
        // Converged on reads rather than crawling: a page-at-a-time walk would have taken
        // fifteen, each one a screenful of scrolling the user watches go past.
        assertTrue(reads < 15)
    }
}
