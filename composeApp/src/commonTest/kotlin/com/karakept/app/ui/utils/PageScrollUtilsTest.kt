package com.karakept.app.ui.utils

import com.karakept.app.data.model.PageTurnDirection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageScrollUtilsTest {

    @Test
    fun `next scrolls forward by the viewport minus the overlap`() {
        val delta = computePageScrollDelta(
            viewportHeightPx = 1000,
            overlapPercent = 10,
            direction = PageTurnDirection.NEXT
        )
        assertEquals(900f, delta)
    }

    @Test
    fun `previous scrolls back by the same magnitude`() {
        val next = computePageScrollDelta(1000, 10, PageTurnDirection.NEXT)
        val previous = computePageScrollDelta(1000, 10, PageTurnDirection.PREVIOUS)
        assertEquals(next, -previous)
        assertTrue(previous < 0f)
    }

    @Test
    fun `zero overlap scrolls a full viewport`() {
        assertEquals(1000f, computePageScrollDelta(1000, 0, PageTurnDirection.NEXT))
    }

    @Test
    fun `overlap above the maximum is clamped rather than inverting the scroll`() {
        // 200% overlap would otherwise produce a negative delta for a forward turn.
        val delta = computePageScrollDelta(1000, 200, PageTurnDirection.NEXT)
        assertEquals(500f, delta)
        assertTrue(delta > 0f)
    }

    @Test
    fun `negative overlap is clamped to zero`() {
        assertEquals(1000f, computePageScrollDelta(1000, -50, PageTurnDirection.NEXT))
    }

    @Test
    fun `unmeasured viewport produces no scroll`() {
        assertEquals(0f, computePageScrollDelta(0, 10, PageTurnDirection.NEXT))
        assertEquals(0f, computePageScrollDelta(-1, 10, PageTurnDirection.PREVIOUS))
    }

    @Test
    fun `overlap is a proportion of the viewport, not a fixed distance`() {
        val small = computePageScrollDelta(400, 25, PageTurnDirection.NEXT)
        val large = computePageScrollDelta(1600, 25, PageTurnDirection.NEXT)
        assertEquals(300f, small)
        assertEquals(1200f, large)
        assertTrue(abs(large / small - 4f) < 0.0001f)
    }
}
