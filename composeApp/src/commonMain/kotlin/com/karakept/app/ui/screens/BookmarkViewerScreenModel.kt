package com.karakept.app.ui.screens

import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.model.ReaderFontFamily
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

    fun loadBookmark(id: Long) {
        screenModelScope.launch {
            try {
                // Load bookmark from DB immediately
                val bookmark = bookmarkDao.getBookmarkById(id)
                    ?: throw Exception("Bookmark not found")

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
}
