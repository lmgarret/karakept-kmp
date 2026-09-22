package com.karakept.app.ui.utils

import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.PageTurnDirection
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Beyond this a "row" is a line of text and the list stops being readable at arm's length, so the
 * quantiser refuses to divide the page any further.
 */
const val MAX_TILED_ROWS_PER_PAGE = 12

/** A fifth of a row: enough to swallow the usual rounding, not enough to restyle the list. */
const val MAX_TILE_DRIFT_FRACTION = 0.2f

/**
 * How small a tile may make the thumbnail before the row stops looking like the one the layout
 * settings describe. Squeezing the last few percent out of the image is what lets a page hold the
 * row count nearest its natural one; squeezing a quarter out of it is a different layout.
 */
const val MIN_TILED_THUMBNAIL_FRACTION = 0.75f

/**
 * The one height every row is held to, and how many of them a page holds.
 */
data class RowTiling(val rowHeightPx: Int, val rowsPerPage: Int) {
    /**
     * What one turn advances by.
     *
     * Integer division leaves `viewport % rowsPerPage` pixels over — under 12px, which is less than
     * a row's own top padding — so what shows under the last row of a page is a sliver of the next
     * row's blank margin rather than a sliced line of text.
     */
    val pageHeightPx: Int get() = rowHeightPx * rowsPerPage
}

/**
 * A single row height that divides the page exactly, or null when the list should be left alone.
 *
 * A viewport can only be tiled by rows of *uniform* height — with variable heights some row always
 * straddles the bottom edge, and the choice is between slicing it and leaving a gap as tall as
 * whatever it cut. On a 1000px page of 260px rows that gap is 220px, a fifth of the screen. Fixing
 * one height for every row removes the leftover entirely, at the cost of rows no longer sizing
 * themselves to their own content.
 *
 * The count is chosen by rounding rather than flooring so rows move as little as possible: a 260px
 * row against a 1000px page becomes four of 250 rather than three of 333.
 *
 * Rounding down to a tile shorter than the row only works because the row gives the difference back
 * — a description line, a few percent off the thumbnail — and [minRowPx] is how far that goes. Past
 * it the tile would cut into the metadata row rather than trim the description, and cropping a row's
 * date and reading time in half is the one outcome worse than a ragged page. So the nearest count is
 * taken when the row can absorb it and one fewer, taller row otherwise.
 *
 * Either way a tile more than [maxDriftFraction] from the row's own height is declined: a row given
 * a fifth of itself again in whitespace, or squeezed a fifth smaller, is no longer the row the
 * layout settings describe.
 */
fun resolveRowTiling(
    viewportPx: Int,
    naturalRowPx: Int,
    minRowPx: Int = naturalRowPx,
    maxRowsPerPage: Int = MAX_TILED_ROWS_PER_PAGE,
    maxDriftFraction: Float = MAX_TILE_DRIFT_FRACTION
): RowTiling? {
    if (viewportPx <= 0 || naturalRowPx <= 0) return null
    val nearest = (viewportPx.toFloat() / naturalRowPx).roundToInt().coerceIn(1, maxRowsPerPage)
    val fitting = (viewportPx / naturalRowPx).coerceIn(1, maxRowsPerPage)
    // `fitting` is either the same count or one fewer, which is to say a taller tile.
    return listOf(nearest, fitting).distinct().firstNotNullOfOrNull { rows ->
        val height = viewportPx / rows
        val drift = abs(height - naturalRowPx).toFloat() / naturalRowPx
        RowTiling(rowHeightPx = height, rowsPerPage = rows)
            .takeIf { height >= minRowPx.coerceAtLeast(1) && drift <= maxDriftFraction }
    }
}

/**
 * Whether a layout's rows can reasonably be held to one height.
 *
 * [LayoutType.CARD] — Magazine — cannot. Its hero image is sized by its own aspect ratio, so two
 * rows differ by hundreds of pixels and no single height is close to both; holding one to a tile
 * crops the image rather than trimming a line of text, and the image is the layout's whole point.
 * The list layouts all size themselves from their thumbnail and their text, both of which the
 * layout declares up front.
 */
fun layoutSupportsTiling(layoutType: LayoutType): Boolean = layoutType != LayoutType.CARD

/**
 * Extra pixels to fold into a page turn so it lands on a row top.
 *
 * Tiling makes this arithmetic rather than a search: every row is [RowTiling.rowHeightPx] tall, so
 * the distance back to the current row's top is exactly the first visible item's scroll offset, and
 * a page is a whole number of rows. A forward turn therefore advances `page - offset` and a
 * backward one `page + offset`, both of which land on a row top from any position — including one
 * the user reached by dragging. Neither skips a row nor repeats one: the page that follows starts
 * where this one ended.
 *
 * The result is added to a turn of [pageDeltaPx], which the caller has already signed, so it is
 * returned as the difference between the two.
 */
fun tiledTurnAdjustment(
    direction: PageTurnDirection,
    pageDeltaPx: Float,
    tiling: RowTiling,
    firstVisibleOffsetPx: Int
): Float {
    val page = tiling.pageHeightPx.toFloat()
    val offset = firstVisibleOffsetPx.coerceAtLeast(0).toFloat()
    return when (direction) {
        PageTurnDirection.NEXT -> page - offset - pageDeltaPx
        PageTurnDirection.PREVIOUS -> pageDeltaPx - page - offset
    }
}

/**
 * The pieces that decide how tall a bookmark row is, in pixels, as the active layout declares them.
 *
 * Declared rather than measured, for two reasons. The natural height has to be known *before* a
 * tile is imposed, and the moment one is every row reports the tile back — a live measurement would
 * be reading its own output and the quantiser would chase it. And reading the layout's own settings
 * does not depend on which bookmarks happen to be on screen: a list opening on two short entries
 * picks the same number of rows per page as one opening on two long ones.
 *
 * It describes the *tallest* row the layout can produce — a title at its two-line cap, a
 * description at its own — and [minHeightPx] the shortest, with the description down to one line
 * and the thumbnail trimmed. Everything between the two is a height the row can be held to without
 * anything being cut off it.
 */
data class BookmarkRowMetrics(
    /** One side of the row's inner padding; a row carries two. */
    val verticalPaddingPx: Float,
    /** Padding the swipe/quick-action wrapper puts around the whole row, both sides together. */
    val wrapperPaddingPx: Float,
    /** The thumbnail the text column is laid out beside, or 0 when the layout hides it. */
    val thumbnailPx: Float,
    val titleLinePx: Float,
    /** Lines the title is capped at. */
    val titleLines: Int,
    /** One line of url below the title, or 0 when it sits elsewhere or is hidden. */
    val urlLinePx: Float,
    val descriptionLinePx: Float,
    /** Lines of description in the text column; 0 when it is hidden or sits with the metadata. */
    val descriptionLines: Int,
    /** Tags, the date/badge row and anything else in the metadata band, spacing included. */
    val metadataPx: Float,
    /**
     * The three rows [metadataPx] adds up, each without the spacing above it.
     *
     * The band's total is what tiling needs; what it is *made of* is what a placeholder needs.
     * A tag chip is a filled block and the trailing row is a date at one end with a link and a
     * reading time at the other — drawn as one bar the height of all three, they read as a thin
     * rule with a large gap around it rather than as the row they stand for.
     */
    val metadataDescriptionPx: Float = 0f,
    val tagsPx: Float = 0f,
    val metadataLinePx: Float = 0f,
    /** Metadata BESIDE sits inside the text column; ABOVE and BELOW span the whole row. */
    val metadataInTextColumn: Boolean,
    /** The title spans the row above the thumbnail instead of sitting beside it. */
    val titleAboveThumbnail: Boolean,
    /** The 4dp gap each block in the text column carries above it. */
    val blockSpacingPx: Float,
    /** The 8dp gap around a band that spans the whole row. */
    val sectionSpacingPx: Float,
    /** The hairline under a flat row, or 0. */
    val dividerPx: Float
) {
    /** Everything that is not the thumbnail-and-text band: padding, spanning bands, the divider. */
    val chromeHeightPx: Float
        get() = wrapperPaddingPx + 2 * verticalPaddingPx + dividerPx +
            (if (titleAboveThumbnail) titleLinePx * titleLines + sectionSpacingPx else 0f) +
            (if (metadataInTextColumn || metadataPx <= 0f) 0f else metadataPx + sectionSpacingPx)

    /** The text column, laid out beside the thumbnail, with the description at [lines]. */
    private fun textColumnPx(lines: Int): Float =
        (if (titleAboveThumbnail) 0f else titleLinePx * titleLines) +
            (if (urlLinePx > 0f) urlLinePx + blockSpacingPx else 0f) +
            (if (lines > 0) descriptionLinePx * lines + blockSpacingPx else 0f) +
            (if (metadataInTextColumn && metadataPx > 0f) metadataPx + blockSpacingPx else 0f)

    /** The band the thumbnail and the text column share, which is the taller of the two. */
    val bodyHeightPx: Float get() = max(thumbnailPx, textColumnPx(descriptionLines))

    /** How tall the row lays itself out when nothing holds it to a height. */
    val naturalHeightPx: Int get() = ceil(chromeHeightPx + bodyHeightPx).toInt()

    /**
     * The shortest tile the row can be held to and still lay out whole: the description down to its
     * last line and the thumbnail down to [MIN_TILED_THUMBNAIL_FRACTION] of itself. Nothing below
     * this can be taken from the row without cutting into the title or the metadata.
     */
    val minHeightPx: Int
        get() = ceil(
            chromeHeightPx + max(
                thumbnailPx * MIN_TILED_THUMBNAIL_FRACTION,
                textColumnPx(descriptionLines.coerceAtMost(1))
            )
        ).toInt()
}
