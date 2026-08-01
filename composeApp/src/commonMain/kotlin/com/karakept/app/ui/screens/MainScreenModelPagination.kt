/** Pagination extension functions for MainScreenModel. */
package com.karakept.app.ui.screens

import androidx.lifecycle.viewModelScope
import com.karakept.app.utils.AppLogger
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.Server
import com.karakept.app.domain.BookmarkFilterUtils
import com.karakept.app.domain.ListHierarchyUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal suspend fun MainScreenModel.expandListsWithChildren(listIds: List<String>): List<String> {
    val result = listIds.toMutableList()
    val allLists = lists.value
    for (listId in listIds) {
        val settings = settingsRepository.getListSettings(listId).first()
        if (settings.includeChildListBookmarks) {
            val childIds = ListHierarchyUtils.getAllDescendantIds(listId, allLists)
            childIds.forEach { if (!result.contains(it)) result.add(it) }
        }
    }
    return result
}

/**
 * Loads a single DB page and applies client-side filters.
 *
 * @return Pair(filteredItems, rawDbRowCount). The raw count is used to
 *   detect true DB exhaustion: rawCount < pageSize means no more pages.
 */
internal suspend fun MainScreenModel.loadBookmarksPage(
    server: Server,
    filter: FilterConfig,
    page: Int
): Pair<List<BookmarkEntity>, Int> {
    val offset = page * pageSize

    // Expand filter lists to include children for lists with
    // includeChildListBookmarks enabled.
    val expandedFilter = if (filter.lists.isNotEmpty()) {
        val expandedLists = expandListsWithChildren(filter.lists)
        if (expandedLists != filter.lists) filter.copy(lists = expandedLists) else filter
    } else {
        filter
    }

    val singleListId = if (expandedFilter.lists.size == 1) expandedFilter.lists.first() else null

    val pagedBookmarks = bookmarkRepository.getBookmarksPaged(
        server = server,
        status = expandedFilter.status,
        offset = offset,
        limit = pageSize,
        sort = expandedFilter.sort,
        listId = singleListId
    )

    val rawCount = pagedBookmarks.size
    val filtered = BookmarkFilterUtils.applyClientSideFilters(
        pagedBookmarks, expandedFilter, skipListFilter = singleListId != null
    )

    return Pair(filtered, rawCount)
}

/**
 * Advances through consecutive DB pages starting at [startPage] until
 * at least one item survives the client-side filter, or the DB is truly
 * exhausted. Delegates to [advancePagesUntilItemsFound] so the algorithm
 * is unit-testable independently.
 */
internal suspend fun MainScreenModel.findPageWithItems(
    server: Server,
    filter: FilterConfig,
    startPage: Int
): Triple<List<BookmarkEntity>, Int, Boolean> =
    advancePagesUntilItemsFound(startPage, pageSize) { page ->
        loadBookmarksPage(server, filter, page)
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

            val (newItems, lastPage, dbExhausted) = findPageWithItems(server, view.filter, nextPage)

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
            if (dbExhausted) {
                _hasMoreItems.value = false
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
    _hasMoreItems.value = true
    _actedOnBookmarkIds.value = emptySet()
    // Fresh view — any pending "N new" indicator no longer applies.
    _newBookmarksAbove.value = 0
    // Block loadNextPage from launching while we are iterating through pages.
    _isLoadingMore.value = true
    _isResettingPagination.value = true

    try {
        // Load items first, then swap atomically to avoid a blank flash.
        val (newItems, lastPage, dbExhausted) = findPageWithItems(server, filter, 0)

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
        _currentPage.value = lastPage
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

    val hadItems = _accumulatedBookmarks.value.isNotEmpty()

    _isLoadingMore.value = true
    try {
        val all = mutableListOf<BookmarkEntity>()
        var page = 0
        var reachedEnd = false
        while (page <= lastLoadedPage) {
            val (items, rawCount) = loadBookmarksPage(server, filter, page)
            all += items
            if (rawCount < pageSize) {
                reachedEnd = true
                break
            }
            page++
        }

        // A reset or a newer refresh started, or the user switched away, while we fetched.
        if (paginationGeneration != myGeneration ||
            refreshGeneration != myRefreshGeneration ||
            currentView() != view
        ) return

        // The window's effective query can widen under a refresh: a list with
        // includeChildListBookmarks only expands into its children once the drawer's lists have
        // loaded (the startup sync refreshes them right before this runs), and a multi-list
        // filter is applied client-side on top of paged DB rows — so every page of the window
        // can come back empty while the view's items sit further down. Hand over to the full
        // reload instead of publishing the blank window: it pages until it finds items and sizes
        // the window to what it found. Paging on here instead would, for a view that really is
        // empty, walk to the last page of the table and leave the window spanning all of it, so
        // every later refresh re-sweeps the whole DB.
        if (all.isEmpty() && !reachedEnd && hadItems) {
            resetPaginationAndLoad(server, filter, scrollToTop = false)
            return
        }

        // Count bookmarks the sync introduced (they land at the top for the default NEWEST sort),
        // so the UI can surface a "N new" pill when the user is scrolled away from the top.
        val previousIds = _accumulatedBookmarks.value.map { it.remoteId }.toSet()
        val added = all.count { it.remoteId !in previousIds }
        if (added > 0) _newBookmarksAbove.value += added

        _loadedView.value = view
        updateAccumulatedBookmarks { all }
        _currentPage.value = if (reachedEnd) page else lastLoadedPage
        _hasMoreItems.value = !reachedEnd
    } finally {
        if (paginationGeneration == myGeneration && refreshGeneration == myRefreshGeneration) {
            _isLoadingMore.value = false
        }
    }
}
