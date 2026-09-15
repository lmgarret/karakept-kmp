package com.karakept.app.ui.screens

import androidx.lifecycle.ViewModel
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.PerfTrace
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.BookmarkCursor
import com.karakept.app.data.model.BookmarkWindow
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
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
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
import com.karakept.app.domain.ListCountUtils
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
    // The derived counts below are the one place this model does real work over the whole table,
    // and viewModelScope is the main dispatcher — so they need somewhere else to run. Injected
    // rather than taken statically so a test's dispatcher stays in control of them.
    private val appDispatchers: com.karakept.app.utils.AppDispatchers,
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

    internal val _pendingBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
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

    // All bookmarks without filtering — for tag extraction and list counts.
    // Uses SharingStarted.WhileSubscribed so Room observers are released when
    // no collectors are active (e.g. app in background).
    val allBookmarks = selectedServer
        .flatMapLatest { server ->
            if (server != null) bookmarkRepository.getBookmarks(server) else flowOf(emptyList())
        }
        .onEach { PerfTrace.count("allBookmarks.emit", "rows=${it.size}") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Bookmark counts per list, for the drawer.
     *
     * Runs off the main thread: it walks the whole table, and every database write re-emits
     * [allBookmarks] — during a scroll that is roughly once a second, and on the UI thread it
     * cost 25-57ms a time, which is two to four dropped frames each.
     */
    val listCounts: StateFlow<Map<String, Int>> = combine(
        selectedServer,
        lists,
        allBookmarks,
        settingsRepository.allListSettings
    ) { server, listItems, bookmarks, allSettings ->
        if (server == null) return@combine emptyMap()
        PerfTrace.measure("listCounts", "lists=${listItems.size} rows=${bookmarks.size}") {
            ListCountUtils.countBookmarksPerList(listItems, bookmarks, allSettings)
        }
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

    val quickFilterCounts: StateFlow<QuickFilterCounts> = combine(
        selectedServer, allBookmarks, offlineBookmarkCount
    ) { server, bookmarks, offline ->
        if (server == null) return@combine QuickFilterCounts()
        PerfTrace.measure("quickFilterCounts", "rows=${bookmarks.size}") {
            QuickFilterCounts(
                all = bookmarks.count { !it.isArchived },
                favorites = bookmarks.count { it.isStarred && !it.isArchived },
                archived = bookmarks.count { it.isArchived },
                offline = offline
            )
        }
    }
        .flowOn(appDispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QuickFilterCounts())

    /**
     * Every bookmark the current view holds, in the order the paged query returns them — the list
     * the loaded window is a prefix of.
     *
     * The fast-scroll cursor needs to name the row at an absolute position the moment the thumb
     * reaches it, and the window cannot: the row is usually hundreds of pages past what has been
     * read, so the tooltip fell back to naming the last row it *had*, which trails the thumb and
     * ticks forward as reads land. These rows are already resident — [allBookmarks] is read for
     * the counts regardless — so the answer costs a filter and a sort rather than a query.
     *
     * Off the main thread, for the same reason the counts are (#273).
     */
    val filteredBookmarks: StateFlow<List<BookmarkEntity>> = combine(
        allBookmarks, effectiveFilter
    ) { all, filter ->
        PerfTrace.measure("filteredBookmarks", "rows=${all.size}") {
            BookmarkFilterUtils.orderedViewFor(all, filter)
        }
    }
        .flowOn(appDispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * How many bookmarks the current view holds — the list's total, and the denominator the
     * scroll cursor maps over.
     *
     * Asked of the database, from the same `WHERE` that selects the view's rows
     * ([BookmarkRepository.buildViewPredicate]), so the count and the rows it counts cannot
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

    // Which slots the list is showing, reported by it so their pages can be read. The list is
    // sized by the view, so this is a position in the view and not in what has been loaded.
    private val _visibleSlots = MutableStateFlow(0..0)

    /** Reports the slots on screen. Cheap to call often — only a page crossing reads anything. */
    fun reportVisibleSlots(range: IntRange) {
        _visibleSlots.value = range
    }

    /**
     * How many pages either side of the visible span to keep loaded, so an ordinary scroll
     * reaches rows that are already there rather than placeholders.
     */
    private val pageMargin = 1

    /**
     * The view as the list addresses it: every slot it holds, with the pages under the viewport
     * read and the rest left as placeholders.
     *
     * Sized by a `COUNT(*)` rather than by what has been read, so an index means a position in
     * the view from the first frame. A jump therefore needs no read to *arrive* — the slot is
     * already there — and the page under it is fetched by its offset while the list sits where it
     * was put. Nothing walks, so there is no accumulated position to drift (#333) and no window
     * to grow: the rows on screen are re-read from their offsets, and rows scrolled away from are
     * dropped rather than carried.
     *
     * Two things drive it, and both have to. The count is re-emitted by Room on every write to
     * the table, which is also what makes a row edited elsewhere — or a page a sync has just
     * committed — reappear correctly here: the pages on screen are read again. The visible span
     * only asks for anything when it crosses a page boundary.
     */
    val bookmarkWindow: StateFlow<BookmarkWindow> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) pagedWindow() else searchWindow(query)
        }
        .combine(_pendingBookmarks) { window, pending ->
            // A bookmark being created is on screen before it is in the database, so the view's
            // total does not count it and it takes the slots above the view.
            if (pending.isEmpty()) window else window.copy(prepended = pending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, BookmarkWindow.EMPTY)

    private fun pagedWindow(): Flow<BookmarkWindow> =
        combine(_selectedServer, effectiveFilter) { server, filter -> server?.let { it to filter } }
            .flatMapLatest { request ->
                if (request == null) return@flatMapLatest flowOf(BookmarkWindow.EMPTY)
                val (server, filter) = request
                // Bucketed to pages before de-duplicating: scrolling within a page asks for
                // nothing, and only crossing into a new one does. The count is deliberately not
                // de-duplicated — a write that edits a row without changing how many there are
                // still has to re-read the rows on screen.
                val visiblePages = _visibleSlots
                    .map { (it.first / pageSize)..(it.last / pageSize) }
                    .distinctUntilChanged()

                bookmarkRepository.countBookmarksForViewFlow(server, filter)
                    .combine(visiblePages) { total, pages -> total to pages }
                    .mapLatest { (total, pages) -> readWindow(server, filter, total, pages) }
                    .catch { e ->
                        AppLogger.e("MainScreenModel", "Failed to read bookmarks: ${e.message}", e)
                        emit(BookmarkWindow.EMPTY)
                    }
            }
            .combine(_bookmarkListVersion) { window, version -> window.copy(generation = version) }

    /** Reads the pages [pageSpan] touches, plus [pageMargin] either side. */
    private suspend fun readWindow(
        server: Server,
        filter: FilterConfig,
        total: Int,
        pageSpan: IntRange
    ): BookmarkWindow {
        val shape = BookmarkWindow(viewTotal = total, pageSize = pageSize)
        if (total <= 0) return shape
        val lastPage = (total - 1) / pageSize
        val first = (pageSpan.first - pageMargin).coerceIn(0, lastPage)
        val last = (pageSpan.last + pageMargin).coerceIn(0, lastPage)
        val pages = PerfTrace.measureSuspending("window.readPages", "pages=${last - first + 1}") {
            (first..last).associateWith { page ->
                bookmarkRepository.getBookmarkPage(server, filter, page * pageSize, pageSize)
            }
        }
        return shape.copy(pages = pages)
    }

    /**
     * Search results, which are not a paged view: the query is matched in memory over the whole
     * table, so every row it admits is on hand at once.
     */
    private fun searchWindow(query: String): Flow<BookmarkWindow> =
        combine(allBookmarks, effectiveFilter) { all, filter ->
            val matches = PerfTrace.measure("applySearchFilter", "rows=${all.size}") {
                BookmarkFilterUtils.applySearchFilter(all, filter, query)
            }
            BookmarkWindow.dense(matches, pageSize = pageSize)
        }

    /**
     * The rows the list currently has, for the callers that want rows rather than slots — the
     * reader's next/previous, the expanded layout's selected bookmark, a range selection.
     *
     * A view of [bookmarkWindow], so there is one answer to what the list is showing. It used to
     * be the other way round.
     */
    val bookmarks: StateFlow<List<BookmarkEntity>> = bookmarkWindow
        .map { it.loadedRows() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // The topmost bookmark the user has actually seen. Everything above it arrived since.
    internal val _seenTopRemoteId = MutableStateFlow<String?>(null)

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
     * Counted against the rows the list has actually read. A row the view holds but has not
     * loaded cannot be above the anchor without a loaded row above it too, so counting what is
     * on hand answers the same question — and a bookmark the user just created, prepended and
     * already scrolled to, is not reported back to them as new.
     *
     * Read bookmarks are left out while they are being faded: the pill offers to take the user
     * to what arrived, and a row already read — marked on another device, or carried in by the
     * reading progress the sync pulls — is not something they are being sent back for. With
     * fading off, read and unread rows look alike and the count covers both.
     */
    val newBookmarksAbove: StateFlow<Int> =
        combine(
            bookmarkWindow,
            _seenTopRemoteId,
            effectiveDimReadBookmarks
        ) { window, seenTop, dimRead ->
            countBookmarksAbove(window.loadedRows(), seenTop, excludeRead = dimRead)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * Marks the row currently at the top as seen, which is what empties the pill. Called when
     * the user taps the pill to jump there.
     */
    fun clearNewBookmarksAbove() {
        _seenTopRemoteId.value = bookmarkWindow.value.bookmarkAt(0)?.remoteId
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
    fun markTopVisibleSeen(remoteId: String) {
        val anchor = _seenTopRemoteId.value
        if (anchor == remoteId) return
        // Which of the two comes first is the whole question, so one pass that stops at
        // whichever it meets answers it. Scrolling down would otherwise scan to the row now on
        // top — further with every row — only to reject the update.
        for (bookmark in bookmarkWindow.value.loadedRows()) {
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
    internal val _actedOnBookmarkIds = MutableStateFlow<Set<String>>(emptySet())
    val actedOnBookmarkIds: StateFlow<Set<String>> = _actedOnBookmarkIds

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
        combine(_initState, bookmarkWindow) { init, window ->
            window.isEmpty && init != InitState.Ready
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

        // A freshly loaded view counts as seen, so the pill stays empty until a later write puts
        // something above it. Anchoring on the first rows to arrive rather than on the list
        // reporting itself at the top keeps the count right when the view opens somewhere else —
        // a restored scroll position, or a reload that deliberately holds its place.
        viewModelScope.launch {
            bookmarkWindow.collect { window ->
                if (_seenTopRemoteId.value == null) {
                    _seenTopRemoteId.value = window.bookmarkAt(0)?.remoteId
                }
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
        _loadedView.value = LoadedView(server.id, filter)
        _actedOnBookmarkIds.value = emptySet()
        // A fresh view has not been seen, so the pill starts empty and re-anchors once the list
        // renders at the top.
        _seenTopRemoteId.value = null
        // Announce the reload *before* the rows arrive. The version bump makes the scroll anchor
        // drop its anchor and the scroll request re-pins the viewport to the top; both are
        // applied on the next measure, which is the one that renders the new view.
        _bookmarkListVersion.value++
        scrollToTop()
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
    fun onBookmarksVisible(remoteIds: List<String>) {
        val server = _selectedServer.value ?: return
        if (remoteIds.isEmpty()) return
        PerfTrace.count("visibleRows.progressPull", "rows=${remoteIds.size}")
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

                // Step 3: Sync all other named lists concurrently.
                // Each call is independently deduplicated by BookmarkRepository.
                val currentKey = resolveCurrentKey(capturedListContext, capturedFilter)
                syncOtherLists(server, currentKey)

                // Nothing to refresh at either step: every pass writes the rows it fetched,
                // and the list re-reads the pages on screen when the table changes. A bookmark
                // this view has never seen appears as soon as whichever pass returns it commits
                // it, rather than at the next refresh call someone remembered to make.

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
    seenTopRemoteId: String?,
    excludeRead: Boolean = false
): Int {
    if (seenTopRemoteId == null) return 0
    val above = bookmarks.indexOfFirst { it.remoteId == seenTopRemoteId }
    if (above <= 0) return 0
    if (!excludeRead) return above
    return bookmarks.asSequence().take(above).count { !it.isRead }
}
