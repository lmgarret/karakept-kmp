package com.karakept.app.ui.screens.main

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.karakept.app.data.local.entity.BookmarkEntity

/**
 * Keeps the viewport pinned to the same bookmark when [bookmarks] mutates beneath
 * the user.
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
    bookmarks: List<BookmarkEntity>,
    bookmarkListVersion: Int
) {
    val currentBookmarks = rememberUpdatedState(bookmarks)
    val currentVersion = rememberUpdatedState(bookmarkListVersion)
    LaunchedEffect(listState) {
        var anchorKey: Long? = null
        var anchorOffset = 0
        var lastList: List<BookmarkEntity>? = null
        var lastVersion = currentVersion.value

        snapshotFlow {
            AnchorSnapshot(
                bookmarks = currentBookmarks.value,
                firstIndex = listState.firstVisibleItemIndex,
                firstOffset = listState.firstVisibleItemScrollOffset,
                isScrolling = listState.isScrollInProgress,
                listVersion = currentVersion.value
            )
        }.collect { snap ->
            val listChanged = lastList != null && snap.bookmarks !== lastList
            val versionChanged = snap.listVersion != lastVersion
            if (listChanged) {
                val target = resolveAnchorScrollTarget(
                    anchorKey = anchorKey,
                    isScrolling = snap.isScrolling,
                    versionChanged = versionChanged,
                    currentFirstIndex = snap.firstIndex,
                    newBookmarks = snap.bookmarks
                )
                if (target != null) {
                    listState.requestScrollToItem(target, anchorOffset)
                }
            } else {
                // List is stable: record the bookmark currently at the top of the viewport.
                anchorKey = snap.bookmarks.getOrNull(snap.firstIndex)?.remoteId
                anchorOffset = snap.firstOffset
            }
            lastList = snap.bookmarks
            lastVersion = snap.listVersion
        }
    }
}

private data class AnchorSnapshot(
    val bookmarks: List<BookmarkEntity>,
    val firstIndex: Int,
    val firstOffset: Int,
    val isScrolling: Boolean,
    val listVersion: Int
)

/**
 * Pure decision function for [PreserveListScrollAnchor].
 *
 * Returns the index the list should be re-pinned to so [anchorKey] stays at the top
 * of the viewport after a surgical list change, or `null` when no correction is needed:
 *  - no anchor recorded yet, or
 *  - the user is actively scrolling (don't fight the gesture), or
 *  - the list version changed — a full reload that wants its own scroll behaviour, or
 *  - the anchor bookmark is gone (it was the removed one), or
 *  - the anchor is already the first visible item (Compose handled it / nothing moved).
 */
internal fun resolveAnchorScrollTarget(
    anchorKey: Long?,
    isScrolling: Boolean,
    versionChanged: Boolean,
    currentFirstIndex: Int,
    newBookmarks: List<BookmarkEntity>
): Int? {
    if (anchorKey == null || isScrolling || versionChanged) return null
    val newIndex = newBookmarks.indexOfFirst { it.remoteId == anchorKey }
    if (newIndex < 0 || newIndex == currentFirstIndex) return null
    return newIndex
}
