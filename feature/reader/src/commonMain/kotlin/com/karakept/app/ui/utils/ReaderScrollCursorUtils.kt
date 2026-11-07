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
 *
 * [epsilon] absorbs the rounding a scroll goes through — a fraction is turned into whole pixels to
 * scroll to, and read back as whole pixels scrolled — so a list parked exactly on a heading still
 * counts as having reached it. The cursor passes about a pixel's worth.
 */
fun sectionIndexAt(fractions: List<Float>, fraction: Float, epsilon: Float = 0.001f): Int {
    var found = -1
    for (i in fractions.indices) {
        if (fractions[i] <= fraction + epsilon) found = i else break
    }
    return found
}

/** A stretch of the track, [start]–[end], over which the list holds still at [scroll]. */
data class SectionDwell(val start: Float, val end: Float, val scroll: Float) {
    val center: Float get() = (start + end) / 2f
}

/**
 * How the reader's scroll cursor turns a thumb position into a scroll position, and back.
 *
 * Linear, it never showed a heading where it could be read. Dragging the thumb a pixel moves a long
 * article by tens of pixels of text, so the frame in which a heading crossed the top of the
 * readable area was skipped straight past: by the first frame its section was named, the heading
 * was already behind the top bar (#277). So every section gets a *dwell* — a short stretch of track
 * over which the scroll holds still at exactly the offset that brings its heading into view. The
 * map is linear between dwells, and continuous and monotonic throughout, so the article never jumps
 * and never runs backwards under the finger; the dwells are where it pauses.
 *
 * Everything is a fraction: thumb positions of the thumb's travel, scroll positions of the scroll
 * range.
 */
class ReaderCursorTrack internal constructor(val dwells: List<SectionDwell>) {

    /** Where the list goes for a thumb at [thumb]. */
    fun scrollAt(thumb: Float): Float {
        val t = thumb.coerceIn(0f, 1f)
        var segmentStart = 0f
        var segmentScroll = 0f
        for (dwell in dwells) {
            if (t < dwell.start) return lerpSegment(segmentStart, segmentScroll, dwell.start, dwell.scroll, t)
            if (t <= dwell.end) return dwell.scroll
            segmentStart = dwell.end
            segmentScroll = dwell.scroll
        }
        return lerpSegment(segmentStart, segmentScroll, 1f, 1f, t)
    }

    /**
     * Where the thumb sits for a list at [scroll] — the inverse of [scrollAt], so a thumb let go
     * over a dwell stays on it rather than jumping to where a linear map would have put it. A list
     * parked within [epsilon] of a dwell's scroll is treated as parked on it, and the thumb goes
     * to the dwell's centre: the mark that names it.
     */
    fun thumbAt(scroll: Float, epsilon: Float = 0.0001f): Float {
        val s = scroll.coerceIn(0f, 1f)
        var segmentStart = 0f
        var segmentScroll = 0f
        for (dwell in dwells) {
            if (s < dwell.scroll - epsilon) {
                return inverseSegment(segmentStart, segmentScroll, dwell.start, dwell.scroll, s)
            }
            if (s <= dwell.scroll + epsilon) return dwell.center
            segmentStart = dwell.end
            segmentScroll = dwell.scroll
        }
        return inverseSegment(segmentStart, segmentScroll, 1f, 1f, s)
    }

    private fun lerpSegment(t0: Float, s0: Float, t1: Float, s1: Float, t: Float): Float =
        if (t1 <= t0) s1 else s0 + (s1 - s0) * ((t - t0) / (t1 - t0))

    private fun inverseSegment(t0: Float, s0: Float, t1: Float, s1: Float, s: Float): Float =
        if (s1 <= s0) t1 else t0 + (t1 - t0) * ((s - s0) / (s1 - s0))
}

/**
 * Builds the track for sections whose headings come into view at the scroll fractions [aims], in
 * document order, giving each a dwell [dwellHalfWidth] either side of its own position.
 *
 * A dwell takes at most a third of the way to each neighbour — the ends of the track count as
 * neighbours — so a run of short sections still gets a dwell each, only narrower, and there is
 * always a slope left between two of them. Cut at the midpoint instead, two close dwells touched,
 * and the article jumped from one heading to the next; capped at thirds, the map is continuous
 * everywhere and never steeper than three times its average.
 *
 * Aims are taken in order, and one that runs backwards (it cannot, in document order, but layout
 * is not ours to promise) is held at the one before it rather than folding the map back on itself.
 */
fun readerCursorTrack(aims: List<Float>, dwellHalfWidth: Float): ReaderCursorTrack {
    val ordered = ArrayList<Float>(aims.size)
    for (aim in aims) ordered += maxOf(aim.coerceIn(0f, 1f), ordered.lastOrNull() ?: 0f)
    val dwells = ordered.mapIndexed { index, aim ->
        val before = if (index == 0) 0f else ordered[index - 1]
        val after = if (index == ordered.lastIndex) 1f else ordered[index + 1]
        SectionDwell(
            start = maxOf(aim - dwellHalfWidth, aim - (aim - before) / 3f),
            end = minOf(aim + dwellHalfWidth, aim + (after - aim) / 3f),
            scroll = aim
        )
    }
    return ReaderCursorTrack(dwells)
}
