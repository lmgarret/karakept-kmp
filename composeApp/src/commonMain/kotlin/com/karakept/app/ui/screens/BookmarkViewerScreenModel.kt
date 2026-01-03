package com.karakept.app.ui.screens

import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.remote.model.ListDto
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val bookmarkActionController: BookmarkActionController
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

    private val _precrawledAssetPath = MutableStateFlow<String?>(null)
    val precrawledAssetPath: StateFlow<String?> = _precrawledAssetPath.asStateFlow()

    private val _lists = MutableStateFlow<List<ListDto>>(emptyList())
    val lists: StateFlow<List<ListDto>> = _lists.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val offlineMode: StateFlow<Boolean> = settingsRepository.offlineMode
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun refreshBookmark(id: Long) {
        val currentState = _loadingState.value
        if (currentState !is BookmarkLoadingState.FullyLoaded) return

        screenModelScope.launch {
            if (offlineMode.value) return@launch
            
            _isRefreshing.value = true
            try {
                bookmarkRepository.syncSingleBookmark(
                    currentState.bookmark.remoteId,
                    currentState.bookmark.serverId
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadBookmark(id: Long) {
        screenModelScope.launch {
            try {
                var hasLoadedOnce = false
                // Observe bookmark changes from DB reactively
                bookmarkDao.observeBookmarkById(id).collect { bookmark ->
                    if (bookmark != null) {
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
}
