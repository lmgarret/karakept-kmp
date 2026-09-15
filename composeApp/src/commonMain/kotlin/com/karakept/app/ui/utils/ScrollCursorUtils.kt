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

/**
 * The list is sized by the view, so every index the thumb can name is a slot that already exists
 * and a jump is `scrollToItem`. There is nothing here to walk to.
 *
 * There used to be: the list was indexed by the rows read so far, so a thumb dropped past them
 * aimed at a row the list had no slot for. Reaching it meant asking for the rows in between and
 * holding the thumb where it was dropped until they arrived — a read between the finger stopping
 * and the list following, measured at a median of 38ms and a p90 of 68ms, roughly sixteen of them
 * for a full drag. Virtualizing removed the class rather than the instance.
 */
