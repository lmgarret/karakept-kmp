package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.api.model.KarakeepList as KarakeepList
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

    // Signals that the default list setting has been loaded and applied to _currentFilter.
    // The pagination observer waits for this to be true before triggering the first load,
    // ensuring the user's default list is used instead of the generic ALL_BOOKMARKS.
    private val _defaultFilterInitialized = MutableStateFlow(false)

    // Tracks the bookmark that triggered a tag filter, so back navigation can return to it
    private val _tagFilterSourceBookmarkId = MutableStateFlow<Long?>(null)
    val tagFilterSourceBookmarkId: StateFlow<Long?> = _tagFilterSourceBookmarkId

    val lists: StateFlow<List<KarakeepList>> = listRepository.lists

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

    private val _createBookmarkResult = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
    val createBookmarkResult: SharedFlow<Result<Unit>> = _createBookmarkResult

    // Placeholder bookmarks shown while creation is in-flight
    private val _pendingBookmarks = MutableStateFlow<List<com.karakept.app.data.local.entity.BookmarkEntity>>(emptyList())
    val pendingBookmarkRemoteIds: StateFlow<Set<Long>> = _pendingBookmarks
        .map { list -> list.map { it.remoteId }.toSet() }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Search query - applied client-side across all cached bookmarks
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

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
    // Respects per-list "countOnlyUnread" setting and includes bookmarks from descendant lists
    val listCounts: StateFlow<Map<String, Int>> = combine(
        selectedServer,
        lists,
        allBookmarks,
        settingsRepository.allListSettings
    ) { server, listItems, bookmarks, allSettings ->
        if (server == null) return@combine emptyMap()

        listItems.associate { list ->
            val listId = list.id ?: ""
            val settings = allSettings[listId] ?: com.karakept.app.data.model.ListSettings()
            val descendantIds = getAllDescendantIds(listId, listItems)
            val relevantIds = setOf(listId) + descendantIds
            val count = bookmarks.count { bookmark ->
                val bookmarkLists = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                bookmarkLists.any { it in relevantIds } && (!settings.countOnlyUnread || !bookmark.isRead)
            }
            listId to count
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

    val customSwipeActionConfigs: StateFlow<List<com.karakept.app.data.model.CustomSwipeActionConfig>> =
        settingsRepository.customSwipeActionConfigs.stateIn(
            screenModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val swipeLeftConfigId: StateFlow<String?> = settingsRepository.swipeLeftConfigId.stateIn(
        screenModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val swipeRightConfigId: StateFlow<String?> = settingsRepository.swipeRightConfigId.stateIn(
        screenModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val dimReadBookmarks: StateFlow<Boolean> = settingsRepository.dimReadBookmarks.stateIn(
        screenModelScope,
        SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    // Per-list scroll action: the action configured for the currently filtered list (if any single list is active)
    val currentListScrollAction: StateFlow<com.karakept.app.data.model.SwipeAction> =
        _currentListContext
            .flatMapLatest { listId ->
                if (listId != null) {
                    settingsRepository.getListSettings(listId).map { it.scrollAction }
                } else {
                    flowOf(com.karakept.app.data.model.SwipeAction.NONE)
                }
            }
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), com.karakept.app.data.model.SwipeAction.NONE)

    // The custom action config for the scroll action (if a custom action is configured)
    val currentListScrollActionConfig: StateFlow<com.karakept.app.data.model.CustomSwipeActionConfig?> =
        combine(
            _currentListContext,
            settingsRepository.allListSettings,
            customSwipeActionConfigs
        ) { listId, allSettings, configs ->
            if (listId == null) return@combine null
            val settings = allSettings[listId] ?: return@combine null
            val configId = settings.scrollActionConfigId ?: return@combine null
            configs.find { it.id == configId }
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Load the user's default list setting and apply it as the initial filter.
        // This must complete before the pagination observer fires its first load so that
        // the app opens directly on the user's preferred list.
        screenModelScope.launch {
            val defaultType = settingsRepository.defaultListType.first()
            val defaultListId = settingsRepository.defaultListId.first()
            val defaultFilter = when (defaultType) {
                DefaultListType.ALL_BOOKMARKS -> FilterConfig()
                DefaultListType.FAVORITES -> FilterConfig(status = FilterStatus.FAVORITES)
                DefaultListType.ARCHIVED -> FilterConfig(status = FilterStatus.ARCHIVED)
                DefaultListType.SPECIFIC_LIST -> if (defaultListId != null) {
                    FilterConfig(lists = listOf(defaultListId))
                } else {
                    FilterConfig()
                }
            }
            _currentFilter.value = defaultFilter
            if (defaultFilter.lists.size == 1) {
                _currentListContext.value = defaultFilter.lists.first()
            }
            _defaultFilterInitialized.value = true
        }

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
            combine(selectedServer, _currentFilter, _defaultFilterInitialized) { server, filter, initialized ->
                Triple(server, filter, initialized)
            }.collect { (server, filter, initialized) ->
                if (server != null && initialized) {
                    // Reset pagination
                    _currentPage.value = 0
                    _accumulatedBookmarks.value = emptyList()
                    _hasMoreItems.value = true

                    // Use findPageWithItems so that we skip over DB pages that are entirely
                    // filtered out by the client-side list filter (e.g. multi-list expansion).
                    // Without this, the initial view could show 0 items when page 0 is filtered
                    // out but later pages contain matching items.
                    val (newItems, lastPage, dbExhausted) = findPageWithItems(server, filter, 0)
                    _accumulatedBookmarks.value = newItems
                    _currentPage.value = lastPage
                    if (dbExhausted) {
                        _hasMoreItems.value = false
                    }
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
                        val (pageItems, _) = loadBookmarksPage(server, filter, page)
                        allItems.addAll(pageItems)
                        // Do NOT break when pageItems.size < pageSize. When multi-list
                        // client-side filtering is active (includeChildListBookmarks = true),
                        // a DB page of 20 items may yield only a few matches. Breaking early
                        // here causes bookmarks to disappear after pull-to-refresh.
                        // The true end-of-data is detected by loadNextPage on the next scroll.
                    }

                    // Swap in the new data atomically (no empty state in between)
                    _accumulatedBookmarks.value = allItems
                    // Reset so the user can scroll to discover whether more data exists;
                    // loadNextPage will set this to false when the DB is truly exhausted.
                    _hasMoreItems.value = true
                }
            }
        }

        // React to bookmark changes triggered by actions in other screens (e.g. BookmarkViewerScreen).
        // This keeps the main list in sync without requiring a full sync.
        screenModelScope.launch {
            bookmarkActionsRepository.bookmarkChangedEvents.collect { remoteId ->
                val serverId = _selectedServer.value?.id ?: return@collect
                val updated = bookmarkRepository.getBookmarkByRemoteId(remoteId, serverId)
                _accumulatedBookmarks.value = if (updated != null) {
                    _accumulatedBookmarks.value.map { if (it.remoteId == remoteId) updated else it }
                } else {
                    _accumulatedBookmarks.value.filter { it.remoteId != remoteId }
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

    val bookmarks: StateFlow<List<BookmarkEntity>> = combine(
        _pendingBookmarks,
        _accumulatedBookmarks,
        allBookmarks,
        _currentFilter,
        _searchQuery
    ) { pending, accumulated, all, filter, query ->
        if (query.isBlank()) {
            pending + accumulated
        } else {
            applySearchFilter(all, filter, query)
        }
    }.stateIn(screenModelScope, SharingStarted.Lazily, emptyList())

    private fun applySearchFilter(
        all: List<BookmarkEntity>,
        filter: FilterConfig,
        query: String
    ): List<BookmarkEntity> {
        val q = query.lowercase()
        var result = when (filter.status) {
            FilterStatus.FAVORITES -> all.filter { it.isStarred }
            FilterStatus.ARCHIVED -> all.filter { it.isArchived }
            FilterStatus.ALL -> all.filter { !it.isArchived }
            FilterStatus.ALL_INCLUDING_ARCHIVED -> all
        }
        if (filter.tags.isNotEmpty()) {
            result = result.filter { bookmark ->
                val tags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                filter.tags.any { tag -> tags.contains(tag) }
            }
        }
        if (filter.lists.isNotEmpty()) {
            result = result.filter { bookmark ->
                val lists = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                filter.lists.any { listId -> lists.contains(listId) }
            }
        }
        return result.filter { bookmark ->
            bookmark.title.lowercase().contains(q) ||
                bookmark.url.lowercase().contains(q) ||
                bookmark.description?.lowercase()?.contains(q) == true
        }
    }

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

    /**
     * Loads a single page from the DB and applies client-side filters.
     *
     * @return Pair of (filtered items, raw DB item count before filtering).
     *         The raw count is used to detect true DB exhaustion: when rawCount < pageSize
     *         there are no more DB pages, even if the filtered list is empty.
     */
    private suspend fun loadBookmarksPage(
        server: Server,
        filter: FilterConfig,
        page: Int
    ): Pair<List<BookmarkEntity>, Int> {
        val offset = page * pageSize

        // Expand filter lists to include child lists for those with includeChildListBookmarks enabled
        val expandedFilter = if (filter.lists.isNotEmpty()) {
            val expandedLists = expandListsWithChildren(filter.lists)
            if (expandedLists != filter.lists) filter.copy(lists = expandedLists) else filter
        } else {
            filter
        }

        // If filtering by a single list, use DB-level list filtering
        val singleListId = if (expandedFilter.lists.size == 1) expandedFilter.lists.first() else null

        // Fetch paginated bookmarks from repository (filtered by status at DB level)
        val pagedBookmarks = bookmarkRepository.getBookmarksPaged(
            server = server,
            status = expandedFilter.status,
            offset = offset,
            limit = pageSize,
            listId = singleListId
        )

        val rawCount = pagedBookmarks.size

        // Apply client-side filters (tags, lists)
        // Note: if we already filtered by single list at DB level, skip client-side list filter
        val filtered = applyClientSideFilters(pagedBookmarks, expandedFilter, skipListFilter = singleListId != null)

        // Apply sorting
        val sorted = applySorting(filtered, expandedFilter.sort)

        return Pair(sorted, rawCount)
    }

    /**
     * Advances through consecutive DB pages starting at [startPage] until either:
     * - At least one item survives the client-side filter, OR
     * - The DB is truly exhausted (raw page size < [pageSize]).
     *
     * Delegates to the package-level [advancePagesUntilItemsFound] so the algorithm is
     * unit-testable without instantiating the ScreenModel.
     *
     * @return Triple(filteredItems, lastPageLoaded, dbExhausted)
     */
    internal suspend fun findPageWithItems(
        server: Server,
        filter: FilterConfig,
        startPage: Int
    ): Triple<List<BookmarkEntity>, Int, Boolean> {
        return advancePagesUntilItemsFound(startPage, pageSize) { page ->
            loadBookmarksPage(server, filter, page)
        }
    }

    private suspend fun expandListsWithChildren(listIds: List<String>): List<String> {
        val result = listIds.toMutableList()
        val allLists = lists.value

        for (listId in listIds) {
            val settings = settingsRepository.getListSettings(listId).first()
            if (settings.includeChildListBookmarks) {
                val childIds = getAllDescendantIds(listId, allLists)
                childIds.forEach { if (!result.contains(it)) result.add(it) }
            }
        }

        return result
    }

    private fun getAllDescendantIds(parentId: String, allLists: List<KarakeepList>): List<String> {
        val directChildren = allLists.filter { it.parentId == parentId }.mapNotNull { it.id }
        val allDescendants = directChildren.toMutableList()
        for (childId in directChildren) {
            allDescendants.addAll(getAllDescendantIds(childId, allLists))
        }
        return allDescendants
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

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun loadNextPage() {
        if (_isLoadingMore.value || !_hasMoreItems.value || _searchQuery.value.isNotBlank()) return

        screenModelScope.launch {
            try {
                _isLoadingMore.value = true

                val server = _selectedServer.value ?: return@launch
                val filter = _currentFilter.value
                val nextPage = _currentPage.value + 1

                // Keep advancing through DB pages until we find visible items or the DB is
                // truly exhausted. When multi-list client-side filtering is active, a full DB
                // page may be entirely filtered out; we must NOT stop there.
                val (newItems, lastPage, dbExhausted) = findPageWithItems(server, filter, nextPage)

                if (newItems.isNotEmpty()) {
                    _accumulatedBookmarks.value = _accumulatedBookmarks.value + newItems
                    _currentPage.value = lastPage
                }
                if (dbExhausted) {
                    _hasMoreItems.value = false
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

    /** Apply a tag filter that originated from a BookmarkViewerScreen, recording the source
     * bookmark ID so that pressing back on the list can return to that viewer. */
    fun applyTagFilter(tag: String, sourceBookmarkId: Long) {
        _tagFilterSourceBookmarkId.value = sourceBookmarkId
        applyFilter(FilterConfig(tags = listOf(tag)))
    }

    /** Consume the pending "return to viewer" bookmark ID (clears it). */
    fun consumeTagFilterSource(): Long? {
        val id = _tagFilterSourceBookmarkId.value
        _tagFilterSourceBookmarkId.value = null
        return id
    }

    fun clearFilter() {
        _currentFilter.value = FilterConfig()
        _currentListContext.value = null
        _tagFilterSourceBookmarkId.value = null
    }

    fun toggleListExpanded(listId: String) {
        _expandedLists.value = if (_expandedLists.value.contains(listId)) {
            _expandedLists.value - listId
        } else {
            _expandedLists.value + listId
        }
    }

    // Auto-expand parent chain when a list is selected
    private fun expandParentChain(listId: String, allLists: List<KarakeepList>) {
        val toExpand = mutableSetOf<String>()
        var currentId: String? = listId

        while (currentId != null) {
            val list = allLists.find { it.id == currentId }
            val pid = list?.parentId
            if (pid != null) {
                toExpand.add(pid)
                currentId = pid
            } else {
                break
            }
        }

        _expandedLists.value = _expandedLists.value + toExpand
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
            val markingUnread = bookmark.isRead
            val event = if (markingUnread) {
                BookmarkActionEvent.MarkUnread(bookmark)
            } else {
                BookmarkActionEvent.MarkRead(bookmark)
            }
            bookmarkActionController.executeAction(event)

            // Update the bookmark in the accumulated list immediately for UI feedback
            val resetProgress = markingUnread && settingsRepository.resetProgressOnMarkUnread.first()
            _accumulatedBookmarks.value = _accumulatedBookmarks.value.map {
                if (it.remoteId == bookmark.remoteId) {
                    if (resetProgress) {
                        it.copy(isRead = false, readingProgress = 0f, readingScrollIndex = 0, readingScrollOffset = 0)
                    } else {
                        it.copy(isRead = !bookmark.isRead)
                    }
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
            // Update local state immediately for UI feedback
            _accumulatedBookmarks.value = _accumulatedBookmarks.value.map {
                if (it.remoteId == bookmark.remoteId) {
                    val currentListIds = it.listIds.split(",").map { id -> id.trim() }.filter { id -> id.isNotBlank() }
                    if (!currentListIds.contains(listId)) {
                        it.copy(listIds = (currentListIds + listId).joinToString(","))
                    } else {
                        it
                    }
                } else {
                    it
                }
            }
        }
    }

    fun addBookmarkTag(bookmark: BookmarkEntity, tagName: String) {
        screenModelScope.launch {
            val currentTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (!currentTags.contains(tagName)) {
                val newTags = currentTags + tagName
                val isOnline = !_isSyncing.value
                bookmarkActionsRepository.updateTags(
                    bookmark.remoteId,
                    bookmark.serverId,
                    newTags,
                    isOnline
                )
                // Update local state immediately for UI feedback
                _accumulatedBookmarks.value = _accumulatedBookmarks.value.map {
                    if (it.remoteId == bookmark.remoteId) {
                        it.copy(tags = newTags.joinToString(","))
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun removeBookmarkTag(bookmark: BookmarkEntity, tagName: String) {
        screenModelScope.launch {
            val currentTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (currentTags.contains(tagName)) {
                val newTags = currentTags.filter { it != tagName }
                val isOnline = !_isSyncing.value
                bookmarkActionsRepository.updateTags(
                    bookmark.remoteId,
                    bookmark.serverId,
                    newTags,
                    isOnline
                )
                _accumulatedBookmarks.value = _accumulatedBookmarks.value.map {
                    if (it.remoteId == bookmark.remoteId) {
                        it.copy(tags = newTags.joinToString(","))
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun removeBookmarkFromList(bookmark: BookmarkEntity, listId: String) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.removeFromList(
                bookmark.remoteId,
                bookmark.serverId,
                listId,
                isOnline
            )
            _accumulatedBookmarks.value = _accumulatedBookmarks.value.map {
                if (it.remoteId == bookmark.remoteId) {
                    val newListIds = it.listIds.split(",").map { id -> id.trim() }
                        .filter { id -> id.isNotBlank() && id != listId }
                    it.copy(listIds = newListIds.joinToString(","))
                } else {
                    it
                }
            }
        }
    }

    /**
     * Mark all bookmarks in a list as read. No snackbar shown.
     */
    fun markAllBookmarksInListAsRead(listId: String) {
        screenModelScope.launch {
            val serverId = _selectedServer.value?.id ?: return@launch
            val unreadInList = allBookmarks.value.filter { bookmark ->
                val bookmarkLists = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                bookmarkLists.contains(listId) && !bookmark.isRead
            }
            unreadInList.forEach { bookmark ->
                bookmarkActionsRepository.markAsRead(bookmark.remoteId, serverId)
            }
            // Update accumulated list immediately for UI feedback
            _accumulatedBookmarks.value = _accumulatedBookmarks.value.map {
                val bookmarkLists = it.listIds.split(",").map { id -> id.trim() }.filter { id -> id.isNotEmpty() }
                if (bookmarkLists.contains(listId)) it.copy(isRead = true) else it
            }
        }
    }

    /**
     * Rename a list and optionally change its icon.
     */
    fun renameList(listId: String, newName: String, newIcon: String?) {
        screenModelScope.launch {
            val server = _selectedServer.value ?: return@launch
            listRepository.renameList(server, listId, newName, newIcon)
        }
    }

    /**
     * Execute a scroll-triggered action on a bookmark silently (no snackbar).
     * Called when a bookmark scrolls off screen or when the bottom of the list is reached.
     */
    fun executeScrollAction(
        bookmark: BookmarkEntity,
        action: com.karakept.app.data.model.SwipeAction,
        config: com.karakept.app.data.model.CustomSwipeActionConfig?
    ) {
        screenModelScope.launch {
            when (action) {
                com.karakept.app.data.model.SwipeAction.MARK_READ -> {
                    if (!bookmark.isRead) {
                        bookmarkActionsRepository.markAsRead(bookmark.remoteId, bookmark.serverId)
                        _accumulatedBookmarks.value = _accumulatedBookmarks.value.map {
                            if (it.remoteId == bookmark.remoteId) it.copy(isRead = true) else it
                        }
                    }
                }
                com.karakept.app.data.model.SwipeAction.ARCHIVE -> {
                    if (!bookmark.isArchived) {
                        bookmarkActionsRepository.archiveBookmark(bookmark.remoteId, bookmark.serverId)
                        _accumulatedBookmarks.value = _accumulatedBookmarks.value.filter {
                            it.remoteId != bookmark.remoteId
                        }
                    }
                }
                com.karakept.app.data.model.SwipeAction.FAVOURITE -> {
                    bookmarkActionsRepository.toggleFavourite(bookmark.remoteId, bookmark.serverId, bookmark.isStarred)
                }
                com.karakept.app.data.model.SwipeAction.ADD_TAG -> {
                    val tagName = config?.tagName
                    if (tagName != null) {
                        addBookmarkTag(bookmark, tagName)
                    }
                }
                com.karakept.app.data.model.SwipeAction.ADD_TO_LIST -> {
                    val listId = config?.listId
                    if (listId != null) {
                        moveBookmarkToList(bookmark, listId)
                    }
                }
                else -> {}
            }
        }
    }

    fun createBookmark(url: String) {
        screenModelScope.launch {
            val server = _selectedServer.value ?: return@launch
            val tempRemoteId = kotlin.random.Random.nextLong(Long.MIN_VALUE, -1L)
            val placeholder = BookmarkEntity(
                remoteId = tempRemoteId,
                originalRemoteId = "pending-$tempRemoteId",
                serverId = server.id,
                url = url,
                title = url,
                content = null,
                imageUrl = null,
                bannerImageAssetId = null,
                screenshotAssetId = null,
                description = null,
                createdAt = 0L,
                isArchived = false,
                isStarred = false,
            )

            _pendingBookmarks.value = listOf(placeholder) + _pendingBookmarks.value
            _scrollToTopTrigger.emit(Unit)

            val result = bookmarkRepository.createBookmark(url)

            result.onSuccess { bookmark ->
                // Prepend real bookmark before removing placeholder to avoid an empty frame
                _accumulatedBookmarks.value = listOf(bookmark) + _accumulatedBookmarks.value
                _pendingBookmarks.value = _pendingBookmarks.value.filter { it.remoteId != tempRemoteId }
                _createBookmarkResult.emit(Result.success(Unit))
            }.onFailure { e ->
                _pendingBookmarks.value = _pendingBookmarks.value.filter { it.remoteId != tempRemoteId }
                _createBookmarkResult.emit(Result.failure(e))
            }
        }
    }
}
