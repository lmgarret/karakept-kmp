package com.karakept.app.domain

import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Resolves the user's persisted default-list setting into a [FilterConfig].
 *
 * Reading [defaultListType] and [defaultListId] are done atomically via [combine]
 * so that a concurrent settings write between the two reads cannot produce an
 * inconsistent result (e.g. type = SPECIFIC_LIST but id = null because the id
 * hasn't been written yet).
 */
class DefaultFilterResolver(private val settingsRepository: SettingsRepository) {

    /**
     * Resolves the startup filter: prefers the last-active filter (persisted on
     * every filter change) so the user returns to where they left off. Falls back
     * to the configured "default list" setting on first launch or when the
     * last-active filter has no data.
     */
    suspend fun resolve(): FilterConfig {
        // Try last-active filter first (survives process death).
        val (lastStatus, lastListId) = combine(
            settingsRepository.lastActiveFilterStatus,
            settingsRepository.lastActiveFilterListId
        ) { s, l -> s to l }.first()

        if (lastStatus != null || lastListId != null) {
            return buildLastActiveFilter(lastStatus, lastListId)
        }

        // Fallback: configured default list.
        val (type, id) = combine(
            settingsRepository.defaultListType,
            settingsRepository.defaultListId
        ) { type, id -> type to id }.first()
        return buildFilter(type, id)
    }

    companion object {
        /**
         * Pure function — testable without a real [SettingsRepository].
         */
        fun buildFilter(type: DefaultListType, listId: String?): FilterConfig = when (type) {
            DefaultListType.ALL_BOOKMARKS -> FilterConfig()
            DefaultListType.FAVORITES     -> FilterConfig(status = FilterStatus.FAVORITES)
            DefaultListType.ARCHIVED      -> FilterConfig(status = FilterStatus.ARCHIVED)
            DefaultListType.SPECIFIC_LIST -> if (listId != null) {
                FilterConfig(lists = listOf(listId))
            } else {
                FilterConfig()
            }
        }

        /**
         * Reconstructs a [FilterConfig] from persisted last-active state.
         */
        fun buildLastActiveFilter(status: String?, listId: String?): FilterConfig {
            val filterStatus = status?.let {
                try { FilterStatus.valueOf(it) } catch (_: Exception) { FilterStatus.ALL }
            } ?: FilterStatus.ALL
            val lists = if (listId != null) listOf(listId) else emptyList()
            return FilterConfig(status = filterStatus, lists = lists)
        }
    }
}
