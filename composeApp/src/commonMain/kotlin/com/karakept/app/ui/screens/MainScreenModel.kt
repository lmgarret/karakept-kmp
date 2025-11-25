package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainScreenModel(
    private val serverRepository: ServerRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val remoteDataSource: RemoteDataSource
) : ScreenModel {

    // For simplicity, we just pick the first server for now, or allow switching.
    // Let's expose the list of servers and the selected server.
    
    val servers = serverRepository.servers
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedServerId = MutableStateFlow<String?>(null)
    
    val selectedServer: StateFlow<Server?> = combine(servers, _selectedServerId) { list, id ->
        if (id != null) list.find { it.id == id } else list.firstOrNull()
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _lists = MutableStateFlow<List<com.karakept.app.data.remote.model.ListDto>>(emptyList())
    val lists: StateFlow<List<com.karakept.app.data.remote.model.ListDto>> = _lists

    private val _currentFilter = MutableStateFlow<String?>(null)

    init {
        // Load lists when the selected server changes
        screenModelScope.launch {
            selectedServer.collect { server ->
                if (server != null) {
                    loadLists()
                } else {
                    _lists.value = emptyList()
                }
            }
        }
    }

    val bookmarks: StateFlow<List<BookmarkEntity>> = combine(
        selectedServer,
        _currentFilter
    ) { server, filter ->
        Pair(server, filter)
    }.flatMapLatest { (server, filter) ->
        if (server != null) {
            bookmarkRepository.getBookmarks(server).map { bookmarks ->
                applyFilterToBookmarks(bookmarks, filter)
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun applyFilterToBookmarks(bookmarks: List<BookmarkEntity>, filter: String?): List<BookmarkEntity> {
        return when (filter) {
            "is:fav" -> bookmarks.filter { it.isStarred }
            "is:archived" -> bookmarks.filter { it.isArchived }
            "-is:archived" -> bookmarks.filter { !it.isArchived }
            null -> bookmarks
            else -> bookmarks
        }
    }

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

    private fun loadLists() {
        screenModelScope.launch {
            selectedServer.value?.let { server ->
                try {
                    val fetchedLists = remoteDataSource.fetchLists(server)
                    _lists.value = fetchedLists
                } catch (e: Exception) {
                    // Handle error - keep existing lists or set to empty
                    e.printStackTrace()
                }
            }
        }
    }

    fun applyFilter(filter: String) {
        _currentFilter.value = filter
    }

    fun clearFilter() {
        _currentFilter.value = null
    }
}
