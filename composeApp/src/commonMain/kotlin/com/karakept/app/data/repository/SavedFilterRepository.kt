package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.SavedFilterDao
import com.karakept.app.data.local.entity.SavedFilterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SavedFilterRepository(
    private val savedFilterDao: SavedFilterDao
) {
    val savedFilters: Flow<List<SavedFilterEntity>> = savedFilterDao.getAllSavedFilters()
    val visibleFilters: Flow<List<SavedFilterEntity>> = savedFilterDao.getVisibleFilters()
        .map { filters -> filters.filter { !it.isQuickFilter } }
    val hiddenFilters: Flow<List<SavedFilterEntity>> = savedFilterDao.getHiddenFilters()
        .map { filters -> filters.filter { !it.isQuickFilter && !it.isDefault } }

    suspend fun saveFilter(
        name: String,
        icon: String = "📋",
        color: Long? = null,
        configJson: String,
        isDefault: Boolean = false,
        isVisibleInDrawer: Boolean = true,
        isQuickFilter: Boolean = false
    ) {
        if (isDefault) {
            val oldDefault = savedFilterDao.getDefaultFilter()
            savedFilterDao.clearDefaultFilter()
            // Delete old default if it was a quick filter
            if (oldDefault != null && oldDefault.isQuickFilter) {
                savedFilterDao.deleteSavedFilter(oldDefault)
            }
        }
        // Get the next display order
        val filters = savedFilterDao.getAllSavedFilters()
        // Since this is a Flow, we need to collect it once or use a different approach
        // For simplicity, we'll set displayOrder to 0 and let the UI handle reordering
        savedFilterDao.insertSavedFilter(
            SavedFilterEntity(
                name = name,
                icon = icon,
                color = color,
                configJson = configJson,
                isDefault = isDefault,
                displayOrder = 0,
                isVisibleInDrawer = isVisibleInDrawer,
                isQuickFilter = isQuickFilter
            )
        )
    }

    suspend fun updateFilter(filter: SavedFilterEntity) {
        if (filter.isDefault) {
            // Get the old default before clearing
            val oldDefault = savedFilterDao.getDefaultFilter()
            savedFilterDao.clearDefaultFilter()
            // Delete old default if it was a quick filter
            if (oldDefault != null && oldDefault.isQuickFilter) {
                savedFilterDao.deleteSavedFilter(oldDefault)
            }
        }
        savedFilterDao.updateSavedFilter(filter)
    }

    suspend fun updateFilterIcon(filter: SavedFilterEntity, newIcon: String) {
        savedFilterDao.updateSavedFilter(filter.copy(icon = newIcon))
    }

    suspend fun toggleFilterVisibility(filter: SavedFilterEntity) {
        savedFilterDao.updateSavedFilter(filter.copy(isVisibleInDrawer = !filter.isVisibleInDrawer))
    }

    suspend fun reorderFilters(filters: List<SavedFilterEntity>) {
        // Update displayOrder for all filters in the list
        val updatedFilters = filters.mapIndexed { index, filter ->
            filter.copy(displayOrder = index)
        }
        savedFilterDao.updateSavedFilters(updatedFilters)
    }

    suspend fun deleteFilter(filter: SavedFilterEntity) {
        savedFilterDao.deleteSavedFilter(filter)
    }

    suspend fun getDefaultFilter(): SavedFilterEntity? {
        return savedFilterDao.getDefaultFilter()
    }
}
