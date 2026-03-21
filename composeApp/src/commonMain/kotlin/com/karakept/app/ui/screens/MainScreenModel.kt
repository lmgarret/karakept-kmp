package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import com.karakept.app.utils.AppLogger
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.api.model.KarakeepList as KarakeepList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.BookmarkActionEvent
import com.karakept.app.domain.BookmarkFilterUtils
import com.karakept.app.domain.DefaultFilterResolver
import com.karakept.app.domain.ListHierarchyUtils

class MainScreenModel(
    private val serverRepository: ServerRepository,
    internal val bookmarkRepository: BookmarkRepository,
    internal val bookmarkActionsRepository: com.karakept.app.data.repository.BookmarkActionsRepository,
    internal val settingsRepository: com.karakept.app.data.repository.SettingsRepository,
    internal val listRepository: com.karakept.app.data.repository.ListRepository,
    internal val bookmarkActionController: BookmarkActionController,
    internal val snackbarManager: ActionSnackbarManager
) : ScreenModel {

    private val defaultFilterResolver = DefaultFilterResolver(settingsRepository)

    val servers = serverRepository.servers
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal val _selectedServer = MutableStateFlow<Server?>(null)
    val selectedServer: StateFlow<Server?> = _selectedServer

    internal val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    internal val _currentFilter = MutableStateFlow(FilterConfig())
    val currentFilter: StateFlow<FilterConfig> = _currentFilter

    private val _tagFilterSourceBookmarkId = MutableStateFlow<Long?>(null)
    val tagFilterSourceBookmarkId: StateFlow<Long?> = _tagFilterSourceBookmarkId

    val lists: StateFlow<List<KarakeepList>> = listRepository.lists

    private val _expandedLists = MutableStateFlow<Set<String>>(emptySet())
    val expandedLists: StateFlow<Set<String>> = _expandedLists

    // Track the current active list filter (if any)
    internal val _currentListContext = MutableStateFlow<String?>(null)
    val currentListContext: StateFlow<String?> = _currentListContext

    // Pagination state
    internal val pageSize = 20
    internal val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    internal val _hasMoreItems = MutableStateFlow(true)
    val hasMoreItems: StateFlow<Boolean> = _hasMoreItems

    internal val _currentPage = MutableStateFlow(0)
    internal val _accumulatedBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())

    internal val bookmarksMutex = Mutex()

    /**
     * Thread-safe mutation of _accumulatedBookmarks.
     * All code that reads-then-writes _accumulatedBookmarks MUST use this helper.
     * Uses Mutex (not MutableStateFlow.update{}) because some callers need to hold
     * the lock across suspension points (e.g., bookmarkChangedEvents DB lookup).
     */
    internal suspend fun updateAccumulatedBookmarks(
        transform: (List<BookmarkEntity>) -> List<BookmarkEntity>
    ) {
        bookmarksMutex.withLock {
            _accumulatedBookmarks.value = transform(_accumulatedBookmarks.value)
        }
    }

    internal val _bookmarkListVersion = MutableStateFlow(0)
    val bookmarkListVersion: StateFlow<Int> = _bookmarkListVersion

    internal val _scrollToTopTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopTrigger: SharedFlow<Unit> = _scrollToTopTrigger

    internal val _createBookmarkResult = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
    val createBookmarkResult: SharedFlow<Result<Unit>> = _createBookmarkResult

    internal val _pendingBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val pendingBookmarkRemoteIds: StateFlow<Set<Long>> = _pendingBookmarks
        .map { list -> list.map { it.remoteId }.toSet() }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    internal val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Multi-select state
    internal val _selectedBookmarkIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedBookmarkIds: StateFlow<Set<Long>> = _selectedBookmarkIds
    val isSelectionMode: StateFlow<Boolean> = _selectedBookmarkIds
        .map { it.isNotEmpty() }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

    val syncProgress: StateFlow<com.karakept.app.data.model.SyncProgress> =
        bookmarkRepository.syncProgress.stateIn(
            screenModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.karakept.app.data.model.SyncProgress.Idle
        )

    // All bookmarks without filtering — for tag extraction and list counts.
    // Uses SharingStarted.WhileSubscribed so Room observers are released when
    // no collectors are active (e.g. app in background).
    val allBookmarks = selectedServer
        .flatMapLatest { server ->
            if (server != null) bookmarkRepository.getBookmarks(server) else flowOf(emptyList())
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            val descendantIds = ListHierarchyUtils.getAllDescendantIds(listId, listItems)
            val relevantIds = setOf(listId) + descendantIds
            val count = bookmarks.count { bookmark ->
                val bookmarkLists = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                bookmarkLists.any { it in relevantIds } && (!settings.countOnlyUnread || !bookmark.isRead)
            }
            listId to count
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val swipeLeftAction: StateFlow<com.karakept.app.data.model.SwipeAction> =
        settingsRepository.swipeLeftAction.stateIn(
            screenModelScope, SharingStarted.WhileSubscribed(5000),
            com.karakept.app.data.model.SwipeAction.MARK_READ
        )

    val swipeRightAction: StateFlow<com.karakept.app.data.model.SwipeAction> =
        settingsRepository.swipeRightAction.stateIn(
            screenModelScope, SharingStarted.WhileSubscribed(5000),
            com.karakept.app.data.model.SwipeAction.ARCHIVE
        )

    val customSwipeActionConfigs: StateFlow<List<com.karakept.app.data.model.CustomSwipeActionConfig>> =
        settingsRepository.customSwipeActionConfigs.stateIn(
            screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    val swipeLeftConfigId: StateFlow<String?> =
        settingsRepository.swipeLeftConfigId.stateIn(
            screenModelScope, SharingStarted.WhileSubscribed(5000), null
        )

    val swipeRightConfigId: StateFlow<String?> =
        settingsRepository.swipeRightConfigId.stateIn(
            screenModelScope, SharingStarted.WhileSubscribed(5000), null
        )

    val dimReadBookmarks: StateFlow<Boolean> =
        settingsRepository.dimReadBookmarks.stateIn(
            screenModelScope, SharingStarted.WhileSubscribed(5000), initialValue = true
        )

    val currentListScrollAction: StateFlow<com.karakept.app.data.model.SwipeAction> =
        _currentListContext
            .flatMapLatest { listId ->
                if (listId != null) {
                    settingsRepository.getListSettings(listId).map { it.scrollAction }
                } else {
                    flowOf(com.karakept.app.data.model.SwipeAction.NONE)
                }
            }
            .stateIn(
                screenModelScope, SharingStarted.WhileSubscribed(5000),
                com.karakept.app.data.model.SwipeAction.NONE
            )

    val currentListScrollActionConfig: StateFlow<com.karakept.app.data.model.CustomSwipeActionConfig?> =
        combine(_currentListContext, settingsRepository.allListSettings, customSwipeActionConfigs) {
            listId, allSettings, configs ->
            if (listId == null) return@combine null
            val settings = allSettings[listId] ?: return@combine null
            val configId = settings.scrollActionConfigId ?: return@combine null
            configs.find { it.id == configId }
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * The active [BookmarkLayout] for the current view.
     * Resolution order: per-list layout → default layout → null (fall back to global settings).
     */
    val activeLayout: StateFlow<BookmarkLayout?> =
        combine(
            _currentListContext,
            settingsRepository.allListSettings,
            settingsRepository.defaultLayoutId,
            settingsRepository.customLayouts
        ) { listId, allSettings, defaultLayoutId, customLayouts ->
            val perListLayoutId = if (listId != null) allSettings[listId]?.layoutId else null
            val resolvedId = perListLayoutId ?: defaultLayoutId ?: return@combine null
            if (BookmarkLayout.isBuiltInId(resolvedId)) {
                BookmarkLayout.getBuiltIn(resolvedId)
            } else {
                customLayouts.find { it.id == resolvedId }
            }
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Two independent bookmark pipelines selected by _searchQuery:
    //  • blank query → paginated view (_pendingBookmarks + _accumulatedBookmarks)
    //  • non-blank   → live DB search (allBookmarks filtered by query)
    // Keeping them separate ensures that DB writes during background sync never
    // trigger recomposition of the normal (non-search) bookmark list.
    val bookmarks: StateFlow<List<BookmarkEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                combine(_pendingBookmarks, _accumulatedBookmarks) { pending, accumulated ->
                    pending + accumulated
                }
            } else {
                combine(allBookmarks, _currentFilter) { all, filter ->
                    BookmarkFilterUtils.applySearchFilter(all, filter, query)
                }
            }
        }
        .stateIn(screenModelScope, SharingStarted.Lazily, emptyList())

    private sealed class InitState {
        data object Idle : InitState()
        data object ResolvingFilter : InitState()
        data class WaitingForServer(val filter: FilterConfig) : InitState()
        data class LoadingInitialPage(val server: Server, val filter: FilterConfig) : InitState()
        data object Ready : InitState()
    }

    private val _initState = MutableStateFlow<InitState>(InitState.Idle)

    init {
        // Log init state transitions for auditability.
        screenModelScope.launch {
            _initState.collect { state ->
                AppLogger.d("MainScreenModel", "Init state: ${state::class.simpleName}")
            }
        }

        // Coroutine A: keep _selectedServer in sync with the server list.
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

        // Coroutine B: explicit state machine for sequential startup.
        screenModelScope.launch {
            _initState.value = InitState.ResolvingFilter
            val defaultFilter = defaultFilterResolver.resolve()
            _currentFilter.value = defaultFilter
            if (defaultFilter.lists.size == 1) {
                _currentListContext.value = defaultFilter.lists.first()
            }

            _initState.value = InitState.WaitingForServer(defaultFilter)
            val server = selectedServer.first { it != null } ?: return@launch

            _initState.value = InitState.LoadingInitialPage(server, defaultFilter)
            resetPaginationAndLoad(server, defaultFilter)

            _initState.value = InitState.Ready

            // Start observers (unchanged from original code)
            launch {
                _currentFilter.drop(1).collectLatest { filter ->
                    val currentServer = _selectedServer.value ?: return@collectLatest
                    resetPaginationAndLoad(currentServer, filter)
                }
            }
            launch {
                _selectedServer.drop(1).collectLatest { newServer ->
                    if (newServer != null) {
                        resetPaginationAndLoad(newServer, _currentFilter.value)
                    }
                }
            }

            val isOffline: Boolean = settingsRepository.offlineMode.first()
            if (!isOffline && !_isSyncing.value) {
                syncBookmarks()
            }
        }

        // Keep the main list up-to-date when another screen mutates a bookmark.
        screenModelScope.launch {
            bookmarkActionsRepository.bookmarkChangedEvents.collect { remoteId ->
                val serverId = _selectedServer.value?.id ?: return@collect
                val updated = bookmarkRepository.getBookmarkByRemoteId(remoteId, serverId)
                updateAccumulatedBookmarks { current ->
                    if (updated != null) {
                        current.map { if (it.remoteId == remoteId) updated else it }
                    } else {
                        current.filter { it.remoteId != remoteId }
                    }
                }
            }
        }

        // Restore bookmarks on undo.
        screenModelScope.launch {
            bookmarkActionController.undoCompletedEvents.collect { event ->
                updateAccumulatedBookmarks { current ->
                    val mutable = current.toMutableList()
                    val existingIndex = mutable.indexOfFirst { it.remoteId == event.restoredBookmark.remoteId }
                    if (existingIndex >= 0) {
                        mutable[existingIndex] = event.restoredBookmark
                    } else {
                        if (event.originalPosition >= 0 && event.originalPosition <= mutable.size) {
                            mutable.add(event.originalPosition, event.restoredBookmark)
                        } else {
                            mutable.add(0, event.restoredBookmark)
                        }
                    }
                    mutable
                }
            }
        }
    }

    // =========================================================================
    // Pagination — see MainScreenModelPagination.kt
    // =========================================================================

    // =========================================================================
    // Sync
    // =========================================================================

    fun syncBookmarks() {
        screenModelScope.launch {
            // Capture state BEFORE any suspension so the sync strategy and the
            // post-sync reload always use the same consistent snapshot.
            val capturedListContext = _currentListContext.value
            val capturedFilter = _currentFilter.value

            val isOffline: Boolean = settingsRepository.offlineMode.first()
            if (isOffline) return@launch

            val server = selectedServer.value ?: return@launch

            try {
                _isSyncing.value = true
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    when {
                        capturedListContext != null ->
                            bookmarkRepository.syncBookmarksForList(server, capturedListContext)
                        capturedFilter.status == FilterStatus.FAVORITES ->
                            bookmarkRepository.syncFavorites(server)
                        capturedFilter.status == FilterStatus.ARCHIVED ->
                            bookmarkRepository.syncArchived(server)
                        else ->
                            bookmarkRepository.syncBookmarks(server)
                    }
                    listRepository.refreshLists(server)
                }

                // Reload with the same filter that was active when sync started.
                // Use resetPaginationAndLoad so the code path is identical to the
                // initial load (proven not to blink). If the user changed the
                // filter while sync was running the drop(1) observer already
                // triggered a reload for the new filter — skip in that case.
                if (_currentFilter.value == capturedFilter) {
                    resetPaginationAndLoad(server, capturedFilter)
                }
            } catch (e: Exception) {
                AppLogger.e("MainScreenModel", "Failed to toggle bookmark state: ${e.message}", e)
                snackbarManager.showErrorWithRetry("Couldn't sync bookmarks") {
                    syncBookmarks()
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // =========================================================================
    // Filter management
    // =========================================================================

    fun applyFilter(filter: FilterConfig) {
        _currentFilter.value = filter
        _currentListContext.value = if (filter.lists.size == 1) filter.lists.first() else null
        if (filter.lists.size == 1) {
            _expandedLists.value = _expandedLists.value +
                ListHierarchyUtils.getAncestorIds(filter.lists.first(), lists.value)
        }
    }

    fun applyTagFilter(tag: String, sourceBookmarkId: Long) {
        _tagFilterSourceBookmarkId.value = sourceBookmarkId
        applyFilter(FilterConfig(tags = listOf(tag)))
    }

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

    fun setDefaultList(listId: String) {
        screenModelScope.launch {
            settingsRepository.setDefaultListType(DefaultListType.SPECIFIC_LIST)
            settingsRepository.setDefaultListId(listId)
        }
    }

    fun setDefaultListType(type: DefaultListType) {
        screenModelScope.launch {
            settingsRepository.setDefaultListType(type)
            if (type != DefaultListType.SPECIFIC_LIST) {
                settingsRepository.setDefaultListId(null)
            }
        }
    }

    fun toggleListExpanded(listId: String) {
        _expandedLists.value = if (_expandedLists.value.contains(listId)) {
            _expandedLists.value - listId
        } else {
            _expandedLists.value + listId
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun selectServer(serverId: String) {
        screenModelScope.launch {
            val server = servers.value.find { it.id == serverId }
            _selectedServer.value = server
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

    // =========================================================================
    // Bookmark actions
    // =========================================================================

    fun toggleBookmarkArchive(bookmark: BookmarkEntity) {
        screenModelScope.launch {
            val position = _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
            val event = if (bookmark.isArchived) {
                BookmarkActionEvent.Unarchive(bookmark)
            } else {
                BookmarkActionEvent.Archive(bookmark)
            }
            bookmarkActionController.executeAction(event, originalPosition = position)
            updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
        }
    }

    fun toggleBookmarkFavorite(bookmark: BookmarkEntity) {
        screenModelScope.launch {
            val position = _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
            bookmarkActionController.executeAction(
                BookmarkActionEvent.ToggleFavorite(bookmark),
                originalPosition = position
            )
            if (bookmark.isStarred && _currentFilter.value.status == FilterStatus.FAVORITES) {
                updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
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

            val resetProgress = markingUnread && settingsRepository.resetProgressOnMarkUnread.first()
            updateAccumulatedBookmarks { list ->
                list.map {
                    if (it.remoteId == bookmark.remoteId) {
                        if (resetProgress) {
                            it.copy(
                                isRead = false,
                                readingProgress = 0f,
                                readingScrollIndex = 0,
                                readingScrollOffset = 0
                            )
                        } else {
                            it.copy(isRead = !bookmark.isRead)
                        }
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        screenModelScope.launch {
            val position = _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
            bookmarkActionController.executeAction(
                BookmarkActionEvent.Delete(bookmark),
                originalPosition = position
            )
            updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
        }
    }

    fun updateBookmarkTags(bookmark: BookmarkEntity, newTags: List<String>) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.updateTags(
                bookmark.remoteId, bookmark.serverId, newTags, isOnline
            )
        }
    }

    fun moveBookmarkToList(bookmark: BookmarkEntity, listId: String) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.moveToList(
                bookmark.remoteId, bookmark.serverId, listId, isOnline
            )
            updateAccumulatedBookmarks { list ->
                list.map {
                    if (it.remoteId == bookmark.remoteId) {
                        val currentListIds = it.listIds
                            .split(",")
                            .map { id -> id.trim() }
                            .filter { id -> id.isNotBlank() }
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
    }

    fun addBookmarkTag(bookmark: BookmarkEntity, tagName: String) {
        screenModelScope.launch {
            val currentTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (!currentTags.contains(tagName)) {
                val newTags = currentTags + tagName
                val isOnline = !_isSyncing.value
                bookmarkActionsRepository.updateTags(
                    bookmark.remoteId, bookmark.serverId, newTags, isOnline
                )
                updateAccumulatedBookmarks { list ->
                    list.map {
                        if (it.remoteId == bookmark.remoteId) it.copy(tags = newTags.joinToString(","))
                        else it
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
                    bookmark.remoteId, bookmark.serverId, newTags, isOnline
                )
                updateAccumulatedBookmarks { list ->
                    list.map {
                        if (it.remoteId == bookmark.remoteId) it.copy(tags = newTags.joinToString(","))
                        else it
                    }
                }
            }
        }
    }

    fun removeBookmarkFromList(bookmark: BookmarkEntity, listId: String) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.removeFromList(
                bookmark.remoteId, bookmark.serverId, listId, isOnline
            )
            updateAccumulatedBookmarks { list ->
                list.map {
                    if (it.remoteId == bookmark.remoteId) {
                        val newListIds = it.listIds
                            .split(",")
                            .map { id -> id.trim() }
                            .filter { id -> id.isNotBlank() && id != listId }
                        it.copy(listIds = newListIds.joinToString(","))
                    } else {
                        it
                    }
                }
            }
        }
    }

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
            updateAccumulatedBookmarks { list ->
                list.map {
                    val bookmarkLists = it.listIds.split(",").map { id -> id.trim() }.filter { id -> id.isNotEmpty() }
                    if (bookmarkLists.contains(listId)) it.copy(isRead = true) else it
                }
            }
        }
    }

    // =========================================================================
    // Multi-select & batch operations
    // =========================================================================

    // Index of the last item that was clicked or selected (for Shift+Click range selection).
    // Tracked even outside selection mode so Shift+Click can use it as a range anchor.
    private var _lastSelectedIndex: Int = -1

    /** Track the last clicked bookmark index (call on every normal click). */
    fun trackLastClickedIndex(index: Int) {
        _lastSelectedIndex = index
    }

    fun enterSelectionMode(bookmark: BookmarkEntity) {
        _selectedBookmarkIds.value = setOf(bookmark.remoteId)
        _lastSelectedIndex = bookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
    }

    /**
     * Enters selection mode and immediately selects a range from the last clicked
     * index (tracked outside selection mode via [trackLastClickedIndex]) to [toIndex].
     * If no anchor exists, just selects the single item at [toIndex].
     */
    fun enterSelectionModeWithRange(toIndex: Int) {
        val list = bookmarks.value
        val anchor = _lastSelectedIndex.takeIf { it >= 0 && it <= list.lastIndex }
        if (anchor != null) {
            val start = minOf(anchor, toIndex)
            val end = minOf(maxOf(anchor, toIndex), list.lastIndex)
            val rangeIds = (start..end).map { list[it].remoteId }.toSet()
            _selectedBookmarkIds.value = rangeIds
        } else {
            val bookmark = list.getOrNull(toIndex) ?: return
            _selectedBookmarkIds.value = setOf(bookmark.remoteId)
        }
        _lastSelectedIndex = toIndex
    }

    fun toggleBookmarkSelection(bookmark: BookmarkEntity) {
        val current = _selectedBookmarkIds.value
        _selectedBookmarkIds.value = if (bookmark.remoteId in current) {
            current - bookmark.remoteId
        } else {
            current + bookmark.remoteId
        }
        _lastSelectedIndex = bookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
    }

    /**
     * Selects all bookmarks in the range [lastSelectedIndex, toIndex] (inclusive).
     * Used for Shift+Click range selection on desktop.
     */
    fun selectRange(toIndex: Int) {
        val fromIndex = _lastSelectedIndex.takeIf { it >= 0 } ?: return
        val list = bookmarks.value
        val start = minOf(fromIndex, toIndex)
        val end = minOf(maxOf(fromIndex, toIndex), list.lastIndex)
        val rangeIds = (start..end).map { list[it].remoteId }.toSet()
        _selectedBookmarkIds.value = _selectedBookmarkIds.value + rangeIds
        _lastSelectedIndex = toIndex
    }

    fun clearSelection() {
        _selectedBookmarkIds.value = emptySet()
        _lastSelectedIndex = -1
    }

    fun selectAll() {
        _selectedBookmarkIds.value = _accumulatedBookmarks.value.map { it.remoteId }.toSet()
    }

    private fun getSelectedBookmarks(): List<BookmarkEntity> {
        val ids = _selectedBookmarkIds.value
        return _accumulatedBookmarks.value.filter { it.remoteId in ids }
    }

    fun batchArchive() {
        val bookmarks = getSelectedBookmarks().filter { !it.isArchived }
        if (bookmarks.isEmpty()) { clearSelection(); return }
        screenModelScope.launch {
            bookmarkActionsRepository.batchArchive(bookmarks)
            val remoteIds = bookmarks.map { it.remoteId }.toSet()
            updateAccumulatedBookmarks { it.filter { b -> b.remoteId !in remoteIds } }
            clearSelection()
        }
    }

    fun batchUnarchive() {
        val bookmarks = getSelectedBookmarks().filter { it.isArchived }
        if (bookmarks.isEmpty()) { clearSelection(); return }
        screenModelScope.launch {
            bookmarkActionsRepository.batchUnarchive(bookmarks)
            val remoteIds = bookmarks.map { it.remoteId }.toSet()
            updateAccumulatedBookmarks { it.filter { b -> b.remoteId !in remoteIds } }
            clearSelection()
        }
    }

    fun batchMarkRead() {
        val bookmarks = getSelectedBookmarks().filter { !it.isRead }
        if (bookmarks.isEmpty()) { clearSelection(); return }
        screenModelScope.launch {
            bookmarkActionsRepository.batchMarkRead(bookmarks)
            val ids = bookmarks.map { it.remoteId }.toSet()
            updateAccumulatedBookmarks { list ->
                list.map { if (it.remoteId in ids) it.copy(isRead = true) else it }
            }
            clearSelection()
        }
    }

    fun batchMarkUnread() {
        val bookmarks = getSelectedBookmarks().filter { it.isRead }
        if (bookmarks.isEmpty()) { clearSelection(); return }
        screenModelScope.launch {
            val resetProgress = settingsRepository.resetProgressOnMarkUnread.first()
            bookmarkActionsRepository.batchMarkUnread(bookmarks, resetProgress)
            val ids = bookmarks.map { it.remoteId }.toSet()
            updateAccumulatedBookmarks { list ->
                list.map {
                    if (it.remoteId in ids) {
                        if (resetProgress) it.copy(isRead = false, readingProgress = 0f, readingScrollIndex = 0, readingScrollOffset = 0)
                        else it.copy(isRead = false)
                    } else it
                }
            }
            clearSelection()
        }
    }

    fun batchFavourite() {
        val bookmarks = getSelectedBookmarks().filter { !it.isStarred }
        if (bookmarks.isEmpty()) { clearSelection(); return }
        screenModelScope.launch {
            bookmarkActionsRepository.batchSetFavourite(bookmarks, makeFavourite = true)
            val ids = bookmarks.map { it.remoteId }.toSet()
            updateAccumulatedBookmarks { list ->
                list.map { if (it.remoteId in ids) it.copy(isStarred = true) else it }
            }
            clearSelection()
        }
    }

    fun batchUnfavourite() {
        val bookmarks = getSelectedBookmarks().filter { it.isStarred }
        if (bookmarks.isEmpty()) { clearSelection(); return }
        screenModelScope.launch {
            bookmarkActionsRepository.batchSetFavourite(bookmarks, makeFavourite = false)
            val ids = bookmarks.map { it.remoteId }.toSet()
            updateAccumulatedBookmarks { list ->
                list.map { if (it.remoteId in ids) it.copy(isStarred = false) else it }
            }
            clearSelection()
        }
    }

    fun batchDelete() {
        val bookmarks = getSelectedBookmarks()
        if (bookmarks.isEmpty()) { clearSelection(); return }
        screenModelScope.launch {
            bookmarkActionsRepository.batchDelete(bookmarks)
            val ids = bookmarks.map { it.remoteId }.toSet()
            updateAccumulatedBookmarks { it.filter { b -> b.remoteId !in ids } }
            clearSelection()
        }
    }

    fun batchSetTags(newTags: List<String>) {
        val bookmarks = getSelectedBookmarks()
        if (bookmarks.isEmpty()) { clearSelection(); return }
        screenModelScope.launch {
            bookmarkActionsRepository.batchUpdateTags(bookmarks, newTags)
            val ids = bookmarks.map { it.remoteId }.toSet()
            val tagString = newTags.joinToString(",")
            updateAccumulatedBookmarks { list ->
                list.map { bookmark ->
                    if (bookmark.remoteId in ids) bookmark.copy(tags = tagString) else bookmark
                }
            }
            clearSelection()
        }
    }

    fun batchMoveToList(listId: String) {
        val bookmarks = getSelectedBookmarks()
        if (bookmarks.isEmpty()) { clearSelection(); return }
        screenModelScope.launch {
            bookmarkActionsRepository.batchMoveToList(bookmarks, listId)
            val ids = bookmarks.map { it.remoteId }.toSet()
            updateAccumulatedBookmarks { list ->
                list.map { bookmark ->
                    if (bookmark.remoteId in ids) {
                        val currentListIds = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        if (!currentListIds.contains(listId)) {
                            bookmark.copy(listIds = (currentListIds + listId).joinToString(","))
                        } else bookmark
                    } else bookmark
                }
            }
            clearSelection()
        }
    }

    fun renameList(listId: String, newName: String, newIcon: String?) {
        screenModelScope.launch {
            val server = _selectedServer.value ?: return@launch
            listRepository.renameList(server, listId, newName, newIcon)
        }
    }

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
                        updateAccumulatedBookmarks { list ->
                            list.map { if (it.remoteId == bookmark.remoteId) it.copy(isRead = true) else it }
                        }
                    }
                }
                com.karakept.app.data.model.SwipeAction.ARCHIVE -> {
                    if (!bookmark.isArchived) {
                        bookmarkActionsRepository.archiveBookmark(bookmark.remoteId, bookmark.serverId)
                        updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
                    }
                }
                com.karakept.app.data.model.SwipeAction.FAVOURITE -> {
                    bookmarkActionsRepository.toggleFavourite(
                        bookmark.remoteId, bookmark.serverId, bookmark.isStarred
                    )
                }
                com.karakept.app.data.model.SwipeAction.ADD_TAG -> {
                    config?.tagName?.let { addBookmarkTag(bookmark, it) }
                }
                com.karakept.app.data.model.SwipeAction.ADD_TO_LIST -> {
                    config?.listId?.let { moveBookmarkToList(bookmark, it) }
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
                updateAccumulatedBookmarks { listOf(bookmark) + it }
                _pendingBookmarks.value = _pendingBookmarks.value.filter { it.remoteId != tempRemoteId }
                _createBookmarkResult.emit(Result.success(Unit))
            }.onFailure { e ->
                _pendingBookmarks.value = _pendingBookmarks.value.filter { it.remoteId != tempRemoteId }
                _createBookmarkResult.emit(Result.failure(e))
            }
        }
    }
}
