package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HighlightsScreenModel(
    private val highlightRepository: HighlightRepository,
    private val bookmarkDao: BookmarkDao,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository
) : ScreenModel {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val highlights: StateFlow<List<Highlight>> = settingsRepository.activeServerId
        .flatMapLatest { serverId ->
            if (serverId == null) flowOf(emptyList())
            else highlightRepository.getAllHighlights(serverId)
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedServer: StateFlow<Server?> = combine(
        serverRepository.servers,
        settingsRepository.activeServerId
    ) { servers, id ->
        servers.find { it.id == id }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun syncHighlights() {
        screenModelScope.launch {
            val server = selectedServer.value ?: return@launch
            _isSyncing.value = true
            try {
                highlightRepository.syncHighlights(server)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun deleteHighlight(highlight: Highlight) {
        screenModelScope.launch {
            val server = selectedServer.value ?: return@launch
            // Look up the bookmark by its original remote ID (string)
            val bookmark = bookmarkDao.getBookmarkByOriginalRemoteId(highlight.bookmarkId, server.id) ?: return@launch
            highlightRepository.deleteHighlight(server, bookmark.localId, highlight.id)
        }
    }

    suspend fun getBookmarkLocalIdForHighlight(highlight: Highlight): Long? {
        val server = selectedServer.value ?: return null
        val bookmark = bookmarkDao.getBookmarkByOriginalRemoteId(highlight.bookmarkId, server.id)
        return bookmark?.localId
    }
}
