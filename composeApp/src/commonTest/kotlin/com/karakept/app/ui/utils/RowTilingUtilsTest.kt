package com.karakept.app.ui.utils

import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.PageTurnDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RowTilingUtilsTest {

    @Test
    fun dividesThePageByTheRowsThatFitInIt() {
        val tiling = assertNotNull(resolveRowTiling(viewportPx = 1000, naturalRowPx = 240))
        assertEquals(4, tiling.rowsPerPage)
        assertEquals(250, tiling.rowHeightPx)
    }

    @Test
    fun takesTheNearestCountWhenTheRowCanGiveTheDifferenceBack() {
        // Four rows of 250 leave a 260px row 10px short, which a description line covers twice over.
        val tiling = assertNotNull(
            resolveRowTiling(viewportPx = 1000, naturalRowPx = 260, minRowPx = 230)
        )
        assertEquals(4, tiling.rowsPerPage)
        assertEquals(250, tiling.rowHeightPx)
    }

    @Test
    fun takesOneRowFewerWhenTheRowCannotShrinkThatFar() {
        // Five rows of 200 would cut into a 220px row's metadata; four of 250 do not.
        val tiling = assertNotNull(
            resolveRowTiling(viewportPx = 1000, naturalRowPx = 220, minRowPx = 215)
        )
        assertEquals(4, tiling.rowsPerPage)
        assertEquals(250, tiling.rowHeightPx)
    }

    @Test
    fun declinesWhenNeitherCountFits() {
        // Four rows are too short for the row to absorb and three stretch it by more than a fifth.
        assertNull(resolveRowTiling(viewportPx = 1000, naturalRowPx = 260, minRowPx = 255))
    }

    @Test
    fun aPageIsAWholeNumberOfRows() {
        val tiling = assertNotNull(resolveRowTiling(viewportPx = 1003, naturalRowPx = 240))
        assertEquals(250, tiling.rowHeightPx)
        assertEquals(1000, tiling.pageHeightPx)
        // Whatever integer division leaves over stays under one pixel per row, which is less than
        // the blank margin at the top of the row it exposes.
        assertTrue(1003 - tiling.pageHeightPx < tiling.rowsPerPage)
    }

    @Test
    fun declinesWhenTheOnlyFitWouldStretchARowTooFar() {
        // One 700px row on a 1000px page would gain nearly half its height again in whitespace.
        assertNull(resolveRowTiling(viewportPx = 1000, naturalRowPx = 700))
    }

    @Test
    fun acceptsGrowthUpToTheLimit() {
        // Two 450px rows become two of 500 — a ninth taller, inside the fifth allowed.
        val tiling = assertNotNull(resolveRowTiling(viewportPx = 1000, naturalRowPx = 450))
        assertEquals(2, tiling.rowsPerPage)
        assertEquals(500, tiling.rowHeightPx)
    }

    @Test
    fun declinesAListWhoseRowsAreTallerThanThePage() {
        assertNull(resolveRowTiling(viewportPx = 1000, naturalRowPx = 1400))
    }

    @Test
    fun theShrunkRowIsTheOneWithNothingElasticLeft() {
        val metrics = rowsLayoutMetrics()
        // The description down to one line, the thumbnail down to three quarters of itself —
        // whichever of the two still decides the band.
        assertEquals(161, metrics.naturalHeightPx)
        assertEquals(77 + 68, metrics.minHeightPx)
    }

    @Test
    fun stopsDividingThePagePastTheRowLimit() {
        // Thirteen rows would fit; the limit takes twelve slightly taller ones instead.
        val tiling = assertNotNull(
            resolveRowTiling(viewportPx = 1000, naturalRowPx = 76, maxRowsPerPage = 12)
        )
        assertEquals(12, tiling.rowsPerPage)
        assertEquals(83, tiling.rowHeightPx)
    }

    @Test
    fun declinesRowsTooShortToTileWithinTheLimit() {
        // Twelve of these would each have to more than double, which the drift check refuses.
        assertNull(resolveRowTiling(viewportPx = 1000, naturalRowPx = 40, maxRowsPerPage = 12))
    }

    @Test
    fun leavesAnUnmeasuredListAlone() {
        assertNull(resolveRowTiling(viewportPx = 0, naturalRowPx = 260))
        assertNull(resolveRowTiling(viewportPx = 1000, naturalRowPx = 0))
    }

    @Test
    fun magazineIsNotTiled() {
        assertTrue(layoutSupportsTiling(LayoutType.LIST))
        assertFalse(layoutSupportsTiling(LayoutType.CARD))
    }

    @Test
    fun anAlignedTurnAdvancesExactlyOnePageOfRows() {
        val tiling = RowTiling(rowHeightPx = 250, rowsPerPage = 4)
        val forward = tiledTurnAdjustment(
            direction = PageTurnDirection.NEXT,
            pageDeltaPx = 1003f,
            tiling = tiling,
            firstVisibleOffsetPx = 0
        )
        // The turn is asked for 1003px and pulled back to the 1000 that four rows occupy.
        assertEquals(-3f, forward)
        val back = tiledTurnAdjustment(
            direction = PageTurnDirection.PREVIOUS,
            pageDeltaPx = 1003f,
            tiling = tiling,
            firstVisibleOffsetPx = 0
        )
        assertEquals(3f, back)
    }

    @Test
    fun aTurnFromAPositionTheUserDraggedToLandsOnARowTop() {
        val tiling = RowTiling(rowHeightPx = 250, rowsPerPage = 4)
        // 130px into the top row: forward stops 130px short, backward goes 130px further, and
        // both land on a row top.
        assertEquals(
            870f,
            1000f + tiledTurnAdjustment(PageTurnDirection.NEXT, 1000f, tiling, 130)
        )
        assertEquals(
            -1130f,
            -1000f + tiledTurnAdjustment(PageTurnDirection.PREVIOUS, 1000f, tiling, 130)
        )
    }

    @Test
    fun naturalHeightIsTheThumbnailWhenTheTextFitsBesideIt() {
        val metrics = rowsLayoutMetrics(descriptionLines = 1)
        // Two title lines of 24 and one description line of 16 under a 4dp gap come to 68, which
        // the 80px thumbnail covers — so the band is the thumbnail, and the rest of the row is its
        // padding, the metadata band with its 8dp gap, and the divider.
        assertEquals(80f, metrics.bodyHeightPx)
        assertEquals(80 + 24 + 44 + 8 + 1, metrics.naturalHeightPx)
    }

    @Test
    fun aTextColumnTallerThanTheThumbnailDecidesTheHeight() {
        val metrics = rowsLayoutMetrics(descriptionLines = 6)
        // Two title lines of 24, then 4dp and six description lines of 16.
        assertEquals(48f + 4f + 96f, metrics.bodyHeightPx)
        assertEquals(148 + 24 + 44 + 8 + 1, metrics.naturalHeightPx)
    }

    @Test
    fun aTitleAboveTheThumbnailSpansTheRowInsteadOfTheColumn() {
        val beside = rowsLayoutMetrics()
        val above = rowsLayoutMetrics(titleAboveThumbnail = true)
        assertEquals(80f, above.bodyHeightPx)
        // The same title, moved out of the column and onto the row, with 8dp under it.
        assertEquals(beside.chromeHeightPx + 48f + 8f, above.chromeHeightPx)
    }

    /** The built-in Rows layout at a density of 1: 80px thumbnail, flat container, a divider. */
    private fun rowsLayoutMetrics(
        descriptionLines: Int = 2,
        titleAboveThumbnail: Boolean = false
    ) = BookmarkRowMetrics(
        verticalPaddingPx = 12f,
        wrapperPaddingPx = 0f,
        thumbnailPx = 80f,
        titleLinePx = 24f,
        titleLines = 2,
        urlLinePx = 0f,
        descriptionLinePx = 16f,
        descriptionLines = descriptionLines,
        metadataPx = 44f,
        metadataInTextColumn = false,
        titleAboveThumbnail = titleAboveThumbnail,
        blockSpacingPx = 4f,
        sectionSpacingPx = 8f,
        dividerPx = 1f
    )
}
