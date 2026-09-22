package com.karakept.app.ui.screens.main

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkWindow
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.input.PageTurnDispatcher
import com.karakept.app.ui.screens.MainScreenModel
import com.karakept.app.ui.screens.executeScrollAction
import org.koin.compose.koinInject

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
    window: BookmarkWindow,
    currentListId: String?,
    currentListScrollAction: SwipeAction,
    currentListScrollActionConfig: CustomSwipeActionConfig?,
    screenModel: MainScreenModel
) {
    // Wrap plain parameters as Compose State so snapshotFlow can detect changes. Without this
    // the snapshotFlow lambda captures the initial parameter value and never sees updates,
    // leaving the window frozen at its first-composition value.
    val currentWindowState = rememberUpdatedState(window)

    // A hardware page turn is a deliberate gesture, but an *instant* one (the e-ink setting)
    // completes inside a single frame, so `isScrollInProgress` is never sampled as true and
    // the list appears to have moved on its own. Counting turns gives the tracker the one
    // thing it cannot read off the scroll state.
    val dispatcher = koinInject<PageTurnDispatcher>()
    var pageTurns by remember { mutableStateOf(0) }
    LaunchedEffect(dispatcher) {
        dispatcher.events.collect { pageTurns++ }
    }

    LaunchedEffect(currentListId, currentListScrollAction, currentListScrollActionConfig) {
        if (currentListScrollAction == SwipeAction.NONE) return@LaunchedEffect
        val tracker = ScrollActionTracker()

        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            ScrollActionSnapshot(
                firstIndex = visibleItems.firstOrNull()?.index ?: 0,
                // The trailing empty/loading/end rows are unkeyed, so their key is not a
                // remoteId — treat anything that isn't one as "no anchor".
                firstKey = visibleItems.firstOrNull()?.key as? String,
                firstOffset = listState.firstVisibleItemScrollOffset,
                lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1,
                window = currentWindowState.value,
                isScrolling = listState.isScrollInProgress,
                actedOnIds = screenModel.actedOnBookmarkIds.value,
                pageTurns = pageTurns
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
    val firstKey: String?,
    val firstOffset: Int,
    val lastVisibleIndex: Int,
    /**
     * The view as the list renders it, so every index here means the same thing.
     *
     * Not the loaded rows: those are the window compacted, a hundred-odd entries sliding along a
     * view thousands long, while `firstIndex` and `lastVisibleIndex` come from the layout and are
     * positions in the *view*. Mixing the two made `lastVisibleIndex >= rows.size - 1` true for
     * good once the user scrolled past the end of what happened to be loaded — the list read as
     * permanently at its bottom, and the bottom rule marked the whole loaded window read.
     */
    val window: BookmarkWindow,
    val isScrolling: Boolean,
    val actedOnIds: Set<String> = emptySet(),
    /** Hardware page turns so far. A change means the user turned a page. */
    val pageTurns: Int = 0
)

/**
 * Stateful half of [MainScreenScrollAction], extracted so the bookkeeping can be unit-tested
 * without a composition.
 *
 * [onSnapshot] returns the bookmarks whose scroll action should fire for that snapshot.
 */
internal class ScrollActionTracker {
    // The row last seen at the top of the viewport, held as a key rather than an index.
    // A sync re-indexes every row below whatever it prepends, so an index means different
    // things before and after one — and the dataset and the layout reach this tracker in
    // separate snapshots, leaving a window where the two disagree. Keys are the same in
    // either list, so the comparison is valid whichever half of the swap a snapshot caught.
    private var anchorKey: String? = null
    private var bottomReached = false

    // Set only by an observed scroll gesture that actually moved the list. A mutation can
    // never set it, which is what stops a sync from unlocking the bottom sweep.
    private var userHasScrolled = false

    private var lastWindow: BookmarkWindow? = null
    private var lastIndex = 0
    private var lastOffset = 0
    private var lastPageTurns = 0

    // Guard against double-firing. Entries are dropped for items that become visible again
    // when the user scrolls back up, so a manually-unread bookmark can be re-triggered on the
    // next scroll-down. Kept across dataset swaps so a sync never re-fires a surviving item.
    private val processedIds = mutableSetOf<String>()

    fun onSnapshot(snapshot: ScrollActionSnapshot): List<BookmarkEntity> {
        val previousWindow = lastWindow
        // What re-indexes rows, and nothing else. A page loading or being dropped changes the
        // window without moving a single row: a slot is a position in the view, so the rows
        // around it keep their indices whether or not they have been read. Only the view
        // growing or shrinking — or becoming a different view — moves anything.
        val datasetChanged = previousWindow != null &&
            (snapshot.window.total != previousWindow.total ||
                snapshot.window.generation != previousWindow.generation)
        val moved = snapshot.firstIndex != lastIndex || snapshot.firstOffset != lastOffset
        val turned = snapshot.pageTurns != lastPageTurns
        lastWindow = snapshot.window
        lastIndex = snapshot.firstIndex
        lastOffset = snapshot.firstOffset
        lastPageTurns = snapshot.pageTurns

        // Requiring actual movement keeps an overscroll gesture — a pull-to-refresh at the top
        // of a list that already fits on screen — from counting as a scroll, and requiring a
        // stable dataset keeps a sync's re-indexing from looking like one. A page turn counts
        // on its own: pressing the button is the gesture, and an instant turn finishes inside
        // one frame, so the scroll it performs is never observed in progress.
        if ((snapshot.isScrolling && moved && !datasetChanged) || turned) userHasScrolled = true

        val anchor = anchorKey
        if (anchor == null) {
            // Nothing to compare against yet — adopt the current position silently.
            anchorKey = snapshot.firstKey
            return emptyList()
        }

        val window = snapshot.window
        // The top row is unchanged on most frames of a scroll, and then neither branch below
        // can fire — so the scans are only worth paying for when it moved, or when the bottom
        // rule is armed and might.
        val bottomRuleCouldFire = !datasetChanged && !bottomReached && userHasScrolled
        if (anchor == snapshot.firstKey && !bottomRuleCouldFire) return emptyList()

        // A slot, so it compares with the layout's own indices. -1 also covers the anchor's
        // page having been dropped, which is not the anchor being gone — but the rows between
        // here and the viewport are unreadable either way, so there is nothing to fire on.
        val anchorIndex = window.indexOfRemoteId(anchor)
        if (anchorIndex < 0) {
            // Anchor gone — a full reload replaced the dataset, or the anchor itself was
            // removed. Adopt the row on screen instead.
            anchorKey = snapshot.firstKey
            // The rows on screen are not the ones the user was scrolling a moment ago, so the
            // gesture that got them here does not carry over. Applying a filter is the case
            // that showed it: the bookmarks it brings into view sit inside one screenful, and
            // an inherited "has scrolled" let the bottom rule mark them read on arrival —
            // before the user could read the one they had gone looking for.
            userHasScrolled = false
            bottomReached = false
            return emptyList()
        }
        val topIndex = snapshot.firstKey?.let(window::indexOfRemoteId) ?: -1

        val fired = mutableListOf<BookmarkEntity>()

        when {
            topIndex > anchorIndex -> {
                // The top of the viewport is now below the row that was there before, which
                // only a scroll can do: a prepend moves both keys together and leaves their
                // order alone. Rows the dataset gained in this same snapshot are excluded —
                // the user cannot have scrolled past a row that was not there.
                val alreadyPresent = if (datasetChanged) {
                    previousWindow.loadedRows().mapTo(HashSet()) { it.remoteId }
                } else {
                    null
                }
                collectRange(anchorIndex, topIndex, snapshot, fired, alreadyPresent)
                anchorKey = snapshot.firstKey
                bottomReached = false
            }
            topIndex in 0 until anchorIndex -> {
                // Scrolled back up: those items are on screen again, so let them re-fire.
                for (i in topIndex until anchorIndex) {
                    window.bookmarkAt(i)?.let { processedIds.remove(it.remoteId) }
                }
                anchorKey = snapshot.firstKey
            }
        }

        // The last screenful never leaves the top, so it needs its own rule. It is index-based
        // and therefore only meaningful once the dataset and the layout agree again; requiring
        // a real gesture is what keeps a sync — which can make the list end at the viewport by
        // prepending rows or by dropping rows off the tail — from firing it on its own.
        if (!datasetChanged) {
            // The view's length, not how much of it is loaded. Against the loaded count this
            // read as "at the bottom" from the first page the user scrolled past, and swept the
            // whole window every time.
            val total = window.total
            val atBottom = total > 0 && snapshot.lastVisibleIndex >= total - 1
            if (!bottomReached && atBottom && userHasScrolled) {
                // Whichever branch above ran (if any) left the anchor on a row whose index is
                // already in hand — no third scan for it.
                collectRange(if (topIndex >= 0) topIndex else anchorIndex, total, snapshot, fired)
                bottomReached = true
            }
        }

        return fired
    }

    /**
     * @param alreadyPresent when non-null, only rows in it may fire — the ids the dataset held
     *   before this snapshot changed it.
     */
    private fun collectRange(
        from: Int,
        until: Int,
        snapshot: ScrollActionSnapshot,
        into: MutableList<BookmarkEntity>,
        alreadyPresent: Set<String>? = null
    ) {
        for (i in from until until) {
            val bookmark = snapshot.window.bookmarkAt(i) ?: continue
            if (alreadyPresent != null && bookmark.remoteId !in alreadyPresent) continue
            if (!processedIds.add(bookmark.remoteId)) continue
            // The user explicitly acted on this bookmark (e.g. moved it to a list that will
            // remove it via async reconciliation) — absorbed above so we never fire on it.
            if (bookmark.remoteId in snapshot.actedOnIds) continue
            into.add(bookmark)
        }
    }
}
