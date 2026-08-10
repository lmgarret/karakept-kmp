package com.karakept.app.ui.utils

import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.PageTurnKeyBindings

/**
 * Pixels to scroll for one page turn.
 *
 * A page is the viewport minus an overlap, so the last line or two of the outgoing page stays on
 * screen — standard e-reader behaviour, and the only way to avoid losing a line that straddles the
 * fold. Positive scrolls forward (content moves up), negative scrolls back.
 *
 * Returns 0 for a non-positive viewport, which happens before the list has been measured.
 */
fun computePageScrollDelta(
    viewportHeightPx: Int,
    overlapPercent: Int,
    direction: PageTurnDirection
): Float {
    if (viewportHeightPx <= 0) return 0f
    val clampedOverlap = overlapPercent.coerceIn(
        PageTurnKeyBindings.MIN_OVERLAP_PERCENT,
        PageTurnKeyBindings.MAX_OVERLAP_PERCENT
    )
    val magnitude = viewportHeightPx * (1f - clampedOverlap / 100f)
    return when (direction) {
        PageTurnDirection.NEXT -> magnitude
        PageTurnDirection.PREVIOUS -> -magnitude
    }
}
