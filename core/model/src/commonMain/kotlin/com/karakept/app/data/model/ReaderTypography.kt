package com.karakept.app.data.model

/**
 * User-controlled reader layout metrics.
 *
 * [lineHeightScale] is a *multiplier* rather than an absolute line height so the per-block ratios
 * baked into the HTML renderer (body 1.6, headings 1.4, code 0.875 × 1.4) stay proportional to
 * each other.
 */
data class ReaderTypography(
    val lineHeightScale: Float = DEFAULT_LINE_HEIGHT_SCALE,
    val horizontalMarginDp: Int = DEFAULT_HORIZONTAL_MARGIN_DP,
    val maxWidthDp: Int = DEFAULT_MAX_WIDTH_DP
) {
    companion object {
        const val DEFAULT_LINE_HEIGHT_SCALE = 1.0f
        const val MIN_LINE_HEIGHT_SCALE = 0.8f
        const val MAX_LINE_HEIGHT_SCALE = 2.0f

        const val DEFAULT_HORIZONTAL_MARGIN_DP = 28
        const val MIN_HORIZONTAL_MARGIN_DP = 8
        const val MAX_HORIZONTAL_MARGIN_DP = 64

        const val DEFAULT_MAX_WIDTH_DP = 900
        const val MIN_MAX_WIDTH_DP = 400
        const val MAX_MAX_WIDTH_DP = 1200
    }
}
