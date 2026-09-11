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
    fun `a target past the window pulls the next page in`() {
        assertEquals(
            ScrollCursorStep.Pull(99),
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
    fun `a pull that grew the window pulls again`() {
        assertEquals(
            ScrollCursorStep.Pull(149),
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

    /** Walks a 1000-row list from a drag made on the first page, one page per step. */
    @Test
    fun `the walk reaches a target far past the window`() {
        val pageSize = 50
        val total = 1000
        val target = scrollCursorIndex(0.75f, total)
        var loaded = pageSize
        var pulledAt: Int? = null
        var landedOn: Int? = null
        var steps = 0

        while (landedOn == null && steps < 100) {
            steps++
            val step = scrollCursorStep(
                targetIndex = target,
                loadedCount = loaded,
                canLoadMore = loaded < total,
                isLoadingMore = false,
                pulledAtCount = pulledAt
            )
            when (step) {
                is ScrollCursorStep.Land -> landedOn = step.index
                is ScrollCursorStep.Pull -> {
                    pulledAt = loaded
                    loaded = minOf(loaded + pageSize, total)
                }
                ScrollCursorStep.Wait -> break
            }
        }

        assertEquals(target, landedOn)
        // One page per step, from the page the drag was made on to the page holding the target.
        assertEquals(target / pageSize, steps - 1)
    }
}
