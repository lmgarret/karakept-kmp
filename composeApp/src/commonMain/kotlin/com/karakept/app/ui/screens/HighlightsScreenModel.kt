package com.karakept.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.model.Highlight
import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HighlightsScreenModel(
    private val highlightRepository: HighlightRepository,
    private val bookmarkDao: BookmarkDao,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val pageSize = 20

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMoreItems = MutableStateFlow(true)
    val hasMoreItems: StateFlow<Boolean> = _hasMoreItems.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    private val _accumulatedHighlights = MutableStateFlow<List<Highlight>>(emptyList())

    val highlights: StateFlow<List<Highlight>> = _accumulatedHighlights.asStateFlow()

    val selectedServer: StateFlow<Server?> = combine(
        serverRepository.servers,
        settingsRepository.activeServerId
    ) { servers, id ->
        servers.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val serverId = settingsRepository.activeServerId.first()
            if (serverId != null) loadInitialPage()
        }
    }

    private suspend fun loadInitialPage() {
        val serverId = settingsRepository.activeServerId.first() ?: return
        _currentPage.value = 0
        _hasMoreItems.value = true
        val items = highlightRepository.getHighlightsPaged(serverId, pageSize, 0)
        _accumulatedHighlights.value = items
        if (items.size < pageSize) _hasMoreItems.value = false
    }

    fun loadNextPage() {
        if (_isLoadingMore.value || !_hasMoreItems.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val serverId = settingsRepository.activeServerId.first() ?: return@launch
                val nextPage = _currentPage.value + 1
                val offset = nextPage * pageSize
                val items = highlightRepository.getHighlightsPaged(serverId, pageSize, offset)
                if (items.isNotEmpty()) {
                    _accumulatedHighlights.value = _accumulatedHighlights.value + items
                    _currentPage.value = nextPage
                }
                if (items.size < pageSize) _hasMoreItems.value = false
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun syncHighlights() {
        viewModelScope.launch {
            val server = selectedServer.value ?: return@launch
            _isSyncing.value = true
            try {
                highlightRepository.syncHighlights(server)
                loadInitialPage()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch {
            val server = selectedServer.value ?: return@launch
            _accumulatedHighlights.value = _accumulatedHighlights.value.filter { it.id != highlight.id }
            // A highlight's bookmarkId is the bookmark's remote id, which is what the queued
            // action is keyed on — no local row needed, and the highlight is still deletable
            // when the bookmark itself has not been synced down.
            highlightRepository.deleteHighlight(server, highlight.bookmarkId, highlight.id)
        }
    }

    suspend fun getBookmarkLocalIdForHighlight(highlight: Highlight): Long? {
        val server = selectedServer.value ?: return null
        val bookmark = bookmarkDao.getBookmarkByRemoteId(highlight.bookmarkId, server.id)
        return bookmark?.localId
    }
}
