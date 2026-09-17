package com.karakept.app.ui.screens.main

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.karakept.app.data.model.BookmarkWindow

/**
 * Keeps the viewport pinned to the same bookmark when the view mutates beneath the user.
 *
 * Concretely: after a quick action adds a bookmark to a list that the current smart
 * list excludes (e.g. adding a Feeds bookmark to Read Later), an asynchronous
 * reconciliation removes that bookmark from the visible list. If the removed item
 * sits above the viewport, the LazyColumn can shift and the list appears to "jump".
 *
 * We continuously record the bookmark currently at the top of the viewport. Whenever
 * the list is mutated *surgically* (an item updated/removed/appended in place) we
 * re-pin that bookmark to its new index via [LazyListState.requestScrollToItem],
 * which is applied during the next remeasure so there is no visible double-scroll.
 *
 * [bookmarkListVersion] increments only on a full reload (sync, filter/server change
 * via resetPaginationAndLoad), which legitimately wants to scroll to the top. We skip
 * re-pinning in that case so we never fight an intended scroll-to-top; we also leave
 * the position alone when the anchor bookmark itself was removed or the user is
 * actively scrolling.
 */
@Composable
internal fun PreserveListScrollAnchor(
    listState: LazyListState,
    window: BookmarkWindow,
    bookmarkListVersion: Int
) {
    val currentWindow = rememberUpdatedState(window)
    val currentVersion = rememberUpdatedState(bookmarkListVersion)
    LaunchedEffect(listState) {
        val anchor = ListScrollAnchorState(initialVersion = currentVersion.value)

        snapshotFlow {
            AnchorSnapshot(
                window = currentWindow.value,
                firstIndex = listState.firstVisibleItemIndex,
                firstOffset = listState.firstVisibleItemScrollOffset,
                isScrolling = listState.isScrollInProgress,
                listVersion = currentVersion.value
            )
        }.collect { snap ->
            val target = anchor.onSnapshot(snap)
            if (target != null) {
                listState.requestScrollToItem(target.index, target.offset)
            }
        }
    }
}

internal data class AnchorSnapshot(
    /**
     * The view as the list renders it, so a slot here is the slot the layout reports.
     *
     * Not the loaded rows: those are the window compacted — a hundred-odd entries sliding along
     * a view thousands long — while [firstIndex] comes from the layout. Reading the anchor out
     * of them by [firstIndex] named the wrong row, and re-pinning to a position found in them
     * could only ever land inside the first screenfuls, which is how scrolling down came to
     * throw the user back towards the top.
     */
    val window: BookmarkWindow,
    val firstIndex: Int,
    val firstOffset: Int,
    val isScrolling: Boolean,
    val listVersion: Int
)

internal data class AnchorScrollTarget(val index: Int, val offset: Int)

/**
 * Stateful half of [PreserveListScrollAnchor], extracted so the anchor bookkeeping can be
 * unit-tested without a composition.
 */
internal class ListScrollAnchorState(initialVersion: Int) {
    private var anchorKey: String? = null
    private var anchorOffset = 0
    private var anchorAtTop = false
    private var lastWindow: BookmarkWindow? = null
    private var lastVersion = initialVersion

    // A full reload bumps the version *and* swaps the dataset, but the two reach the UI
    // through different flows and usually land in separate snapshots. Latch the bump until
    // the swap actually arrives — otherwise the swap looks like a surgical change and we
    // re-pin the viewport to a bookmark carried over from the list the user just left,
    // which leaves the new list scrolled to an arbitrary position.
    private var reloadPending = false

    fun onSnapshot(snap: AnchorSnapshot): AnchorScrollTarget? {
        if (snap.listVersion != lastVersion) reloadPending = true
        lastVersion = snap.listVersion

        // What can move a row, and nothing else. Pages loading and being dropped change the
        // window constantly — a sync invalidates every one of them on each write — but a slot is
        // a position in the view, so none of that re-indexes anything. Reference inequality
        // counted all of it as a mutation.
        val previous = lastWindow
        val listChanged = previous != null &&
            (snap.window.total != previous.total || snap.window.generation != previous.generation)
        lastWindow = snap.window

        if (!listChanged) {
            // View is stable: record the bookmark currently at the top of the viewport.
            anchorKey = snap.window.bookmarkAt(snap.firstIndex)?.remoteId
            anchorOffset = snap.firstOffset
            anchorAtTop = snap.firstIndex == 0 && snap.firstOffset == 0
            return null
        }

        // Someone parked at the very top has not scrolled at all, so bookmarks a sync brings
        // in belong on screen rather than pushed above the viewport — pinning them out of
        // sight is what raises a "N new" pill for a user who is already looking at the top.
        // Compose re-anchors by key on a prepend, so holding position takes an explicit
        // request to index 0; returning null here would let the viewport drift down.
        if (anchorAtTop && anchorKey != null && !snap.isScrolling && !reloadPending) {
            return AnchorScrollTarget(0, 0)
        }

        val target = resolveAnchorScrollTarget(
            anchorKey = anchorKey,
            isScrolling = snap.isScrolling,
            versionChanged = reloadPending,
            currentFirstIndex = snap.firstIndex,
            window = snap.window
        )
        reloadPending = false
        return target?.let { AnchorScrollTarget(it, anchorOffset) }
    }
}

/**
 * Pure decision function for [PreserveListScrollAnchor].
 *
 * Returns the index the list should be re-pinned to so [anchorKey] stays at the top
 * of the viewport after a surgical list change, or `null` when no correction is needed:
 *  - no anchor recorded yet, or
 *  - the user is actively scrolling (don't fight the gesture), or
 *  - the list version changed — a full reload that wants its own scroll behaviour, or
 *  - the anchor bookmark is gone, or its page is no longer loaded, or
 *  - the anchor is already the first visible item (Compose handled it / nothing moved).
 */
internal fun resolveAnchorScrollTarget(
    anchorKey: String?,
    isScrolling: Boolean,
    versionChanged: Boolean,
    currentFirstIndex: Int,
    window: BookmarkWindow
): Int? {
    if (anchorKey == null || isScrolling || versionChanged) return null
    // A slot, so it is comparable with the layout's index and usable as a scroll target.
    val newIndex = window.indexOfRemoteId(anchorKey)
    if (newIndex < 0 || newIndex == currentFirstIndex) return null
    return newIndex
}
