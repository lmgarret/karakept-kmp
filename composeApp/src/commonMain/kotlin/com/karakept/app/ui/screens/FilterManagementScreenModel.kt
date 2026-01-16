package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.local.entity.SavedFilterEntity
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.data.repository.SavedFilterRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.api.model.KarakeepList as KarakeepList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class FilterManagementScreenModel(
    private val savedFilterRepository: SavedFilterRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val serverRepository: ServerRepository,
    private val remoteDataSource: RemoteDataSource
) : ScreenModel {

    val visibleFilters = savedFilterRepository.visibleFilters
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val hiddenFilters = savedFilterRepository.hiddenFilters
        .map { filters -> filters.filter { !it.isDefault } }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All filters (including defaults AND quick filters) for logic checks
    val allSavedFilters = savedFilterRepository.savedFilters
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Data for editing filters
    private val selectedServer = serverRepository.servers
        .map { it.firstOrNull() } // Simplified: assume first server
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    // All bookmarks for tag calculation
    private val allBookmarks = selectedServer.flatMapLatest { server ->
        if (server != null) {
            bookmarkRepository.getBookmarks(server)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Top 10 most used tags with counts
    val topTagsWithCounts = allBookmarks.map { bookmarks ->
        bookmarks
            .flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All tags for the dialog
    val availableTags = allBookmarks.map { bookmarks ->
        bookmarks.flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }
            .distinct()
            .sortedBy { it.lowercase() }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _availableLists = MutableStateFlow<List<KarakeepList>>(emptyList())
    val availableLists: StateFlow<List<KarakeepList>> = _availableLists

    init {
        screenModelScope.launch {
            selectedServer.collect { server ->
                if (server != null) {
                    try {
                        _availableLists.value = remoteDataSource.fetchLists(server)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun updateFilterAppearance(filter: SavedFilterEntity, newIcon: String, newColor: Long?) {
        screenModelScope.launch {
            savedFilterRepository.updateFilter(filter.copy(icon = newIcon, color = newColor))
        }
    }

    fun updateFilterConfig(filter: SavedFilterEntity, newConfig: FilterConfig) {
        screenModelScope.launch {
            val configJson = Json.encodeToString(FilterConfig.serializer(), newConfig)
            savedFilterRepository.updateFilter(filter.copy(configJson = configJson))
        }
    }

    fun toggleFilterVisibility(filter: SavedFilterEntity) {
        screenModelScope.launch {
            savedFilterRepository.toggleFilterVisibility(filter)
        }
    }

    fun setFilterAsDefault(filter: SavedFilterEntity) {
        screenModelScope.launch {
            savedFilterRepository.updateFilter(filter.copy(isDefault = true))
        }
    }

    fun unsetDefaultFilter(filter: SavedFilterEntity) {
        screenModelScope.launch {
            savedFilterRepository.updateFilter(filter.copy(isDefault = false))
        }
    }

    fun deleteFilter(filter: SavedFilterEntity) {
        screenModelScope.launch {
            savedFilterRepository.deleteFilter(filter)
        }
    }

    fun reorderVisibleFilters(filters: List<SavedFilterEntity>) {
        screenModelScope.launch {
            savedFilterRepository.reorderFilters(filters)
        }
    }

    fun reorderHiddenFilters(filters: List<SavedFilterEntity>) {
        screenModelScope.launch {
            savedFilterRepository.reorderFilters(filters)
        }
    }

    fun moveFilterToVisible(filter: SavedFilterEntity) {
        screenModelScope.launch {
            savedFilterRepository.updateFilter(filter.copy(isVisibleInDrawer = true))
        }
    }

    fun moveFilterToHidden(filter: SavedFilterEntity) {
        screenModelScope.launch {
            savedFilterRepository.updateFilter(filter.copy(isVisibleInDrawer = false))
        }
    }

    fun saveNewFilter(name: String, icon: String, color: Long?, config: FilterConfig, isDefault: Boolean, isVisibleInDrawer: Boolean = true, isQuickFilter: Boolean = false) {
        screenModelScope.launch {
            val configJson = Json.encodeToString(FilterConfig.serializer(), config)
            savedFilterRepository.saveFilter(
                name = name,
                icon = icon,
                color = color,
                configJson = configJson,
                isDefault = isDefault,
                isVisibleInDrawer = isVisibleInDrawer,
                isQuickFilter = isQuickFilter
            )
        }
    }
}
