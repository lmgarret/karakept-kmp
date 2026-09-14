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

    /**
     * The target lies past the window — ask for the rows out to [throughIndex] and stay put.
     *
     * The list deliberately does not move on a pull. Scrolling to the end of the window on the
     * way makes every page that lands visible, which turns a jump to the end of a large list
     * into a crawl through all of it.
     */
    data class Pull(val throughIndex: Int) : ScrollCursorStep

    /** A read is already on its way; it decides where the next step goes. */
    data object Wait : ScrollCursorStep
}

/**
 * The move that brings the list closest to [targetIndex] given the [loadedCount] rows on hand.
 *
 * A target past the window is normally resolved by one [ScrollCursorStep.Pull] — the read it asks
 * for reaches the target — and then one [ScrollCursorStep.Land]. A read that comes back short of
 * the target (a filter discarding more than the estimate allowed for) simply pulls again from the
 * larger window, which is the "fake infinite scroll" the cursor needs to reach an absolute
 * position in a list it has only partly loaded.
 *
 * [pulledAtCount] is the window size the last pull asked from, and is what stops that going on
 * forever: a read that comes back having added nothing, with nothing in flight, cannot be waited
 * out — it failed, or the total is ahead of what the table actually holds — so the cursor lands
 * on the last row it has instead of asking again.
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
    return ScrollCursorStep.Pull(targetIndex)
}
