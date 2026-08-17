package com.karakept.app.ui.utils

/**
 * How far a reader turn may walk back to reach the top of a text line. A line is a few percent of
 * a page, so the only thing this quarter-page limit rejects is a fold that landed inside an image
 * or a table — those register no snap points, and rewinding to the last line above them would lose
 * a screenful of content.
 */
const val READER_MAX_SNAP_FRACTION = 0.25f

/**
 * Extra scroll that pulls a page turn back onto a content boundary, so the element straddling the
 * fold is shown whole on the new page instead of sliced.
 *
 * [residualPx] is how far past the last boundary the fold landed. The result is negative (walk
 * back) or 0 when snapping would cost more than [maxSnapFraction] of the page — an element taller
 * than that would otherwise rewind most of the turn, or leave it standing still.
 *
 * Direction-agnostic: a forward and a backward turn both align the viewport top to the boundary at
 * or above it, so the caller passes the page magnitude rather than the signed delta.
 */
fun computeSnapAdjustment(
    residualPx: Float,
    pageDeltaPx: Float,
    maxSnapFraction: Float
): Float {
    if (pageDeltaPx <= 0f || residualPx <= 0f) return 0f
    if (residualPx > pageDeltaPx * maxSnapFraction) return 0f
    return -residualPx
}

/**
 * Height of the blank band that hides the element the bottom edge cuts through, so a page *ends*
 * on a boundary as well as starting on one.
 *
 * Aligning both edges by scrolling is impossible — they are one page apart and the content decides
 * where the boundaries fall — so the leftover space is simply not drawn, as in any paginated
 * reader. The band is defined as the mirror of [computeSnapAdjustment] rather than recomputed,
 * because it must cover *exactly* what the next turn will bring back: mask more and that content is
 * lost, mask less and a sliver of a line still shows. In particular an element too tall to snap is
 * not banded either, and stays sliced across the two pages.
 */
fun bottomMaskHeight(residualPx: Float, pageDeltaPx: Float, maxSnapFraction: Float): Float {
    val adjustment = computeSnapAdjustment(residualPx, pageDeltaPx, maxSnapFraction)
    // Negating 0f yields -0f, which is not `equals` to 0f and would leak into callers' arithmetic
    // and tests as a different value.
    return if (adjustment == 0f) 0f else -adjustment
}

/**
 * Blank space to leave after the content so the final turn can put the last row or line at the top
 * of the page.
 *
 * Without it a forward turn at the end of the content clamps against the bottom, and the last page
 * repeats most of the previous one. [tailUnitPx] is one unit of content — a row, a line — so the
 * deepest reachable position shows that much at the top and nothing below. Over-padding is harmless
 * because a clamped turn still snaps back onto the last boundary; under-padding is not, which is
 * why the estimate errs generous.
 */
fun computeTrailingPagePadding(viewportPx: Int, tailUnitPx: Int): Int =
    (viewportPx - tailUnitPx).coerceAtLeast(0)

/**
 * How far the content would have to move back down for its end to reach the bottom edge — the
 * blank space left below the last row or line — and 0 whenever content still runs past that edge.
 *
 * [computeTrailingPagePadding] leaves room for a *turn* to land on the previous page's handover; it
 * is not somewhere to come to rest. Anything that positions the list by itself — restoring a saved
 * reading position above all — has to pull back out of that blank space, or reopening a
 * finished article lands on a page holding a single line.
 */
fun blankBelowContentPx(contentBottomPx: Int, visibleBottomPx: Int): Int =
    (visibleBottomPx - contentBottomPx).coerceAtLeast(0)

/**
 * Whether a forward turn would bring anything worth a page into view.
 *
 * A lazy item is not the same shape as the content inside it: the reader's article box runs some
 * 50px past its final line, in paragraph padding and the renderer's own bottom margin. Asking
 * whether the *item* is fully on screen therefore says "not yet" long after the last word has been
 * read, and the turn that follows lands on blank space.
 *
 * [contentEndGapPx] is how far the content runs past the bottom edge; a gap shorter than
 * [minAdvancePx] — one line — is that margin rather than something to read. The [hasLineBelow] term
 * is not redundant with it: an edge falling *inside* the final line leaves less than a line below
 * and still has to turn. Conversely a trailing image or table registers no lines at all, and only
 * the gap keeps it reachable.
 */
fun pageWorthTurning(
    hasLineBelow: Boolean,
    contentEndGapPx: Float,
    minAdvancePx: Float
): Boolean = hasLineBelow || contentEndGapPx > minAdvancePx
