package com.karakept.app.ui.components.reader

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the two pure halves of the reader's link hover: which link a character belongs to, and
 * where the tooltip naming it lands.
 */
class ReaderLinkHoverTest {

    private fun linkedText() = buildAnnotatedString {
        append("read the docs here")
        // "docs" — characters 9 until 13.
        addStringAnnotation(LINK_ANNOTATION_TAG, "https://example.com/docs", 9, 13)
    }

    @Test
    fun `a character inside a link reports its URL`() {
        assertEquals("https://example.com/docs", linkedText().linkAt(10))
    }

    @Test
    fun `the link covers its first character and stops before the one after its last`() {
        val text = linkedText()
        assertEquals("https://example.com/docs", text.linkAt(9))
        assertEquals("https://example.com/docs", text.linkAt(12))
        assertNull(text.linkAt(8))
        assertNull(text.linkAt(13))
    }

    @Test
    fun `an offset outside the text reports no link`() {
        val text = linkedText()
        assertNull(text.linkAt(-1))
        assertNull(text.linkAt(text.length))
        assertNull(text.linkAt(text.length + 5))
    }

    @Test
    fun `text with no links reports none anywhere`() {
        val text = buildAnnotatedString { append("plain paragraph") }
        for (offset in 0 until text.length) assertNull(text.linkAt(offset))
    }

    @Test
    fun `the tooltip sits below and to the right of the pointer`() {
        val position = tooltipPosition(
            cursor = IntOffset(100, 200),
            gap = IntOffset(16, 16),
            windowSize = IntSize(1000, 800),
            tooltipSize = IntSize(240, 40)
        )
        assertEquals(IntOffset(116, 216), position)
    }

    @Test
    fun `the tooltip clears the pointer hotspot so it cannot steal the hover`() {
        val cursor = IntOffset(100, 200)
        val gap = IntOffset(16, 16)
        val position = tooltipPosition(cursor, gap, IntSize(1000, 800), IntSize(240, 40))
        assertTrue(position.x > cursor.x)
        assertTrue(position.y > cursor.y)
    }

    @Test
    fun `a pointer near the bottom edge flips the tooltip above it`() {
        val position = tooltipPosition(
            cursor = IntOffset(100, 780),
            gap = IntOffset(16, 16),
            windowSize = IntSize(1000, 800),
            tooltipSize = IntSize(240, 40)
        )
        assertEquals(780 - 16 - 40, position.y)
    }

    @Test
    fun `a long URL near the right edge is pulled back inside the window`() {
        val position = tooltipPosition(
            cursor = IntOffset(900, 200),
            gap = IntOffset(16, 16),
            windowSize = IntSize(1000, 800),
            tooltipSize = IntSize(420, 40)
        )
        assertEquals(1000 - 420, position.x)
    }

    @Test
    fun `a tooltip wider or taller than the window still starts on screen`() {
        val position = tooltipPosition(
            cursor = IntOffset(10, 10),
            gap = IntOffset(16, 16),
            windowSize = IntSize(300, 60),
            tooltipSize = IntSize(420, 200)
        )
        assertEquals(IntOffset(0, 0), position)
    }
}
