package com.karakept.app.ui.screens

import androidx.lifecycle.ViewModel
import com.karakept.app.utils.AppLogger
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.SYNC_KEY_ARCHIVED
import com.karakept.app.data.model.SYNC_KEY_FAVORITES
import com.karakept.app.data.model.SyncKey
import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.remote.hasHttpStatus
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.data.repository.discardFailedActions
import com.karakept.app.data.repository.retryFailedActions
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.karakept.app.domain.action.AiAction
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.TagFilterRequests
import com.karakept.app.domain.BookmarkFilterUtils
import com.karakept.app.domain.DefaultFilterResolver
import com.karakept.app.domain.ListHierarchyUtils

data class QuickFilterCounts(
    val all: Int = 0,
    val favorites: Int = 0,
    val archived: Int = 0,
    val offline: Int = 0
)

/** Identity of a bookmark view: the server plus the filter being displayed. */
internal data class LoadedView(val serverId: String, val filter: FilterConfig)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainScreenModel(
    private val serverRepository: ServerRepository,
    internal val bookmarkRepository: BookmarkRepository,
    internal val bookmarkActionsRepository: com.karakept.app.data.repository.BookmarkActionsRepository,
    internal val settingsRepository: com.karakept.app.data.repository.SettingsRepository,
    internal val listRepository: com.karakept.app.data.repository.ListRepository,
    internal val bookmarkActionController: BookmarkActionController,
    internal val snackbarManager: ActionSnackbarManager,
    private val highlightRepository: HighlightRepository,
    // Defaulted so a test can construct the model without wiring a request source it never uses.
    private val tagFilterRequests: TagFilterRequests = TagFilterRequests()
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

    private val _tagFilterSourceBookmarkId = MutableStateFlow<Long?>(null)
    val tagFilterSourceBookmarkId: StateFlow<Long?> = _tagFilterSourceBookmarkId

    val lists: StateFlow<List<KarakeepList>> = listRepository.lists

    private val _expandedLists = MutableStateFlow<Set<String>>(emptySet())
    val expandedLists: StateFlow<Set<String>> = _expandedLists

    private val listSettings: StateFlow<Map<String, ListSettings>> = settingsRepository.allListSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * The filter the data layer is queried with: [currentFilter] plus the child lists it expands
     * into for lists configured with `includeChildListBookmarks`.
     *
     * The expansion depends on the drawer's lists, which load asynchronously, so it can change
     * after a view has been rendered. It is therefore part of the view's identity (see
     * [currentView]) and drives the reload path: when it changes, the view reloads like any
     * other view switch, instead of the query quietly changing under a loaded window.
     *
     * [effectiveFilterNow] is the same value read synchronously. The flow drives the reload;
     * the synchronous read is for the guards, which must flip in the same breath as the tap
     * that changed [currentFilter] rather than a dispatch later.
     */
    val effectiveFilter: StateFlow<FilterConfig> = combine(
        _currentFilter,
        lists,
        listSettings
    ) { filter, allLists, settings ->
        ListHierarchyUtils.expandFilterLists(filter, allLists, settings)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FilterConfig())

    /** The effective filter for [filter] against the lists and settings known right now. */
    internal fun effectiveFilterNow(filter: FilterConfig = _currentFilter.value): FilterConfig =
        ListHierarchyUtils.expandFilterLists(filter, lists.value, listSettings.value)

    // Smart lists that may have stale membership after a recent list-action.
    // Cleared per-list after a successful sync when the user navigates to one.
    internal val _smartListsNeedingRefresh = MutableStateFlow<Set<String>>(emptySet())

    // Pagination state
    internal val pageSize = PAGE_SIZE
    internal val _isLoadingMore = MutableStateFlow(false)

    // resetPaginationAndLoad reuses _isLoadingMore to block loadNextPage while it walks to
    // page 0. That is a mutex, not a UI signal — this flag separates the two so a view
    // switch stops rendering the bottom "loading more" spinner.
    internal val _isResettingPagination = MutableStateFlow(false)

    /**
     * Bottom-of-list spinner: genuine pagination only, never a view switch.
     * Shared eagerly — this was a plain StateFlow, and callers still read `.value` directly.
     */
    val isLoadingMore: StateFlow<Boolean> =
        combine(_isLoadingMore, _isResettingPagination) { loading, resetting -> loading && !resetting }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    internal val _hasMoreItems = MutableStateFlow(true)
    val hasMoreItems: StateFlow<Boolean> = _hasMoreItems

    internal val _currentPage = MutableStateFlow(0)
    internal val _accumulatedBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())

    // Incremented at the start of every resetPaginationAndLoad call. loadNextPage captures
    // this value before its DB fetch and discards results if the value changed (i.e. a
    // reset overtook it), preventing duplicate entries in the LazyColumn.
    internal var paginationGeneration = 0

    // Incremented by refreshLoadedPagesInPlace only, so two overlapping in-place refreshes
    // resolve last-one-wins. Refreshes deliberately leave paginationGeneration alone: a
    // reset is a user-initiated view switch and must always win over a background refresh,
    // otherwise the refresh discards the reset's page and the list the user just left stays
    // on screen under the new list's title.
    internal var refreshGeneration = 0

    // The view the accumulated window was loaded for. Every write into the window is rejected
    // unless this still matches the view on screen: the requested view flips synchronously when
    // the user taps a list while the reload it triggers runs on an observer coroutine, so
    // without this a page fetched for the new list lands on top of the old list's items.
    internal val _loadedView = MutableStateFlow<LoadedView?>(null)

    /** The view the UI is currently asking for, or null while no server is selected. */
    internal fun currentView(): LoadedView? =
        _selectedServer.value?.let { LoadedView(it.id, effectiveFilterNow()) }

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

    /** The global setting. Read through [effectiveDimReadBookmarks], which applies the layout. */
    private val dimReadBookmarks: StateFlow<Boolean> =
        settingsRepository.dimReadBookmarks.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = true
        )

    /**
     * Whether read bookmarks are faded in the list right now — the active layout's choice when
     * it has one, the global setting otherwise. Resolved here rather than at each use so the
     * rendering and the "N new" pill cannot disagree about it.
     */
    val effectiveDimReadBookmarks: StateFlow<Boolean> =
        combine(activeLayout, dimReadBookmarks) { layout, global ->
            layout?.dimReadBookmarks ?: global
        }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // The topmost bookmark the user has actually seen. Everything above it arrived since.
    internal val _seenTopRemoteId = MutableStateFlow<Long?>(null)

    /**
     * How many bookmarks sit above the topmost one the user has seen — the "N new" pill.
     *
     * Derived from the loaded window every time rather than accumulated from per-refresh diffs.
     * A diff counts anything new to the *window*, which over-reports in three ways: it ignores
     * where the row landed (so a non-NEWEST sort, or an insert below the viewport, still counts
     * as "above"), it never decrements when rows leave, and because a refresh re-reads a fixed
     * page range, a row evicted off the tail by a prepend is counted a second time if a later
     * removal pulls it back into the window. Counting positions asks the list where things
     * actually are, so it is correct under any sort and self-corrects on every change.
     *
     * Counted against [_accumulatedBookmarks] rather than [bookmarks] so a bookmark the user
     * just created — prepended as a placeholder, and already scrolled to — is not reported back
     * to them as new. Eager sharing keeps the value readable without a collector.
     *
     * Read bookmarks are left out while they are being faded: the pill offers to take the user
     * to what arrived, and a row already read — marked on another device, or carried in by the
     * reading progress the sync pulls — is not something they are being sent back for. With
     * fading off, read and unread rows look alike and the count covers both.
     */
    val newBookmarksAbove: StateFlow<Int> =
        combine(
            _accumulatedBookmarks,
            _seenTopRemoteId,
            effectiveDimReadBookmarks
        ) { window, seenTop, dimRead ->
            countBookmarksAbove(window, seenTop, excludeRead = dimRead)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * Marks the row currently at the top as seen, which is what empties the pill. Called when
     * the user taps the pill to jump there.
     */
    fun clearNewBookmarksAbove() {
        _seenTopRemoteId.value = _accumulatedBookmarks.value.firstOrNull()?.remoteId
    }

    /**
     * Reports the topmost bookmark on screen. The anchor follows the viewport *up* the list and
     * never back down, so scrolling up through what arrived retires it row by row while
     * scrolling away downwards leaves the count alone.
     *
     * Reaching the exact first row used to be the only thing that moved the anchor. Reading the
     * new arrivals and stopping a row short of the top therefore left it where it was, and the
     * pill went on offering a trip to bookmarks the user had just read — every time they
     * scrolled away from the top again, with no sync in between.
     */
    fun markTopVisibleSeen(remoteId: Long) {
        val anchor = _seenTopRemoteId.value
        if (anchor == remoteId) return
        // Which of the two comes first is the whole question, so one pass that stops at
        // whichever it meets answers it. Scrolling down would otherwise scan to the row now on
        // top — further with every row — only to reject the update.
        for (bookmark in _accumulatedBookmarks.value) {
            when (bookmark.remoteId) {
                // The row on screen is above the anchor: it is the topmost one seen now.
                remoteId -> { _seenTopRemoteId.value = remoteId; return }
                // The anchor is still above it, so nothing has been seen above the anchor.
                anchor -> return
            }
        }
        // Neither is in the window. An anchor that has left it can no longer be compared
        // against, and holding on to it pins the count to zero until the user reaches the top.
        if (anchor != null) _seenTopRemoteId.value = null
    }

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

    // True while the current selection came from Select All rather than being hand-picked.
    // AI actions are withheld in that case: Select All can reach the whole library, and each
    // bookmark is a separate LLM call on the server.
    internal val _selectedViaSelectAll = MutableStateFlow(false)
    val selectedViaSelectAll: StateFlow<Boolean> = _selectedViaSelectAll

    // Non-null while a batch AI run is in progress; drives the selection bar's title and Cancel.
    internal val _aiBatchProgress = MutableStateFlow<AiBatchProgress?>(null)
    val aiBatchProgress: StateFlow<AiBatchProgress?> = _aiBatchProgress
    internal var aiBatchJob: kotlinx.coroutines.Job? = null

    // What the selected server will let this account do. Probed once per server; see
    // refreshAiCapabilities. Defaults to "summarize yes, admin no" until the probe answers.
    val aiCapabilities: StateFlow<AiCapabilities> =
        combine(_selectedServer, bookmarkActionsRepository.aiCapabilities) { server, byServer ->
            byServer[server?.id] ?: AiCapabilities()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiCapabilities())

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
            val relevantIds = if (settings.includeChildListBookmarks) {
                setOf(listId) + ListHierarchyUtils.getAllDescendantIds(listId, listItems)
            } else {
                setOf(listId)
            }
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

    /**
     * Number of queued changes that could not be synced (exhausted retries or a
     * permanent server rejection). Surfaced so the user can retry or discard them
     * instead of the change silently vanishing.
     */
    val failedActionCount: StateFlow<Int> = selectedServer
        .flatMapLatest { server ->
            if (server != null) bookmarkActionsRepository.failedActionsCount(server.id) else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun retryFailedActions() {
        viewModelScope.launch {
            val server = _selectedServer.value ?: return@launch
            bookmarkActionsRepository.retryFailedActions(server)
        }
    }

    fun discardFailedActions() {
        viewModelScope.launch {
            val server = _selectedServer.value ?: return@launch
            bookmarkActionsRepository.discardFailedActions(server.id)
        }
    }

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

    val rowActionMode: StateFlow<RowActionMode> =
        settingsRepository.rowActionMode.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), RowActionMode.SWIPE
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
                combine(allBookmarks, effectiveFilter) { all, filter ->
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

    /**
     * Nothing to render yet, but something is on its way — so the empty state must stay
     * hidden. False once there are items (a view switch keeps the outgoing list on screen)
     * and false when the view has genuinely resolved to empty, which is what distinguishes
     * "still loading" from "no bookmarks here".
     */
    val isLoadingInitialPage: StateFlow<Boolean> =
        combine(_initState, _isResettingPagination, _accumulatedBookmarks) { init, resetting, items ->
            items.isEmpty() && (init != InitState.Ready || resetting)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
            AppLogger.d(
                "MainScreenModel",
                "Startup filter: status=${defaultFilter.status} lists=${defaultFilter.lists}"
            )
            _currentFilter.value = defaultFilter
            if (defaultFilter.lists.size == 1) {
                _currentListContext.value = defaultFilter.lists.first()
            }

            _initState.value = InitState.WaitingForServer(defaultFilter)
            val server = selectedServer.first { it != null } ?: return@launch
            _initState.value = InitState.LoadingInitialPage(server, defaultFilter)

            // One reload path for every input the query is built from — the server and the
            // effective filter. The initial load is simply this observer's first emission, so
            // startup and later changes cannot drift apart: whatever changes the requested view
            // (tapping a list, switching server, the drawer's lists arriving and expanding the
            // current list into its children) reloads it the same way.
            launch {
                combine(_selectedServer, effectiveFilter) { currentServer, filter ->
                    currentServer?.let { it to filter }
                }.collectLatest { request ->
                    val (currentServer, filter) = request ?: return@collectLatest
                    if (_loadedView.value == LoadedView(currentServer.id, filter)) return@collectLatest

                    // Switch to the new view immediately with local data so navigation feels
                    // instant, instead of blocking on a network sync of the target list.
                    AppLogger.d(
                        "MainScreenModel",
                        "Loading view: status=${filter.status} lists=${filter.lists} tags=${filter.tags}"
                    )
                    resetPaginationAndLoad(currentServer, filter)
                    refreshStaleSmartList(currentServer, filter)
                }
            }

            _loadedView.first { it != null }
            _initState.value = InitState.Ready

            val isOffline: Boolean = settingsRepository.offlineMode.first()
            // Throttle the automatic startup sync so re-entering this screen (e.g. back
            // from the reader) doesn't fire a full sync every time (#276). Manual
            // pull-to-refresh and the sync button are never throttled.
            if (!isOffline && bookmarkRepository.shouldAutoSync(server.id)) {
                syncBookmarks()
            }
        }

        // When a background sync finishes, refresh the currently-displayed list in place so
        // newly synced bookmarks appear (and bump the "N new" pill) without the user having to
        // navigate or pull-to-refresh. The foreground syncBookmarks() already refreshes itself.
        viewModelScope.launch {
            bookmarkRepository.backgroundSyncCompleted.collect {
                val server = _selectedServer.value ?: return@collect
                if (_initState.value == InitState.Ready) {
                    val filter = effectiveFilterNow()
                    try {
                        refreshLoadedPagesInPlace(server, filter)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.w("MainScreenModel", "Post-background-sync refresh failed: ${e.message}")
                    }
                }
            }
        }

        // A long sync commits metadata page by page. Refresh in place as pages land, so a
        // large library fills in progressively instead of appearing all at once at the end.
        // Only for the key currently on screen — other lists syncing must not touch it.
        viewModelScope.launch {
            bookmarkRepository.pageCommitted
                .filter { key ->
                    key == resolveCurrentKey(_currentListContext.value, _currentFilter.value) ||
                        (key == null && _currentListContext.value == null)
                }
                .conflate()
                .collect {
                    val server = _selectedServer.value ?: return@collect
                    if (_initState.value != InitState.Ready) return@collect
                    // The window is loaded with the effective filter, so a refresh must ask for
                    // it too — the raw filter is a different view and would be rejected.
                    val filter = effectiveFilterNow()
                    try {
                        refreshLoadedPagesInPlace(server, filter)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.w("MainScreenModel", "Per-page refresh failed: ${e.message}")
                    }
                }
        }

        // Surface non-fatal sync warnings (swallowed content/highlight failures) once per
        // report, so the user isn't told "sync complete" when part of it failed (Group H).
        viewModelScope.launch {
            bookmarkRepository.syncReports.collect { report ->
                if (report.hasWarnings) {
                    val n = report.warnings.size
                    val label = if (n == 1) "Sync finished — 1 item couldn't be synced"
                                else "Sync finished — $n items couldn't be synced"
                    snackbarManager.showErrorWithRetry(label, androidx.compose.material3.SnackbarDuration.Long) {
                        syncBookmarks()
                    }
                }
            }
        }

        // Surface queued changes that couldn't be synced (once, on the rising edge)
        // so the user can retry them instead of the change silently disappearing.
        viewModelScope.launch {
            var previous = 0
            failedActionCount.collect { count ->
                if (count > previous && count > 0) {
                    val label = if (count == 1) "1 change couldn't be synced" else "$count changes couldn't be synced"
                    snackbarManager.showErrorWithRetry(label, androidx.compose.material3.SnackbarDuration.Long) {
                        retryFailedActions()
                    }
                }
                previous = count
            }
        }

        // A pass that touched many rows at once — the reading-progress pull — asks for a
        // re-read rather than naming each row. One query, and it is the only form that gets
        // membership right: a row the pull turns back to unread has to be able to join a view
        // filtered on unread, which patching rows already in the window cannot do.
        viewModelScope.launch {
            bookmarkActionsRepository.bookmarksReloaded.conflate().collect {
                val server = _selectedServer.value ?: return@collect
                if (_initState.value != InitState.Ready) return@collect
                try {
                    refreshLoadedPagesInPlace(server, effectiveFilterNow())
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w("MainScreenModel", "Post-progress-pass refresh failed: ${e.message}")
                }
            }
        }

        // Tapping a tag in the reader filters this list by it. The reader cannot call the model
        // on screen — screen models are scoped per back-stack entry — so the request arrives
        // through a shared single instead. See TagFilterRequests.
        viewModelScope.launch {
            tagFilterRequests.requests.collect { request ->
                applyTagFilter(request.tag, request.sourceBookmarkId)
            }
        }

        // Find out what AI actions the selected server will accept, so the menus can hide the
        // ones it would only reject. Cheap and idempotent — one tRPC probe per server change.
        viewModelScope.launch {
            _selectedServer.filterNotNull().distinctUntilChanged { a, b -> a.id == b.id }
                .collect { server -> bookmarkActionsRepository.refreshAiCapabilities(server) }
        }

        // Keep the main list up-to-date when another screen mutates a bookmark.
        viewModelScope.launch {
            bookmarkActionsRepository.bookmarkChangedEvents.collect { remoteId ->
                val serverId = _selectedServer.value?.id ?: return@collect
                // The reading-progress sync notifies for rows anywhere in the library — a
                // backfill covers all of it — and only the loaded window has anything to
                // re-read. Nothing to remove either: a row outside it is already not shown.
                if (_accumulatedBookmarks.value.none { it.remoteId == remoteId }) return@collect
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

    /**
     * Syncs the list just navigated to when a recent list-membership action may have left its
     * smart-list membership stale (GET /lists/{id}/bookmarks is always fresh), then folds the
     * result into the view in place so the correction lands without the list jumping.
     *
     * Called from the reload observer, so navigating away cancels it before it runs.
     */
    private suspend fun refreshStaleSmartList(server: Server, filter: FilterConfig) {
        val listId = _currentListContext.value ?: return
        if (listId !in _smartListsNeedingRefresh.value) return
        _smartListsNeedingRefresh.value -= listId
        try {
            // The list just navigated to, so it takes the same exemption as syncCurrentView.
            bookmarkRepository.syncBookmarksForList(server, listId, isCurrentView = true)
            if (currentView() == LoadedView(server.id, filter)) {
                refreshLoadedPagesInPlace(server, filter)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("MainScreenModel", "Smart list refresh failed for $listId: ${e.message}", e)
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

    /**
     * Fills in reading progress for the rows currently on screen.
     *
     * The sync pass covers a bounded slice of the library, which leaves rows further down the
     * list showing nothing until a later pass reaches them. Scrolling to them asks for exactly
     * those, so what the user is looking at is right even when the rotation has not. Only rows
     * whose progress is missing or stale cost a request, so scrolling back and forth is free.
     */
    fun onBookmarksVisible(remoteIds: List<Long>) {
        val server = _selectedServer.value ?: return
        if (remoteIds.isEmpty()) return
        viewModelScope.launch {
            try {
                bookmarkRepository.pullReadingProgressForVisible(server.id, remoteIds)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.d("MainScreenModel", "Visible-row progress pull failed: ${e.message}")
            }
        }
    }

    fun syncBookmarks() {
        viewModelScope.launch {
            // Capture state BEFORE any suspension so the sync strategy and the
            // post-sync reload always use the same consistent snapshot.
            val capturedListContext = _currentListContext.value
            val capturedFilter = _currentFilter.value
            val capturedEffectiveFilter = effectiveFilterNow()

            val isOffline: Boolean = settingsRepository.offlineMode.first()
            if (isOffline) return@launch

            val server = selectedServer.value ?: return@launch

            try {
                // Step 1: Fetch lists first so the drawer updates immediately.
                listRepository.refreshLists(server)

                // Step 2: Sync the currently-rendered list/filter first so the user
                // sees their bookmarks as soon as possible.
                syncCurrentView(server, capturedListContext, capturedFilter)

                // Step 3: Refresh the current view in place after its metadata lands, so
                // newly synced bookmarks appear without the list blinking and jumping to the
                // top (which a full resetPaginationAndLoad would cause).
                if (effectiveFilterNow() == capturedEffectiveFilter) {
                    refreshLoadedPagesInPlace(server, capturedEffectiveFilter)
                }

                // Step 4: Sync all other named lists concurrently.
                // Each call is independently deduplicated by BookmarkRepository.
                val currentKey = resolveCurrentKey(capturedListContext, capturedFilter)
                syncOtherLists(server, currentKey)

                // Step 5: Refresh once more now the whole sync is done. Step 3 only sees what
                // the current view's own pass fetched; a bookmark this view has never seen is
                // inserted by whichever pass returns it, which for a smart list is usually one
                // of the passes above. Without this they stay invisible — and uncounted by the
                // "N new" pill — until the user navigates away and back.
                if (effectiveFilterNow() == capturedEffectiveFilter) {
                    refreshLoadedPagesInPlace(server, capturedEffectiveFilter)
                }

                // Record completion so the startup auto-sync is throttled next time (#276).
                bookmarkRepository.markAutoSyncCompleted(server.id)
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

    /**
     * Dispatches the correct repository call for the currently-viewed list or filter.
     *
     * `isCurrentView` exempts this pass from the reading-progress ration, which is per server:
     * a list opened moments after another list's pass took the slot would otherwise skip its
     * own, leaving the one view the user is actually looking at as the place the counts stay
     * stale until scrolling fetched them row by row.
     */
    private suspend fun syncCurrentView(
        server: com.karakept.app.data.model.Server,
        listContext: String?,
        filter: FilterConfig
    ) {
        when {
            listContext != null ->
                bookmarkRepository.syncBookmarksForList(server, listContext, isCurrentView = true)
            filter.status == FilterStatus.FAVORITES ->
                bookmarkRepository.syncFavorites(server, isCurrentView = true)
            filter.status == FilterStatus.ARCHIVED ->
                bookmarkRepository.syncArchived(server, isCurrentView = true)
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
        // Bounded, like BookmarkRepository.syncAllWithLists. Unbounded fan-out here put one
        // paginated fetch per list on the wire at once, starving the list the user is
        // actually looking at (and its content downloads) of connections.
        val semaphore = kotlinx.coroutines.sync.Semaphore(3)
        val allLists = listRepository.lists.value
        AppLogger.d(
            "MainScreenModel",
            "syncOtherLists: ${allLists.size} known list(s), skipping key=$skipKey"
        )
        coroutineScope {
            allLists.forEach { list ->
                val key = list.id ?: return@forEach
                if (key == skipKey) return@forEach
                launch {
                    semaphore.acquire()
                    try {
                        bookmarkRepository.syncBookmarksForList(server, key)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.w("MainScreenModel", "Other-list sync failed for list $key: ${e.message}")
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
    }

    // Filter management

    fun applyFilter(filter: FilterConfig) {
        AppLogger.d("MainScreenModel", "applyFilter: status=${filter.status} lists=${filter.lists} tags=${filter.tags}")
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
        AppLogger.d("MainScreenModel", "clearFilter")
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

    // tryEmit rather than launch { emit }: the trigger is consumed alongside a list swap, and
    // an extra coroutine hop is enough to push the scroll reset a frame past it. The flow has
    // spare buffer capacity, so the emission always lands.
    fun scrollToTop() {
        _scrollToTopTrigger.tryEmit(Unit)
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

/**
 * Rows per DB read for the bookmark list.
 *
 * The paged query selects `'' as content` (see `BookmarkRepository.BOOKMARK_SELECT`), so a row
 * carries metadata rather than an article body and a larger page costs little. What it buys is a
 * shorter walk: the forward search steps a page at a time until something survives the
 * client-side filters, so a view whose filter admits few rows — an unread filter over a mostly
 * read feed — reads the table this many rows at a time. A 1400-row list took 66 steps at 20 and
 * takes 27 here.
 *
 * Tests derive their fixtures from this rather than restating it, so tuning it stays a one-line
 * change instead of a sweep through every pagination fixture.
 */
internal const val PAGE_SIZE = 50

/**
 * Number of bookmarks sitting above [seenTopRemoteId] in [bookmarks] — the "N new" pill count.
 *
 * Zero when nothing has been marked seen yet, or when the seen bookmark is no longer in the
 * list: it left the loaded window, so the rows above it are no longer meaningfully "new" and
 * guessing a count from a missing anchor is what a diff-based counter got wrong.
 *
 * @param excludeRead leaves already-read rows out of the count, for when the list fades them.
 *   The rows above the anchor still *are* new to the window — they are simply not worth
 *   offering a trip to the top for.
 */
internal fun countBookmarksAbove(
    bookmarks: List<BookmarkEntity>,
    seenTopRemoteId: Long?,
    excludeRead: Boolean = false
): Int {
    if (seenTopRemoteId == null) return 0
    val above = bookmarks.indexOfFirst { it.remoteId == seenTopRemoteId }
    if (above <= 0) return 0
    if (!excludeRead) return above
    return bookmarks.asSequence().take(above).count { !it.isRead }
}
