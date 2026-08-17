package com.karakept.app.ui.utils

import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.ui.components.reader.ReaderSnapRegistry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The individual helpers are covered in `PageSnapUtilsTest`; what breaks in practice is the way
 * `PageTurnScrollEffect` and the paged rendering compose them — turning a page, deciding whether
 * the result may be pulled onto a boundary, and blanking whatever the bottom edge still cuts.
 * This simulation models the reader's scrolling closely enough to run that whole sequence.
 *
 * The property worth defending is **contiguity**: the visible content of one page ends exactly
 * where the next begins. It fails in both directions — a gap means a line was skipped, an overlap
 * means one was shown twice — so it subsumes the individual "no line sliced" checks.
 */
class PageTurnSnapSimulationTest {

    /** The chrome-covered band above the readable area, standing in for the overlaid top bar. */
    private val foldRootY = 120f

    private class ReaderSimulation(
        val lineDocYs: List<Float>,
        val readable: Int,
        val foldRootY: Float,
        val paged: Boolean = true,
        /**
         * Space between the end of the article and the end of the lazy item holding it: the last
         * block's padding, the renderer's bottom spacer and its Column padding, ~52dp in all. The
         * simulation used to assume this was zero, which is exactly why it missed the spurious
         * final page.
         */
        val trailingMargin: Float = TRAILING_MARGIN,
        /** A closing image or table: content with no lines, so only its extent makes it reachable. */
        val trailingBlockHeight: Float = 0f
    ) {
        var scroll = 0f
            private set

        /** Where the article stops — what the renderer's marker reports. */
        val contentEnd = lineDocYs.last() + LINE_HEIGHT + trailingBlockHeight
        val docHeight = contentEnd + trailingMargin
        val trailingPad = if (paged && docHeight > readable) {
            computeTrailingPagePadding(readable, LINE_HEIGHT.toInt())
        } else 0
        val maxScroll = (docHeight + trailingPad - readable).coerceAtLeast(0f)
        private val registry = ReaderSnapRegistry()

        /** What the text blocks report from their layout callbacks after each scroll. */
        private fun relayout() {
            lineDocYs.forEachIndexed { index, docY ->
                registry.report(
                    index,
                    topInRoot = foldRootY + docY - scroll,
                    lineTops = floatArrayOf(0f),
                    lineBottoms = floatArrayOf(LINE_HEIGHT)
                )
            }
        }

        private fun residualAt(rootY: Float): Float {
            relayout()
            return registry.lineTopAt(rootY)?.let { rootY - it } ?: 0f
        }

        fun tailFullyVisible() = trailingPad > 0 && docHeight <= scroll + readable

        /** Mirrors the reader's `moreContentBelow`, in document coordinates. */
        fun worthTurning(): Boolean {
            relayout()
            val fold = foldRootY + readable
            return pageWorthTurning(
                hasLineBelow = registry.hasLineBelow(fold),
                contentEndGapPx = contentEnd - (scroll + readable),
                minAdvancePx = LINE_HEIGHT
            )
        }

        fun turn(direction: PageTurnDirection) {
            if (direction == PageTurnDirection.NEXT && trailingPad > 0 &&
                (tailFullyVisible() || !worthTurning())
            ) return
            val delta = computePageScrollDelta(
                readable, overlapPercent = if (paged) 0 else 8, direction = direction
            )
            val magnitude = abs(delta)
            val landing = when (direction) {
                PageTurnDirection.NEXT -> foldRootY + magnitude
                PageTurnDirection.PREVIOUS -> foldRootY - magnitude
            }
            val adjustment = if (!paged) 0f else computeSnapAdjustment(
                residualPx = residualAt(landing),
                pageDeltaPx = magnitude,
                maxSnapFraction = READER_MAX_SNAP_FRACTION
            )
            scroll = (scroll + delta + adjustment).coerceIn(0f, maxScroll)
        }

        fun restoreTo(target: Float) {
            scroll = target.coerceIn(0f, maxScroll)
            val blank = blankBelowContentPx((docHeight - scroll).toInt(), readable)
            if (blank > 0) scroll = (scroll - blank).coerceAtLeast(0f)
        }

        fun blankBelow(): Int = blankBelowContentPx((docHeight - scroll).toInt(), readable)

        /** Where the visible — unbanded — content of the current page ends, in document space. */
        fun visibleContentBottom(): Float {
            val edge = scroll + readable
            if (!paged) return edge
            return edge - bottomMaskHeight(
                residualAt(foldRootY + readable), readable.toFloat(), READER_MAX_SNAP_FRACTION
            )
        }

        companion object {
            const val LINE_HEIGHT = 30f
            const val TRAILING_MARGIN = 52f
        }
    }

    private fun evenlySpacedLines(count: Int) =
        List(count) { it * ReaderSimulation.LINE_HEIGHT }

    private fun ReaderSimulation.assertContiguousForward(turns: Int) {
        repeat(turns) {
            val before = scroll
            val visibleUntil = visibleContentBottom()
            turn(PageTurnDirection.NEXT)
            if (scroll == before) return
            assertEquals(visibleUntil, scroll, "page $it handed over at the wrong place")
        }
    }

    @Test
    fun `reader pages hand over exactly, losing and repeating no line`() {
        // 700 is not a multiple of the 30px line pitch, so an unpaged turn always splits one.
        ReaderSimulation(evenlySpacedLines(200), readable = 700, foldRootY = foldRootY)
            .assertContiguousForward(10)
    }

    @Test
    fun `a page neither begins nor ends in the middle of a line`() {
        val reader = ReaderSimulation(evenlySpacedLines(200), readable = 700, foldRootY = foldRootY)
        repeat(8) {
            reader.turn(PageTurnDirection.NEXT)
            assertEquals(0f, reader.scroll % ReaderSimulation.LINE_HEIGHT, "began mid-line")
            // On the last page the content simply runs out; the blank below it is not a cut line.
            if (!reader.tailFullyVisible()) {
                val extent = reader.visibleContentBottom() - reader.scroll
                assertEquals(0f, extent % ReaderSimulation.LINE_HEIGHT, "ended mid-line")
            }
        }
    }

    @Test
    fun `every line is shown whole at least once`() {
        val lines = evenlySpacedLines(200)
        val reader = ReaderSimulation(lines, readable = 700, foldRootY = foldRootY)

        val seenWhole = mutableSetOf<Int>()
        repeat(30) {
            val pageTop = reader.scroll
            val pageBottom = reader.visibleContentBottom()
            lines.forEachIndexed { index, docY ->
                if (docY >= pageTop && docY + ReaderSimulation.LINE_HEIGHT <= pageBottom) {
                    seenWhole += index
                }
            }
            reader.turn(PageTurnDirection.NEXT)
        }
        assertEquals(lines.indices.toSet(), seenWhole, "some lines were never shown whole")
    }

    @Test
    fun `backward turns land on lines too`() {
        val reader = ReaderSimulation(evenlySpacedLines(200), readable = 700, foldRootY = foldRootY)
        repeat(5) { reader.turn(PageTurnDirection.NEXT) }
        repeat(4) {
            reader.turn(PageTurnDirection.PREVIOUS)
            if (reader.scroll > 0f) {
                assertEquals(0f, reader.scroll % ReaderSimulation.LINE_HEIGHT, "began mid-line")
            }
        }
    }

    @Test
    fun `an image at the fold is left sliced rather than rewound`() {
        // Ten lines, then a tall image, then more text. The bottom edge lands inside the image:
        // there is no line there to keep whole, so neither the snap nor the band fires and the
        // turn stands where it landed — losing a screenful to clear a picture would be worse.
        val lines = evenlySpacedLines(10) + List(10) { 2000f + it * ReaderSimulation.LINE_HEIGHT }
        val reader = ReaderSimulation(lines, readable = 700, foldRootY = foldRootY)

        assertEquals(700f, reader.visibleContentBottom(), "the image was banded away")
        reader.turn(PageTurnDirection.NEXT)
        assertEquals(700f, reader.scroll)
    }

    @Test
    fun `the reader's last page carries on from the previous one`() {
        val lines = evenlySpacedLines(60)
        val reader = ReaderSimulation(lines, readable = 700, foldRootY = foldRootY)
        assertTrue(reader.trailingPad > 0, "an article this long should be padded")

        var handover = 0f
        repeat(30) {
            val before = reader.scroll
            val visibleUntil = reader.visibleContentBottom()
            reader.turn(PageTurnDirection.NEXT)
            if (reader.scroll == before) return@repeat
            handover = visibleUntil
        }

        assertEquals(handover, reader.scroll, "the last page repeated a line")
        assertTrue(reader.tailFullyVisible(), "the end of the article is not on the final page")
        assertEquals(0f, reader.scroll % ReaderSimulation.LINE_HEIGHT, "began mid-line")
    }

    @Test
    fun `reopening a finished article shows a full page of text, not one line`() {
        // Reading progress 1.0 asks to scroll to the very bottom; without the correction that
        // clamps into the padding and the article reopens on a near-empty page.
        val reader = ReaderSimulation(evenlySpacedLines(60), readable = 700, foldRootY = foldRootY)
        reader.restoreTo(reader.maxScroll)

        assertEquals(0, reader.blankBelow(), "the restore stayed in the trailing padding")
        assertEquals(reader.docHeight - reader.readable, reader.scroll)
    }

    @Test
    fun `paging stops on the page holding the last line, with no blank page after it`() {
        // The article box runs ~52px past its final line. Measured against the lazy *item* that
        // still counts as "content below the edge", so one more turn fired and landed on blank.
        val lines = evenlySpacedLines(60)
        val reader = ReaderSimulation(lines, readable = 700, foldRootY = foldRootY)
        assertTrue(reader.docHeight > reader.contentEnd, "the margin should be modelled")

        repeat(30) { reader.turn(PageTurnDirection.NEXT) }
        val settled = reader.scroll

        // The last line is on this page…
        assertTrue(
            lines.last() >= settled && lines.last() + ReaderSimulation.LINE_HEIGHT <= settled + reader.readable,
            "the final line is not on the last page"
        )
        // …and there is no further page to turn to.
        reader.turn(PageTurnDirection.NEXT)
        assertEquals(settled, reader.scroll, "turned onto a page with nothing on it")
    }

    @Test
    fun `an article ending in an image can still be paged to`() {
        // A closing image registers no lines, so text alone would call the article finished and
        // strand it off screen. Its extent is what keeps the turn available.
        val lines = evenlySpacedLines(24)
        val reader = ReaderSimulation(
            lines, readable = 700, foldRootY = foldRootY, trailingBlockHeight = 600f
        )
        repeat(30) { reader.turn(PageTurnDirection.NEXT) }

        assertTrue(
            reader.scroll + reader.readable >= reader.contentEnd,
            "the closing image never came fully into view (stopped at ${reader.scroll})"
        )
    }

    @Test
    fun `an article shorter than the viewport is not padded into scrolling`() {
        val reader = ReaderSimulation(evenlySpacedLines(4), readable = 700, foldRootY = foldRootY)
        assertEquals(0, reader.trailingPad)
        reader.turn(PageTurnDirection.NEXT)
        assertEquals(0f, reader.scroll)
    }
}
