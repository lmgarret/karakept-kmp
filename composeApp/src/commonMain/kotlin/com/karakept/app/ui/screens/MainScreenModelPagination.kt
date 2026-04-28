/** Pagination extension functions for MainScreenModel. */
package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.screenModelScope
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
    // Set before launching so rapid consecutive calls all see the flag immediately,
    // even before the coroutine body runs.
    _isLoadingMore.value = true

    screenModelScope.launch {
        try {
            val generation = paginationGeneration
            val server = _selectedServer.value ?: return@launch
            val filter = _currentFilter.value
            val nextPage = _currentPage.value + 1

            val (newItems, lastPage, dbExhausted) = findPageWithItems(server, filter, nextPage)

            // Discard results if a resetPaginationAndLoad started while we were fetching.
            if (paginationGeneration != generation) return@launch

            if (newItems.isNotEmpty()) {
                updateAccumulatedBookmarks { current ->
                    BookmarkFilterUtils.applySorting(current + newItems, filter.sort)
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
            _isLoadingMore.value = false
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

    _currentPage.value = 0
    _hasMoreItems.value = true
    // Block loadNextPage from launching while we are iterating through pages.
    _isLoadingMore.value = true

    try {
        // Load items first, then swap atomically to avoid a blank flash.
        val (newItems, lastPage, dbExhausted) = findPageWithItems(server, filter, 0)

        // Another reset started while we were fetching — our results are stale.
        if (paginationGeneration != myGeneration) return

        updateAccumulatedBookmarks { newItems }
        _bookmarkListVersion.value++
        _currentPage.value = lastPage
        if (dbExhausted) {
            _hasMoreItems.value = false
        }
    } finally {
        if (paginationGeneration == myGeneration) {
            _isLoadingMore.value = false
        }
    }
    // Scroll to top after data is ready, so plain back-navigation from the viewer
    // (which doesn't call resetPaginationAndLoad) never triggers an unwanted scroll.
    if (scrollToTop) scrollToTop()
}
