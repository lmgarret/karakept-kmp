package com.karakept.app.ui.screens

import androidx.compose.ui.graphics.Color
import com.karakept.app.utils.AppLogger
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.BookmarkEntity
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.BookmarkActionEvent
import com.karakept.app.ui.utils.ParsedDocumentCache
import getPlatform

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
) : ScreenModel {
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
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ViewerMode.READER)
    }

    val hideArticleThumbnails: StateFlow<Boolean> = settingsRepository.hideArticleThumbnails
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), true)

    val htmlTextColor: StateFlow<Color?> = settingsRepository.htmlTextColor
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    val htmlBackgroundColor: StateFlow<Color?> = settingsRepository.htmlBackgroundColor
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    val htmlFontSize: StateFlow<Int> = settingsRepository.htmlFontSize
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), 16)

    val htmlFontFamily: StateFlow<ReaderFontFamily> = settingsRepository.htmlFontFamily
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ReaderFontFamily.SYSTEM)

    val showTags: StateFlow<Boolean> = settingsRepository.showTagsInViewer
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), true)

    val scrollToTopEnabled: StateFlow<Boolean> = settingsRepository.scrollToTopEnabled
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dateDisplayMode: StateFlow<DateDisplayMode> = settingsRepository.dateDisplayMode
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), DateDisplayMode.ELAPSED)

    val linkOpenMode: StateFlow<LinkOpenMode> = settingsRepository.linkOpenMode
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), LinkOpenMode.CUSTOM_TAB)

    private val _precrawledAssetPath = MutableStateFlow<String?>(null)
    val precrawledAssetPath: StateFlow<String?> = _precrawledAssetPath.asStateFlow()

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
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineMode: StateFlow<Boolean> = settingsRepository.offlineMode
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

    val trackReadingProgress: StateFlow<Boolean> = settingsRepository.trackReadingProgress
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), true)

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
        @OptIn(FlowPreview::class)
        screenModelScope.launch {
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

    @OptIn(DelicateCoroutinesApi::class)
    override fun onDispose() {
        parsedDocumentCache.clear()
        val state = pendingReadingState ?: return
        val serverId = (_loadingState.value as? BookmarkLoadingState.FullyLoaded)?.bookmark?.serverId
        // screenModelScope is being cancelled, so use GlobalScope for this
        // fire-and-forget DB write that must complete.
        GlobalScope.launch(Dispatchers.IO) {
            bookmarkDao.updateReadingProgress(
                state.localId, state.progress, state.scrollIndex, state.scrollOffset
            )
            bookmarkActionsRepository.notifyBookmarkChanged(state.remoteId)
            // Queue the final reading progress sync so it is pushed on the next sync cycle.
            if (serverId != null) {
                bookmarkActionsRepository.queueReadingProgressUpdate(
                    bookmarkRemoteId = state.remoteId,
                    serverId = serverId,
                    progressPercent = (state.progress * 100).toInt()
                )
            }
        }
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

        screenModelScope.launch {
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
        screenModelScope.launch {
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
                            screenModelScope.launch {
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
                                screenModelScope.launch {
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

                        // Load precrawled asset if exists
                        val assets = assetDao.getAssetsForBookmark(bookmark.remoteId, bookmark.serverId)
                        val archive = assets.find { it.assetType == "precrawledArchive" }
                        _precrawledAssetPath.value = archive?.localPath

                        // Load hero assets if exist
                        val bannerAsset = assets.find { it.assetType == "bannerImage" }
                        _bannerImageLocalPath.value = bannerAsset?.localPath
                        
                        val screenshotAsset = assets.find { it.assetType == "screenshot" }
                        _screenshotLocalPath.value = screenshotAsset?.localPath
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
        screenModelScope.launch {
            settingsRepository.setViewerMode(mode)
        }
    }

    fun setHtmlTextColor(color: Color?) {
        screenModelScope.launch {
            settingsRepository.setHtmlTextColor(color)
        }
    }

    fun setHtmlBackgroundColor(color: Color?) {
        screenModelScope.launch {
            settingsRepository.setHtmlBackgroundColor(color)
        }
    }

    fun setHtmlFontSize(size: Int) {
        screenModelScope.launch {
            settingsRepository.setHtmlFontSize(size)
        }
    }

    fun setHtmlFontFamily(family: ReaderFontFamily) {
        screenModelScope.launch {
            settingsRepository.setHtmlFontFamily(family)
        }
    }

    suspend fun resetReaderAppearance() {
        settingsRepository.resetReaderAppearance()
    }

    fun setScrollToTopEnabled(enabled: Boolean) {
        screenModelScope.launch { settingsRepository.setScrollToTopEnabled(enabled) }
    }

    fun loadLists(server: Server) {
        screenModelScope.launch {
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
        screenModelScope.launch {
            val event = if (bookmark.isArchived) {
                BookmarkActionEvent.Unarchive(bookmark)
            } else {
                BookmarkActionEvent.Archive(bookmark)
            }
            bookmarkActionController.executeAction(event)
        }
    }

    fun toggleBookmarkFavorite(bookmark: BookmarkEntity) {
        screenModelScope.launch {
            bookmarkActionController.executeAction(
                BookmarkActionEvent.ToggleFavorite(bookmark)
            )
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
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity, onSuccess: () -> Unit) {
        screenModelScope.launch {
            bookmarkActionController.executeAction(
                BookmarkActionEvent.Delete(bookmark)
            )
            onSuccess()
        }
    }

    fun moveBookmarkToList(bookmark: BookmarkEntity, listId: String) {
        screenModelScope.launch {
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
        screenModelScope.launch {
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
        screenModelScope.launch {
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
        screenModelScope.launch {
            val servers = serverRepository.servers.first()
            val server = servers.find { it.id == bookmark.serverId } ?: return@launch
            highlightRepository.updateHighlight(server, bookmark.localId, highlightId, note, color)
        }
    }

    fun deleteHighlight(bookmark: BookmarkEntity, highlightId: String) {
        screenModelScope.launch {
            val servers = serverRepository.servers.first()
            val server = servers.find { it.id == bookmark.serverId } ?: return@launch
            highlightRepository.deleteHighlight(server, bookmark.localId, highlightId)
        }
    }
}
