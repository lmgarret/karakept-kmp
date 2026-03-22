package com.karakept.app.ui.screens.main

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.screens.MainScreenModel
import com.karakept.app.ui.screens.executeScrollAction

/**
 * Scroll-triggered action: apply the active list's scroll action silently (no snackbar)
 * when bookmarks scroll off the top, or when reaching the bottom of the list.
 *
 * How we distinguish real scrolling from list mutations:
 * LazyList uses stable keys (bookmark remoteIds). When items are prepended above the
 * viewport, Compose adjusts firstVisibleItemIndex so the same item stays on screen --
 * the key at the current position is unchanged, only the index grows. When the user
 * actually scrolls down, a new item becomes first-visible (different key). We track
 * the "anchor" (key + index of the first visible item) and compare it on each emission.
 *
 * Prepend at absolute top (firstVisibleItemIndex stays 0):
 * When the user is at index 0 with no scroll offset, Compose does NOT shift the index
 * on prepend -- the new items appear above and the key at index 0 changes. We detect this
 * by searching the full bookmarks list for the old anchor key: if it moved to a higher
 * index, N items were prepended. We record this as newItemsUntil so the fire loop skips
 * those slots until the user explicitly scrolls past them.
 *
 * List replacement (sync / filter change):
 * MainScreenModel increments bookmarkListVersion on every resetPaginationAndLoad. When
 * the version changes we run the same old-anchor search so newly inserted items are
 * protected by newItemsUntil -- preventing them from being bulk-fired before the user
 * has scrolled past them individually. processedIds persists across replacements to
 * avoid double-firing on bookmarks that survive the swap.
 */
@Composable
fun MainScreenScrollAction(
    listState: LazyListState,
    bookmarks: List<BookmarkEntity>,
    bookmarkListVersion: Int,
    currentListScrollAction: SwipeAction,
    currentListScrollActionConfig: CustomSwipeActionConfig?,
    screenModel: MainScreenModel
) {
    // Wrap plain parameters as Compose State so snapshotFlow can detect changes.
    // Without this, the snapshotFlow lambda captures the initial parameter values and
    // never sees updates (bookmarks would stay empty, listVersion would stay at 0).
    val currentBookmarksState = rememberUpdatedState(bookmarks)
    val currentListVersionState = rememberUpdatedState(bookmarkListVersion)

    LaunchedEffect(currentListScrollAction, currentListScrollActionConfig) {
        if (currentListScrollAction != SwipeAction.NONE) {
            var anchorKey: Any? = null   // key of the first visible item we're tracking
            var anchorIndex = 0          // current index of that anchor item
            var bottomReached = false
            var wasScrolling = false
            // Indices [0, newItemsUntil) contain items that appeared via prepend/sync.
            // The fire loop skips those slots so the action isn't triggered on bookmarks
            // the user hasn't explicitly scrolled past.
            var newItemsUntil = 0
            var lastSeenListVersion = bookmarkListVersion
            // Guard against double-firing: tracks remoteIds we've already acted on.
            // Cleared for items that become visible again when the user scrolls back up,
            // so a manually-unread bookmark can be re-triggered on the next scroll-down.
            // Kept across list replacements so surviving bookmarks are not re-fired by sync.
            val processedIds = mutableSetOf<Long>()

            data class ScrollSnapshot(
                val firstIndex: Int,
                val firstKey: Any?,
                val lastVisibleIndex: Int,
                val currentBookmarks: List<BookmarkEntity>,
                val isScrolling: Boolean,
                val listVersion: Int
            )

            snapshotFlow {
                ScrollSnapshot(
                    firstIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0,
                    firstKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key,
                    lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                    currentBookmarks = currentBookmarksState.value,
                    isScrolling = listState.isScrollInProgress,
                    listVersion = currentListVersionState.value
                )
            }.collect { snapshot ->
                val currentBookmarks = snapshot.currentBookmarks
                val totalBookmarks = currentBookmarks.size
                val newFirstIndex = snapshot.firstIndex
                val newFirstKey = snapshot.firstKey
                val isScrolling = snapshot.isScrolling

                // scrollJustStopped is used only for the short-list bottom case below.
                val scrollJustStopped = wasScrolling && !isScrolling
                wasScrolling = isScrolling

                // List replacement detection: bookmarkListVersion is incremented on every
                // resetPaginationAndLoad (sync, filter change, server switch). When it
                // changes we must re-initialize the anchor AND apply the same prepend-
                // detection logic used in the else branch below: if the old anchor survived
                // in the new list at a higher index, new items were inserted above it and
                // must be protected by newItemsUntil so they aren't bulk-fired before the
                // user has scrolled past each one individually. processedIds is kept intact
                // so bookmarks that survived the swap are never acted on twice.
                if (snapshot.listVersion != lastSeenListVersion) {
                    lastSeenListVersion = snapshot.listVersion
                    val oldAnchorNewIndex = if (anchorKey != null) {
                        currentBookmarks.indexOfFirst { it.remoteId == anchorKey }
                    } else -1
                    if (oldAnchorNewIndex > anchorIndex) {
                        // Items were inserted above the old anchor -- protect the new slots.
                        newItemsUntil = maxOf(newItemsUntil, oldAnchorNewIndex)
                    } else {
                        // Full replacement or anchor not found -- reset all guards.
                        newItemsUntil = 0
                    }
                    anchorKey = newFirstKey
                    anchorIndex = newFirstIndex
                    bottomReached = false
                    return@collect
                }

                if (anchorKey == null) {
                    // First emission: initialise anchor without firing any actions.
                    anchorKey = newFirstKey
                    anchorIndex = newFirstIndex
                    return@collect
                }

                when {
                    newFirstIndex > anchorIndex -> {
                        if (newFirstKey == anchorKey) {
                            // Same item at a higher index: Compose shifted the index because
                            // items were prepended above the viewport. Protect those new slots.
                            newItemsUntil = maxOf(newItemsUntil, newFirstIndex)
                            anchorIndex = newFirstIndex
                        } else {
                            // A different item is now first-visible -- the user actually
                            // scrolled down. Items [anchorIndex, newFirstIndex) left the top.
                            for (i in anchorIndex until newFirstIndex) {
                                if (i < newItemsUntil) continue  // skip newly prepended items
                                val scrolledBookmark = currentBookmarks.getOrNull(i) ?: continue
                                if (scrolledBookmark.remoteId in processedIds) continue
                                processedIds.add(scrolledBookmark.remoteId)
                                screenModel.executeScrollAction(scrolledBookmark, currentListScrollAction, currentListScrollActionConfig)
                            }
                            anchorIndex = newFirstIndex
                            anchorKey = newFirstKey
                            bottomReached = false
                            if (anchorIndex >= newItemsUntil) newItemsUntil = 0
                        }
                    }
                    newFirstIndex < anchorIndex -> {
                        // Scrolled back up. Items [newFirstIndex, anchorIndex) are now
                        // visible again -- remove them from processedIds so a bookmark that
                        // was manually marked as unread can be re-triggered on the next
                        // scroll-down.
                        for (i in newFirstIndex until anchorIndex) {
                            val bookmark = currentBookmarks.getOrNull(i) ?: continue
                            processedIds.remove(bookmark.remoteId)
                        }
                        anchorIndex = newFirstIndex
                        anchorKey = newFirstKey ?: anchorKey
                        bottomReached = false
                    }
                    else -> {
                        // firstVisibleItemIndex is unchanged. The key may have changed if
                        // items were prepended while the user was at the absolute top
                        // (index 0, zero scroll offset): Compose keeps the index at 0 and
                        // the new items slide in above, making a different key appear at 0.
                        if (newFirstKey != null && newFirstKey != anchorKey) {
                            // Search the FULL bookmarks list for the old anchor -- not just
                            // visible items -- so we catch prepends larger than the viewport.
                            val oldAnchorNewIndex = currentBookmarks.indexOfFirst {
                                it.remoteId == anchorKey
                            }
                            if (oldAnchorNewIndex > anchorIndex) {
                                // Old anchor moved down: N items were prepended. Protect
                                // those new slots so they are skipped until intentionally
                                // scrolled past.
                                newItemsUntil = maxOf(newItemsUntil, oldAnchorNewIndex)
                            } else {
                                // Old anchor not found or unchanged -- unexpected state;
                                // reset the skip guard conservatively.
                                newItemsUntil = 0
                            }
                            anchorKey = newFirstKey
                        }
                    }
                }

                // When the last visible item is the last bookmark, apply the action to all
                // remaining visible items that haven't been processed yet.
                // For long lists: anchorIndex > 0 means the user has scrolled at least one
                // item off the top in the current direction. This naturally resets to false
                // when the user scrolls back to the top (anchorIndex returns to 0), preventing
                // newly prepended items from being fired when the user hasn't scrolled.
                // For short lists where no item ever leaves the top: fires when the user
                // finishes a scroll gesture at the bottom (scrollJustStopped).
                val atBottom = totalBookmarks > 0 && snapshot.lastVisibleIndex >= totalBookmarks - 1
                if (!bottomReached && atBottom && (anchorIndex > 0 || scrollJustStopped)) {
                    for (i in anchorIndex until totalBookmarks) {
                        if (i < newItemsUntil) continue
                        val scrolledBookmark = currentBookmarks.getOrNull(i) ?: continue
                        if (scrolledBookmark.remoteId in processedIds) continue
                        processedIds.add(scrolledBookmark.remoteId)
                        screenModel.executeScrollAction(scrolledBookmark, currentListScrollAction, currentListScrollActionConfig)
                    }
                    anchorIndex = totalBookmarks
                    bottomReached = true
                    if (anchorIndex >= newItemsUntil) newItemsUntil = 0
                }
            }
        }
    }
}
