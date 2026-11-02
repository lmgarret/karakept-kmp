package com.karakept.app.data.repository

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.domain.OfflineScope
import kotlinx.coroutines.flow.first

/** The [OfflineScope] the current settings describe, with [allLists] for list hierarchy. */
internal suspend fun SettingsRepository.resolveOfflineScope(allLists: List<KarakeepList>): OfflineScope =
    OfflineScope.resolve(
        strategy = contentSyncStrategy.first(),
        syncConfig = contentSyncConfig.first(),
        listSettings = allListSettings.first(),
        allLists = allLists
    )

internal fun ListEntity.toHierarchyNode(): KarakeepList = KarakeepList(id = remoteId, name = name, parentId = parentId)
