package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.SavedFilterRepository
import com.karakept.app.data.repository.ServerRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.BookmarkActionEvent

class MainScreenModel(
    private val serverRepository: ServerRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val remoteDataSource: RemoteDataSource,
    private val savedFilterRepository: SavedFilterRepository,
    private val bookmarkActionsRepository: com.karakept.app.data.repository.BookmarkActionsRepository,
    private val settingsRepository: com.karakept.app.data.repository.SettingsRepository,
    private val listRepository: com.karakept.app.data.repository.ListRepository,
    private val bookmarkActionController: BookmarkActionController
) : ScreenModel {

    // For simplicity, we just pick the first server for now, or allow switching.
    // Let's expose the list of servers and the selected server.
    
    val servers = serverRepository.servers
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedServer = MutableStateFlow<Server?>(null)
    val selectedServer: StateFlow<Server?> = _selectedServer

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _currentFilter = MutableStateFlow(FilterConfig())
    val currentFilter: StateFlow<FilterConfig> = _currentFilter

    val savedFilters = savedFilterRepository.visibleFilters
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lists: StateFlow<List<com.karakept.app.data.remote.model.ListDto>> = listRepository.lists

    // Track which lists are expanded (by list ID)
    private val _expandedLists = MutableStateFlow<Set<String>>(emptySet())
    val expandedLists: StateFlow<Set<String>> = _expandedLists

    // Track the current active list filter (if any)
    private val _currentListContext = MutableStateFlow<String?>(null)
    val currentListContext: StateFlow<String?> = _currentListContext

    // Pagination state
    private val pageSize = 20
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMoreItems = MutableStateFlow(true)
    val hasMoreItems: StateFlow<Boolean> = _hasMoreItems

    private val _currentPage = MutableStateFlow(0)
    private val _accumulatedBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())

    // Reload trigger for sync completion - increment to trigger reload without clearing UI
    private val _reloadTrigger = MutableStateFlow(0)

    // Event flow for scroll-to-top trigger
    private val _scrollToTopTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopTrigger: SharedFlow<Unit> = _scrollToTopTrigger

    // Sync progress from repository
    val syncProgress: StateFlow<com.karakept.app.data.model.SyncProgress> =
        bookmarkRepository.syncProgress.stateIn(
            screenModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.karakept.app.data.model.SyncProgress.Idle
        )

    // All bookmarks without filtering - for tag extraction
    val allBookmarks = selectedServer
        .flatMapLatest { server ->
            if (server != null) {
                bookmarkRepository.getBookmarks(server)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Track list counts (map of list ID to bookmark count)
    val listCounts: StateFlow<Map<String, Int>> = combine(
        selectedServer,
        lists,
        allBookmarks
    ) { server, listItems, bookmarks ->
        if (server == null) return@combine emptyMap()

        listItems.associate { list ->
            val count = bookmarks.count { bookmark ->
                val bookmarkLists = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                bookmarkLists.contains(list.id)
            }
            list.id to count
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val swipeLeftAction: StateFlow<com.karakept.app.data.model.SwipeAction> = settingsRepository.swipeLeftAction.stateIn(
        screenModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.karakept.app.data.model.SwipeAction.MARK_READ
    )

    val swipeRightAction: StateFlow<com.karakept.app.data.model.SwipeAction> = settingsRepository.swipeRightAction.stateIn(
        screenModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.karakept.app.data.model.SwipeAction.ARCHIVE
    )

    val dimReadBookmarks: StateFlow<Boolean> = settingsRepository.dimReadBookmarks.stateIn(
        screenModelScope,
        SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    init {
        // Initialize selected server
        screenModelScope.launch {
            servers.collect { serverList ->
                if (_selectedServer.value == null && serverList.isNotEmpty()) {
                    _selectedServer.value = serverList.first()
                    loadLists()
                } else if (serverList.isEmpty()) {
                    _selectedServer.value = null
                }
            }
        }

        // Load default filter on startup
        screenModelScope.launch {
            savedFilterRepository.getDefaultFilter()?.let { saved ->
                try {
                    val config = kotlinx.serialization.json.Json.decodeFromString<FilterConfig>(saved.configJson)
                    _currentFilter.value = config
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Auto-sync on startup if offline mode is disabled
        screenModelScope.launch {
            selectedServer.collect { server ->
                if (server != null) {
                    val isOffline: Boolean = settingsRepository.offlineMode.first()
                    if (!isOffline && !_isSyncing.value) {
                        syncBookmarks()
                    }
                    cancel()
                }
            }
        }

        // Observe filter and server changes to trigger initial load
        screenModelScope.launch {
            combine(selectedServer, _currentFilter) { server, filter ->
                Pair(server, filter)
            }.collect { (server, filter) ->
                if (server != null) {
                    // Reset pagination
                    _currentPage.value = 0
                    _accumulatedBookmarks.value = emptyList()
                    _hasMoreItems.value = true

                    // Load first page
                    val newItems = loadBookmarksPage(server, filter, 0)
                    _accumulatedBookmarks.value = newItems
                }
            }
        }

        // Observe reload trigger separately to avoid clearing UI
        screenModelScope.launch {
            var first = true
            _reloadTrigger.collect {
                if (first) {
                    first = false // Skip initial value
                    return@collect
                }

                val server = _selectedServer.value
                val filter = _currentFilter.value
                if (server != null) {
                    // Reload all pages that were previously loaded to preserve scroll position
                    val currentPage = _currentPage.value
                    val allItems = mutableListOf<BookmarkEntity>()

                    for (page in 0..currentPage) {
                        val pageItems = loadBookmarksPage(server, filter, page)
                        allItems.addAll(pageItems)
                        if (pageItems.size < pageSize) {
                            // Reached the end
                            _hasMoreItems.value = false
                            break
                        }
                    }

                    // Swap in the new data atomically (no empty state in between)
                    _accumulatedBookmarks.value = allItems
                }
            }
        }

        // Listen to undo events to restore bookmarks to the accumulated list
        screenModelScope.launch {
            bookmarkActionController.undoCompletedEvents.collect { event ->
                val current = _accumulatedBookmarks.value.toMutableList()
                val existingIndex = current.indexOfFirst { it.remoteId == event.restoredBookmark.remoteId }

                if (existingIndex >= 0) {
                    // Bookmark is still in the list (e.g., mark-as-read undo)
                    // Update it in place with the restored state
                    current[existingIndex] = event.restoredBookmark
                    _accumulatedBookmarks.value = current
                } else {
                    // Bookmark was removed from the list (e.g., archive/favorite/delete undo)
                    // Re-add to original position if known, otherwise prepend
                    if (event.originalPosition >= 0 && event.originalPosition <= current.size) {
                        current.add(event.originalPosition, event.restoredBookmark)
                    } else {
                        current.add(0, event.restoredBookmark)
                    }
                    _accumulatedBookmarks.value = current
                }
            }
        }
    }

    val bookmarks: StateFlow<List<BookmarkEntity>> = _accumulatedBookmarks.stateIn(
        screenModelScope,
        SharingStarted.Lazily,
        emptyList()
    )

    private fun applyFilterToBookmarks(bookmarks: List<BookmarkEntity>, filter: FilterConfig): List<BookmarkEntity> {
        var result = bookmarks

        // 1. Status Filter
        // Note: Database-level filtering already handles most status filters
        // This is only used for the non-paginated allBookmarks flow (tag extraction)
        result = when (filter.status) {
            FilterStatus.FAVORITES -> result.filter { it.isStarred }
            FilterStatus.ARCHIVED -> result.filter { it.isArchived }
            FilterStatus.ALL -> result.filter { !it.isArchived } // ALL = non-archived
            FilterStatus.ALL_INCLUDING_ARCHIVED -> result // No filtering, show all
        }

        // 2. Tags Filter (OR logic: bookmark must have AT LEAST ONE of the selected tags)
        if (filter.tags.isNotEmpty()) {
            result = result.filter { bookmark ->
                val bookmarkTags = bookmark.tags.split(",").filter { it.isNotEmpty() }
                filter.tags.any { tag -> bookmarkTags.contains(tag) }
            }
        }

        // 3. Lists Filter (OR logic: bookmark must be in AT LEAST ONE of the selected lists)
        // Usually list filtering is "Show me items in List A OR List B".
        if (filter.lists.isNotEmpty()) {
            println("applyFilterToBookmarks: Filtering by lists: ${filter.lists}")
            println("applyFilterToBookmarks: Total bookmarks before list filter: ${result.size}")
            result = result.filter { bookmark ->
                val bookmarkLists = bookmark.listIds.split(",").filter { it.isNotEmpty() }
                val matches = filter.lists.any { listId -> bookmarkLists.contains(listId) }
                if (!matches && bookmark.listIds.isNotEmpty()) {
                    println("applyFilterToBookmarks: Bookmark ${bookmark.title} has listIds '${bookmark.listIds}' but doesn't match filter ${filter.lists}")
                }
                matches
            }
            println("applyFilterToBookmarks: Total bookmarks after list filter: ${result.size}")
        }

        // 4. Sort
        result = when (filter.sort) {
            SortOption.NEWEST -> result.sortedByDescending { it.createdAt }
            SortOption.OLDEST -> result.sortedBy { it.createdAt }
            SortOption.TITLE_AZ -> result.sortedBy { it.title.lowercase() }
            SortOption.TITLE_ZA -> result.sortedByDescending { it.title.lowercase() }
            SortOption.READING_TIME_SHORT -> result.sortedBy { it.readingTimeMinutes }
            SortOption.READING_TIME_LONG -> result.sortedByDescending { it.readingTimeMinutes }
        }

        return result
    }

    // Pagination helper functions
    private suspend fun loadBookmarksPage(
        server: Server,
        filter: FilterConfig,
        page: Int
    ): List<BookmarkEntity> {
        val offset = page * pageSize

        // If filtering by a single list, use DB-level list filtering
        val singleListId = if (filter.lists.size == 1) filter.lists.first() else null

        // Fetch paginated bookmarks from repository (filtered by status at DB level)
        val pagedBookmarks = bookmarkRepository.getBookmarksPaged(
            server = server,
            status = filter.status,
            offset = offset,
            limit = pageSize,
            listId = singleListId
        )

        // Apply client-side filters (tags, lists)
        // Note: if we already filtered by single list at DB level, skip client-side list filter
        val filtered = applyClientSideFilters(pagedBookmarks, filter, skipListFilter = singleListId != null)

        // Apply sorting
        val sorted = applySorting(filtered, filter.sort)

        return sorted
    }

    private fun applyClientSideFilters(
        bookmarks: List<BookmarkEntity>,
        filter: FilterConfig,
        skipListFilter: Boolean = false
    ): List<BookmarkEntity> {
        var result = bookmarks

        // Tag filter (OR logic)
        if (filter.tags.isNotEmpty()) {
            result = result.filter { bookmark ->
                val bookmarkTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                bookmarkTags.any { tag -> filter.tags.contains(tag) }
            }
        }

        // List filter (OR logic) - skip if already filtered at DB level
        if (filter.lists.isNotEmpty() && !skipListFilter) {
            println("applyClientSideFilters: Filtering by lists: ${filter.lists}")
            println("applyClientSideFilters: Bookmarks before list filter: ${result.size}")
            result = result.filter { bookmark ->
                val bookmarkLists = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val matches = bookmarkLists.any { listId -> filter.lists.contains(listId) }
                println("applyClientSideFilters: Bookmark ${bookmark.title} listIds='${bookmark.listIds}' -> parsed=$bookmarkLists -> matches=$matches")
                matches
            }
            println("applyClientSideFilters: Bookmarks after list filter: ${result.size}")
        } else if (skipListFilter) {
            println("applyClientSideFilters: Skipping list filter (already filtered at DB level)")
        }

        return result
    }

    private fun applySorting(
        bookmarks: List<BookmarkEntity>,
        sort: SortOption
    ): List<BookmarkEntity> {
        return when (sort) {
            SortOption.NEWEST -> bookmarks.sortedByDescending { it.createdAt }
            SortOption.OLDEST -> bookmarks.sortedBy { it.createdAt }
            SortOption.TITLE_AZ -> bookmarks.sortedBy { it.title.lowercase() }
            SortOption.TITLE_ZA -> bookmarks.sortedByDescending { it.title.lowercase() }
            SortOption.READING_TIME_SHORT -> bookmarks.sortedBy { it.readingTimeMinutes }
            SortOption.READING_TIME_LONG -> bookmarks.sortedByDescending { it.readingTimeMinutes }
        }
    }

    fun loadNextPage() {
        if (_isLoadingMore.value || !_hasMoreItems.value) return

        screenModelScope.launch {
            try {
                _isLoadingMore.value = true

                val server = _selectedServer.value ?: return@launch
                val filter = _currentFilter.value
                val nextPage = _currentPage.value + 1

                val newItems = loadBookmarksPage(server, filter, nextPage)

                if (newItems.isEmpty()) {
                    _hasMoreItems.value = false
                } else {
                    // Append to accumulated list
                    _accumulatedBookmarks.value = _accumulatedBookmarks.value + newItems
                    _currentPage.value = nextPage
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun selectServer(serverId: String) {
        screenModelScope.launch {
            val server = servers.value.find { it.id == serverId }
            _selectedServer.value = server
        }
    }

    fun syncBookmarks() {
        screenModelScope.launch {
            // Guard: Don't sync if offline mode is enabled
            val isOffline: Boolean = settingsRepository.offlineMode.first()
            if (isOffline) {
                return@launch
            }

            selectedServer.value?.let { server ->
                try {
                    _isSyncing.value = true
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        // Context-aware bookmark sync
                        val activeListId = _currentListContext.value
                        val currentStatus = _currentFilter.value.status

                        when {
                            activeListId != null -> {
                                // Sync only bookmarks from the current list
                                bookmarkRepository.syncBookmarksForList(server, activeListId)
                            }
                            currentStatus == FilterStatus.FAVORITES -> {
                                // Sync only favorites
                                bookmarkRepository.syncFavorites(server)
                            }
                            currentStatus == FilterStatus.ARCHIVED -> {
                                // Sync only archived
                                bookmarkRepository.syncArchived(server)
                            }
                            else -> {
                                // Sync all bookmarks (ALL filter)
                                bookmarkRepository.syncBookmarks(server)
                            }
                        }

                        // Always refresh lists metadata (lightweight)
                        listRepository.refreshLists(server)
                    }

                    // Trigger reload without clearing the UI (prevents flashing)
                    // This increments the reload trigger which loads new data in background
                    // and swaps it in atomically
                    _reloadTrigger.value += 1
                } catch (e: Exception) {
                    // Handle error
                } finally {
                    _isSyncing.value = false
                }
            }
        }
    }

    fun scrollToTop() {
        screenModelScope.launch {
            _scrollToTopTrigger.emit(Unit)
        }
    }

    private fun loadLists() {
        screenModelScope.launch {
            selectedServer.value?.let { server ->
                listRepository.refreshLists(server)
            }
        }
    }

    fun applyFilter(filter: FilterConfig) {
        _currentFilter.value = filter
        // Track if we're viewing a single list
        _currentListContext.value = if (filter.lists.size == 1) filter.lists.first() else null
        // Auto-expand parent chain if filtering by a single list
        if (filter.lists.size == 1) {
            expandParentChain(filter.lists.first(), lists.value)
        }
    }

    fun clearFilter() {
        _currentFilter.value = FilterConfig()
        _currentListContext.value = null
    }

    fun toggleListExpanded(listId: String) {
        _expandedLists.value = if (_expandedLists.value.contains(listId)) {
            _expandedLists.value - listId
        } else {
            _expandedLists.value + listId
        }
    }

    // Auto-expand parent chain when a list is selected
    private fun expandParentChain(listId: String, allLists: List<com.karakept.app.data.remote.model.ListDto>) {
        val toExpand = mutableSetOf<String>()
        var currentId: String? = listId

        while (currentId != null) {
            val list = allLists.find { it.id == currentId }
            if (list?.parentId != null) {
                toExpand.add(list.parentId)
                currentId = list.parentId
            } else {
                break
            }
        }

        _expandedLists.value = _expandedLists.value + toExpand
    }
    
    fun saveFilter(name: String, icon: String = "📋", color: Long? = null, isDefault: Boolean = false) {
        screenModelScope.launch {
            val configJson = kotlinx.serialization.json.Json.encodeToString(FilterConfig.serializer(), _currentFilter.value)
            savedFilterRepository.saveFilter(
                name = name,
                icon = icon,
                color = color,
                configJson = configJson,
                isDefault = isDefault,
                isVisibleInDrawer = true
            )
        }
    }
    
    fun deleteSavedFilter(filter: com.karakept.app.data.local.entity.SavedFilterEntity) {
        screenModelScope.launch {
            savedFilterRepository.deleteFilter(filter)
        }
    }
    
    fun updateSavedFilterName(filter: com.karakept.app.data.local.entity.SavedFilterEntity, newName: String) {
        screenModelScope.launch {
            val updated = filter.copy(name = newName)
            savedFilterRepository.updateFilter(updated)
        }
    }
    
    fun applySavedFilter(filter: com.karakept.app.data.local.entity.SavedFilterEntity) {
        screenModelScope.launch {
            try {
                val config = kotlinx.serialization.json.Json.decodeFromString(FilterConfig.serializer(), filter.configJson)
                _currentFilter.value = config
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Bookmark Actions

    fun toggleBookmarkArchive(bookmark: BookmarkEntity) {
        screenModelScope.launch {
            // Find the current position for undo restoration
            val position = _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }

            val event = if (bookmark.isArchived) {
                BookmarkActionEvent.Unarchive(bookmark)
            } else {
                BookmarkActionEvent.Archive(bookmark)
            }
            bookmarkActionController.executeAction(event, originalPosition = position)

            // Remove from current view immediately for better UX
            // The item will be filtered out on next load anyway
            _accumulatedBookmarks.value = _accumulatedBookmarks.value.filter {
                it.remoteId != bookmark.remoteId
            }
        }
    }

    fun toggleBookmarkFavorite(bookmark: BookmarkEntity) {
        screenModelScope.launch {
            // Find the current position for undo restoration
            val position = _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }

            bookmarkActionController.executeAction(
                BookmarkActionEvent.ToggleFavorite(bookmark),
                originalPosition = position
            )

            // Remove from current view if unfavoriting in Favorites view
            // (When adding to favorites in non-Favorites view, it stays in the list)
            if (bookmark.isStarred && _currentFilter.value.status == FilterStatus.FAVORITES) {
                _accumulatedBookmarks.value = _accumulatedBookmarks.value.filter {
                    it.remoteId != bookmark.remoteId
                }
            }
        }
    }

    fun toggleBookmarkRead(bookmark: BookmarkEntity) {
        screenModelScope.launch {
            val event = if (bookmark.isRead) {
                BookmarkActionEvent.MarkUnread(bookmark)
            } else {
                BookmarkActionEvent.MarkRead(bookmark)
            }
            bookmarkActionController.executeAction(event)

            // Update the bookmark in the accumulated list immediately for UI feedback
            _accumulatedBookmarks.value = _accumulatedBookmarks.value.map {
                if (it.remoteId == bookmark.remoteId) {
                    // Update the read state and tags immediately
                    val newIsRead = !bookmark.isRead
                    val currentTags = it.tags.split(",").map { t -> t.trim() }.filter { t -> t.isNotBlank() }.toMutableList()
                    if (newIsRead) {
                        if (!currentTags.contains("karakept:read")) {
                            currentTags.add("karakept:read")
                        }
                    } else {
                        currentTags.remove("karakept:read")
                    }
                    it.copy(isRead = newIsRead, tags = currentTags.joinToString(","))
                } else {
                    it
                }
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        screenModelScope.launch {
            // Find the current position for undo restoration
            val position = _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }

            bookmarkActionController.executeAction(
                BookmarkActionEvent.Delete(bookmark),
                originalPosition = position
            )

            // Remove from current view immediately
            _accumulatedBookmarks.value = _accumulatedBookmarks.value.filter {
                it.remoteId != bookmark.remoteId
            }
        }
    }
    
    fun updateBookmarkTags(bookmark: BookmarkEntity, newTags: List<String>) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.updateTags(
                bookmark.remoteId,
                bookmark.serverId,
                newTags,
                isOnline
            )
        }
    }
    
    fun moveBookmarkToList(bookmark: BookmarkEntity, listId: String) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.moveToList(
                bookmark.remoteId,
                bookmark.serverId,
                listId,
                isOnline
            )
        }
    }
}
