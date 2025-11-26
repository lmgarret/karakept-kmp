package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookmarkViewerScreenModel(
    private val bookmarkDao: BookmarkDao,
    private val settingsRepository: SettingsRepository
) : ScreenModel {
    private val _loadingState = MutableStateFlow<BookmarkLoadingState>(BookmarkLoadingState.Initial)
    val loadingState: StateFlow<BookmarkLoadingState> = _loadingState.asStateFlow()

    val viewerMode: StateFlow<ViewerMode> = settingsRepository.viewerMode
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ViewerMode.READER)

    fun loadBookmark(id: Long) {
        screenModelScope.launch {
            try {
                // Load bookmark from DB immediately
                val bookmark = bookmarkDao.getBookmarkById(id)
                    ?: throw Exception("Bookmark not found")

                // Stage 1: Title (immediate)
                _loadingState.value = BookmarkLoadingState.TitleLoaded(bookmark.title)
                delay(50) // Smooth animation timing

                // Stage 2: Thumbnail
                _loadingState.value = BookmarkLoadingState.ThumbnailLoaded(
                    bookmark.title,
                    bookmark.imageUrl
                )
                delay(100) // Allow banner to render

                // Stage 3: Full content
                _loadingState.value = BookmarkLoadingState.FullyLoaded(bookmark)
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
}
