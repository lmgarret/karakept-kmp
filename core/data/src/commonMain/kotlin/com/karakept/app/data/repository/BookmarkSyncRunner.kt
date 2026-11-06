package com.karakept.app.data.repository

import com.karakept.app.data.cache.ImageCacheManager
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.SYNC_KEY_ARCHIVED
import com.karakept.app.data.model.SYNC_KEY_FAVORITES
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncKey
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs [BookmarkSyncPipeline]s for [BookmarkRepository] and owns everything that outlives one
 * run: per-key deduplication, the progress and report flows screens observe, the
 * reading-progress ration, and the bookmarks the last run inserted.
 *
 * One instance per repository — its mutexes and key sets are what keeps two syncs of the same
 * view from overlapping, so it must never be constructed twice for the same repository.
 */
internal class BookmarkSyncRunner(
    private val bookmarkDao: BookmarkDao,
    private val assetDao: AssetDao,
    private val remoteDataSource: RemoteDataSource,
    private val bookmarkActionsRepository: BookmarkActionsRepository,
    private val settingsRepository: SettingsRepository,
    private val highlightRepository: HighlightRepository,
    private val imageCacheManager: ImageCacheManager,
    private val listDao: ListDao,
    private val appDispatchers: AppDispatchers,
    private val assetCache: BookmarkAssetCache,
    private val contentSync: BookmarkContentSync,
    private val offlineCacheRepository: OfflineCacheRepository?,
    private val scope: CoroutineScope,
) {
    private val syncKeys = SyncKeyTracker()
    val perKeyProgress: StateFlow<Map<SyncKey, ListSyncStatus>> = syncKeys.perKeyProgress

    // Kept for BackgroundSyncOrchestrator backward compatibility.
    private val _syncProgress = MutableStateFlow<com.karakept.app.data.model.SyncProgress>(com.karakept.app.data.model.SyncProgress.Idle)
    val syncProgress: StateFlow<com.karakept.app.data.model.SyncProgress> = _syncProgress.asStateFlow()

    // Emits a report whenever a pipeline finishes with non-fatal warnings, so partial
    // failures are surfaced to the user instead of silently reporting "sync complete" (Group H).
    private val _syncReports = kotlinx.coroutines.flow.MutableSharedFlow<com.karakept.app.data.model.SyncReport>(extraBufferCapacity = 16)
    val syncReports: kotlinx.coroutines.flow.SharedFlow<com.karakept.app.data.model.SyncReport> = _syncReports

    // Emits once when a background full sync (syncAllWithLists) finishes, so a foreground screen
    // can refresh its currently-displayed list in place to surface newly synced bookmarks.
    private val _backgroundSyncCompleted = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val backgroundSyncCompleted: kotlinx.coroutines.flow.SharedFlow<Unit> = _backgroundSyncCompleted

    // Emits the SyncKey of a pipeline that just committed a page of metadata. Lets a screen
    // refresh in place while a long sync is still running, instead of only at the end.
    // Conflates rather than buffers: a screen only needs to know "there is newer data".
    private val _pageCommitted = kotlinx.coroutines.flow.MutableSharedFlow<SyncKey>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val pageCommitted: kotlinx.coroutines.flow.SharedFlow<SyncKey> = _pageCommitted

    /** Bookmarks inserted during the last sync, used for per-list notification counts. */
    var lastSyncNewBookmarks: List<BookmarkEntity> = emptyList()
        private set

    /** Resets sync progress to Idle. Used when a sync is cancelled (not failed). */
    fun resetSyncProgress() {
        _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
    }

    // Rationing for the reading-progress pass. It is per server rather than per sync flavour:
    // a fan-out of list syncs would otherwise each run their own pass over the same library.
    private val lastProgressPullAt = mutableMapOf<String, Long>()
    private val progressPullMutex = Mutex()

    suspend fun tryAcquireReadingProgressPull(serverId: String, force: Boolean): Boolean =
        progressPullMutex.withLock {
            val now = System.currentTimeMillis()
            val last = lastProgressPullAt[serverId] ?: 0L
            if (!force && now - last < BookmarkRepository.PROGRESS_PULL_MIN_INTERVAL_MS) return@withLock false
            lastProgressPullAt[serverId] = now
            true
        }

    suspend fun syncAllWithLists(server: Server, skipKey: SyncKey = null): Int {
        val newCount = executeSyncPipeline(SyncConfiguration.Full(server))
        val lists = listDao.getListsForServerOnce(server.id)
        val semaphore = kotlinx.coroutines.sync.Semaphore(3)
        kotlinx.coroutines.coroutineScope {
            lists.forEach { list ->
                val listId = list.remoteId
                if (listId == skipKey) return@forEach
                launch {
                    semaphore.acquire()
                    try {
                        executeSyncPipeline(SyncConfiguration.ForList(server, listId))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.w("BookmarkRepo", "List membership sync failed for $listId: ${e.message}")
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
        _backgroundSyncCompleted.tryEmit(Unit)
        return newCount
    }

    // After every sync rather than on a timer of its own: that is when read state and deletions
    // have just caught up with the server. Off the caller's path — nothing on screen waits on it.
    private fun cleanUpOfflineCache() {
        val cleaner = offlineCacheRepository ?: return
        scope.launch {
            try {
                cleaner.cleanUpAfterSync()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BookmarkRepo", "Offline cache cleanup failed: ${e.message}", e)
            }
        }
    }

    /**
     * Executes the sync pipeline with the given configuration.
     * Uses per-key deduplication: if a pipeline for the same key is already running,
     * this call returns 0 immediately without starting a new pipeline.
     * Returns the number of new bookmarks inserted.
     */
    suspend fun executeSyncPipeline(
        config: SyncConfiguration,
        forceProgressPull: Boolean = false
    ): Int = withContext(appDispatchers.io) {
        val key: SyncKey = when (config) {
            is SyncConfiguration.Full -> null
            is SyncConfiguration.Filtered -> if (config.favourited == true) SYNC_KEY_FAVORITES else SYNC_KEY_ARCHIVED
            is SyncConfiguration.ForList -> config.listId
        }

        if (!syncKeys.tryAcquireKey(key)) {
            // A skipped list pass is a silently missed membership reconcile — the caller
            // gets 0 back and cannot tell it from "nothing to do".
            AppLogger.d("BookmarkRepo", "SKIPPED sync for key=$key — already in progress")
            return@withContext 0
        }

        syncKeys.setKeyStatus(key, ListSyncStatus.FetchingMetadata())
        // The key is handed off from the foreground stage to enrichment mid-run, so the
        // finally block must not release a key a *newer* sync has since acquired.
        var foregroundKeyReleased = false
        var holdsEnrichmentKey = false
        try {
            val pipeline = BookmarkSyncPipeline(
                config = config,
                bookmarkDao = bookmarkDao,
                assetDao = assetDao,
                remoteDataSource = remoteDataSource,
                bookmarkActionsRepository = bookmarkActionsRepository,
                settingsRepository = settingsRepository,
                highlightRepository = highlightRepository,
                imageCacheManager = imageCacheManager,
                listDao = listDao,
                syncProgress = _syncProgress,
                fetchRemoteContent = contentSync::fetchRemoteContent,
                cacheHeroAssetsForBookmark = assetCache::cacheHeroAssets,
                onProgress = { status -> syncKeys.setKeyStatus(key, status) },
                onPageCommitted = { _pageCommitted.tryEmit(key) },
                // Drop the busy indicator AND release the key once the visible rows have
                // landed. Holding it through enrichment made a pull-to-refresh during a long
                // content download hit the dedup check and silently do nothing.
                onForegroundComplete = {
                    syncKeys.setKeyStatus(key, ListSyncStatus.Idle)
                    syncKeys.releaseKey(key)
                    foregroundKeyReleased = true
                },
                shouldRunEnrichment = {
                    syncKeys.tryAcquireEnrichmentKey(key).also { holdsEnrichmentKey = it }
                },
                shouldPullReadingProgress = {
                    tryAcquireReadingProgressPull(
                        serverId = config.server.id,
                        // A full sync covers the library, and the view on screen is the one
                        // whose staleness the user can see. Everything else takes its turn.
                        force = config is SyncConfiguration.Full || forceProgressPull
                    )
                }
            )
            val result = pipeline.execute()
            lastSyncNewBookmarks = pipeline.newlyInsertedBookmarks
            cleanUpOfflineCache()
            if (pipeline.warnings.isNotEmpty()) {
                _syncReports.tryEmit(
                    com.karakept.app.data.model.SyncReport(key, result, pipeline.warnings)
                )
            }
            return@withContext result
        } catch (e: kotlinx.coroutines.CancellationException) {
            _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
            throw e
        } catch (e: Exception) {
            AppLogger.e("BookmarkRepo", "Failed to fetch bookmarks: ${e.message}", e)
            _syncProgress.value = com.karakept.app.data.model.SyncProgress.Error(e.message ?: "Unknown error")
            throw e
        } finally {
            if (!foregroundKeyReleased) syncKeys.releaseKey(key)
            if (holdsEnrichmentKey) syncKeys.releaseEnrichmentKey(key)
            syncKeys.setKeyStatus(key, ListSyncStatus.Idle)
            if (_syncProgress.value !is com.karakept.app.data.model.SyncProgress.Error) {
                _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
            }
        }
    }
}
