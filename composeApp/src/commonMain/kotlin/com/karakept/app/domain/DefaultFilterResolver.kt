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

    suspend fun resolve(): FilterConfig {
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
    }
}
