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
     * Resolves the startup filter from the configured "default list" setting —
     * the home view always wins on startup. Only when the user explicitly picked
     * [DefaultListType.LAST_VIEWED] is the last-active filter (persisted on every
     * filter change) restored instead.
     */
    suspend fun resolve(): FilterConfig {
        val (type, id) = combine(
            settingsRepository.defaultListType,
            settingsRepository.defaultListId
        ) { type, id -> type to id }.first()

        if (type != DefaultListType.LAST_VIEWED) return buildFilter(type, id)

        val (lastStatus, lastListId) = combine(
            settingsRepository.lastActiveFilterStatus,
            settingsRepository.lastActiveFilterListId
        ) { s, l -> s to l }.first()
        return buildLastActiveFilter(lastStatus, lastListId)
    }

    companion object {
        /**
         * Pure function — testable without a real [SettingsRepository].
         */
        fun buildFilter(type: DefaultListType, listId: String?): FilterConfig = when (type) {
            DefaultListType.ALL_BOOKMARKS -> FilterConfig()
            DefaultListType.FAVORITES     -> FilterConfig(status = FilterStatus.FAVORITES)
            DefaultListType.ARCHIVED      -> FilterConfig(status = FilterStatus.ARCHIVED)
            // ALL_INCLUDING_ARCHIVED is what tapping the list in the drawer produces, so opening
            // on it at startup lands on the same view rather than a near-identical one that
            // hides the list's archived bookmarks and reloads the moment the list is tapped.
            DefaultListType.SPECIFIC_LIST -> if (listId != null) {
                FilterConfig(status = FilterStatus.ALL_INCLUDING_ARCHIVED, lists = listOf(listId))
            } else {
                FilterConfig()
            }
            // Resolved from the persisted last-active filter by [resolve]; the
            // empty filter is the fallback when nothing has been persisted yet.
            DefaultListType.LAST_VIEWED -> FilterConfig()
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
