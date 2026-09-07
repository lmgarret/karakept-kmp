package com.karakept.app.ui.utils

import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.PageTurnKeyBindings
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `RowTilingUtilsTest` covers the helpers; what breaks in practice is the way
 * `PageTurnScrollEffect` and the tiled list compose them — turning by a page of rows, refusing the
 * turn that would land in the trailing padding, and clamping at the end of the content.
 *
 * The property worth defending is the same one the reader defends: **contiguity**. The rows visible
 * on one page end exactly where the next page's begin. It fails in both directions — a gap means a
 * bookmark was never shown, an overlap means one was shown twice — so it subsumes the individual
 * "no row sliced" checks. On top of it the list claims something the reader cannot: every page but
 * the last is *full*, holding the same number of whole rows.
 */
class TiledPageTurnSimulationTest {

    private class ListSimulation(
        val rowCount: Int,
        val viewport: Int,
        naturalRowPx: Int,
        /** The "No more bookmarks" footer, which is not a row and is not tiled. */
        val footerPx: Int = 52
    ) {
        val tiling = assertNotNull(resolveRowTiling(viewport, naturalRowPx))
        val rowPx = tiling.rowHeightPx
        val contentHeight = rowCount * rowPx + footerPx
        val trailingPad = if (contentHeight > viewport) {
            computeTrailingPagePadding(viewport, rowPx)
        } else 0
        val maxScroll = (contentHeight + trailingPad - viewport).coerceAtLeast(0)

        var scroll = 0
            private set

        fun scrollTo(px: Int) {
            scroll = px.coerceIn(0, maxScroll)
        }

        /** Mirrors `tailFullyVisible`: the end of the content is already above the bottom edge. */
        private val tailFullyVisible: Boolean get() = contentHeight - scroll <= viewport

        /** Mirrors the effect's guard: the first visible item is a bookmark row, not the footer. */
        private val firstVisibleOffset: Int?
            get() = if (scroll < rowCount * rowPx) scroll % rowPx else null

        /** One press of a page-turn button. Returns false when the effect declines to turn. */
        fun turn(direction: PageTurnDirection): Boolean {
            val delta = computePageScrollDelta(
                viewportHeightPx = viewport,
                overlapPercent = PageTurnKeyBindings.MIN_OVERLAP_PERCENT,
                direction = direction
            )
            if (delta == 0f) return false
            if (direction == PageTurnDirection.NEXT && trailingPad > 0 && tailFullyVisible) {
                return false
            }
            val adjustment = firstVisibleOffset?.let { offset ->
                tiledTurnAdjustment(direction, abs(delta), tiling, offset)
            } ?: 0f
            val before = scroll
            scrollTo((scroll + delta + adjustment).toInt())
            return scroll != before
        }

        /** The rows shown whole on the current page, which is what the reader gets to read. */
        val visibleRows: List<Int>
            get() = (0 until rowCount).filter { row ->
                row * rowPx >= scroll && (row + 1) * rowPx <= scroll + viewport
            }
    }

    @Test
    fun everyPageIsFullAndNoBookmarkIsSkippedOrRepeated() {
        val sim = ListSimulation(rowCount = 41, viewport = 1000, naturalRowPx = 240)
        assertEquals(4, sim.tiling.rowsPerPage)

        val pages = mutableListOf<List<Int>>()
        pages += sim.visibleRows
        while (sim.turn(PageTurnDirection.NEXT)) {
            pages += sim.visibleRows
            assertTrue(pages.size < TURN_LIMIT, "page turns did not terminate")
        }

        pages.dropLast(1).forEach { page ->
            assertEquals(sim.tiling.rowsPerPage, page.size, "a page short of rows: $page")
        }
        // Contiguity: flattening the pages reproduces the list exactly once, in order.
        assertEquals((0 until sim.rowCount).toList(), pages.flatten())
    }

    @Test
    fun turningBackRetracesTheSamePages() {
        val sim = ListSimulation(rowCount = 41, viewport = 1000, naturalRowPx = 240)
        val forward = mutableListOf(sim.visibleRows)
        while (sim.turn(PageTurnDirection.NEXT)) forward += sim.visibleRows

        val backward = mutableListOf(sim.visibleRows)
        while (sim.turn(PageTurnDirection.PREVIOUS)) backward += sim.visibleRows
        assertEquals(forward.reversed(), backward)
        assertEquals(0, sim.scroll)
    }

    @Test
    fun aTurnFromADraggedPositionLandsOnARowTop() {
        val sim = ListSimulation(rowCount = 41, viewport = 1000, naturalRowPx = 240)
        sim.scrollTo(sim.rowPx * 4 + 130)
        assertTrue(sim.turn(PageTurnDirection.NEXT))
        assertEquals(0, sim.scroll % sim.rowPx)
        // The row the fold had cut through is shown whole on the page that follows, not skipped.
        assertEquals(8, sim.visibleRows.first())
    }

    @Test
    fun theLastPageStartsOnARowTopRatherThanClampingAgainstTheFooter() {
        val sim = ListSimulation(rowCount = 41, viewport = 1000, naturalRowPx = 240)
        while (sim.turn(PageTurnDirection.NEXT)) Unit
        assertEquals(0, sim.scroll % sim.rowPx)
        assertEquals(listOf(40), sim.visibleRows)
    }

    @Test
    fun aListThatFitsOnOnePageNeverTurns() {
        val sim = ListSimulation(rowCount = 3, viewport = 1000, naturalRowPx = 240)
        assertEquals(0, sim.trailingPad)
        assertEquals(listOf(0, 1, 2), sim.visibleRows)
        // No trailing padding, so the turn is not refused outright — it simply has nowhere to go.
        sim.turn(PageTurnDirection.NEXT)
        assertEquals(listOf(0, 1, 2), sim.visibleRows)
    }

    private companion object {
        /** A runaway turn loop should fail as a test, not hang the suite. */
        const val TURN_LIMIT = 200
    }
}
