package com.karakept.app.ui.utils

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Line-height multiplier the HTML renderer bakes into body text, documented on `ReaderTypography`.
 * Used to size one line of prose without measuring one.
 */
const val BODY_LINE_HEIGHT_RATIO = 1.6f

/**
 * Where a page turn left the list, so the paged bottom edge can be drawn only while the content is
 * still sitting exactly there.
 *
 * A blank band makes sense on a settled page and nowhere else: mid-drag it reads as content that
 * ends early and rows that pop in at the edge. Comparing positions rather than listening for drag
 * interactions catches every other way the list can move — fling, wheel, scroll restoration, a
 * programmatic jump to a highlight — without enumerating them.
 */
class PagedPositionState {
    private var index by mutableStateOf(NONE)
    private var offset by mutableStateOf(NONE)

    fun markSettled(state: LazyListState) {
        index = state.firstVisibleItemIndex
        offset = state.firstVisibleItemScrollOffset
    }

    fun isSettled(state: LazyListState): Boolean =
        index == state.firstVisibleItemIndex && offset == state.firstVisibleItemScrollOffset

    private companion object {
        const val NONE = -1
    }
}

/**
 * Trailing padding for [layoutInfo]'s list, or 0 when the content already fits.
 *
 * The overflow test deliberately reads item *sizes* rather than the scroll position: padding does
 * not move the items, so this answer does not change as the user scrolls into the padding, and the
 * padding cannot feed back into its own condition.
 */
fun trailingPagePaddingFor(layoutInfo: LazyListLayoutInfo, tailUnitPx: Int): Int {
    val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val visible = layoutInfo.visibleItemsInfo
    if (viewport <= 0 || visible.isEmpty()) return 0
    val overflows = visible.size < layoutInfo.totalItemsCount ||
        visible.sumOf { it.size } > viewport
    if (!overflows) return 0
    return computeTrailingPagePadding(viewport, tailUnitPx)
}

/**
 * Whether the end of the content is already showing above the bottom edge, so a forward turn has
 * nothing left to reveal.
 *
 * Trailing padding makes this worth asking. Without it the list simply runs out of scroll, but the
 * padding lets a turn keep going into the blank space, producing a page that shows only the tail —
 * content the previous page had already displayed in full. Stopping here is what makes the last
 * page land on the previous page's handover instead of past it.
 */
fun tailFullyVisible(layoutInfo: LazyListLayoutInfo, obscuredBottomPx: Int): Boolean {
    val last = layoutInfo.lastItemIfComplete() ?: return false
    return last.offset + last.size <= layoutInfo.viewportEndOffset - obscuredBottomPx
}

/**
 * The blank space below the end of the content, or 0 while the last item is still off screen or
 * running past the bottom edge. See [blankBelowContentPx] for why a caller would want it.
 *
 * Note this is 0 both when there is no gap and when the question does not apply, which is why
 * [tailFullyVisible] tests the flush case itself rather than reading a zero here — content ending
 * exactly on the bottom edge is a full last page, not an absent one.
 */
fun blankBelowContent(layoutInfo: LazyListLayoutInfo, obscuredBottomPx: Int): Int {
    val last = layoutInfo.lastItemIfComplete() ?: return 0
    return blankBelowContentPx(
        contentBottomPx = last.offset + last.size,
        visibleBottomPx = layoutInfo.viewportEndOffset - obscuredBottomPx
    )
}

/** The last visible item, but only when it is genuinely the end of the content. */
private fun LazyListLayoutInfo.lastItemIfComplete(): LazyListItemInfo? =
    visibleItemsInfo.lastOrNull()?.takeIf { it.index == totalItemsCount - 1 }

/**
 * Paints over whatever the bottom edge of the page cuts through, so a page ends on a whole row or
 * a whole line rather than a sliced one. The covered content is exactly what the next turn brings
 * back — see [bottomMaskHeight].
 *
 * [residualPx] is given the visible bottom edge in this node's own coordinates and returns how far
 * that edge sits past the last boundary. It runs in the draw phase, so whatever it reads determines
 * when the band is repainted: a screen whose boundaries come from somewhere other than snapshot
 * state has to touch an observable itself.
 *
 * The band runs to the very bottom of the node rather than stopping at the visible edge, so text
 * scrolling under a translucent system bar is covered too instead of reappearing below the gap.
 */
fun Modifier.pagedBottomEdge(
    enabled: Boolean,
    color: Color,
    maxSnapFraction: Float,
    obscuredTopPx: Int = 0,
    obscuredBottomPx: Int = 0,
    isSettledOnPage: () -> Boolean,
    residualPx: (visibleBottomPx: Float) -> Float
): Modifier = drawWithContent {
    drawContent()
    if (!enabled || !isSettledOnPage()) return@drawWithContent
    val visibleBottom = size.height - obscuredBottomPx
    val page = size.height - obscuredTopPx - obscuredBottomPx
    val band = bottomMaskHeight(residualPx(visibleBottom), page, maxSnapFraction)
    if (band <= 0f) return@drawWithContent
    val top = visibleBottom - band
    drawRect(color, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
}
