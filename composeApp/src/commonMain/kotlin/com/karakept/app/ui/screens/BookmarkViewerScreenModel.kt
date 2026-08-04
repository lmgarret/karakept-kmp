package com.karakept.app.ui.screens

import androidx.compose.ui.graphics.Color
import com.karakept.app.utils.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ContentSource
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.api.model.KarakeepList as KarakeepList
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.pullReadingProgressFromServer
import com.karakept.app.data.repository.setViewerMode
import com.karakept.app.data.repository.setHtmlTextColor
import com.karakept.app.data.repository.setHtmlBackgroundColor
import com.karakept.app.data.repository.setHtmlFontSize
import com.karakept.app.data.repository.setHtmlFontFamily
import com.karakept.app.data.repository.resetReaderAppearance
import com.karakept.app.data.repository.setScrollToTopEnabled
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.karakept.app.data.remote.OfflineModeException
import com.karakept.app.data.remote.UnsupportedServerActionException
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.BookmarkActionEvent
import com.karakept.app.domain.action.ServerCrawlAction
import com.karakept.app.ui.utils.ParsedDocumentCache
import getPlatform

// Crawl jobs are queued server-side, so the result is polled rather than waited for once.
// Five attempts four seconds apart covers the ~20s a typical page takes without pinning the
// spinner indefinitely on a slow one.
private const val CRAWL_POLL_INTERVAL_MS = 4_000L
private const val CRAWL_POLL_ATTEMPTS = 5

class BookmarkViewerScreenModel(
    private val bookmarkDao: BookmarkDao,
    private val assetDao: AssetDao,
    private val settingsRepository: SettingsRepository,
    private val bookmarkActionsRepository: BookmarkActionsRepository,
    private val remoteDataSource: RemoteDataSource,
    private val serverRepository: ServerRepository,
    private val bookmarkRepository: com.karakept.app.data.repository.BookmarkRepository,
    private val bookmarkActionController: BookmarkActionController,
    private val highlightRepository: com.karakept.app.data.repository.HighlightRepository,
    private val snackbarManager: ActionSnackbarManager
) : ViewModel() {
    private val parsedDocumentCache = ParsedDocumentCache(maxSize = 5)

    private val _loadingState = MutableStateFlow<BookmarkLoadingState>(BookmarkLoadingState.Initial)
    val loadingState: StateFlow<BookmarkLoadingState> = _loadingState.asStateFlow()

    // Becomes true once the initial server-side reading-progress check has completed (or was
    // skipped because local progress is already > 0 or the app is offline).  The composable uses
    // this to avoid setting hasRestoredScroll=true before the async pull can update the DB.
    private val _serverProgressChecked = MutableStateFlow(false)
    val serverProgressChecked: StateFlow<Boolean> = _serverProgressChecked.asStateFlow()

    // Becomes true once the on-demand content fetch has been attempted (success or failure)
    // or is not needed (content already in DB / offline mode / no applicable strategy).
    // The composable uses this to avoid hiding the LazyColumn indefinitely when content
    // cannot be loaded (network error, no content on server, etc.).
    private val _contentFetchAttempted = MutableStateFlow(false)
    val contentFetchAttempted: StateFlow<Boolean> = _contentFetchAttempted.asStateFlow()

    val viewerMode: StateFlow<ViewerMode> = if (getPlatform().isDesktop) {
        // Desktop only supports READER mode (no WebView)
        MutableStateFlow(ViewerMode.READER)
    } else {
        settingsRepository.viewerMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewerMode.READER)
    }

    val hideArticleThumbnails: StateFlow<Boolean> = settingsRepository.hideArticleThumbnails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val htmlTextColor: StateFlow<Color?> = settingsRepository.htmlTextColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val htmlBackgroundColor: StateFlow<Color?> = settingsRepository.htmlBackgroundColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val htmlFontSize: StateFlow<Int> = settingsRepository.htmlFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16)

    val htmlFontFamily: StateFlow<ReaderFontFamily> = settingsRepository.htmlFontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderFontFamily.SYSTEM)

    val showTags: StateFlow<Boolean> = settingsRepository.showTagsInViewer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val scrollToTopEnabled: StateFlow<Boolean> = settingsRepository.scrollToTopEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dateDisplayMode: StateFlow<DateDisplayMode> = settingsRepository.dateDisplayMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DateDisplayMode.ELAPSED)

    val linkOpenMode: StateFlow<LinkOpenMode> = settingsRepository.linkOpenMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LinkOpenMode.CUSTOM_TAB)

    private val _precrawledAssetPath = MutableStateFlow<String?>(null)
    val precrawledAssetPath: StateFlow<String?> = _precrawledAssetPath.asStateFlow()

    // Session-only content source selection (not persisted to settings)
    private val _selectedSource = MutableStateFlow(ContentSource.EXTRACTED)
    val selectedSource: StateFlow<ContentSource> = _selectedSource.asStateFlow()

    // True when a full page archive exists on the server for this bookmark
    // (localPath may still be null — use precrawledAssetPath != null for "locally cached")
    private val _archiveAvailable = MutableStateFlow(false)
    val archiveAvailable: StateFlow<Boolean> = _archiveAvailable.asStateFlow()

    // Full list of asset entities for this bookmark (server metadata + local cache status)
    private val _assets = MutableStateFlow<List<com.karakept.app.data.local.entity.AssetEntity>>(emptyList())
    val assets: StateFlow<List<com.karakept.app.data.local.entity.AssetEntity>> = _assets.asStateFlow()

    // HTML string override for FULL_PAGE_ARCHIVE + READER mode (session-only)
    private val _sourceContentOverride = MutableStateFlow<String?>(null)
    val sourceContentOverride: StateFlow<String?> = _sourceContentOverride.asStateFlow()

    // True while archive is being downloaded for source switching
    private val _isLoadingSource = MutableStateFlow(false)
    val isLoadingSource: StateFlow<Boolean> = _isLoadingSource.asStateFlow()

    // Non-null while a server-side crawl request is being sent and re-synced
    private val _serverCrawlInFlight = MutableStateFlow<ServerCrawlAction?>(null)
    val serverCrawlInFlight: StateFlow<ServerCrawlAction?> = _serverCrawlInFlight.asStateFlow()

    // Assets currently downloading, keyed by asset id. The value is the 0f..1f progress
    // fraction, or null when the response has no Content-Length (indeterminate).
    private val _assetDownloads = MutableStateFlow<Map<String, Float?>>(emptyMap())
    val assetDownloads: StateFlow<Map<String, Float?>> = _assetDownloads.asStateFlow()

    private val _bannerImageLocalPath = MutableStateFlow<String?>(null)
    val bannerImageLocalPath: StateFlow<String?> = _bannerImageLocalPath.asStateFlow()

    private val _screenshotLocalPath = MutableStateFlow<String?>(null)
    val screenshotLocalPath: StateFlow<String?> = _screenshotLocalPath.asStateFlow()

    private val _lists = MutableStateFlow<List<KarakeepList>>(emptyList())
    val lists: StateFlow<List<KarakeepList>> = _lists.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _bookmarkId = MutableStateFlow<Long?>(null)
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val highlights: StateFlow<List<com.karakept.app.data.model.Highlight>> = _bookmarkId.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(emptyList())
        
        // We need the string remoteId to fetch highlights
        // We can get it from the bookmarkDao reactively
        bookmarkDao.observeBookmarkById(id).flatMapLatest { bookmark ->
            if (bookmark == null) flowOf(emptyList())
            else highlightRepository.getHighlightsForBookmark(bookmark.originalRemoteId, bookmark.serverId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineMode: StateFlow<Boolean> = settingsRepository.offlineMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val trackReadingProgress: StateFlow<Boolean> = settingsRepository.trackReadingProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // --- Reading progress persistence (flow-based debounce) ---

    private data class PendingReadingState(
        val localId: Long,
        val remoteId: Long,
        val progress: Float,
        val scrollIndex: Int,
        val scrollOffset: Int
    )

    @Volatile
    private var pendingReadingState: PendingReadingState? = null

    private val readingStateUpdates = MutableSharedFlow<PendingReadingState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        // When viewer mode switches to READER while FULL_PAGE_ARCHIVE is selected,
        // ensure the archive string is loaded for the native renderer.
        viewModelScope.launch {
            viewerMode.collect { mode ->
                if (_selectedSource.value == ContentSource.FULL_PAGE_ARCHIVE
                    && mode == ViewerMode.READER
                    && _sourceContentOverride.value == null
                ) {
                    val localPath = _precrawledAssetPath.value
                    if (localPath != null) loadArchiveContentString(localPath)
                }
            }
        }

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            readingStateUpdates
                .debounce(500)
                .collect { state ->
                    // Debounce fired — save to DB
                    bookmarkDao.updateReadingProgress(
                        state.localId, state.progress, state.scrollIndex, state.scrollOffset
                    )
                    bookmarkActionsRepository.notifyBookmarkChanged(state.remoteId)
                    pendingReadingState = null

                    // Queue reading progress sync to server (offline-first).
                    // The pending action will be pushed during the next sync cycle.
                    if (trackReadingProgress.value) {
                        val currentBookmark =
                            (_loadingState.value as? BookmarkLoadingState.FullyLoaded)?.bookmark
                        if (currentBookmark != null) {
                            // Queue server push for next sync cycle
                            bookmarkActionsRepository.queueReadingProgressUpdate(
                                bookmarkRemoteId = state.remoteId,
                                serverId = currentBookmark.serverId,
                                progressPercent = (state.progress * 100).toInt()
                            )
                        } else {
                            // No FullyLoaded bookmark — skip queue
                        }
                    } else {
                        // trackReadingProgress is disabled — skip queue
                    }
                }
        }
    }

    /**
     * Called by the composable on every meaningful scroll change.
     * Updates are debounced (500 ms) before writing to the database.
     * The latest state is also kept in memory so [onDispose] can persist
     * it if the user navigates away before the debounce window closes.
     */
    fun onReadingStateChanged(localId: Long, remoteId: Long, progress: Float, scrollIndex: Int, scrollOffset: Int) {
        // Update pending state and emit for debounced persistence
        val state = PendingReadingState(localId, remoteId, progress, scrollIndex, scrollOffset)
        pendingReadingState = state
        readingStateUpdates.tryEmit(state)
    }

    override fun onCleared() {
        flushOnDispose()
    }

    /**
     * Flushes pending reading progress and clears caches. Called automatically by [onCleared]
     * when this ViewModel's nav entry leaves, and manually by the expanded (inline) viewer
     * layout when the displayed bookmark changes without a nav-stack change.
     */
    fun flushOnDispose() {
        parsedDocumentCache.clear()
        val state = pendingReadingState ?: return
        val serverId = (_loadingState.value as? BookmarkLoadingState.FullyLoaded)?.bookmark?.serverId
        // viewModelScope is being cancelled here, so the repository owns this write: it is a
        // Koin single whose scope outlives the screen without leaking into GlobalScope.
        bookmarkActionsRepository.persistFinalReadingProgress(
            bookmarkLocalId = state.localId,
            bookmarkRemoteId = state.remoteId,
            serverId = serverId,
            progress = state.progress,
            scrollIndex = state.scrollIndex,
            scrollOffset = state.scrollOffset
        )
    }

    /**
     * Returns a cached parsed Document for the given bookmark, or parses [html]
     * and caches the result.  This avoids re-parsing on back-navigation when
     * the ScreenModel is still alive.
     */
    fun getCachedOrParseDocument(bookmarkId: Long, html: String): com.fleeksoft.ksoup.nodes.Document? {
        parsedDocumentCache.get(bookmarkId)?.let { return it }
        return try {
            com.fleeksoft.ksoup.Ksoup.parse(html).also {
                parsedDocumentCache.put(bookmarkId, it)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun refreshBookmark(id: Long) {
        val currentState = _loadingState.value
        if (currentState !is BookmarkLoadingState.FullyLoaded) {
            AppLogger.d("ViewerModel", "refreshBookmark called but state is not FullyLoaded")
            return
        }

        viewModelScope.launch {
            if (offlineMode.value) {
                AppLogger.d("ViewerModel", "refreshBookmark skipped - offline mode")
                return@launch
            }

            AppLogger.d("ViewerModel", "Starting refresh for bookmark ${currentState.bookmark.remoteId}")
            _isRefreshing.value = true
            try {
                // Sync bookmark content
                bookmarkRepository.syncSingleBookmark(
                    currentState.bookmark.remoteId,
                    currentState.bookmark.serverId
                )
                AppLogger.d("ViewerModel", "Bookmark content synced")

                // Also sync highlights for this bookmark
                val servers = serverRepository.servers.first()
                val server = servers.find { it.id == currentState.bookmark.serverId }
                if (server != null) {
                    val remoteId = currentState.bookmark.originalRemoteId ?: currentState.bookmark.remoteId.toString()
                    AppLogger.d("ViewerModel", "Syncing highlights for remoteId=$remoteId")
                    highlightRepository.syncHighlightsForBookmark(server, remoteId)
                    AppLogger.d("ViewerModel", "Highlights synced")

                    // Also pull latest reading progress from server
                    if (trackReadingProgress.value) {
                        bookmarkActionsRepository.pullReadingProgressFromServer(
                            currentState.bookmark.remoteId, currentState.bookmark.serverId
                        )
                    }
                } else {
                    AppLogger.w("ViewerModel", "Server not found for serverId=${currentState.bookmark.serverId}")
                }
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to load bookmark content: ${e.message}", e)
                snackbarManager.showErrorWithRetry("Couldn't refresh bookmark") {
                    refreshBookmark(id)
                }
            } finally {
                _isRefreshing.value = false
                AppLogger.d("ViewerModel", "Refresh complete")
            }
        }
    }

    fun loadBookmark(id: Long) {
        _bookmarkId.value = id
        viewModelScope.launch {
            try {
                var hasLoadedOnce = false
                // Cache for transient content (not persisted to DB). Without this cache,
                // every DB update (e.g. reading-progress saves) would re-emit a bookmark
                // with null content, causing the screen model to re-fetch the content and
                // reset the WebView — making scrolling hectic.
                var transientContent: String? = null
                // Observe bookmark changes from DB reactively
                bookmarkDao.observeBookmarkById(id).collect { bookmark ->
                    if (bookmark != null) {
                        if (!hasLoadedOnce) {
                            // Trigger on-demand sync for highlights
                            viewModelScope.launch {
                                try {
                                    val servers = serverRepository.servers.first()
                                    val server = servers.find { it.id == bookmark.serverId }
                                    if (server != null) {
                                        highlightRepository.syncHighlightsForBookmark(server, bookmark.originalRemoteId ?: bookmark.remoteId.toString())
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("ViewerModel", "Failed to sync highlights: ${e.message}", e)
                                    snackbarManager.showSnackbar("Couldn't sync highlights")
                                }
                            }
                            // Pull reading progress from server (cross-device sync).
                            // Always check server for the latest progress — if the server
                            // has a higher value (read further on another device), apply it.
                            // Signal serverProgressChecked=true only AFTER the pull completes so
                            // the composable doesn't finalize hasRestoredScroll before the DB is
                            // updated with the server value.
                            if (!settingsRepository.offlineMode.first()) {
                                // Pull reading progress from server for cross-device sync
                                viewModelScope.launch {
                                    val updated = bookmarkActionsRepository.pullReadingProgressFromServer(
                                        bookmark.remoteId, bookmark.serverId
                                    )
                                    // If server had newer progress, yield for DB propagation
                                    // If the server had newer progress, yield once so the DB
                                    // update can propagate through observeBookmarkById and
                                    // update loadingState before we signal serverProgressChecked.
                                    // This prevents a race where the UI sees serverProgressChecked=true
                                    // but loadingState still holds the old 0% progress.
                                    if (updated) kotlinx.coroutines.yield()
                                    _serverProgressChecked.value = true
                                }
                            } else {
                                // Offline mode — skip server pull
                                _serverProgressChecked.value = true
                            }
                        }
                        hasLoadedOnce = true

                        // Check for content availability
                        if (bookmark.content.isNullOrBlank()) {
                            // If we already fetched transient content this session, reuse it
                            // to avoid resetting the WebView on every DB change (e.g. reading-
                            // progress saves). The updated bookmark metadata (scroll position,
                            // reading progress, etc.) is still reflected via bookmark.copy().
                            if (transientContent != null) {
                                _loadingState.value = BookmarkLoadingState.FullyLoaded(bookmark.copy(content = transientContent))
                                _contentFetchAttempted.value = true
                            } else {
                                // Show existing with loading indicator if possible, or just the bookmark metadata
                                _loadingState.value = BookmarkLoadingState.FullyLoaded(bookmark)

                                // Trigger on-demand fetch
                                try {
                                    val strategy = settingsRepository.contentSyncStrategy.first()
                                    if (strategy == com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK ||
                                        strategy == com.karakept.app.data.model.SyncStrategy.NEVER ||
                                        strategy == com.karakept.app.data.model.SyncStrategy.PER_LIST) {

                                        val content = bookmarkRepository.fetchBookmarkContent(bookmark.remoteId, bookmark.serverId)
                                        if (!content.isNullOrBlank()) {
                                            var shouldPersist = false

                                            if (strategy == com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK) {
                                                shouldPersist = true
                                            } else if (strategy == com.karakept.app.data.model.SyncStrategy.PER_LIST) {
                                                // Check if in target list
                                                val targetLists = settingsRepository.contentSyncTargetLists.first()
                                                val bookmarkListIds = bookmark.listIds.split(",").filter { it.isNotBlank() }
                                                shouldPersist = bookmarkListIds.any { targetLists.contains(it) }
                                            }

                                            if (shouldPersist) {
                                                // Persist it
                                                val readingTime = com.karakept.app.utils.ReadingTimeCalculator.calculateReadingTime(content)
                                                bookmarkDao.updateContent(bookmark.localId, content, readingTime)
                                                // The flow will emit the updated bookmark automatically
                                            } else {
                                                // Transient (NEVER or PER_LIST outside target list):
                                                // cache content so future DB observations don't re-fetch
                                                transientContent = content
                                                val transientBookmark = bookmark.copy(content = content)
                                                _loadingState.value = BookmarkLoadingState.FullyLoaded(transientBookmark)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("ViewerModel", "Failed to update highlight: ${e.message}", e)
                                    snackbarManager.showErrorWithRetry("Couldn't load bookmark content") {
                                        loadBookmark(id)
                                    }
                                } finally {
                                    // Signal that the fetch attempt is done (success, failure, or
                                    // no content available) so the UI can stop waiting and show
                                    // whatever is available rather than staying hidden indefinitely.
                                    _contentFetchAttempted.value = true
                                }
                            }
                        } else {
                            _loadingState.value = BookmarkLoadingState.FullyLoaded(bookmark)
                            _contentFetchAttempted.value = true
                        }

                        reloadAssets(bookmark, applyPreferredSource = true)
                    } else if (!hasLoadedOnce) {
                        // Only show error if we never loaded the bookmark
                        // Don't show error during disposal/navigation
                        _loadingState.value = BookmarkLoadingState.Error("Bookmark not found")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _loadingState.value = BookmarkLoadingState.Error(
                    e.message ?: "Unknown error"
                )
            }
        }
    }

    fun setViewerMode(mode: ViewerMode) {
        viewModelScope.launch {
            settingsRepository.setViewerMode(mode)
        }
    }

    fun setHtmlTextColor(color: Color?) {
        viewModelScope.launch {
            settingsRepository.setHtmlTextColor(color)
        }
    }

    fun setHtmlBackgroundColor(color: Color?) {
        viewModelScope.launch {
            settingsRepository.setHtmlBackgroundColor(color)
        }
    }

    fun setHtmlFontSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.setHtmlFontSize(size)
        }
    }

    fun setHtmlFontFamily(family: ReaderFontFamily) {
        viewModelScope.launch {
            settingsRepository.setHtmlFontFamily(family)
        }
    }

    suspend fun resetReaderAppearance() {
        settingsRepository.resetReaderAppearance()
    }

    fun setScrollToTopEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setScrollToTopEnabled(enabled) }
    }

    fun setContentSource(source: ContentSource, bookmark: BookmarkEntity?) {
        viewModelScope.launch {
            _selectedSource.value = source
            if (source == ContentSource.FULL_PAGE_ARCHIVE) {
                val localPath = _precrawledAssetPath.value
                if (localPath != null) {
                    // Archive is cached — load as string if READER mode needs it
                    if (viewerMode.value == ViewerMode.READER && _sourceContentOverride.value == null) {
                        loadArchiveContentString(localPath)
                    }
                } else if (!offlineMode.value && bookmark != null) {
                    fetchAndCacheArchive(bookmark)
                } else {
                    snackbarManager.showSnackbar("Full page archive not available offline")
                    _selectedSource.value = ContentSource.EXTRACTED
                }
            } else {
                _sourceContentOverride.value = null
            }
        }
    }

    private suspend fun loadArchiveContentString(localPath: String) {
        try {
            _sourceContentOverride.value = com.karakept.app.utils.FileUtils.readFileAsText(localPath)
        } catch (e: Exception) {
            AppLogger.e("ViewerModel", "Failed to read archive as string: ${e.message}", e)
        }
    }

    fun fetchAndCacheArchive(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            _isLoadingSource.value = true
            try {
                val servers = serverRepository.servers.first()
                val server = servers.find { it.id == bookmark.serverId } ?: run {
                    snackbarManager.showSnackbar("Server not found")
                    _selectedSource.value = ContentSource.EXTRACTED
                    return@launch
                }
                val fullBookmark = remoteDataSource.fetchBookmark(
                    server, bookmark.originalRemoteId ?: bookmark.remoteId.toString()
                )
                val asset = fullBookmark.assets?.find {
                    it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.FULL_PAGE_ARCHIVE
                        || it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.PRECRAWLED_ARCHIVE
                }
                if (asset == null) {
                    snackbarManager.showSnackbar("No full page archive available for this bookmark")
                    _selectedSource.value = ContentSource.EXTRACTED
                    return@launch
                }
                val bytes = remoteDataSource.downloadAsset(server, asset.id ?: "")
                val cacheDir = com.karakept.app.utils.FileUtils.getImageCacheDirectory()
                val localPath = com.karakept.app.utils.FileUtils.saveFile(
                    cacheDir, "archive_${asset.id}", bytes
                )
                assetDao.insertAssets(listOf(
                    com.karakept.app.data.local.entity.AssetEntity(
                        id = asset.id ?: "",
                        bookmarkRemoteId = bookmark.remoteId,
                        serverId = bookmark.serverId,
                        assetType = asset.assetType?.value ?: "fullPageArchive",
                        fileName = "archive_${asset.id}",
                        contentType = null,
                        localPath = localPath
                    )
                ))
                _precrawledAssetPath.value = localPath
                _archiveAvailable.value = true
                if (viewerMode.value == ViewerMode.READER) {
                    _sourceContentOverride.value = bytes.decodeToString()
                }
                _selectedSource.value = ContentSource.FULL_PAGE_ARCHIVE
                // Refresh the exposed asset list so the details panel reflects the new localPath
                val updatedAssets = assetDao.getAssetsForBookmark(bookmark.remoteId, bookmark.serverId)
                _assets.value = updatedAssets
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to fetch archive: ${e.message}", e)
                snackbarManager.showSnackbar("Couldn't load full page archive")
                _selectedSource.value = ContentSource.EXTRACTED
            } finally {
                _isLoadingSource.value = false
            }
        }
    }

    /**
     * Re-reads this bookmark's asset rows and republishes every piece of state derived from
     * them. Called after anything that adds, removes, or caches an asset.
     *
     * [applyPreferredSource] is only true on initial load — later reloads must not yank the
     * user out of the content source they picked.
     */
    private suspend fun reloadAssets(
        bookmark: BookmarkEntity,
        applyPreferredSource: Boolean = false
    ) {
        val assets = assetDao.getAssetsForBookmark(bookmark.remoteId, bookmark.serverId)
        _assets.value = assets

        val archive = assets.find { it.assetType == "precrawledArchive" }
            ?: assets.find { it.assetType == "fullPageArchive" }
        _precrawledAssetPath.value = archive?.localPath
        // Archive is "available" if ANY archive row exists (even without localPath),
        // so the "Load full page archive" button appears when the server has it.
        _archiveAvailable.value = archive != null

        if (applyPreferredSource) {
            // Default to FULL_PAGE_ARCHIVE only when the user has explicitly opted in.
            // Without the setting, extracted content is always the default so that
            // "Extracted" source selection reliably shows extracted HTML (or no content).
            val preferArchive = settingsRepository.preferFullPageHtml.first()
            if (archive?.localPath != null && preferArchive && _selectedSource.value == ContentSource.EXTRACTED) {
                _selectedSource.value = ContentSource.FULL_PAGE_ARCHIVE
            }
        } else if (_selectedSource.value == ContentSource.FULL_PAGE_ARCHIVE && archive?.localPath == null) {
            // The archive we were displaying is gone (deleted locally or on the server).
            _selectedSource.value = ContentSource.EXTRACTED
            _sourceContentOverride.value = null
        }

        _bannerImageLocalPath.value = assets.find { it.assetType == "bannerImage" }?.localPath
        _screenshotLocalPath.value = assets.find { it.assetType == "screenshot" }?.localPath
    }

    fun deleteAssetLocal(asset: com.karakept.app.data.local.entity.AssetEntity) {
        viewModelScope.launch {
            try {
                val localPath = asset.localPath
                if (localPath != null) {
                    try { com.karakept.app.utils.FileUtils.deleteFile(localPath) } catch (_: Exception) {}
                }
                assetDao.clearLocalPath(asset.id)
                val bookmark = (_loadingState.value as? BookmarkLoadingState.FullyLoaded)?.bookmark ?: return@launch
                reloadAssets(bookmark)
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to delete asset local copy: ${e.message}", e)
                snackbarManager.showSnackbar("Couldn't delete local file")
            }
        }
    }

    /**
     * Delete an asset on the server (not just the local copy). Irreversible — the UI gates
     * this behind a confirmation dialog.
     */
    fun deleteAssetOnServer(
        asset: com.karakept.app.data.local.entity.AssetEntity,
        bookmark: BookmarkEntity
    ) {
        viewModelScope.launch {
            try {
                val server = serverRepository.servers.first().find { it.id == bookmark.serverId }
                if (server == null) {
                    snackbarManager.showSnackbar("Server not found")
                    return@launch
                }
                remoteDataSource.detachAsset(server, bookmark.originalRemoteId, asset.id)
                asset.localPath?.let {
                    try { com.karakept.app.utils.FileUtils.deleteFile(it) } catch (_: Exception) {}
                }
                assetDao.deleteAsset(asset.id)
                reloadAssets(bookmark)
                snackbarManager.showSnackbar("Deleted from server")
            } catch (e: OfflineModeException) {
                snackbarManager.showSnackbar("Not available in offline mode")
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to delete asset on server: ${e.message}", e)
                snackbarManager.showSnackbar("Couldn't delete from server")
            }
        }
    }

    /**
     * Ask the server to re-crawl this bookmark. The server runs the crawl as a background job,
     * so we re-sync the bookmark after a short delay to pick up whatever it produced.
     */
    fun requestServerCrawl(bookmark: BookmarkEntity, action: ServerCrawlAction) {
        // Claim the slot before launching — two taps in the same frame would both see null
        // if the check happened inside the coroutine.
        if (!_serverCrawlInFlight.compareAndSet(null, action)) return
        viewModelScope.launch {
            try {
                val server = serverRepository.servers.first().find { it.id == bookmark.serverId }
                if (server == null) {
                    snackbarManager.showSnackbar("Server not found")
                    return@launch
                }
                remoteDataSource.recrawlBookmark(
                    server = server,
                    bookmarkId = bookmark.originalRemoteId,
                    archiveFullPage = action.archiveFullPage,
                    storePdf = action.storePdf
                )
                snackbarManager.showSnackbar(
                    when (action) {
                        ServerCrawlAction.REFRESH -> "Refresh requested"
                        ServerCrawlAction.PRESERVE_ARCHIVE -> "Archive requested"
                        ServerCrawlAction.PRESERVE_PDF -> "PDF requested"
                    }
                )
                awaitCrawlResult(bookmark, action)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: OfflineModeException) {
                snackbarManager.showSnackbar("Not available in offline mode")
            } catch (e: UnsupportedServerActionException) {
                snackbarManager.showSnackbar(e.message ?: "Not supported by this server")
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Server crawl request failed: ${e.message}", e)
                snackbarManager.showErrorWithRetry("Couldn't reach the server") {
                    requestServerCrawl(bookmark, action)
                }
            } finally {
                _serverCrawlInFlight.value = null
            }
        }
    }

    /**
     * Re-syncs the bookmark until the crawl job's output shows up, or the budget runs out.
     *
     * A single fixed delay was never enough: the server enqueues the crawl, so a page of any
     * size finished well after we had already re-synced, and the new asset only appeared when
     * the user ran the action a second time.
     */
    private suspend fun awaitCrawlResult(bookmark: BookmarkEntity, action: ServerCrawlAction) {
        // The asset type the job is expected to produce. REFRESH rewrites metadata rather than
        // adding an asset, so there is nothing to wait for beyond the first sync.
        val expectedAssetType = when (action) {
            ServerCrawlAction.REFRESH -> null
            ServerCrawlAction.PRESERVE_ARCHIVE -> "fullPageArchive"
            ServerCrawlAction.PRESERVE_PDF -> "pdf"
        }

        repeat(CRAWL_POLL_ATTEMPTS) { attempt ->
            delay(CRAWL_POLL_INTERVAL_MS)
            bookmarkRepository.syncSingleBookmark(bookmark.remoteId, bookmark.serverId)
            reloadAssets(bookmark)

            if (expectedAssetType == null) return
            if (_assets.value.any { it.assetType == expectedAssetType }) {
                snackbarManager.showSnackbar(
                    if (action == ServerCrawlAction.PRESERVE_PDF) "PDF ready" else "Archive ready"
                )
                return
            }
            if (attempt == CRAWL_POLL_ATTEMPTS - 1) {
                // Still running server-side. Say so rather than leaving the user staring at a
                // row that never changed.
                snackbarManager.showSnackbar("Still processing on the server — pull to refresh later")
            }
        }
    }

    /** Cache filename for an asset. PDFs keep the extension so the OS picks a reader. */
    private fun cacheFileNameFor(asset: com.karakept.app.data.local.entity.AssetEntity): String =
        when (asset.assetType) {
            "fullPageArchive", "precrawledArchive" -> "archive_${asset.id}"
            "bannerImage" -> "hero_banner_${asset.id}"
            "screenshot" -> "hero_screenshot_${asset.id}"
            "pdf" -> "asset_${asset.id}.pdf"
            else -> "asset_${asset.id}"
        }

    /**
     * Downloads [asset] and stores the local path against that exact row.
     *
     * Archives used to route through [fetchAndCacheArchive], which re-resolved the asset from
     * the server and could stamp the local path onto a *different* archive row — leaving the
     * one the user tapped still reading "On server" with no way to delete its local copy.
     * Downloading by id is also what lets any asset type work here, PDFs included.
     */
    fun downloadOrRefreshAsset(
        asset: com.karakept.app.data.local.entity.AssetEntity,
        bookmark: com.karakept.app.data.local.entity.BookmarkEntity,
        // True when the download was started by tapping the row rather than by picking
        // "Download a copy" from the menu: finish the job the tap implied.
        useWhenDone: Boolean = false
    ) {
        // Claim the slot synchronously — two taps in the same frame would both pass a check
        // made inside the coroutine and start the download twice.
        val before = _assetDownloads.getAndUpdate { current ->
            if (current.containsKey(asset.id)) current else current + (asset.id to null)
        }
        if (before.containsKey(asset.id)) return

        viewModelScope.launch {
            try {
                val server = serverRepository.servers.first().find { it.id == bookmark.serverId }
                if (server == null) {
                    snackbarManager.showSnackbar("Server not found")
                    return@launch
                }
                // Refresh: drop the stale copy first so a failure can't leave both around.
                asset.localPath?.let { previous ->
                    try { com.karakept.app.utils.FileUtils.deleteFile(previous) } catch (_: Exception) {}
                    assetDao.clearLocalPath(asset.id)
                }

                val bytes = remoteDataSource.downloadAsset(server, asset.id) { fraction ->
                    _assetDownloads.update { it + (asset.id to fraction) }
                }
                val localPath = com.karakept.app.utils.FileUtils.saveFile(
                    com.karakept.app.utils.FileUtils.getImageCacheDirectory(),
                    cacheFileNameFor(asset),
                    bytes
                )
                val downloaded = asset.copy(localPath = localPath)
                assetDao.insertAssets(listOf(downloaded))
                reloadAssets(bookmark)

                val isArchive = asset.assetType == "fullPageArchive" || asset.assetType == "precrawledArchive"
                when {
                    // Tapping the row meant "I want to use this", so finish the thought once
                    // the bytes are here rather than making the user tap again.
                    useWhenDone && isArchive -> {
                        _sourceContentOverride.value = bytes.decodeToString()
                        _selectedSource.value = ContentSource.FULL_PAGE_ARCHIVE
                    }
                    useWhenDone && asset.assetType == "pdf" -> openAssetExternally(downloaded)
                    // Not a tap, but the reader is already showing this archive — swap in the
                    // fresh bytes so a re-download is reflected immediately.
                    isArchive && _selectedSource.value == ContentSource.FULL_PAGE_ARCHIVE &&
                        viewerMode.value == ViewerMode.READER -> {
                        _sourceContentOverride.value = bytes.decodeToString()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: OfflineModeException) {
                snackbarManager.showSnackbar("Not available in offline mode")
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to download asset: ${e.message}", e)
                snackbarManager.showSnackbar("Couldn't download asset")
            } finally {
                _assetDownloads.update { it - asset.id }
            }
        }
    }

    /**
     * Hands a downloaded asset to whatever app the platform has registered for it — the app
     * has no PDF renderer of its own.
     */
    fun openAssetExternally(asset: com.karakept.app.data.local.entity.AssetEntity) {
        val localPath = asset.localPath
        if (localPath == null) {
            viewModelScope.launch { snackbarManager.showSnackbar("Download it first") }
            return
        }
        viewModelScope.launch {
            val opened = com.karakept.app.utils.FileUtils.openFileExternally(
                localPath,
                mimeTypeFor(asset.assetType)
            )
            if (!opened) snackbarManager.showSnackbar("No app available to open this file")
        }
    }

    private fun mimeTypeFor(assetType: String): String = when (assetType) {
        "pdf" -> "application/pdf"
        "fullPageArchive", "precrawledArchive", "linkHtmlContent" -> "text/html"
        "bannerImage", "screenshot" -> "image/*"
        else -> "*/*"
    }

    fun loadLists(server: Server) {
        viewModelScope.launch {
            try {
                val fetchedLists = remoteDataSource.fetchLists(server)
                _lists.value = fetchedLists
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to load highlights: ${e.message}", e)
                snackbarManager.showErrorWithRetry("Couldn't load lists") {
                    loadLists(server)
                }
            }
        }
    }

    // Bookmark Actions

    fun toggleBookmarkArchive(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            val event = if (bookmark.isArchived) {
                BookmarkActionEvent.Unarchive(bookmark)
            } else {
                BookmarkActionEvent.Archive(bookmark)
            }
            bookmarkActionController.executeAction(event)
        }
    }

    fun toggleBookmarkFavorite(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            bookmarkActionController.executeAction(
                BookmarkActionEvent.ToggleFavorite(bookmark)
            )
        }
    }

    fun toggleBookmarkRead(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            val event = if (bookmark.isRead) {
                BookmarkActionEvent.MarkUnread(bookmark)
            } else {
                BookmarkActionEvent.MarkRead(bookmark)
            }
            bookmarkActionController.executeAction(event)
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            bookmarkActionController.executeAction(
                BookmarkActionEvent.Delete(bookmark)
            )
            onSuccess()
        }
    }

    fun moveBookmarkToList(bookmark: BookmarkEntity, listId: String) {
        viewModelScope.launch {
            val isOffline = settingsRepository.offlineMode.first()
            bookmarkActionsRepository.moveToList(
                bookmark.remoteId,
                bookmark.serverId,
                listId,
                !isOffline
            )
        }
    }

    fun updateBookmarkTags(bookmark: BookmarkEntity, tags: List<String>) {
        viewModelScope.launch {
            val isOffline = settingsRepository.offlineMode.first()
            bookmarkActionsRepository.updateTags(
                bookmark.remoteId,
                bookmark.serverId,
                tags,
                !isOffline
            )
        }
    }

    fun createHighlight(bookmark: BookmarkEntity, text: String, startOffset: Int, endOffset: Int, note: String? = null, color: String? = null, onCreated: (String) -> Unit = {}) {
        AppLogger.d("ViewerModel", "createHighlight requested - text='${text.take(30)}...', start=$startOffset, end=$endOffset")
        viewModelScope.launch {
            try {
                val servers = serverRepository.servers.first()
                val server = servers.find { it.id == bookmark.serverId } ?: run {
                    AppLogger.w("ViewerModel", "Server not found for serverId=${bookmark.serverId}")
                    return@launch
                }
                val remoteId = bookmark.originalRemoteId ?: bookmark.remoteId.toString()
                AppLogger.d("ViewerModel", "Calling highlightRepository.createHighlight for remoteId=$remoteId")
                val highlightId = highlightRepository.createHighlight(server, bookmark.localId, remoteId, text, startOffset, endOffset, note, color)
                AppLogger.d("ViewerModel", "Highlight created successfully, id=$highlightId")
                onCreated(highlightId)
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to save reading progress: ${e.message}", e)
                snackbarManager.showSnackbar("Couldn't save highlight")
            }
        }
    }

    fun updateHighlight(bookmark: BookmarkEntity, highlightId: String, note: String?, color: String?) {
        viewModelScope.launch {
            val servers = serverRepository.servers.first()
            val server = servers.find { it.id == bookmark.serverId } ?: return@launch
            highlightRepository.updateHighlight(server, bookmark.localId, highlightId, note, color)
        }
    }

    fun deleteHighlight(bookmark: BookmarkEntity, highlightId: String) {
        viewModelScope.launch {
            val servers = serverRepository.servers.first()
            val server = servers.find { it.id == bookmark.serverId } ?: return@launch
            highlightRepository.deleteHighlight(server, bookmark.localId, highlightId)
        }
    }
}
