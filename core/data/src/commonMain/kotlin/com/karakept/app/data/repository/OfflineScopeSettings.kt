package com.karakept.app.data.repository

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.ListSyncConfig
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.domain.OfflineScope
import kotlinx.coroutines.flow.first

/** The [OfflineScope] the current settings describe, with [allLists] for list hierarchy. */
suspend fun SettingsRepository.resolveOfflineScope(allLists: List<KarakeepList>): OfflineScope {
    val strategy = contentSyncStrategy.first()
    return OfflineScope.resolve(
        strategy = strategy,
        // Only Per list has lists of its own to read.
        syncConfig = if (strategy == SyncStrategy.PER_LIST) contentSyncConfig.first() else ListSyncConfig(emptySet(), emptySet()),
        listSettings = allListSettings.first(),
        allLists = allLists
    )
}

fun ListEntity.toHierarchyNode(): KarakeepList = KarakeepList(id = remoteId, name = name, parentId = parentId)
