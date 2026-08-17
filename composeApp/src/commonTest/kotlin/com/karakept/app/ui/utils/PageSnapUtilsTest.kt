package com.karakept.app.ui.utils

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageSnapUtilsTest {

    @Test
    fun `a fold already on a boundary needs no adjustment`() {
        assertEquals(0f, computeSnapAdjustment(0f, 1000f, READER_MAX_SNAP_FRACTION))
    }

    @Test
    fun `a fold just past a boundary walks back to it`() {
        assertEquals(-40f, computeSnapAdjustment(40f, 1000f, READER_MAX_SNAP_FRACTION))
    }

    @Test
    fun `an element taller than the limit is left sliced rather than rewinding the turn`() {
        // An image or a table: aligning to the last line above it would undo most of the turn.
        assertEquals(0f, computeSnapAdjustment(600f, 1000f, READER_MAX_SNAP_FRACTION))
    }

    @Test
    fun `the limit is inclusive at its boundary`() {
        assertEquals(-250f, computeSnapAdjustment(250f, 1000f, READER_MAX_SNAP_FRACTION))
        assertEquals(0f, computeSnapAdjustment(251f, 1000f, READER_MAX_SNAP_FRACTION))
    }

    @Test
    fun `a residual longer than the page never reverses the turn`() {
        assertEquals(0f, computeSnapAdjustment(1500f, 1000f, READER_MAX_SNAP_FRACTION))
    }

    @Test
    fun `an unmeasured page produces no adjustment`() {
        assertEquals(0f, computeSnapAdjustment(40f, 0f, READER_MAX_SNAP_FRACTION))
        assertEquals(0f, computeSnapAdjustment(40f, -1000f, READER_MAX_SNAP_FRACTION))
    }

    @Test
    fun `a negative residual is ignored rather than pushing the turn further`() {
        assertEquals(0f, computeSnapAdjustment(-40f, 1000f, READER_MAX_SNAP_FRACTION))
    }

    @Test
    fun `the band covers exactly what the next turn brings back`() {
        // This is the invariant that keeps content from being lost: band more than the turn
        // restores and those pixels are never seen; band less and a sliver of a line still shows.
        for (residual in listOf(0f, 1f, 120f, 400f, 500f, 501f, 900f)) {
            assertEquals(
                abs(computeSnapAdjustment(residual, 1000f, READER_MAX_SNAP_FRACTION)),
                bottomMaskHeight(residual, 1000f, READER_MAX_SNAP_FRACTION),
                "residual $residual"
            )
        }
    }

    @Test
    fun `an element too tall to snap is not banded either`() {
        // A Magazine card or an image stays sliced across both pages rather than disappearing.
        assertEquals(0f, bottomMaskHeight(600f, 1000f, READER_MAX_SNAP_FRACTION))
        assertEquals(0f, bottomMaskHeight(333f, 1000f, READER_MAX_SNAP_FRACTION))
    }

    @Test
    fun `a clean bottom edge needs no band`() {
        assertEquals(0f, bottomMaskHeight(0f, 1000f, READER_MAX_SNAP_FRACTION))
    }

    @Test
    fun `trailing padding leaves room for everything but the last unit`() {
        assertEquals(920, computeTrailingPagePadding(viewportPx = 1000, tailUnitPx = 80))
    }

    @Test
    fun `a tail unit taller than the page needs no padding`() {
        assertEquals(0, computeTrailingPagePadding(viewportPx = 400, tailUnitPx = 900))
        assertEquals(0, computeTrailingPagePadding(viewportPx = 0, tailUnitPx = 80))
    }

    @Test
    fun `content running past the bottom edge leaves no blank space`() {
        assertEquals(0, blankBelowContentPx(contentBottomPx = 1400, visibleBottomPx = 1000))
    }

    @Test
    fun `content ending exactly on the bottom edge leaves no blank space`() {
        assertEquals(0, blankBelowContentPx(contentBottomPx = 1000, visibleBottomPx = 1000))
    }

    @Test
    fun `a restore stranded in the trailing padding reports the gap to walk back`() {
        // The article ends 940px up the page: reopening a finished bookmark landed here, showing
        // one line of text and blank below it.
        assertEquals(940, blankBelowContentPx(contentBottomPx = 60, visibleBottomPx = 1000))
    }

    @Test
    fun `an unread line below the edge is always worth a page`() {
        // Even a sliver: an edge falling inside the final line leaves less than a line below it.
        assertTrue(pageWorthTurning(hasLineBelow = true, contentEndGapPx = 4f, minAdvancePx = 26f))
    }

    @Test
    fun `a last block's own padding is not worth a page`() {
        // The article end is reported above the renderer's bottom margin, so that ~44px never
        // reaches here — but the final paragraph's own 8px of padding sits inside it, and turning
        // for that alone lands on a page with nothing to read.
        assertTrue(!pageWorthTurning(hasLineBelow = false, contentEndGapPx = 8f, minAdvancePx = 26f))
    }

    @Test
    fun `the threshold is one line, so anything shorter is margin rather than content`() {
        assertTrue(!pageWorthTurning(hasLineBelow = false, contentEndGapPx = 26f, minAdvancePx = 26f))
        assertTrue(pageWorthTurning(hasLineBelow = false, contentEndGapPx = 27f, minAdvancePx = 26f))
    }

    @Test
    fun `a trailing image stays reachable even though it registers no lines`() {
        assertTrue(pageWorthTurning(hasLineBelow = false, contentEndGapPx = 600f, minAdvancePx = 26f))
    }

    @Test
    fun `content ending at or above the edge is never worth a page`() {
        assertTrue(!pageWorthTurning(hasLineBelow = false, contentEndGapPx = 0f, minAdvancePx = 26f))
        assertTrue(!pageWorthTurning(hasLineBelow = false, contentEndGapPx = -40f, minAdvancePx = 26f))
    }

    @Test
    fun `the adjustment always leaves the turn advancing`() {
        // The caller adds this to the page delta, so an accepted snap must never cancel it out.
        val pageDelta = 1000f
        for (residual in listOf(1f, 50f, 120f, 249f, 250f)) {
            val net = pageDelta + computeSnapAdjustment(residual, pageDelta, READER_MAX_SNAP_FRACTION)
            assertTrue(net > 0f, "residual $residual left a net turn of $net")
        }
    }
}
