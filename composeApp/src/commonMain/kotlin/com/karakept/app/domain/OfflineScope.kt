package com.karakept.app.domain

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.ListSyncConfig
import com.karakept.app.data.model.SyncStrategy

/**
 * Which bookmarks keep an offline copy: everything under [SyncStrategy.ALL], the strategy's
 * lists under [SyncStrategy.PER_LIST], what the user opened under [SyncStrategy.PER_BOOKMARK],
 * and the lists whose own "Sync offline" toggle is on under any strategy.
 *
 * Sync downloads what this covers and cleanup evicts what it does not, so both must build it
 * the same way — two definitions that disagree download and delete the same bookmark on every
 * pass.
 */
class OfflineScope(
    val strategy: SyncStrategy,
    val strategyListIds: Set<String>,
    val offlineListIds: Set<String>
) {
    /** Whether every bookmark is covered, so nothing is ever out of scope. */
    val coversEverything: Boolean get() = strategy == SyncStrategy.ALL

    /**
     * [opened] is whether the user has opened the bookmark: under [SyncStrategy.PER_BOOKMARK]
     * that is what stored its copy. Sync passes false — the reader stores those copies, sync
     * never downloads them.
     */
    fun covers(listIds: String, opened: Boolean): Boolean = when (strategy) {
        SyncStrategy.ALL -> true
        SyncStrategy.PER_LIST -> intersects(listIds, strategyListIds)
        SyncStrategy.PER_BOOKMARK -> opened
        SyncStrategy.NEVER -> false
    } || intersects(listIds, offlineListIds)

    private fun intersects(listIds: String, targets: Set<String>): Boolean =
        targets.isNotEmpty() && listIds.split(",").any { it.isNotEmpty() && it in targets }

    companion object {
        /**
         * [allLists] resolves the "with children" modes of both the strategy's lists and the
         * per-list toggles; a list missing from it contributes no descendants.
         */
        fun resolve(
            strategy: SyncStrategy,
            syncConfig: ListSyncConfig,
            listSettings: Map<String, ListSettings>,
            allLists: List<KarakeepList>
        ): OfflineScope {
            val strategyLists = if (strategy == SyncStrategy.PER_LIST) {
                syncConfig.getEffectiveSyncLists(allLists)
            } else emptySet()

            val offlineLists = mutableSetOf<String>()
            for ((listId, settings) in listSettings) {
                if (!settings.syncOffline) continue
                offlineLists += listId
                if (settings.includeChildListBookmarks) {
                    offlineLists += ListHierarchyUtils.getAllDescendantIds(listId, allLists)
                }
            }
            return OfflineScope(strategy, strategyLists, offlineLists)
        }
    }
}
