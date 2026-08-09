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
 * when bookmarks scroll off the top, or when the user reaches the bottom of the list.
 *
 * The whole difficulty is telling a real scroll gesture apart from the list mutating
 * underneath the viewport. A sync does the latter constantly — `refreshLoadedPagesInPlace`
 * runs once per committed page plus twice more per `syncBookmarks()` — and each pass can
 * prepend rows, which makes Compose re-index every item below them. Comparing raw indices
 * across such a swap reads the re-indexing as a scroll and fires the action on bookmarks
 * the user never saw.
 *
 * [ScrollActionTracker] therefore re-baselines its anchor whenever the dataset changes and
 * fires nothing for that snapshot: the shift belongs to the mutation, not to the user. Only
 * while the dataset is stable can a change in the first visible index mean a scroll, and
 * only then is anything fired.
 *
 * The effect is keyed on [currentListId] so it restarts on list switch, guaranteeing a
 * fresh tracker and preventing the action from leaking across lists.
 */
@Composable
fun MainScreenScrollAction(
    listState: LazyListState,
    bookmarks: List<BookmarkEntity>,
    currentListId: String?,
    currentListScrollAction: SwipeAction,
    currentListScrollActionConfig: CustomSwipeActionConfig?,
    screenModel: MainScreenModel
) {
    // Wrap plain parameters as Compose State so snapshotFlow can detect changes. Without this
    // the snapshotFlow lambda captures the initial parameter value and never sees updates,
    // leaving `bookmarks` frozen at its first-composition value.
    val currentBookmarksState = rememberUpdatedState(bookmarks)

    LaunchedEffect(currentListId, currentListScrollAction, currentListScrollActionConfig) {
        if (currentListScrollAction == SwipeAction.NONE) return@LaunchedEffect
        val tracker = ScrollActionTracker()

        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            ScrollActionSnapshot(
                firstIndex = visibleItems.firstOrNull()?.index ?: 0,
                // The trailing empty/loading/end rows are unkeyed, so their key is not a
                // remoteId — treat anything that isn't one as "no anchor".
                firstKey = visibleItems.firstOrNull()?.key as? Long,
                firstOffset = listState.firstVisibleItemScrollOffset,
                lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1,
                bookmarks = currentBookmarksState.value,
                isScrolling = listState.isScrollInProgress,
                actedOnIds = screenModel.actedOnBookmarkIds.value
            )
        }.collect { snapshot ->
            tracker.onSnapshot(snapshot).forEach { bookmark ->
                screenModel.executeScrollAction(
                    bookmark, currentListScrollAction, currentListScrollActionConfig
                )
            }
        }
    }
}

internal data class ScrollActionSnapshot(
    val firstIndex: Int,
    val firstKey: Long?,
    val firstOffset: Int,
    val lastVisibleIndex: Int,
    val bookmarks: List<BookmarkEntity>,
    val isScrolling: Boolean,
    val actedOnIds: Set<Long> = emptySet()
)

/**
 * Stateful half of [MainScreenScrollAction], extracted so the bookkeeping can be unit-tested
 * without a composition.
 *
 * [onSnapshot] returns the bookmarks whose scroll action should fire for that snapshot.
 */
internal class ScrollActionTracker {
    private var anchorKey: Long? = null
    private var anchorIndex = 0
    private var bottomReached = false

    // Set only by an observed scroll gesture that actually moved the list. A mutation can
    // never set it, which is what stops a sync from unlocking the bottom sweep.
    private var userHasScrolled = false

    private var lastBookmarks: List<BookmarkEntity>? = null
    private var lastIndex = 0
    private var lastOffset = 0

    // Guard against double-firing. Entries are dropped for items that become visible again
    // when the user scrolls back up, so a manually-unread bookmark can be re-triggered on the
    // next scroll-down. Kept across dataset swaps so a sync never re-fires a surviving item.
    private val processedIds = mutableSetOf<Long>()

    fun onSnapshot(snapshot: ScrollActionSnapshot): List<BookmarkEntity> {
        val previousBookmarks = lastBookmarks
        val moved = snapshot.firstIndex != lastIndex || snapshot.firstOffset != lastOffset
        lastBookmarks = snapshot.bookmarks
        lastIndex = snapshot.firstIndex
        lastOffset = snapshot.firstOffset

        if (previousBookmarks != null && snapshot.bookmarks !== previousBookmarks) {
            // The dataset and layoutInfo update in separate snapshots, so this snapshot's
            // indices still describe the outgoing list. Re-baseline only, fire nothing.
            rebaseline(snapshot)
            return emptyList()
        }

        // Requiring actual movement keeps an overscroll gesture — a pull-to-refresh at the top
        // of a list that already fits on screen — from counting as a scroll.
        if (snapshot.isScrolling && moved) userHasScrolled = true

        if (anchorKey == null) {
            // Nothing to compare against yet — adopt the current position silently.
            anchorKey = snapshot.firstKey
            anchorIndex = snapshot.firstIndex
            return emptyList()
        }

        val fired = mutableListOf<BookmarkEntity>()

        // The dataset is stable, so a change in the first visible index is a genuine scroll:
        // no re-indexing can have happened.
        when {
            snapshot.firstIndex > anchorIndex -> {
                collectRange(anchorIndex, snapshot.firstIndex, snapshot, fired)
                anchorIndex = snapshot.firstIndex
                anchorKey = snapshot.firstKey ?: anchorKey
                bottomReached = false
            }
            snapshot.firstIndex < anchorIndex -> {
                // Scrolled back up: those items are on screen again, so let them re-fire.
                for (i in snapshot.firstIndex until anchorIndex) {
                    snapshot.bookmarks.getOrNull(i)?.let { processedIds.remove(it.remoteId) }
                }
                anchorIndex = snapshot.firstIndex
                anchorKey = snapshot.firstKey ?: anchorKey
            }
        }

        // The last screenful never leaves the top, so it needs its own rule. Requiring a real
        // gesture is what keeps a sync — which can make the list end at the viewport by
        // prepending rows or by dropping rows off the tail — from firing this on its own.
        val total = snapshot.bookmarks.size
        val atBottom = total > 0 && snapshot.lastVisibleIndex >= total - 1
        if (!bottomReached && atBottom && userHasScrolled) {
            collectRange(anchorIndex, total, snapshot, fired)
            bottomReached = true
        }

        return fired
    }

    /**
     * Follows the anchor bookmark to its index in the new dataset so the next comparison is
     * made in the new list's coordinates. [bottomReached] is deliberately preserved: a
     * mutation is not a reason to sweep the bottom again, only a fresh scroll down is.
     */
    private fun rebaseline(snapshot: ScrollActionSnapshot) {
        val key = anchorKey ?: return
        val newIndex = snapshot.bookmarks.indexOfFirst { it.remoteId == key }
        if (newIndex >= 0) {
            anchorIndex = newIndex
        } else {
            // Anchor gone — a full reload replaced the dataset, or the anchor itself was
            // removed. Re-adopt from the next stable snapshot rather than guessing an index
            // from layout info that still describes the old list.
            anchorKey = null
            anchorIndex = 0
        }
    }

    private fun collectRange(
        from: Int,
        until: Int,
        snapshot: ScrollActionSnapshot,
        into: MutableList<BookmarkEntity>
    ) {
        for (i in from until until) {
            val bookmark = snapshot.bookmarks.getOrNull(i) ?: continue
            if (!processedIds.add(bookmark.remoteId)) continue
            // The user explicitly acted on this bookmark (e.g. moved it to a list that will
            // remove it via async reconciliation) — absorbed above so we never fire on it.
            if (bookmark.remoteId in snapshot.actedOnIds) continue
            into.add(bookmark)
        }
    }
}
