package com.karakept.app.ui.screens

import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.api.model.KarakeepList as KarakeepList
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.BookmarkActionEvent

class BookmarkViewerScreenModel(
    private val bookmarkDao: BookmarkDao,
    private val assetDao: AssetDao,
    private val settingsRepository: SettingsRepository,
    private val bookmarkActionsRepository: BookmarkActionsRepository,
    private val remoteDataSource: RemoteDataSource,
    private val serverRepository: ServerRepository,
    private val bookmarkRepository: com.karakept.app.data.repository.BookmarkRepository,
    private val bookmarkActionController: BookmarkActionController,
    private val highlightRepository: com.karakept.app.data.repository.HighlightRepository
) : ScreenModel {
    private val _loadingState = MutableStateFlow<BookmarkLoadingState>(BookmarkLoadingState.Initial)
    val loadingState: StateFlow<BookmarkLoadingState> = _loadingState.asStateFlow()

    val viewerMode: StateFlow<ViewerMode> = settingsRepository.viewerMode
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ViewerMode.READER)

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

    val autoMarkReadOnScroll: StateFlow<Boolean> = settingsRepository.autoMarkReadOnScroll
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showTags: StateFlow<Boolean> = settingsRepository.showTags
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), true)

    val linkOpenMode: StateFlow<LinkOpenMode> = settingsRepository.linkOpenMode
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), LinkOpenMode.EXTERNAL_BROWSER)

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

    fun refreshBookmark(id: Long) {
        val currentState = _loadingState.value
        if (currentState !is BookmarkLoadingState.FullyLoaded) {
            println("BookmarkViewerScreenModel: refreshBookmark called but state is not FullyLoaded")
            return
        }

        screenModelScope.launch {
            if (offlineMode.value) {
                println("BookmarkViewerScreenModel: refreshBookmark skipped - offline mode")
                return@launch
            }

            println("BookmarkViewerScreenModel: Starting refresh for bookmark ${currentState.bookmark.remoteId}")
            _isRefreshing.value = true
            try {
                // Sync bookmark content
                bookmarkRepository.syncSingleBookmark(
                    currentState.bookmark.remoteId,
                    currentState.bookmark.serverId
                )
                println("BookmarkViewerScreenModel: Bookmark content synced")

                // Also sync highlights for this bookmark
                val servers = serverRepository.servers.first()
                val server = servers.find { it.id == currentState.bookmark.serverId }
                if (server != null) {
                    val remoteId = currentState.bookmark.originalRemoteId ?: currentState.bookmark.remoteId.toString()
                    println("BookmarkViewerScreenModel: Syncing highlights for remoteId=$remoteId")
                    highlightRepository.syncHighlightsForBookmark(server, remoteId)
                    println("BookmarkViewerScreenModel: Highlights synced")
                } else {
                    println("BookmarkViewerScreenModel: Server not found for serverId=${currentState.bookmark.serverId}")
                }
            } catch (e: Exception) {
                println("BookmarkViewerScreenModel: Error during refresh: ${e.message}")
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
                println("BookmarkViewerScreenModel: Refresh complete")
            }
        }
    }

    fun loadBookmark(id: Long) {
        _bookmarkId.value = id
        screenModelScope.launch {
            try {
                var hasLoadedOnce = false
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
                                    println("Error during on-demand highlight sync: ${e.message}")
                                }
                            }
                        }
                        hasLoadedOnce = true
                        
                        // Check for content availability
                        if (bookmark.content.isNullOrBlank()) {
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
                                            // Transient (NEVER or PER_LIST outside target list)
                                            val transientBookmark = bookmark.copy(content = content)
                                            _loadingState.value = BookmarkLoadingState.FullyLoaded(transientBookmark)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            _loadingState.value = BookmarkLoadingState.FullyLoaded(bookmark)
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

    fun loadLists(server: Server) {
        screenModelScope.launch {
            try {
                val fetchedLists = remoteDataSource.fetchLists(server)
                _lists.value = fetchedLists
            } catch (e: Exception) {
                // Handle error silently or log
                e.printStackTrace()
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
        screenModelScope.launch {
            val servers = serverRepository.servers.first()
            val server = servers.find { it.id == bookmark.serverId } ?: return@launch
            val remoteId = bookmark.originalRemoteId ?: bookmark.remoteId.toString()
            val highlightId = highlightRepository.createHighlight(server, bookmark.localId, remoteId, text, startOffset, endOffset, note, color)
            onCreated(highlightId)
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
