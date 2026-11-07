package com.karakept.app.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.api.model.KarakeepList as KarakeepList
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.BookmarkCursor
import com.karakept.app.data.model.BookmarkViewKey
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.BookmarkWindow
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.data.model.SYNC_KEY_ARCHIVED
import com.karakept.app.data.model.SYNC_KEY_FAVORITES
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncKey
import com.karakept.app.data.remote.hasHttpStatus
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.ServerVersionRepository
import com.karakept.app.data.repository.discardFailedActions
import com.karakept.app.data.repository.retryFailedActions
import com.karakept.app.data.repository.setDefaultListId
import com.karakept.app.data.repository.setDefaultListType
import com.karakept.app.domain.DefaultFilterResolver
import com.karakept.app.domain.ListCountUtils
import com.karakept.app.domain.ListHierarchyUtils
import com.karakept.app.domain.ServerCompatibility
import com.karakept.app.domain.ServerVersionUtils
import com.karakept.app.domain.TagCountUtils
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.AiAction
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.TagFilterRequests
import com.karakept.app.ui.components.scrollToTop
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    // The derived counts below are the one place this model does real work over the whole table,
    // and viewModelScope is the main dispatcher — so they need somewhere else to run. Injected
    // rather than taken statically so a test's dispatcher stays in control of them.
    private val appDispatchers: com.karakept.app.utils.AppDispatchers,
    // No default: the reader posts to the Koin single, so a model built with its own instance
    // never hears a tapped tag — which is what the DI binding silently did while this had one.
    private val tagFilterRequests: TagFilterRequests,
    // Nullable so a test can construct the model without the startup version check it never uses.
    private val serverVersionRepository: ServerVersionRepository? = null
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

    internal val _tagFilterSourceBookmarkId = MutableStateFlow<Long?>(null)
    val tagFilterSourceBookmarkId: StateFlow<Long?> = _tagFilterSourceBookmarkId

    val lists: StateFlow<List<KarakeepList>> = listRepository.lists

    internal val _expandedLists = MutableStateFlow<Set<String>>(emptySet())
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

    // The view the accumulated window was loaded for. Every write into the window is rejected
    // unless this still matches the view on screen: the requested view flips synchronously when
    // the user taps a list while the reload it triggers runs on an observer coroutine, so
    // without this a page fetched for the new list lands on top of the old list's items.
    internal val _loadedView = MutableStateFlow<LoadedView?>(null)

    /** The view the UI is currently asking for, or null while no server is selected. */
    internal fun currentView(): LoadedView? =
        _selectedServer.value?.let { LoadedView(it.id, effectiveFilterNow()) }

    internal val _bookmarkListVersion = MutableStateFlow(0)
    val bookmarkListVersion: StateFlow<Int> = _bookmarkListVersion

    private val pager = BookmarkListPager(
        bookmarkRepository = bookmarkRepository,
        selectedServer = _selectedServer,
        effectiveFilter = effectiveFilter,
        effectiveFilterNow = { effectiveFilterNow() },
        listVersion = _bookmarkListVersion,
        pageSize = pageSize
    )

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

    internal val _pendingBookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val pendingBookmarkRemoteIds: StateFlow<Set<String>> = _pendingBookmarks
        .map { list -> list.map { it.remoteId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    internal val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Multi-select state
    internal val _selectedBookmarkIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedBookmarkIds: StateFlow<Set<String>> = _selectedBookmarkIds
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

    /** Tag sets and their row counts, for the tag picker and the drawer's top tags. */
    private val tagGroups = selectedServer
        .flatMapLatest { server ->
            if (server != null) bookmarkRepository.tagGroups(server.id) else flowOf(emptyList())
        }

    /** Every tag in use, for the tag editor's suggestions. */
    val allAvailableTags: StateFlow<List<String>> = tagGroups
        .map { TagCountUtils.allTags(it) }
        .flowOn(appDispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The drawer's most-used tags, with whatever the filter is currently on kept visible. */
    val topTagsWithCounts: StateFlow<List<String>> =
        combine(tagGroups, _currentFilter) { groups, filter ->
            TagCountUtils.topTagsWithCounts(groups, filter.tags)
        }
            .flowOn(appDispatchers.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Rows collapsed to what the drawer's counts need: memberships and read state. */
    private val listMembershipGroups = selectedServer
        .flatMapLatest { server ->
            if (server != null) bookmarkRepository.listMembershipGroups(server.id)
            else flowOf(emptyList())
        }

    /**
     * Bookmark counts per list, for the drawer.
     *
     * Counted from buckets the database groups, not from the rows. It used to walk the whole
     * table on every database write — roughly once a second during a scroll — which cost 25-57ms
     * a time on the main thread, two to four dropped frames each. Off the main thread still,
     * because the bucketing is the model's own work.
     */
    val listCounts: StateFlow<Map<String, Int>> = combine(
        listMembershipGroups,
        lists,
        settingsRepository.allListSettings
    ) { groups, listItems, allSettings ->
        ListCountUtils.countBookmarksPerList(listItems, groups, allSettings)
    }
        .flowOn(appDispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

    /**
     * The drawer's quick-filter counts, read in one pass over the table rather than counted over
     * every row of it in memory.
     */
    val quickFilterCounts: StateFlow<QuickFilterCounts> = selectedServer
        .flatMapLatest { server ->
            if (server == null) flowOf(QuickFilterCounts())
            else combine(
                bookmarkRepository.quickFilterCounts(server.id),
                offlineBookmarkCount
            ) { counts, offline ->
                QuickFilterCounts(
                    all = counts.all_,
                    favorites = counts.favorites,
                    archived = counts.archived,
                    offline = offline
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QuickFilterCounts())

    /** Names the row at [index] in the view on screen, for the fast-scroll cursor's tooltip. */
    suspend fun bookmarkAtIndex(index: Int): Bookmark? = pager.bookmarkAtIndex(index)

    /**
     * How many bookmarks the current view holds — the list's total, and the denominator the
     * scroll cursor maps over.
     *
     * Asked of the database, from the same `WHERE` that selects the view's rows
     * ([BookmarkViewQueries.buildViewPredicate]), so the count and the rows it counts cannot
     * disagree: a thumb at the end of the track points at a row the query will return.
     *
     * It used to be the size of [filteredBookmarks] — the whole view rebuilt in memory on every
     * database write, to learn one number. It also had to special-case `FilterStatus.OFFLINE`,
     * whose real predicate reads a `content` column the in-memory rows do not carry; the query
     * reads the column it means, so that exception is gone.
     */
    val filteredBookmarkCount: StateFlow<Int> =
        combine(selectedServer, effectiveFilter) { server, filter -> server to filter }
            .flatMapLatest { (server, filter) ->
                if (server == null) flowOf(0)
                else bookmarkRepository.countBookmarksForViewFlow(server, filter)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

    /** Reports the slots on screen. Cheap to call often — only a page crossing reads anything. */
    fun reportVisibleSlots(range: IntRange) {
        pager.reportVisibleSlots(range)
        // Scrolling up through what a sync brought in retires it; scrolling back down does not
        // put it back, so the mark only ever falls.
        if (range.first < _topSlotLowWater.value) _topSlotLowWater.value = range.first
    }

    /** The view as the list addresses it — see [BookmarkListPager.window]. */
    val bookmarkWindow: StateFlow<BookmarkWindow> = _searchQuery
        .flatMapLatest { query -> pager.window(query) }
        .combine(_pendingBookmarks) { window, pending ->
            // A bookmark being created is on screen before it is in the database, so the view's
            // total does not count it and it takes the slots above the view.
            if (pending.isEmpty()) window else window.copy(prepended = pending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, BookmarkWindow.EMPTY)

    /**
     * The rows the list currently has, for the callers that want rows rather than slots — the
     * reader's next/previous, the expanded layout's selected bookmark, a range selection.
     *
     * A view of [bookmarkWindow], so there is one answer to what the list is showing. It used to
     * be the other way round.
     */
    val bookmarks: StateFlow<List<Bookmark>> = bookmarkWindow
        .map { it.loadedRows() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // How many rows a sync put above where the user was standing. Set when a sync finishes and
    // at no other time — the pill is a report about a sync, so scrolling must not raise it.
    internal val _syncedAbove = MutableStateFlow(0)

    // The highest the viewport has reached since that sync, as an absolute slot. Scrolling up
    // through the arrivals retires them; scrolling back down leaves the count alone, which is
    // what "low water" means here.
    internal val _topSlotLowWater = MutableStateFlow(Int.MAX_VALUE)

    /**
     * How many bookmarks a sync brought in above the user — the "N new" pill.
     *
     * Two numbers, and the smaller wins. [_syncedAbove] is what the database said had landed
     * above the row the user was on when the sync started; [_topSlotLowWater] is how far up they
     * have scrolled since. Sitting at slot 3 with ten new rows above means seven are still
     * unseen, and reaching slot 0 means none are.
     *
     * Both are positions in the *view*, so neither depends on which rows happen to be loaded.
     * The count used to be taken by walking the loaded rows for an anchor row's id, which the
     * virtualized list broke: pages the user scrolls away from are dropped, so the anchor was
     * usually not among the rows walked, and the walk then moved the anchor down to whatever was
     * on screen. Every scroll down quietly re-armed the pill, and scrolling back up raised it
     * with no sync anywhere in sight.
     *
     * Read bookmarks are left out of the count while they are being faded: the pill offers a
     * trip to what arrived, and a row already read — marked on another device, or carried in by
     * the reading progress the sync pulls — is not something the user is being sent back for.
     */
    val newBookmarksAbove: StateFlow<Int> =
        combine(_syncedAbove, _topSlotLowWater) { above, lowWater ->
            minOf(above, lowWater).coerceAtLeast(0)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private fun requestedView(): BookmarkViewKey? =
        _selectedServer.value?.let { BookmarkViewKey(it.id, effectiveFilterNow()) }

    /** Empties the pill. Called when the user taps it to jump to what arrived. */
    fun clearNewBookmarksAbove() {
        resetNewBookmarksAbove()
    }

    /** Forgets what a previous sync reported — a different view is a different question. */
    internal fun resetNewBookmarksAbove() {
        _syncedAbove.value = 0
        _topSlotLowWater.value = Int.MAX_VALUE
    }

    // RemoteIds of bookmarks on which the user has explicitly performed a list-membership
    // action (add/remove list). Prevents the scroll-triggered action from auto-firing on a
    // bookmark that is about to leave the list via async reconciliation — the reconcile
    // involves network calls so the window can be several seconds long.
    // Cleared on every full list reload (resetPaginationAndLoad).
    internal val _actedOnBookmarkIds = MutableStateFlow<Set<String>>(emptySet())
    val actedOnBookmarkIds: StateFlow<Set<String>> = _actedOnBookmarkIds

    // Hoisted scroll position — survives pushing another screen on top, because Nav3 keeps this
    // entry's ViewModelStore (and so this instance) for as long as the entry is on the back stack.
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
        bookmarkWindow
            .map { !it.resolved }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
                    switchToView(currentServer, filter)
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

        // Warn once per process when the selected server is older than the features in this
        // build need. Non-blocking: everything keeps working that the server does support.
        if (serverVersionRepository != null) {
            viewModelScope.launch {
                _selectedServer.filterNotNull().distinctUntilChanged { a, b -> a.id == b.id }
                    .collect { server ->
                        val check = serverVersionRepository.refresh(server) ?: return@collect
                        if (check.compatibility == ServerCompatibility.OUTDATED &&
                            serverVersionRepository.claimOutdatedWarning(server.id)
                        ) {
                            snackbarManager.showSnackbar(
                                ServerVersionUtils.outdatedWarning(check),
                                androidx.compose.material3.SnackbarDuration.Long
                            )
                        }
                    }
            }
        }

        // The "N new" pill reports on a sync, so a sync is the only thing that raises it.
        //
        // Where the user is standing is snapshotted as a cursor when the sync starts — a cursor
        // rather than a row id, so a sync that deletes that row still leaves a position the
        // count means something against. When it finishes, the database says how many rows of
        // the view now sort above it.
        //
        // The cursor is only ever taken from rows read for the view on screen. Right after a
        // switch the window still holds the previous view's rows, and a sync starting then (the
        // new view's own, or one already running for it) would otherwise be anchored on a row
        // of the list the user just left — counting that list's arrivals into this one.
        viewModelScope.launch {
            var anchor: BookmarkCursor? = null
            var anchorView: BookmarkViewKey? = null
            var aboveAtStart = 0
            var excludeRead = false
            suspend fun countAbove(view: BookmarkViewKey, cursor: BookmarkCursor): Int? {
                val server = _selectedServer.value?.takeIf { it.id == view.serverId } ?: return null
                return runCatching {
                    bookmarkRepository.countBookmarksBefore(server, view.filter, cursor, excludeRead)
                }.getOrNull()
            }
            combine(isSyncing, bookmarkWindow) { syncing, window -> syncing to window }
                .collect { (syncing, window) ->
                    val view = requestedView()
                    if (syncing) {
                        if (anchorView != view) {
                            anchor = null
                            anchorView = null
                        }
                        if (anchor == null && view != null && window.resolved && window.view == view) {
                            val cursor = pager.topVisibleCursor(window) ?: return@collect
                            excludeRead = effectiveDimReadBookmarks.value
                            aboveAtStart = countAbove(view, cursor) ?: return@collect
                            anchor = cursor
                            anchorView = view
                        }
                        return@collect
                    }
                    val startedAt = anchor ?: return@collect
                    val startedIn = anchorView ?: return@collect
                    anchor = null
                    anchorView = null
                    // isSyncing follows the view's sync key, so switching view drops it too:
                    // that is the user leaving, not the sync finishing.
                    if (view != startedIn) return@collect
                    // What the sync added, not what sits above the anchor: rows that were there
                    // before it started are not new, wherever the anchor happened to land.
                    val aboveAtEnd = countAbove(startedIn, startedAt) ?: return@collect
                    if (requestedView() != startedIn) return@collect
                    _syncedAbove.value = (aboveAtEnd - aboveAtStart).coerceAtLeast(0)
                    // Whatever is on screen now has been seen, so a sync landing while the user
                    // sits at the top raises nothing.
                    _topSlotLowWater.value = pager.visibleSlots.value.first
                }
        }

        // A bookmark mutated by another screen, and a bookmark restored by undo, both reach
        // the list the same way: whoever changed it wrote the row, and the pages on screen are
        // read again. Patching the list here was a second answer to that, and the one that had
        // to guess where a restored row belonged — it re-inserted at a remembered index, while
        // the query puts it where the sort does.
    }

    /**
     * Switches the list to [filter]'s view.
     *
     * Nothing is loaded here. The window reads whatever view the filter names, so the switch is
     * already in flight by the time this runs; what is left is the part the window does not know
     * about — telling the list this is a different view rather than the same one changed, so it
     * scrolls to the top and drops the anchors belonging to the view being left.
     */
    private fun switchToView(server: Server, filter: FilterConfig) {
        val leaving = _loadedView.value
        _loadedView.value = LoadedView(server.id, filter)
        _actedOnBookmarkIds.value = emptySet()
        // A different view is a different question, so whatever a previous sync reported about
        // the view being left does not carry over.
        resetNewBookmarksAbove()
        // The slots on screen belong to the view being left until the list reports again from
        // the top of this one; read against this view they name rows the user never saw.
        if (leaving != null) pager.reportVisibleSlots(0..0)
        // Announce the reload *before* the rows arrive. The version bump makes the scroll anchor
        // drop its anchor and the scroll request re-pins the viewport to the top; both are
        // applied on the next measure, which is the one that renders the new view.
        _bookmarkListVersion.value++
        scrollToTop()
    }

    // Sync — see MainScreenModelSync.kt
    // Filters, search and navigation — see MainScreenModelFilters.kt
    // Bookmark actions — see MainScreenModelActions.kt
    // Multi-select & batch operations — see MainScreenModelBatch.kt

    // Index of the last item that was clicked or selected (for Shift+Click range selection).
    // Tracked even outside selection mode so Shift+Click can use it as a range anchor.
    // Kept in the class because extension functions cannot hold mutable state.
    internal var _lastSelectedIndex: Int = -1
}

/** Rows to a page. One value, so a slot's index and its page agree everywhere. */
internal const val PAGE_SIZE = BookmarkWindow.DEFAULT_PAGE_SIZE

