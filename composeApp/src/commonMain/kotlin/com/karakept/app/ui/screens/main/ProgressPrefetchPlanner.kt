package com.karakept.app.ui.screens.main

import com.karakept.app.data.model.BookmarkWindow
import kotlin.math.abs

/**
 * Decides which slots to pull reading progress for as the list scrolls, so a row's read state
 * is settled before it reaches the screen rather than after.
 *
 * Asking only for the rows on screen meant a row changed on another device arrived showing its
 * old state and flipped a moment later. The planner asks for a band running [AHEAD] rows past
 * the viewport in the direction of travel, and asks again once the viewport comes within
 * [TRIGGER_MARGIN] rows of the band's edge — one request per [AHEAD] − [TRIGGER_MARGIN] rows
 * scrolled, however many frames that takes.
 *
 * A fling is left alone: rows crossing faster than [MAX_ROWS_PER_SECOND] are gone before an
 * answer could land, so nothing is asked until the list slows or settles.
 *
 * Slots are layout indices. Which bookmarks they name is the caller's business.
 */
internal class ProgressPrefetchPlanner {
    private var lastVisible: IntRange? = null
    private var lastMoveAtMs = 0L
    private var forward = true
    private var requested: IntRange? = null

    /** The slots to ask for now that the viewport moved to [visible], or null for none. */
    fun onScrolled(visible: IntRange, total: Int, nowMs: Long): IntRange? {
        val previous = lastVisible
        lastVisible = visible
        if (previous == null) {
            lastMoveAtMs = nowMs
            return null
        }
        val moved = (visible.first - previous.first).takeIf { it != 0 } ?: (visible.last - previous.last)
        if (moved == 0) return null
        forward = moved > 0
        val elapsedMs = (nowMs - lastMoveAtMs).coerceAtLeast(1)
        lastMoveAtMs = nowMs
        if (abs(moved) * 1000L / elapsedMs > MAX_ROWS_PER_SECOND) return null
        if (!nearEdgeOfRequested(visible, total)) return null
        return request(visible, total)
    }

    /** The slots to ask for once scrolling has come to rest on [visible]. */
    fun onSettled(visible: IntRange, total: Int): IntRange {
        lastVisible = visible
        return request(visible, total)
    }

    private fun nearEdgeOfRequested(visible: IntRange, total: Int): Boolean {
        val band = requested ?: return true
        return if (forward) {
            band.last < total - 1 && visible.last + TRIGGER_MARGIN > band.last
        } else {
            band.first > 0 && visible.first - TRIGGER_MARGIN < band.first
        }
    }

    private fun request(visible: IntRange, total: Int): IntRange {
        if (total <= 0) return IntRange.EMPTY
        val before = if (forward) BEHIND else AHEAD
        val after = if (forward) AHEAD else BEHIND
        val band = (visible.first - before).coerceIn(0, total - 1)..
            (visible.last + after).coerceIn(0, total - 1)
        requested = band
        return band
    }

    companion object {
        /** Two progress batches' worth: the rows a steady scroll reaches in the next few seconds. */
        const val AHEAD = 40
        const val BEHIND = 10
        const val TRIGGER_MARGIN = 10
        const val MAX_ROWS_PER_SECOND = 20
    }
}

/** The viewport as the layout reports it, with one loaded row named so it can be re-found. */
internal data class VisibleRows(
    val slots: IntRange,
    val anchorKey: String?,
    val anchorIndex: Int
)

/**
 * The bookmarks in [slots], which were counted against [viewport].
 *
 * The layout lags the window by a snapshot: a sync re-indexes everything below whatever it
 * prepends, so layout indices read straight off the new window can name rows that are nowhere
 * near the screen. The anchor row is looked up by key and the slots shifted by however far it
 * moved. Slots the window has not read are skipped — their progress arrives with the row.
 */
internal fun BookmarkWindow.remoteIdsAround(viewport: VisibleRows, slots: IntRange): List<String> {
    if (slots.isEmpty()) return emptyList()
    val anchorNow = viewport.anchorKey?.let(::indexOfRemoteId)?.takeIf { it >= 0 }
    val shift = if (anchorNow != null) anchorNow - viewport.anchorIndex else 0
    return remoteIdsInSlots(slots.first + shift, slots.last + shift).toList()
}
