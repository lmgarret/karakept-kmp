package com.karakept.app.ui.utils

/** A `LazyListState.scrollToItem` argument pair. */
data class ReaderScrollTarget(val index: Int, val offsetPx: Int)

/**
 * Geometry for the reader's scroll cursor.
 *
 * The bookmark list's cursor works in row indices — every row is an item, so "60% down the list"
 * is the row 60% of the way through, and `ScrollCursorUtils` is that one line of arithmetic. The
 * reader's cannot: its whole article is a *single* lazy item. It works in content pixels instead,
 * reconstructing the absolute scroll position from the heights of the items it has seen, the same
 * way `rememberReadingProgress` does. Kept here as plain functions so that reconstruction is
 * testable without a composition.
 *
 * [itemHeights] is indexed by item position; a height of 0 means "not measured yet", which is
 * treated as an item occupying no space rather than one of unknown size — the reader has a handful
 * of items and the article dominates all of them.
 */
fun readerAbsoluteScrollOffset(
    itemHeights: List<Int>,
    firstVisibleIndex: Int,
    firstVisibleItemScrollOffset: Int
): Int {
    var offset = firstVisibleItemScrollOffset
    for (i in 0 until firstVisibleIndex.coerceAtMost(itemHeights.size)) {
        offset += itemHeights[i]
    }
    return offset
}

/**
 * How far the list can travel: everything below the first screenful.
 *
 * [contentPaddingPx] is scrollable too — under E-ink mode the reader adds a generous trailing page
 * of it so the last turn lands on a handover — so it counts towards the range even though no item
 * reports it.
 */
fun readerScrollRange(
    itemHeights: List<Int>,
    viewportHeightPx: Int,
    contentPaddingPx: Int = 0
): Int = (itemHeights.sum() + contentPaddingPx - viewportHeightPx).coerceAtLeast(0)

/** The item and in-item offset that put content pixel [absolutePx] at the top of the viewport. */
fun readerScrollTarget(itemHeights: List<Int>, absolutePx: Int): ReaderScrollTarget {
    if (itemHeights.isEmpty()) return ReaderScrollTarget(0, 0)
    val target = absolutePx.coerceAtLeast(0)
    var consumed = 0
    for (index in itemHeights.indices) {
        val height = itemHeights[index]
        if (height > 0 && target < consumed + height) {
            return ReaderScrollTarget(index, target - consumed)
        }
        consumed += height
    }
    // Past the end of everything measured: rest on the last item that has a height.
    val lastMeasured = itemHeights.indexOfLast { it > 0 }
    if (lastMeasured < 0) return ReaderScrollTarget(0, 0)
    return ReaderScrollTarget(lastMeasured, itemHeights[lastMeasured])
}

/**
 * Which section [fraction] falls in: the last heading at or above it, or -1 when the position is
 * above the first heading (an article's lede, or one with no headings at all).
 */
fun sectionIndexAt(fractions: List<Float>, fraction: Float): Int {
    var found = -1
    for (i in fractions.indices) {
        if (fractions[i] <= fraction + FRACTION_EPSILON) found = i else break
    }
    return found
}

private const val FRACTION_EPSILON = 0.001f

/**
 * Which table-of-contents labels fit alongside the track without overlapping each other.
 *
 * A long article has more headings than a phone has room for, so labels are thinned by distance:
 * [current] is always kept — it is the one the drag is asking about — and the rest are accepted
 * outward from it while they stay [minSpacingPx] apart, alternating up and down so the preview
 * stays balanced around the thumb rather than trailing off in one direction.
 *
 * Returns indices into [fractions], ascending.
 */
fun pickTocLabels(
    fractions: List<Float>,
    trackHeightPx: Float,
    minSpacingPx: Float,
    current: Int,
    maxLabels: Int = 14
): List<Int> {
    if (fractions.isEmpty() || trackHeightPx <= 0f || maxLabels <= 0) return emptyList()

    val anchor = current.coerceIn(0, fractions.lastIndex)
    fun y(index: Int) = fractions[index] * trackHeightPx

    val accepted = mutableListOf(anchor)
    var up = anchor - 1
    var down = anchor + 1
    var lastUp = y(anchor)
    var lastDown = y(anchor)

    while (accepted.size < maxLabels && (up >= 0 || down < fractions.size)) {
        if (down < fractions.size) {
            if (y(down) - lastDown >= minSpacingPx) {
                accepted.add(down)
                lastDown = y(down)
            }
            down++
        }
        if (accepted.size >= maxLabels) break
        if (up >= 0) {
            if (lastUp - y(up) >= minSpacingPx) {
                accepted.add(up)
                lastUp = y(up)
            }
            up--
        }
    }

    return accepted.sorted()
}
