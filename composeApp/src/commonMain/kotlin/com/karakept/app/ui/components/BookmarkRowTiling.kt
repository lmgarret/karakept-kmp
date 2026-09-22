package com.karakept.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.TitlePosition
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.ui.utils.BookmarkRowMetrics
import com.karakept.app.ui.utils.RowTiling
import com.karakept.app.ui.utils.layoutSupportsTiling
import com.karakept.app.ui.utils.resolveRowTiling
import kotlin.math.max

/**
 * The tile a paged bookmark list holds every row to.
 *
 * [itemHeight] is the whole list item, [rowHeight] the row inside the padding its swipe wrapper
 * adds, and [bodyHeight] the part of that the thumbnail-and-text band gets — which is the space an
 * automatic description count fills.
 */
@Immutable
data class TiledRows(
    val tiling: RowTiling,
    val itemHeight: Dp,
    val rowHeight: Dp,
    val bodyHeight: Dp
)

/**
 * Resolves the height every row is held to so an exact number of them fills the page, or null to
 * leave rows sizing themselves.
 *
 * [viewportPx] is the height of the list itself, taken from the constraints rather than from
 * `layoutInfo`: the answer is then known before the first row is laid out, so the list is never
 * drawn once at its natural height and again at the tile.
 */
@Composable
fun rememberTiledRows(
    enabled: Boolean,
    viewportPx: Int,
    layoutType: LayoutType,
    metrics: BookmarkRowMetrics
): TiledRows? {
    val density = LocalDensity.current
    return remember(enabled, viewportPx, layoutType, metrics, density) {
        if (!enabled || !layoutSupportsTiling(layoutType)) return@remember null
        val tiling = resolveRowTiling(
            viewportPx = viewportPx,
            naturalRowPx = metrics.naturalHeightPx,
            minRowPx = metrics.minHeightPx
        ) ?: return@remember null
        with(density) {
            TiledRows(
                tiling = tiling,
                itemHeight = tiling.rowHeightPx.toDp(),
                rowHeight = (tiling.rowHeightPx - metrics.wrapperPaddingPx).toDp(),
                // What is left for the thumbnail and the text beside it once the row's padding,
                // its metadata band and its divider have taken their share. Handing the row the
                // whole tile instead would let an automatic description grow straight through
                // everything below it.
                bodyHeight = max(0f, tiling.rowHeightPx - metrics.chromeHeightPx).toDp()
            )
        }
    }
}

/**
 * Describes one row of [BookmarkListLayout] as the layout settings declare it — see
 * [BookmarkRowMetrics] for why this is declared rather than measured.
 */
@Composable
internal fun rememberBookmarkRowMetrics(
    itemContainerStyle: ItemContainerStyle,
    showThumbnail: Boolean,
    thumbnailSize: Int,
    titlePosition: TitlePosition,
    showDescription: Boolean,
    descriptionMaxLines: Int,
    descriptionPosition: DescriptionPosition,
    showUrl: Boolean,
    urlPosition: UrlPosition,
    showTags: Boolean,
    showDate: Boolean,
    showReadingTime: Boolean,
    metadataPosition: MetadataPosition,
    showRowDivider: Boolean
): BookmarkRowMetrics {
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.titleMedium
    val bodyStyle = MaterialTheme.typography.bodySmall
    val labelStyle = MaterialTheme.typography.labelSmall
    return remember(
        density, titleStyle, bodyStyle, labelStyle, itemContainerStyle, showThumbnail,
        thumbnailSize, titlePosition, showDescription, descriptionMaxLines, descriptionPosition,
        showUrl, urlPosition, showTags, showDate, showReadingTime, metadataPosition, showRowDivider
    ) {
        bookmarkRowMetrics(
            density = density,
            titleStyle = titleStyle,
            bodyStyle = bodyStyle,
            labelStyle = labelStyle,
            itemContainerStyle = itemContainerStyle,
            showThumbnail = showThumbnail,
            thumbnailSize = thumbnailSize,
            titlePosition = titlePosition,
            showDescription = showDescription,
            descriptionMaxLines = descriptionMaxLines,
            descriptionPosition = descriptionPosition,
            showUrl = showUrl,
            urlPosition = urlPosition,
            showTags = showTags,
            showDate = showDate,
            showReadingTime = showReadingTime,
            metadataPosition = metadataPosition,
            showRowDivider = showRowDivider
        )
    }
}

private fun bookmarkRowMetrics(
    density: Density,
    titleStyle: TextStyle,
    bodyStyle: TextStyle,
    labelStyle: TextStyle,
    itemContainerStyle: ItemContainerStyle,
    showThumbnail: Boolean,
    thumbnailSize: Int,
    titlePosition: TitlePosition,
    showDescription: Boolean,
    descriptionMaxLines: Int,
    descriptionPosition: DescriptionPosition,
    showUrl: Boolean,
    urlPosition: UrlPosition,
    showTags: Boolean,
    showDate: Boolean,
    showReadingTime: Boolean,
    metadataPosition: MetadataPosition,
    showRowDivider: Boolean
): BookmarkRowMetrics = with(density) {
    val isFlat = itemContainerStyle == ItemContainerStyle.FLAT
    val bodyLinePx = bodyStyle.lineHeightPx(density)
    // A chip and a badge are both one label line inside 4dp of padding, top and bottom.
    val chipPx = labelStyle.lineHeightPx(density) + 8.dp.toPx()
    val blockSpacingPx = 4.dp.toPx()

    val descriptionInColumn = showDescription &&
        descriptionPosition == DescriptionPosition.BELOW_TITLE
    val descriptionLines = when {
        !descriptionInColumn -> 0
        // Automatic fills whatever space is left over, so it adds nothing of its own beyond the
        // one line it never goes below — unless there is no thumbnail to fill against, where the
        // row falls back to a fixed count.
        descriptionMaxLines == BookmarkLayout.DESCRIPTION_LINES_AUTO ->
            if (showThumbnail) 1 else BookmarkLayout.DESCRIPTION_LINES_DEFAULT
        else -> descriptionMaxLines.coerceIn(1, BookmarkLayout.DESCRIPTION_LINES_MAX)
    }

    // The band is three stacked rows, not one block. Kept apart because a placeholder has to
    // draw them apart: a tag chip is a filled block and the date/link/time row is a few small
    // marks, and one bar spanning the lot reads as a rule adrift in a large gap.
    // The description sits inside the band in this position, capped at two lines.
    val metadataDescriptionPx =
        if (showDescription && descriptionPosition == DescriptionPosition.ABOVE_METADATA) {
            2 * bodyLinePx
        } else 0f
    val tagsPx = if (showTags) chipPx else 0f
    val metadataLinePx = max(
        if (showDate) bodyLinePx else 0f,
        if (showReadingTime) chipPx else 0f
    )
    val metadataPx = listOf(
        if (metadataDescriptionPx > 0f) metadataDescriptionPx + blockSpacingPx else 0f,
        if (tagsPx > 0f) tagsPx + blockSpacingPx else 0f,
        metadataLinePx
    ).sum()

    BookmarkRowMetrics(
        verticalPaddingPx = (if (isFlat) 12.dp else 16.dp).toPx(),
        // Cards are spaced apart by their wrapper; flat rows are full-bleed and are not.
        wrapperPaddingPx = if (isFlat) 0f else 2 * 8.dp.toPx(),
        thumbnailPx = if (showThumbnail) thumbnailSize.dp.toPx() else 0f,
        titleLinePx = titleStyle.lineHeightPx(density),
        titleLines = TITLE_LINE_CAP,
        urlLinePx = if (showUrl && urlPosition == UrlPosition.BELOW_TITLE) bodyLinePx else 0f,
        descriptionLinePx = bodyLinePx,
        descriptionLines = descriptionLines,
        metadataPx = metadataPx,
        metadataDescriptionPx = metadataDescriptionPx,
        tagsPx = tagsPx,
        metadataLinePx = metadataLinePx,
        metadataInTextColumn = metadataPosition == MetadataPosition.BESIDE,
        titleAboveThumbnail = showThumbnail && titlePosition == TitlePosition.ABOVE_THUMBNAIL,
        blockSpacingPx = blockSpacingPx,
        sectionSpacingPx = 8.dp.toPx(),
        dividerPx = if (isFlat && showRowDivider) 1.dp.toPx() else 0f
    )
}

/** What [BookmarkListLayout] caps every title at. */
private const val TITLE_LINE_CAP = 2

/** Material sets a line height on every style; the ratio is only there so a custom one cannot
 * return zero and collapse the estimate. */
private const val FALLBACK_LINE_HEIGHT_RATIO = 1.4f

private fun TextStyle.lineHeightPx(density: Density): Float = with(density) {
    when {
        lineHeight.isSpecified && lineHeight.type == TextUnitType.Sp -> lineHeight.toPx()
        fontSize.isSpecified && fontSize.type == TextUnitType.Sp ->
            fontSize.toPx() * FALLBACK_LINE_HEIGHT_RATIO
        else -> 0f
    }
}
