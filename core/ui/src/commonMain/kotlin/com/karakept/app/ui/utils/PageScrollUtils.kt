package com.karakept.app.ui.utils

import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.PageTurnKeyBindings

/**
 * Pixels to scroll for one page turn.
 *
 * A page is the *visible* band minus an overlap, so the last line or two of the outgoing page stays
 * on screen — standard e-reader behaviour, and the only way to avoid losing a line that straddles
 * the fold. Positive scrolls forward (content moves up), negative scrolls back.
 *
 * [obscuredTopPx] and [obscuredBottomPx] are the parts of the viewport covered by chrome painted
 * over the list. A screen whose bars sit *above* its content (the reader: no Scaffold `topBar`, an
 * overlaid `ViewerTopBar`, edge-to-edge under the system bars) still reports those pixels in its
 * viewport, and paging by them buries a line behind the bar on every turn. Screens whose Scaffold
 * padding already excludes their bars — the bookmark list — leave both at 0.
 *
 * Returns 0 for a non-positive viewport, which happens before the list has been measured.
 */
fun computePageScrollDelta(
    viewportHeightPx: Int,
    overlapPercent: Int,
    direction: PageTurnDirection,
    obscuredTopPx: Int = 0,
    obscuredBottomPx: Int = 0
): Float {
    if (viewportHeightPx <= 0) return 0f
    val clampedOverlap = overlapPercent.coerceIn(
        PageTurnKeyBindings.MIN_OVERLAP_PERCENT,
        PageTurnKeyBindings.MAX_OVERLAP_PERCENT
    )
    val readable = viewportHeightPx -
        obscuredTopPx.coerceAtLeast(0) -
        obscuredBottomPx.coerceAtLeast(0)
    // Insets taller than the viewport mean a half-measured frame, not a zero-height page. Falling
    // back to the raw viewport turns one page slightly too far; returning 0 would stop the button
    // working at all.
    val effectiveHeight = if (readable > 0) readable else viewportHeightPx
    val magnitude = effectiveHeight * (1f - clampedOverlap / 100f)
    return when (direction) {
        PageTurnDirection.NEXT -> magnitude
        PageTurnDirection.PREVIOUS -> -magnitude
    }
}
