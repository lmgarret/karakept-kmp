package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainScreenModel(
    private val serverRepository: ServerRepository,
    private val bookmarkRepository: BookmarkRepository
) : ScreenModel {

    // For simplicity, we just pick the first server for now, or allow switching.
    // Let's expose the list of servers and the selected server.
    
    val servers = serverRepository.servers
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedServerId = MutableStateFlow<String?>(null)
    
    val selectedServer: StateFlow<Server?> = combine(servers, _selectedServerId) { list, id ->
        if (id != null) list.find { it.id == id } else list.firstOrNull()
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bookmarks: StateFlow<List<BookmarkEntity>> = selectedServer.flatMapLatest { server ->
        if (server != null) {
            bookmarkRepository.getBookmarks(server)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectServer(serverId: String) {
        _selectedServerId.value = serverId
    }

    fun syncBookmarks() {
        screenModelScope.launch {
            selectedServer.value?.let { server ->
                try {
                    bookmarkRepository.syncBookmarks(server)
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }
}
