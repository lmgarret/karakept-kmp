package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.SavedFilterRepository
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
    private val remoteDataSource: RemoteDataSource,
    private val savedFilterRepository: SavedFilterRepository,
    private val bookmarkActionsRepository: com.karakept.app.data.repository.BookmarkActionsRepository
) : ScreenModel {

    // For simplicity, we just pick the first server for now, or allow switching.
    // Let's expose the list of servers and the selected server.
    
    val servers = serverRepository.servers
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedServer = MutableStateFlow<Server?>(null)
    val selectedServer: StateFlow<Server?> = _selectedServer

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _currentFilter = MutableStateFlow(FilterConfig())
    val currentFilter: StateFlow<FilterConfig> = _currentFilter

    val savedFilters = savedFilterRepository.visibleFilters
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lists = MutableStateFlow<List<com.karakept.app.data.remote.model.ListDto>>(emptyList())
    val lists: StateFlow<List<com.karakept.app.data.remote.model.ListDto>> = _lists

    // All bookmarks without filtering - for tag extraction
    val allBookmarks = selectedServer
        .flatMapLatest { server ->
            if (server != null) {
                bookmarkRepository.getBookmarks(server)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initialize selected server
        screenModelScope.launch {
            servers.collect { serverList ->
                if (_selectedServer.value == null && serverList.isNotEmpty()) {
                    _selectedServer.value = serverList.first()
                    loadLists()
                } else if (serverList.isEmpty()) {
                    _selectedServer.value = null
                }
            }
        }

        // Load default filter on startup
        screenModelScope.launch {
            savedFilterRepository.getDefaultFilter()?.let { saved ->
                try {
                    val config = kotlinx.serialization.json.Json.decodeFromString<FilterConfig>(saved.configJson)
                    _currentFilter.value = config
                } catch (e: Exception) {
                    e.printStackTrace()
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
    }.stateIn(screenModelScope, SharingStarted.Lazily, emptyList())

    private fun applyFilterToBookmarks(bookmarks: List<BookmarkEntity>, filter: FilterConfig): List<BookmarkEntity> {
        var result = bookmarks

        // 1. Status Filter
        result = when (filter.status) {
            FilterStatus.FAVORITES -> result.filter { it.isStarred }
            FilterStatus.ARCHIVED -> result.filter { it.isArchived }
            FilterStatus.NOT_ARCHIVED -> result.filter { !it.isArchived }
            FilterStatus.ALL -> result
        }

        // 2. Tags Filter (OR logic: bookmark must have AT LEAST ONE of the selected tags)
        if (filter.tags.isNotEmpty()) {
            result = result.filter { bookmark ->
                val bookmarkTags = bookmark.tags.split(",").filter { it.isNotEmpty() }
                filter.tags.any { tag -> bookmarkTags.contains(tag) }
            }
        }

        // 3. Lists Filter (OR logic: bookmark must be in AT LEAST ONE of the selected lists)
        // Usually list filtering is "Show me items in List A OR List B".
        if (filter.lists.isNotEmpty()) {
            result = result.filter { bookmark ->
                val bookmarkLists = bookmark.listIds.split(",").filter { it.isNotEmpty() }
                filter.lists.any { listId -> bookmarkLists.contains(listId) }
            }
        }

        // 4. Sort
        result = when (filter.sort) {
            SortOption.NEWEST -> result.sortedByDescending { it.createdAt }
            SortOption.OLDEST -> result.sortedBy { it.createdAt }
            SortOption.TITLE_AZ -> result.sortedBy { it.title.lowercase() }
            SortOption.TITLE_ZA -> result.sortedByDescending { it.title.lowercase() }
        }

        return result
    }

    fun selectServer(serverId: String) {
        screenModelScope.launch {
            val server = servers.value.find { it.id == serverId }
            _selectedServer.value = server
        }
    }

    fun syncBookmarks() {
        screenModelScope.launch {
            selectedServer.value?.let { server ->
                try {
                    _isSyncing.value = true
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        bookmarkRepository.syncBookmarks(server)
                    }
                } catch (e: Exception) {
                    // Handle error
                } finally {
                    _isSyncing.value = false
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

    fun applyFilter(filter: FilterConfig) {
        _currentFilter.value = filter
    }

    fun clearFilter() {
        _currentFilter.value = FilterConfig()
    }
    
    fun saveFilter(name: String, icon: String = "📋", color: Long? = null, isDefault: Boolean = false) {
        screenModelScope.launch {
            val configJson = kotlinx.serialization.json.Json.encodeToString(FilterConfig.serializer(), _currentFilter.value)
            savedFilterRepository.saveFilter(
                name = name,
                icon = icon,
                color = color,
                configJson = configJson,
                isDefault = isDefault,
                isVisibleInDrawer = true
            )
        }
    }
    
    fun deleteSavedFilter(filter: com.karakept.app.data.local.entity.SavedFilterEntity) {
        screenModelScope.launch {
            savedFilterRepository.deleteFilter(filter)
        }
    }
    
    fun updateSavedFilterName(filter: com.karakept.app.data.local.entity.SavedFilterEntity, newName: String) {
        screenModelScope.launch {
            val updated = filter.copy(name = newName)
            savedFilterRepository.updateFilter(updated)
        }
    }
    
    fun applySavedFilter(filter: com.karakept.app.data.local.entity.SavedFilterEntity) {
        screenModelScope.launch {
            try {
                val config = kotlinx.serialization.json.Json.decodeFromString(FilterConfig.serializer(), filter.configJson)
                _currentFilter.value = config
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Bookmark Actions
    
    fun toggleBookmarkArchive(bookmark: BookmarkEntity, onActionComplete: (String) -> Unit = {}) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value // Simple check, could be improved
            if (bookmark.isArchived) {
                bookmarkActionsRepository.unarchiveBookmark(bookmark.remoteId, bookmark.serverId)
                onActionComplete("Bookmark unarchived")
            } else {
                bookmarkActionsRepository.archiveBookmark(bookmark.remoteId, bookmark.serverId)
                onActionComplete("Bookmark archived")
            }
        }
    }
    
    fun toggleBookmarkFavorite(bookmark: BookmarkEntity, onActionComplete: (String) -> Unit = {}) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.toggleFavourite(
                bookmark.remoteId,
                bookmark.serverId,
                bookmark.isStarred
            )
            onActionComplete(if (bookmark.isStarred) "Removed from favorites" else "Added to favorites")
        }
    }
    
    fun toggleBookmarkRead(bookmark: BookmarkEntity, onActionComplete: (String) -> Unit = {}) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            if (bookmark.isRead) {
                val tags = bookmark.tags.split(",").filter { it.isNotBlank() }
                bookmarkActionsRepository.markAsUnread(bookmark.remoteId, bookmark.serverId, tags)
                onActionComplete("Marked as unread")
            } else {
                bookmarkActionsRepository.markAsRead(bookmark.remoteId, bookmark.serverId)
                onActionComplete("Marked as read")
            }
        }
    }
    
    fun deleteBookmark(bookmark: BookmarkEntity, onActionComplete: (String) -> Unit = {}) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.deleteBookmark(
                bookmark.localId,
                bookmark.remoteId,
                bookmark.serverId
            )
            onActionComplete("Bookmark deleted")
        }
    }
    
    fun updateBookmarkTags(bookmark: BookmarkEntity, newTags: List<String>) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.updateTags(
                bookmark.remoteId,
                bookmark.serverId,
                newTags,
                isOnline
            )
        }
    }
    
    fun moveBookmarkToList(bookmark: BookmarkEntity, listId: String) {
        screenModelScope.launch {
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.moveToList(
                bookmark.remoteId,
                bookmark.serverId,
                listId,
                isOnline
            )
        }
    }
}
