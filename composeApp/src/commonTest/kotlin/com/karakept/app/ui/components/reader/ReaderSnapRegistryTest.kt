package com.karakept.app.ui.components.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderSnapRegistryTest {

    /**
     * Two paragraphs of contiguous 20px lines — one starting at root y=100, the next at y=300 —
     * with 140px of image or whitespace between them.
     */
    private fun twoParagraphs() = ReaderSnapRegistry().apply {
        report(
            "p1", topInRoot = 100f,
            lineTops = floatArrayOf(0f, 20f, 40f),
            lineBottoms = floatArrayOf(20f, 40f, 60f)
        )
        report(
            "p2", topInRoot = 300f,
            lineTops = floatArrayOf(0f, 20f),
            lineBottoms = floatArrayOf(20f, 40f)
        )
    }

    @Test
    fun `an edge inside a line reports that line's top`() {
        // 135 falls through the second line of the first paragraph, which spans 120..140.
        assertEquals(120f, twoParagraphs().lineTopAt(135f))
    }

    @Test
    fun `an edge exactly on a line top belongs to that line`() {
        assertEquals(140f, twoParagraphs().lineTopAt(140f))
    }

    @Test
    fun `blocks are searched as one document, not one at a time`() {
        assertEquals(320f, twoParagraphs().lineTopAt(330f))
    }

    @Test
    fun `an edge in the gap between blocks has no line to keep whole`() {
        // 250 is in the image or whitespace after the first paragraph. Reporting the last line
        // above it would rewind the turn by a screenful to clear something that is not a line.
        assertNull(twoParagraphs().lineTopAt(250f))
    }

    @Test
    fun `an edge past the end of a block does not leak into the next line`() {
        // 160 is one pixel past the last line of p1, which ends at 160.
        assertNull(twoParagraphs().lineTopAt(160f))
    }

    @Test
    fun `an edge above the first line has nothing to snap to`() {
        assertNull(twoParagraphs().lineTopAt(50f))
    }

    @Test
    fun `an empty registry snaps to nothing`() {
        // Web mode, or an article of nothing but images — the caller leaves the turn alone.
        assertNull(ReaderSnapRegistry().lineTopAt(500f))
    }

    @Test
    fun `re-reporting a block replaces its lines instead of accumulating them`() {
        val registry = twoParagraphs()
        registry.report("p1", 500f, floatArrayOf(0f), floatArrayOf(20f))
        assertEquals(500f, registry.lineTopAt(510f))
        // The stale 120..160 lines must be gone.
        assertNull(registry.lineTopAt(135f))
    }

    @Test
    fun `a line straddling the edge still counts as below it`() {
        // p2's last line spans 320..340, so an edge at 330 leaves half of it unread.
        assertTrue(twoParagraphs().hasLineBelow(330f))
    }

    @Test
    fun `nothing is below the last line's bottom`() {
        assertFalse(twoParagraphs().hasLineBelow(340f))
        assertFalse(twoParagraphs().hasLineBelow(400f))
    }

    @Test
    fun `the article end is unknown until the renderer reports it`() {
        // A caller with no answer must assume there is more below, not less, so a reader without
        // a registry keeps behaving as it always did.
        assertNull(ReaderSnapRegistry().contentEndInRoot)
    }

    @Test
    fun `the reported article end replaces the previous one as the page scrolls`() {
        val registry = twoParagraphs()
        registry.reportContentEnd(348f)
        assertEquals(348f, registry.contentEndInRoot)
        registry.reportContentEnd(120f)
        assertEquals(120f, registry.contentEndInRoot)
    }

    @Test
    fun `a forgotten block stops contributing`() {
        val registry = twoParagraphs()
        registry.forget("p2")
        assertNull(registry.lineTopAt(330f))
    }
}
