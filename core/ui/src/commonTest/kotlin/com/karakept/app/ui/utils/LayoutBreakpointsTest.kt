package com.karakept.app.ui.utils

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The expanded breakpoint decides whether a window gets the three-column layout. A 7-inch e-ink
 * panel must stay below it in both orientations — three columns there would leave no usable
 * measure in any of them.
 */
class LayoutBreakpointsTest {

    @Test
    fun `a phone in portrait stays single pane`() {
        assertFalse(isExpandedWidth(411.dp))
    }

    @Test
    fun `a seven-inch panel stays single pane in landscape too`() {
        for (width in listOf(600.dp, 700.dp, 800.dp)) {
            assertFalse(isExpandedWidth(width), "$width has no room for three columns")
        }
    }

    @Test
    fun `a desktop window gets the expanded layout`() {
        assertTrue(isExpandedWidth(1280.dp))
    }

    @Test
    fun `the breakpoint is inclusive at its exact width`() {
        assertTrue(isExpandedWidth(ExpandedWidthBreakpoint))
        assertFalse(isExpandedWidth(ExpandedWidthBreakpoint - 1.dp))
    }
}
