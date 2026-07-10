package com.karakept.app.ui.screens

import androidx.lifecycle.ViewModel
import com.karakept.app.utils.AppLogger
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.SYNC_KEY_ARCHIVED
import com.karakept.app.data.model.SYNC_KEY_FAVORITES
import com.karakept.app.data.model.SyncKey
import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.remote.hasHttpStatus
import com.karakept.app.data.repository.setDefaultListType
import com.karakept.app.data.repository.setDefaultListId
import com.karakept.api.model.KarakeepList as KarakeepList
import kotlinx.coroutines.coroutineScope
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
import com.karakept.app.domain.BookmarkFilterUtils
import com.karakept.app.domain.DefaultFilterResolver
import com.karakept.app.domain.ListHierarchyUtils

data class QuickFilterCounts(
    val all: Int = 0,
    val favorites: Int = 0,
    val archived: Int = 0,
    val offline: Int = 0
)

class MainScreenModel(
    private val serverRepository: ServerRepository,
    internal val bookmarkRepository: BookmarkRepository,
    internal val bookmarkActionsRepository: com.karakept.app.data.repository.BookmarkActionsRepository,
    internal val settingsRepository: com.karakept.app.data.repository.SettingsRepository,
    internal val listRepository: com.karakept.app.data.repository.ListRepository,
    internal val bookmarkActionController: BookmarkActionController,
    internal val snackbarManager: ActionSnackbarManager,
    private val highlightRepository: HighlightRepository
) : ViewModel() {

    private val defaultFilterResolver = DefaultFilterResolver(settingsRepository)

    val servers = serverRepository.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal val _selectedServer = MutableStateFlow<Server?>(null)
    val selectedServer: StateFlow<Server?> = _selectedServer

    internal val _currentFilter = MutableStateFlow(FilterConfig())
    val currentFilter: StateFlow<FilterConfig> = _currentFilter

    // Track the current active list filter (if any)
    internal val _currentListContext = MutableStateFlow<String?>(null)
    val currentListContext: StateFlow<String?> = _currentListContext

    // Per-key sync status from the repository; drives both drawer indicators and the top bar.
    val listSyncStatuses: StateFlow<Map<SyncKey, ListSyncStatus>> =
        bookmarkRepository.perKeyProgress
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Sync status for the list/filter currently on screen. Reacts immediately when the user
    // navigates to a different list so the top bar always reflects the current view.
    val currentSyncStatus: StateFlow<ListSyncStatus> = combine(
        _currentListContext,
        _currentFilter,
        bookmarkRepository.perKeyProgress
    ) { listId, filter, statuses ->
        statuses[resolveCurrentKey(listId, filter)] ?: ListSyncStatus.Idle
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListSyncStatus.Idle)

    // Backward-compat for PullToRefreshBox: only active when the CURRENT list is syncing.
    // Other lists syncing in background show their status only in the drawer.
    val isSyncing: StateFlow<Boolean> = combine(
        _currentListContext,
        _currentFilter,
        bookmarkRepository.perKeyProgress
    ) { listId, filter, statuses ->
        statuses[resolveCurrentKey(listId, filter)] != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @Deprecated("Use currentSyncStatus instead", ReplaceWith("currentSyncStatus"))
    internal val _isSyncing = MutableStateFlow(false)

    private val _tagFilterSourceBookmarkId = MutableStateFlow<Long?>(null)
    val tagFilterSourceBookmarkId: StateFlow<Long?> = _tagFilterSourceBookmarkId

    val lists: StateFlow<List<KarakeepList>> = listRepository.lists

    private val _expandedLists = MutableStateFlow<Set<String>>(emptySet())
    val expandedLists: StateFlow<Set<String>> = _expandedLists

    // Smart lists that may have stale membership after a recent list-action.
    // Cleared per-list after a successful sync when the user navigates to one.
    internal val _smartListsNeedingRefresh = MutableStateFlow<Set<String>>(emptySet())

    // Pagination state
    internal val pageSize = 20
    internal val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    internal val _hasMoreItems = MutableStateFlow(true)
    val hasMoreItems: StateFlow<Boolean> = _hasMoreItems

    internal val _currentPage = MutableStateFlow(0)
    internal val _accumulatedBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())

    // Incremented at the start of every resetPaginationAndLoad call. loadNextPage captures
    // this value before its DB fetch and discards results if the value changed (i.e. a
    // reset overtook it), preventing duplicate entries in the LazyColumn.
    internal var paginationGeneration = 0

    internal val bookmarksMutex = Mutex()

    /**
     * Thread-safe mutation of _accumulatedBookmarks.
     * All code that reads-then-writes _accumulatedBookmarks MUST use this helper.
     * Uses Mutex (not MutableStateFlow.update{}) because some callers need to hold
     * the lock across suspension points (e.g., bookmarkChangedEvents DB lookup).
     *
     * The result is de-duplicated by remoteId: the list feeds a LazyColumn keyed on
     * remoteId, and a duplicate key crashes the app (#274). Duplicates can slip in
     * when a background sync inserts rows mid-pagination (OFFSET drift) or when an
     * undo re-insertion races a concurrent transform.
     */
    internal suspend fun updateAccumulatedBookmarks(
        transform: (List<BookmarkEntity>) -> List<BookmarkEntity>
    ) {
        bookmarksMutex.withLock {
            _accumulatedBookmarks.value = transform(_accumulatedBookmarks.value)
                .distinctBy { it.remoteId }
        }
    }

    /**
     * Position of a bookmark in the accumulated list, read under the same mutex that
     * guards mutations so action handlers capture a position consistent with the
     * list they are about to modify.
     */
    internal suspend fun lockedPositionOf(remoteId: Long): Int = bookmarksMutex.withLock {
        _accumulatedBookmarks.value.indexOfFirst { it.remoteId == remoteId }
    }

    internal val _bookmarkListVersion = MutableStateFlow(0)
    val bookmarkListVersion: StateFlow<Int> = _bookmarkListVersion

    internal val _scrollToTopTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopTrigger: SharedFlow<Unit> = _scrollToTopTrigger

    // RemoteIds of bookmarks on which the user has explicitly performed a list-membership
    // action (add/remove list). Prevents the scroll-triggered action from auto-firing on a
    // bookmark that is about to leave the list via async reconciliation — the reconcile
    // involves network calls so the window can be several seconds long.
    // Cleared on every full list reload (resetPaginationAndLoad).
    internal val _actedOnBookmarkIds = MutableStateFlow<Set<Long>>(emptySet())
    val actedOnBookmarkIds: StateFlow<Set<Long>> = _actedOnBookmarkIds

    // Hoisted scroll position — survives Voyager push/pop within the same Navigator because
    // the same MainScreenModel instance is reused for the same Navigator's ScreenModelStore.
    // Updated by a LaunchedEffect in MainScreen that observes LazyListState;
    // read back when the composable re-enters composition to initialise a new LazyListState.
    @Volatile var savedScrollIndex: Int = 0
        internal set
    @Volatile var savedScrollOffset: Int = 0
        internal set

    fun saveScrollPosition(index: Int, offset: Int) {
        savedScrollIndex = index
        savedScrollOffset = offset
    }

    internal val _createBookmarkResult = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
    val createBookmarkResult: SharedFlow<Result<Unit>> = _createBookmarkResult

    internal val _pendingBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val pendingBookmarkRemoteIds: StateFlow<Set<Long>> = _pendingBookmarks
        .map { list -> list.map { it.remoteId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    internal val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Multi-select state
    internal val _selectedBookmarkIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedBookmarkIds: StateFlow<Set<Long>> = _selectedBookmarkIds
    val isSelectionMode: StateFlow<Boolean> = _selectedBookmarkIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Backward-compat for BookmarkListContent's top progress bar.
    // Derived from the current list's sync status rather than the global repository flow.
    val syncProgress: StateFlow<com.karakept.app.data.model.SyncProgress> =
        currentSyncStatus.map { status ->
            when (status) {
                is ListSyncStatus.FetchingMetadata -> com.karakept.app.data.model.SyncProgress.FetchingMetadata(0, status.bookmarksCount)
                is ListSyncStatus.FetchingContent  -> com.karakept.app.data.model.SyncProgress.FetchingContent(status.current, status.total)
                is ListSyncStatus.Idle             -> com.karakept.app.data.model.SyncProgress.Idle
            }
        }.stateIn(
            viewModelScope,
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val offlineBookmarkCount: StateFlow<Int> = selectedServer
        .flatMapLatest { server ->
            if (server != null) bookmarkRepository.getOfflineBookmarkCount(server.id)
            else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val quickFilterCounts: StateFlow<QuickFilterCounts> = combine(
        selectedServer, allBookmarks, offlineBookmarkCount
    ) { server, bookmarks, offline ->
        if (server == null) return@combine QuickFilterCounts()
        QuickFilterCounts(
            all = bookmarks.count { !it.isArchived },
            favorites = bookmarks.count { it.isStarred && !it.isArchived },
            archived = bookmarks.count { it.isArchived },
            offline = offline
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QuickFilterCounts())

    val highlightsCount: StateFlow<Int> = selectedServer
        .flatMapLatest { server ->
            if (server != null) highlightRepository.getHighlightsCount(server.id)
            else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val swipeLeftAction: StateFlow<com.karakept.app.data.model.SwipeAction> =
        settingsRepository.swipeLeftAction.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            com.karakept.app.data.model.SwipeAction.MARK_READ
        )

    val swipeRightAction: StateFlow<com.karakept.app.data.model.SwipeAction> =
        settingsRepository.swipeRightAction.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            com.karakept.app.data.model.SwipeAction.ARCHIVE
        )

    val customSwipeActionConfigs: StateFlow<List<com.karakept.app.data.model.CustomSwipeActionConfig>> =
        settingsRepository.customSwipeActionConfigs.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    val swipeLeftConfigId: StateFlow<String?> =
        settingsRepository.swipeLeftConfigId.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), null
        )

    val swipeRightConfigId: StateFlow<String?> =
        settingsRepository.swipeRightConfigId.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), null
        )

    val dimReadBookmarks: StateFlow<Boolean> =
        settingsRepository.dimReadBookmarks.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = true
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
                viewModelScope, SharingStarted.WhileSubscribed(5000),
                com.karakept.app.data.model.SwipeAction.NONE
            )

    val currentListScrollActionConfig: StateFlow<com.karakept.app.data.model.CustomSwipeActionConfig?> =
        combine(_currentListContext, settingsRepository.allListSettings, customSwipeActionConfigs) {
            listId, allSettings, configs ->
            if (listId == null) return@combine null
            val settings = allSettings[listId] ?: return@combine null
            val configId = settings.scrollActionConfigId ?: return@combine null
            configs.find { it.id == configId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Two independent bookmark pipelines selected by _searchQuery:
    //  • blank query → paginated view (_pendingBookmarks + _accumulatedBookmarks)
    //  • non-blank   → live DB search (allBookmarks filtered by query)
    // Keeping them separate ensures that DB writes during background sync never
    // trigger recomposition of the normal (non-search) bookmark list.
    val bookmarks: StateFlow<List<BookmarkEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                combine(_pendingBookmarks, _accumulatedBookmarks) { pending, accumulated ->
                    // Guard against a just-created bookmark appearing in both flows for a
                    // frame — duplicate remoteIds crash the keyed LazyColumn (#274).
                    val pendingIds = pending.map { it.remoteId }.toSet()
                    pending + accumulated.filter { it.remoteId !in pendingIds }
                }
            } else {
                combine(allBookmarks, _currentFilter) { all, filter ->
                    BookmarkFilterUtils.applySearchFilter(all, filter, query)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
        viewModelScope.launch {
            _initState.collect { state ->
                AppLogger.d("MainScreenModel", "Init state: ${state::class.simpleName}")
            }
        }

        // Coroutine A: keep _selectedServer in sync with the server list.
        viewModelScope.launch {
            servers.collect { serverList ->
                val current = _selectedServer.value
                when {
                    current == null && serverList.isNotEmpty() -> {
                        _selectedServer.value = serverList.first()
                        loadLists()
                    }
                    serverList.isEmpty() -> _selectedServer.value = null
                    else -> {
                        // Re-resolve so a re-authentication (same server id, new apiKey)
                        // propagates immediately instead of persisting a stale snapshot
                        // that 401s until app restart (#173).
                        _selectedServer.value = serverList.find { it.id == current?.id } ?: serverList.first()
                    }
                }
            }
        }

        // Coroutine B: explicit state machine for sequential startup.
        viewModelScope.launch {
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
                    // If navigating to a smart list that needs refresh (stale after a
                    // recent list-membership action), sync it from the server first.
                    // GET /lists/{id}/bookmarks is always fresh, unlike the per-bookmark endpoint.
                    val listId = filter.lists.singleOrNull()
                    val needsRefresh = listId != null && listId in _smartListsNeedingRefresh.value
                    if (needsRefresh) {
                        _smartListsNeedingRefresh.value -= listId!!
                        try {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                bookmarkRepository.syncBookmarksForList(currentServer, listId)
                            }
                        } catch (e: Exception) {
                            AppLogger.e("MainScreenModel", "Smart list refresh failed for $listId: ${e.message}", e)
                        }
                    }
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
            if (!isOffline) {
                syncBookmarks()
            }
        }

        // Keep the main list up-to-date when another screen mutates a bookmark.
        viewModelScope.launch {
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
        viewModelScope.launch {
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

    // Pagination — see MainScreenModelPagination.kt

    /** Maps the current view (list context + filter) to a [SyncKey] for progress tracking. */
    private fun resolveCurrentKey(listId: String?, filter: FilterConfig): SyncKey = when {
        listId != null -> listId
        filter.status == FilterStatus.FAVORITES -> SYNC_KEY_FAVORITES
        filter.status == FilterStatus.ARCHIVED  -> SYNC_KEY_ARCHIVED
        else -> null
    }

    fun syncBookmarks() {
        viewModelScope.launch {
            // Capture state BEFORE any suspension so the sync strategy and the
            // post-sync reload always use the same consistent snapshot.
            val capturedListContext = _currentListContext.value
            val capturedFilter = _currentFilter.value

            val isOffline: Boolean = settingsRepository.offlineMode.first()
            if (isOffline) return@launch

            val server = selectedServer.value ?: return@launch

            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // Step 1: Fetch lists first so the drawer updates immediately.
                    listRepository.refreshLists(server)

                    // Step 2: Sync the currently-rendered list/filter first so the user
                    // sees their bookmarks as soon as possible.
                    syncCurrentView(server, capturedListContext, capturedFilter)
                }

                // Step 3: Reload the current view immediately after its metadata lands.
                if (_currentFilter.value == capturedFilter) {
                    resetPaginationAndLoad(server, capturedFilter)
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // Step 4: Sync all other named lists concurrently.
                    // Each call is independently deduplicated by BookmarkRepository.
                    val currentKey = resolveCurrentKey(capturedListContext, capturedFilter)
                    syncOtherLists(server, currentKey)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Scope cancelled (e.g. screen navigated away) — not a sync error.
                bookmarkRepository.resetSyncProgress()
                throw e
            } catch (e: Exception) {
                AppLogger.e("MainScreenModel", "Sync failed: ${e.message}", e)
                if (e.hasHttpStatus(401)) {
                    // Retrying with the same credentials can't succeed — point the user
                    // at re-authentication instead (#173).
                    snackbarManager.showSnackbar(
                        "Authentication failed — check your API key in server settings",
                        androidx.compose.material3.SnackbarDuration.Long
                    )
                } else {
                    snackbarManager.showErrorWithRetry("Couldn't sync bookmarks") {
                        syncBookmarks()
                    }
                }
            }
        }
    }

    /** Dispatches the correct repository call for the currently-viewed list or filter. */
    private suspend fun syncCurrentView(
        server: com.karakept.app.data.model.Server,
        listContext: String?,
        filter: FilterConfig
    ) {
        when {
            listContext != null -> bookmarkRepository.syncBookmarksForList(server, listContext)
            filter.status == FilterStatus.FAVORITES -> bookmarkRepository.syncFavorites(server)
            filter.status == FilterStatus.ARCHIVED  -> bookmarkRepository.syncArchived(server)
            else -> bookmarkRepository.syncBookmarks(server)
        }
    }

    /**
     * Syncs all named lists except [skipKey] concurrently.
     * Failures of individual lists are logged but not re-thrown — they are non-fatal
     * and do not block the user from seeing already-synced data.
     */
    private suspend fun syncOtherLists(
        server: com.karakept.app.data.model.Server,
        skipKey: SyncKey
    ) {
        coroutineScope {
            listRepository.lists.value.forEach { list ->
                val key = list.id ?: return@forEach
                if (key == skipKey) return@forEach
                launch {
                    try {
                        bookmarkRepository.syncBookmarksForList(server, key)
                    } catch (e: Exception) {
                        AppLogger.w("MainScreenModel", "Other-list sync failed for list $key: ${e.message}")
                    }
                }
            }
        }
    }

    // Filter management

    fun applyFilter(filter: FilterConfig) {
        _currentFilter.value = filter
        _currentListContext.value = if (filter.lists.size == 1) filter.lists.first() else null
        if (filter.lists.size == 1) {
            _expandedLists.value = _expandedLists.value +
                ListHierarchyUtils.getAncestorIds(filter.lists.first(), lists.value)
        }
        // scrollToTop() removed — resetPaginationAndLoad emits it after the new data is ready
        persistActiveFilter(filter)
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
        // scrollToTop() removed — resetPaginationAndLoad emits it after the new data is ready
        persistActiveFilter(FilterConfig())
    }

    private fun persistActiveFilter(filter: FilterConfig) {
        viewModelScope.launch {
            settingsRepository.saveLastActiveFilter(
                status = filter.status.name,
                listId = filter.lists.singleOrNull()
            )
        }
    }

    fun setDefaultList(listId: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultListType(DefaultListType.SPECIFIC_LIST)
            settingsRepository.setDefaultListId(listId)
        }
    }

    fun setDefaultListType(type: DefaultListType) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            val server = servers.value.find { it.id == serverId }
            _selectedServer.value = server
        }
    }

    fun scrollToTop() {
        viewModelScope.launch {
            _scrollToTopTrigger.emit(Unit)
        }
    }

    private fun loadLists() {
        viewModelScope.launch {
            selectedServer.value?.let { server ->
                listRepository.refreshLists(server)
            }
        }
    }

    // Bookmark actions — see MainScreenModelActions.kt
    // Multi-select & batch operations — see MainScreenModelBatch.kt

    // Index of the last item that was clicked or selected (for Shift+Click range selection).
    // Tracked even outside selection mode so Shift+Click can use it as a range anchor.
    // Kept in the class because extension functions cannot hold mutable state.
    internal var _lastSelectedIndex: Int = -1
}
