package com.karakept.app.ui.screens.main

import com.karakept.app.data.model.BookmarkWindow

/**
 * Decides whether the LazyColumn should animate its item changes.
 *
 * Item animations are for *surgical* changes — an item removed by smart-list reconciliation,
 * an item updated in place by a sync, a page appended by load-more. Switching lists instead
 * replaces the whole dataset, and animating that fades a whole list in over the one being
 * replaced. The decision is derived from the data itself, and taken during composition, so it
 * lands in the very frame that renders the swap; a flag delivered by a separate flow, or read
 * from an effect, always arrives at least one frame too late to suppress anything.
 *
 * A prepend is excluded too. Rows inserted at the head displace every row below them, and
 * the viewport is held at the top for a user who has not scrolled — so the placement spring
 * animates the whole visible list down from the top edge, which reads as the order shuffling
 * and settling back rather than as new items arriving. Landing them in place is what looks
 * like an insertion.
 *
 * Plain fields rather than snapshot state: updating them must not invalidate the composition
 * that is reading them.
 */
internal class ItemAnimationGate(initial: BookmarkWindow) {
    private var previous = initial
    private var previousRows = initial.loadedRows()
    private var enabled = true

    fun update(window: BookmarkWindow): Boolean {
        if (window === previous) return enabled
        val previousIds = previousRows.mapTo(HashSet(previousRows.size)) { it.remoteId }
        val rows = window.loadedRows()
        val survivors = rows.count { it.remoteId in previousIds }
        // Half of the shorter list surviving still reads as "the same list, changed".
        val sameList = previousRows.isEmpty() || rows.isEmpty() ||
            survivors * 2 >= minOf(previousRows.size, rows.size)
        enabled = sameList && !isPrepend(window)
        previous = window
        previousRows = rows
        return enabled
    }

    /**
     * True when rows were inserted above the row that used to be first.
     *
     * Asked of slots rather than of the loaded rows. Those are the window compacted, and they
     * slide as the user scrolls — so the row that was at their head lands further down them on
     * every scroll back up, which read as a prepend and switched the animations off. A slot is a
     * position in the view: it only moves when something really was inserted above it.
     */
    private fun isPrepend(window: BookmarkWindow): Boolean {
        val previousFirstId = previousRows.firstOrNull()?.remoteId ?: return false
        val was = previous.indexOfRemoteId(previousFirstId)
        val now = window.indexOfRemoteId(previousFirstId)
        return was >= 0 && now > was
    }
}
