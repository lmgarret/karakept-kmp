package com.karakept.app.ui.components.bookmark

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.ui.utils.computeAutoDescriptionLines

/**
 * Resolves a description line count against the space the row has for one.
 *
 * [BookmarkLayout.DESCRIPTION_LINES_AUTO] fills whatever the rest of the row leaves over. A fixed
 * count is honoured as a *cap* rather than a promise: under a tile the row may have room for fewer
 * lines than the layout asks for, and shedding one is how it gives back the pixels the tile took —
 * far better than rendering them and having the tile clip the metadata underneath.
 *
 * Measures the title with a [TextMeasurer] rather than assuming a line count — a wrapped title
 * costs a whole line, and guessing wrong either overflows the row or wastes the space this is
 * meant to reclaim. Measuring is synchronous and needs no subcomposition, but it does need the
 * row's width, which only the caller knows.
 *
 * Falls back to the requested count whenever the space cannot be reasoned about: nothing to fill
 * against, no width supplied, or metadata sitting in the text column (tags are a `FlowRow` whose
 * height a text measurer cannot predict).
 */
@Composable
internal fun rememberEffectiveDescriptionLines(
    requested: Int,
    rowWidth: Dp?,
    showThumbnail: Boolean,
    thumbnailSize: Dp,
    title: String,
    titleCountsAgainstThumbnail: Boolean,
    showUrlBelowTitle: Boolean,
    /**
     * Height the thumbnail-and-text band is held to when the list tiles its rows. It replaces the
     * thumbnail as the space to fill — and unlike the thumbnail it is there even when the layout
     * shows no image, which is the case e-ink actually uses.
     */
    fillHeight: Dp? = null
): Int {
    val isAuto = requested == BookmarkLayout.DESCRIPTION_LINES_AUTO
    val cap = if (isAuto) {
        BookmarkLayout.DESCRIPTION_LINES_MAX
    } else {
        requested.coerceIn(1, BookmarkLayout.DESCRIPTION_LINES_MAX)
    }
    // A fixed count is only worth measuring against a tile — a row sizing itself already fits the
    // lines it asks for — while automatic needs either a tile or a thumbnail to fill.
    val measurable = fillHeight != null || (isAuto && showThumbnail)
    if (!measurable || rowWidth == null) {
        return if (isAuto) BookmarkLayout.DESCRIPTION_LINES_DEFAULT else cap
    }

    val measurer = rememberTextMeasurer(cacheSize = AUTO_LINES_MEASURE_CACHE)
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.titleMedium
    val bodyStyle = MaterialTheme.typography.bodySmall

    // Row padding (16dp each side), then the thumbnail and the 12dp gap beside it when there is one.
    val thumbnailWidth = if (showThumbnail) thumbnailSize + 12.dp else 0.dp
    val textColumnWidth = rowWidth - 32.dp - thumbnailWidth
    if (textColumnWidth <= 0.dp) return cap

    return with(density) {
        // A title above the thumbnail is not in the text column at all, and the tile has already
        // had that band deducted, so either way it costs the description nothing here.
        val titleHeight = if (titleCountsAgainstThumbnail) {
            measurer.measure(
                text = title,
                style = titleStyle,
                maxLines = 2,
                constraints = Constraints(maxWidth = textColumnWidth.roundToPx())
            ).size.height.toFloat()
        } else {
            0f
        }
        // The url is a single line by construction, and each block above the description carries
        // 4dp of top padding.
        val urlHeight = if (showUrlBelowTitle) bodyStyle.lineHeight.toPx() + 4.dp.toPx() else 0f
        computeAutoDescriptionLines(
            availableHeightPx = (fillHeight ?: thumbnailSize).toPx(),
            consumedHeightPx = titleHeight + urlHeight + 4.dp.toPx(),
            descriptionLineHeightPx = bodyStyle.lineHeight.toPx(),
            maxLines = cap
        )
    }
}

/**
 * Roughly a screenful of rows. The default of 8 evicts constantly while scrolling, so every row
 * would re-measure even though the same titles keep coming back into view.
 */
private const val AUTO_LINES_MEASURE_CACHE = 24
