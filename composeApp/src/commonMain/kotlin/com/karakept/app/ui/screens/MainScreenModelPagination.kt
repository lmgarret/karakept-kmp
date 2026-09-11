/** Pagination extension functions for MainScreenModel. */
package com.karakept.app.ui.screens

import androidx.lifecycle.viewModelScope
import com.karakept.app.utils.AppLogger
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkCursor
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.Server
import com.karakept.app.domain.BookmarkFilterUtils
import kotlinx.coroutines.launch

/**
 * Reads the [limit] rows following [after] and applies client-side filters.
 */
private suspend fun MainScreenModel.loadBookmarkRows(
    server: Server,
    filter: FilterConfig,
    after: BookmarkCursor?,
    limit: Int
): PageRead<BookmarkEntity, BookmarkCursor> {
    // [filter] is the effective filter (see MainScreenModel.effectiveFilter), so a read's
    // contents depend only on it and where it resumes from — never on state that can change
    // between the load of a window and its refresh.
    val singleListId = filter.lists.singleOrNull()

    val rows = bookmarkRepository.getBookmarksPaged(
        server = server,
        status = filter.status,
        limit = limit,
        after = after,
        sort = filter.sort,
        listId = singleListId
    )

    val filtered = BookmarkFilterUtils.applyClientSideFilters(
        rows, filter, skipListFilter = singleListId != null
    )

    // The cursor tracks the *raw* last row, not the last visible one. A page whose rows the
    // client-side filter all discarded still has to advance, or the walk asks for it again.
    return PageRead(filtered, rows.size, rows.lastOrNull()?.let(BookmarkCursor::of))
}

/** Index of the page the last of [rowCount] rows falls on. */
private fun MainScreenModel.lastPageHolding(rowCount: Int): Int =
    if (rowCount <= 0) 0 else (rowCount - 1) / pageSize

/**
 * The loaded window read back as a single query.
 *
 * The window spans pages `0..lastLoadedPage`, and reading it one page at a time is what let it
 * tear: a sync commits rows while the reads run, and every row inserted above a read position
 * shifts the pages below it, so a page-by-page walk re-reads rows it already held and never
 * reaches the ones those insertions displaced. One query is evaluated against one consistent
 * snapshot, so it cannot tear — rows committed after it are picked up by the next read instead
 * of falling into a gap (#333).
 */
private data class WindowRead(
    val rows: List<BookmarkEntity>,
    val rawCount: Int,
    val windowSize: Int,
    val endCursor: BookmarkCursor?
) {
    /** The query could not fill the window, so the table ends inside it. */
    val reachedEnd: Boolean get() = rawCount < windowSize
}

/** Reads pages `0..[lastLoadedPage]` in one query. */
private suspend fun MainScreenModel.readWholeWindow(
    server: Server,
    filter: FilterConfig,
    lastLoadedPage: Int
): WindowRead {
    val windowSize = (lastLoadedPage + 1) * pageSize
    val read = loadBookmarkRows(server, filter, after = null, limit = windowSize)
    return WindowRead(read.items, read.rawCount, windowSize, read.nextCursor)
}

/**
 * Publishes a [WindowRead] as the window on screen.
 *
 * A table that has shrunk since the window was loaded shrinks the window with it, so later
 * reads stop sweeping page ranges the query can no longer fill.
 *
 * @param hasMoreBeyondWindow whether rows may still lie past the window. A read can only answer
 *   that from whether it managed to fill the window, which is a statement about the window and
 *   not about what lies beyond it — so a caller that knows better says so. The forward walk
 *   does: it has just paged to the end of the table and found nothing, and a window that
 *   happens to be full says nothing to the contrary.
 */
private suspend fun MainScreenModel.publishWindow(
    view: LoadedView,
    read: WindowRead,
    lastLoadedPage: Int,
    hasMoreBeyondWindow: Boolean = !read.reachedEnd
) {
    _loadedView.value = view
    updateAccumulatedBookmarks { read.rows }
    _currentPage.value =
        if (read.reachedEnd) lastPageHolding(read.rawCount) else lastLoadedPage
    // The window was just re-read whole, so the next page resumes after its own last row —
    // keeping the cursor from before would re-read rows the window already holds.
    paginationCursor = read.endCursor
    _hasMoreItems.value = hasMoreBeyondWindow
}

/**
 * Advances through consecutive DB pages starting after [startCursor] until at least one item
 * survives the client-side filter, or the DB is truly exhausted. Delegates to
 * [advancePagesUntilItemsFound] so the algorithm is unit-testable independently.
 */
internal suspend fun MainScreenModel.findPageWithItems(
    server: Server,
    filter: FilterConfig,
    startPage: Int,
    startCursor: BookmarkCursor?
): PageWalk<BookmarkEntity, BookmarkCursor> =
    advancePagesUntilItemsFound(startPage, startCursor, pageSize) { after ->
        loadBookmarkRows(server, filter, after, pageSize)
    }

/**
 * Loads the next page of bookmarks when the user scrolls near the bottom.
 */
fun MainScreenModel.loadNextPage() {
    if (_isLoadingMore.value || !_hasMoreItems.value || _searchQuery.value.isNotBlank()) return
    // Pages may only be appended to a window that still belongs to the view on screen, and
    // must be fetched with that window's own filter. Reading _currentFilter here instead would
    // fetch the newly-selected list with the previous list's page offset and append the result
    // to the previous list's items, rendering two lists interleaved in one LazyColumn.
    val view = _loadedView.value ?: return
    if (view != currentView()) return
    val generation = paginationGeneration
    // Set before launching so rapid consecutive calls all see the flag immediately,
    // even before the coroutine body runs.
    _isLoadingMore.value = true

    viewModelScope.launch {
        try {
            val server = _selectedServer.value ?: return@launch
            val nextPage = _currentPage.value + 1

            val walk = findPageWithItems(server, view.filter, nextPage, paginationCursor)
            val newItems = walk.items
            val lastPage = walk.lastPage
            val dbExhausted = walk.dbExhausted

            // Discard results if a reset started, or the user switched view, while we fetched.
            if (paginationGeneration != generation ||
                _loadedView.value != view ||
                currentView() != view
            ) return@launch

            if (newItems.isNotEmpty()) {
                updateAccumulatedBookmarks { current ->
                    // A sync inserting rows mid-pagination shifts DB offsets, so a page can
                    // re-return items already accumulated — drop them to keep keys unique (#274).
                    val existingIds = current.map { it.remoteId }.toSet()
                    val trulyNew = newItems.filter { it.remoteId !in existingIds }
                    BookmarkFilterUtils.applySorting(current + trulyNew, view.filter.sort)
                }
                _currentPage.value = lastPage
            }
            // Advance even when the page yielded nothing visible: the rows were read, and the
            // next walk must not start over on them.
            paginationCursor = walk.nextCursor
            if (dbExhausted) {
                _hasMoreItems.value = false
            }

            // The walk has reached the end of the table, but the window it leaves behind can
            // still be short of it. Every row a sync commits above the read position shifts the
            // pages below it, so the walk re-reads rows it already held — dropped just above as
            // duplicates — and never reaches the rows the same shift displaced past it. Moving
            // only forward, it never comes back for them. The window then claims to be complete,
            // `_hasMoreItems` is false, and scrolling can no longer recover anything: the state
            // #333 reported, with the missing rows being the freshly synced, still-unread ones.
            //
            // Re-reading the window as one query closes that gap, and this is the moment to do
            // it — the walk has just established there is nothing further to page to, so a short
            // window here has no other way back. Read inline rather than through
            // [refreshLoadedPagesInPlace]: that manages `_isLoadingMore` itself and would race
            // this function's own `finally`.
            if (dbExhausted) {
                val lastLoadedPage = _currentPage.value
                val read = readWholeWindow(server, view.filter, lastLoadedPage)

                if (paginationGeneration == generation &&
                    _loadedView.value == view &&
                    currentView() == view &&
                    // A re-read holding fewer rows than the window already does cannot be a
                    // heal — the walk has just appended what it found — so publishing it would
                    // drop rows that are legitimately loaded. Leave the window alone.
                    read.rows.size >= _accumulatedBookmarks.value.size
                ) {
                    // The walk has already established there is nothing past the window, and
                    // that answer stands: letting the re-read decide instead re-enables paging
                    // whenever the window happens to come back full, and the next scroll walks
                    // the table again to reach the same conclusion. On a view whose filter
                    // admits few rows that is a hot loop — the trace behind this fix walked
                    // thirty-eight pages roughly four times a second, indefinitely.
                    publishWindow(view, read, lastLoadedPage, hasMoreBeyondWindow = false)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("MainScreenModel", "Failed to load bookmarks: ${e.message}", e)
            snackbarManager.showErrorWithRetry("Couldn't load bookmarks") {
                loadNextPage()
            }
        } finally {
            // A reset that overtook us owns the flag and clears it when its own page lands.
            if (paginationGeneration == generation) _isLoadingMore.value = false
        }
    }
}

/**
 * Clears accumulated bookmarks and loads the first page for [filter].
 * This is the single entry-point for "start displaying a filter".
 */
internal suspend fun MainScreenModel.resetPaginationAndLoad(server: Server, filter: FilterConfig, scrollToTop: Boolean = true) {
    // Increment generation so any in-flight loadNextPage knows its results are stale.
    paginationGeneration++
    val myGeneration = paginationGeneration
    val view = LoadedView(server.id, filter)

    _currentPage.value = 0
    // A fresh view resumes from the top of the table, not from wherever the previous one ended.
    paginationCursor = null
    _hasMoreItems.value = true
    _actedOnBookmarkIds.value = emptySet()
    // Fresh view — nothing in it has been seen yet, so the "N new" indicator starts empty and
    // re-anchors once the list renders at the top.
    _seenTopRemoteId.value = null
    // Block loadNextPage from launching while we are iterating through pages.
    _isLoadingMore.value = true
    _isResettingPagination.value = true

    try {
        // Load items first, then swap atomically to avoid a blank flash.
        val walk = findPageWithItems(server, filter, startPage = 0, startCursor = null)
        val newItems = walk.items
        val lastPage = walk.lastPage
        val dbExhausted = walk.dbExhausted

        // Another reset started, or the user switched away, while we were fetching.
        if (paginationGeneration != myGeneration || currentView() != view) return

        _loadedView.value = view
        // Announce the reload *before* publishing the items. The version bump makes the
        // scroll anchor drop its anchor and the scroll request re-pins the viewport to the
        // top; both are applied on the next measure, which is the one that renders the new
        // items. Publishing first instead leaves the incoming list drawn at the outgoing
        // list's scroll offset for a frame. Only resetPaginationAndLoad scrolls, so plain
        // back-navigation from the viewer never triggers an unwanted scroll.
        _bookmarkListVersion.value++
        if (scrollToTop) scrollToTop()
        updateAccumulatedBookmarks { newItems }
        // A freshly loaded view counts as seen, so the pill stays empty until a later sync puts
        // something above it. Anchoring here rather than waiting for the list to report itself
        // at the top keeps the count right when the view opens somewhere else — a restored
        // scroll position, or a reload that deliberately holds its place.
        _seenTopRemoteId.value = newItems.firstOrNull()?.remoteId
        // The window spans pages 0..currentPage and is re-read in full on every refresh, so it
        // must end at the page that *contributed* the items. Finding nothing means the search
        // walked the table without loading a window — recording the page it gave up on would
        // make every later refresh ask for the whole table too.
        _currentPage.value = if (newItems.isEmpty()) 0 else lastPage
        paginationCursor = walk.nextCursor
        if (dbExhausted) {
            _hasMoreItems.value = false
        }
    } finally {
        if (paginationGeneration == myGeneration) {
            _isLoadingMore.value = false
            _isResettingPagination.value = false
        }
    }
}

/**
 * Refreshes the currently-loaded pages in place after a sync, so newly synced bookmarks
 * appear without the disruptive full reset.
 *
 * Unlike [resetPaginationAndLoad] this keeps the loaded window (reloads pages 0..currentPage
 * instead of shrinking to page 0), does NOT bump [_bookmarkListVersion], and does NOT scroll
 * to the top. Leaving the version untouched lets [PreserveListScrollAnchor] re-pin the viewport
 * to the bookmark the user is looking at, so the list updates beneath them instead of blinking
 * and jumping to the top when the background sync finishes on open.
 *
 * The whole window is read in **one** query rather than one query per page. A sync commits its
 * rows while this runs, and every row it inserts above the read position shifts the OFFSET of
 * every page not yet read: the walk then re-read rows it already held (dropped again by
 * [updateAccumulatedBookmarks]'s de-duplication, see #274) and never read the rows those
 * insertions had pushed past it. The window came back short a whole batch of freshly synced
 * bookmarks, with `_currentPage` and `_hasMoreItems` claiming it was complete — so scrolling
 * to the end never fetched them and only re-selecting the filter brought them back (#333).
 * A single query is evaluated against one consistent snapshot, so it cannot tear.
 */
internal suspend fun MainScreenModel.refreshLoadedPagesInPlace(server: Server, filter: FilterConfig) {
    // A refresh only *observes* paginationGeneration — bumping it would let a background
    // refresh discard a reset the user just triggered, leaving the previous list on screen.
    // refreshGeneration resolves two refreshes racing each other.
    val view = LoadedView(server.id, filter)
    if (currentView() != view) return
    val myGeneration = paginationGeneration
    refreshGeneration++
    val myRefreshGeneration = refreshGeneration
    val lastLoadedPage = _currentPage.value

    _isLoadingMore.value = true
    try {
        val read = readWholeWindow(server, filter, lastLoadedPage)

        // A reset or a newer refresh started, or the user switched away, while we fetched.
        if (paginationGeneration != myGeneration ||
            refreshGeneration != myRefreshGeneration ||
            currentView() != view
        ) return

        // The "N new" pill counts rows sitting above the last one the user saw, derived from the
        // list itself (see MainScreenModel.newBookmarksAbove) — a refresh publishes the new
        // window and the count follows, with nothing to tally here.
        publishWindow(view, read, lastLoadedPage)
    } finally {
        if (paginationGeneration == myGeneration && refreshGeneration == myRefreshGeneration) {
            _isLoadingMore.value = false
        }
    }
}
