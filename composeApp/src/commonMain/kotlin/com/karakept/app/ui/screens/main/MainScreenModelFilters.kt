package com.karakept.app.ui.screens.main

import androidx.lifecycle.viewModelScope
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.repository.setDefaultListId
import com.karakept.app.data.repository.setDefaultListType
import com.karakept.app.domain.ListHierarchyUtils
import com.karakept.app.ui.components.scrollToTop
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Filter, search, server and drawer-expansion state changes for MainScreenModel.

fun MainScreenModel.applyFilter(filter: FilterConfig) {
    AppLogger.d("MainScreenModel", "applyFilter: status=${filter.status} lists=${filter.lists} tags=${filter.tags}")
    _currentFilter.value = filter
    _currentListContext.value = if (filter.lists.size == 1) filter.lists.first() else null
    if (filter.lists.size == 1) {
        _expandedLists.value = _expandedLists.value +
            ListHierarchyUtils.getAncestorIds(filter.lists.first(), lists.value)
    }
    // scrollToTop() removed — resetPaginationAndLoad emits it after the new data is ready
    persistActiveFilter(filter)
}

fun MainScreenModel.applyTagFilter(tag: String, sourceBookmarkId: Long) {
    _tagFilterSourceBookmarkId.value = sourceBookmarkId
    applyFilter(FilterConfig(tags = listOf(tag)))
}

fun MainScreenModel.consumeTagFilterSource(): Long? {
    val id = _tagFilterSourceBookmarkId.value
    _tagFilterSourceBookmarkId.value = null
    return id
}

fun MainScreenModel.clearFilter() {
    AppLogger.d("MainScreenModel", "clearFilter")
    _currentFilter.value = FilterConfig()
    _currentListContext.value = null
    _tagFilterSourceBookmarkId.value = null
    // scrollToTop() removed — resetPaginationAndLoad emits it after the new data is ready
    persistActiveFilter(FilterConfig())
}

private fun MainScreenModel.persistActiveFilter(filter: FilterConfig) {
    viewModelScope.launch {
        settingsRepository.saveLastActiveFilter(
            status = filter.status.name,
            listId = filter.lists.singleOrNull()
        )
    }
}

fun MainScreenModel.setDefaultList(listId: String) {
    viewModelScope.launch {
        settingsRepository.setDefaultListType(DefaultListType.SPECIFIC_LIST)
        settingsRepository.setDefaultListId(listId)
    }
}

fun MainScreenModel.setDefaultListType(type: DefaultListType) {
    viewModelScope.launch {
        settingsRepository.setDefaultListType(type)
        if (type != DefaultListType.SPECIFIC_LIST) {
            settingsRepository.setDefaultListId(null)
        }
    }
}

fun MainScreenModel.toggleListExpanded(listId: String) {
    _expandedLists.value = if (_expandedLists.value.contains(listId)) {
        _expandedLists.value - listId
    } else {
        _expandedLists.value + listId
    }
}

fun MainScreenModel.updateSearchQuery(query: String) {
    _searchQuery.value = query
}

fun MainScreenModel.clearSearch() {
    _searchQuery.value = ""
}

fun MainScreenModel.selectServer(serverId: String) {
    viewModelScope.launch {
        val server = servers.value.find { it.id == serverId }
        _selectedServer.value = server
    }
}

// tryEmit rather than launch { emit }: the trigger is consumed alongside a list swap, and
// an extra coroutine hop is enough to push the scroll reset a frame past it. The flow has
// spare buffer capacity, so the emission always lands.
fun MainScreenModel.scrollToTop() {
    _scrollToTopTrigger.tryEmit(Unit)
}

internal fun MainScreenModel.loadLists() {
    viewModelScope.launch {
        selectedServer.value?.let { server ->
            listRepository.refreshLists(server)
        }
    }
}
