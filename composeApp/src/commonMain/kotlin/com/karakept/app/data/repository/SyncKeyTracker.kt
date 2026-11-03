package com.karakept.app.data.repository

import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.SyncKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SyncKeyTracker {
    // Per-key sync status, keyed by SyncKey (null = All, sentinel = Favorites/Archived, listId = list).
    // Idle entries are removed from the map to keep it small.
    private val _perKeyProgress = MutableStateFlow<Map<SyncKey, ListSyncStatus>>(emptyMap())
    val perKeyProgress: StateFlow<Map<SyncKey, ListSyncStatus>> = _perKeyProgress.asStateFlow()

    fun setKeyStatus(key: SyncKey, status: ListSyncStatus) {
        _perKeyProgress.update { map ->
            if (status is ListSyncStatus.Idle) map - key else map + (key to status)
        }
    }

    // Deduplication: track which SyncKeys are currently running a pipeline.
    private val keysMutex = Mutex()
    private val activeKeys = mutableSetOf<SyncKey>()

    suspend fun tryAcquireKey(key: SyncKey): Boolean = keysMutex.withLock {
        if (key in activeKeys) return@withLock false
        activeKeys.add(key)
        true
    }

    suspend fun releaseKey(key: SyncKey) = keysMutex.withLock { activeKeys.remove(key) }

    // Enrichment (highlights, content download, reading progress) outlives the foreground
    // stage and is deduplicated separately, so releasing the sync key early doesn't let two
    // syncs download the same content at once.
    private val enrichmentKeys = mutableSetOf<SyncKey>()

    suspend fun tryAcquireEnrichmentKey(key: SyncKey): Boolean = keysMutex.withLock {
        if (key in enrichmentKeys) return@withLock false
        enrichmentKeys.add(key)
        true
    }

    suspend fun releaseEnrichmentKey(key: SyncKey) =
        keysMutex.withLock { enrichmentKeys.remove(key) }
}
