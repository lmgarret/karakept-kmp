package com.karakept.app.ui.utils

import kotlin.math.roundToInt

/**
 * Maps the fast-scroll cursor onto the whole list rather than onto the loaded window.
 *
 * The bookmark list pages in as it is scrolled, so a thumb divided by the rows currently loaded
 * walks back up the track every time a page lands — it reports a position within the window, not
 * within the list (#273). Everything here is expressed over [total], the number of rows the
 * active filter matches in the database; the loaded window only bounds where a jump can land
 * right now, and [scrollCursorStep] is what walks the rest of the way.
 */

/** Where the thumb sits when the row at [firstVisibleIndex] is at the top of the viewport. */
fun scrollCursorFraction(firstVisibleIndex: Int, total: Int): Float {
    if (total <= 1) return 0f
    return (firstVisibleIndex.toFloat() / (total - 1).toFloat()).coerceIn(0f, 1f)
}

/** The row a thumb at [fraction] points at, over the full [total]. */
fun scrollCursorIndex(fraction: Float, total: Int): Int {
    if (total <= 1) return 0
    return (fraction.coerceIn(0f, 1f) * (total - 1)).roundToInt().coerceIn(0, total - 1)
}

/** What a jump to a target row can do against the window loaded right now. */
sealed interface ScrollCursorStep {
    /** The target is loaded — jump to it and the drag is resolved. */
    data class Land(val index: Int) : ScrollCursorStep

    /** The target lies past the window — go as far as it reaches and pull the next page in. */
    data class Pull(val index: Int) : ScrollCursorStep

    /** A page is already on its way; it decides where the next step goes. */
    data object Wait : ScrollCursorStep
}

/**
 * The move that brings the list closest to [targetIndex] given the [loadedCount] rows on hand.
 *
 * A target past the window resolves over several steps — one page per [ScrollCursorStep.Pull] —
 * which is the "fake infinite scroll" the cursor needs to reach an absolute position in a list
 * it has only partly loaded.
 *
 * [pulledAtCount] is the window size the last [ScrollCursorStep.Pull] asked from, and is what
 * stops the walk going on forever: a page request that comes back having added nothing, with
 * nothing in flight, cannot be waited out — the load failed, or [total] is ahead of what the
 * table actually holds — so the walk lands on the last row it has instead of asking again.
 */
fun scrollCursorStep(
    targetIndex: Int,
    loadedCount: Int,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    pulledAtCount: Int? = null
): ScrollCursorStep {
    if (loadedCount <= 0) return ScrollCursorStep.Wait
    val lastLoaded = loadedCount - 1
    if (targetIndex <= lastLoaded) return ScrollCursorStep.Land(targetIndex.coerceAtLeast(0))
    if (isLoadingMore) return ScrollCursorStep.Wait
    if (!canLoadMore || pulledAtCount == loadedCount) return ScrollCursorStep.Land(lastLoaded)
    return ScrollCursorStep.Pull(lastLoaded)
}
