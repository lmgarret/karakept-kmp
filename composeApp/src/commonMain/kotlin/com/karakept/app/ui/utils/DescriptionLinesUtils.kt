package com.karakept.app.ui.utils

import kotlin.math.floor

/**
 * How many description lines fit in the space left beside the thumbnail.
 *
 * The row is as tall as its thumbnail, so whatever the title, url and metadata do not use is dead
 * space — usually 20-30dp of it, and much more when the title sits above the thumbnail. This turns
 * that leftover into description lines instead of stopping at a fixed count.
 *
 * Pure arithmetic on already-measured heights: the caller measures the title with a `TextMeasurer`
 * so the input is exact rather than assumed.
 *
 * @param thumbnailHeightPx the height the row will take regardless, i.e. the thumbnail.
 * @param consumedHeightPx everything above the description in the text column that is already
 *   measured — title (zero when it sits above the thumbnail), url, metadata, and their spacing.
 * @param descriptionLineHeightPx one line of description text.
 * @param maxLines ceiling, so a very tall thumbnail cannot produce an absurd wall of text.
 */
fun computeAutoDescriptionLines(
    thumbnailHeightPx: Float,
    consumedHeightPx: Float,
    descriptionLineHeightPx: Float,
    maxLines: Int = AUTO_DESCRIPTION_LINE_CEILING
): Int {
    if (descriptionLineHeightPx <= 0f) return 1
    val available = thumbnailHeightPx - consumedHeightPx
    if (available <= 0f) return 1
    val fits = floor(available / descriptionLineHeightPx).toInt()
    // Always at least one line: a description that is configured to show but renders zero lines
    // reads as a bug, and one clipped line is more useful than none.
    return fits.coerceIn(1, maxLines)
}

/**
 * Upper bound for the automatic line count. Beyond this the row stops being a list item.
 */
const val AUTO_DESCRIPTION_LINE_CEILING = 8
