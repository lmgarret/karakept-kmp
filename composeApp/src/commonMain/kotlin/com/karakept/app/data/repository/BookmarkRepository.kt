package com.karakept.app.data.repository

import androidx.room.RoomRawQuery
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.SYNC_KEY_ARCHIVED
import com.karakept.app.data.model.SYNC_KEY_FAVORITES
import com.karakept.app.data.model.SyncKey
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.ReadingTimeCalculator
import com.karakept.app.utils.ImageCacheManager
import com.karakept.app.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

class BookmarkRepository(
    private val bookmarkDao: BookmarkDao,
    private val assetDao: AssetDao,
    private val remoteDataSource: RemoteDataSource,
    private val bookmarkActionsRepository: com.karakept.app.data.repository.BookmarkActionsRepository,
    private val settingsRepository: com.karakept.app.data.repository.SettingsRepository,
    private val serverRepository: com.karakept.app.data.repository.ServerRepository,
    private val highlightRepository: com.karakept.app.data.repository.HighlightRepository,
    private val imageCacheManager: ImageCacheManager,
    private val listDao: ListDao
) {
    fun getBookmarks(server: Server): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getBookmarksForServer(server.id)
    }

    fun getOfflineBookmarkCount(serverId: String): Flow<Int> {
        return bookmarkDao.getOfflineCountFlow(serverId)
    }

    suspend fun getBookmarkByRemoteId(remoteId: Long, serverId: String): BookmarkEntity? {
        return bookmarkDao.getBookmarkByRemoteId(remoteId, serverId)
    }

    // Per-key sync status, keyed by SyncKey (null = All, sentinel = Favorites/Archived, listId = list).
    // Idle entries are removed from the map to keep it small.
    private val _perKeyProgress = MutableStateFlow<Map<SyncKey, ListSyncStatus>>(emptyMap())
    val perKeyProgress: StateFlow<Map<SyncKey, ListSyncStatus>> = _perKeyProgress.asStateFlow()

    private fun setKeyStatus(key: SyncKey, status: ListSyncStatus) {
        _perKeyProgress.update { map ->
            if (status is ListSyncStatus.Idle) map - key else map + (key to status)
        }
    }

    // Deduplication: track which SyncKeys are currently running a pipeline.
    private val keysMutex = Mutex()
    private val activeKeys = mutableSetOf<SyncKey>()

    private suspend fun tryAcquireKey(key: SyncKey): Boolean = keysMutex.withLock {
        if (key in activeKeys) return@withLock false
        activeKeys.add(key)
        true
    }

    private suspend fun releaseKey(key: SyncKey) = keysMutex.withLock { activeKeys.remove(key) }

    // Enrichment (highlights, content download, reading progress) outlives the foreground
    // stage and is deduplicated separately, so releasing the sync key early doesn't let two
    // syncs download the same content at once.
    private val enrichmentKeys = mutableSetOf<SyncKey>()

    private suspend fun tryAcquireEnrichmentKey(key: SyncKey): Boolean = keysMutex.withLock {
        if (key in enrichmentKeys) return@withLock false
        enrichmentKeys.add(key)
        true
    }

    private suspend fun releaseEnrichmentKey(key: SyncKey) =
        keysMutex.withLock { enrichmentKeys.remove(key) }

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
    private var _lastSyncNewBookmarks: List<BookmarkEntity> = emptyList()

    /** Resets sync progress to Idle. Used when a sync is cancelled (not failed). */
    fun resetSyncProgress() {
        _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
    }

    // Last successful top-level auto-sync per server (epoch millis). Lives here (Koin single)
    // so it survives MainScreenModel recreation — the ScreenModel is rebuilt whenever its
    // Nav3 entry re-enters the back stack (e.g. returning from the reader), which used to
    // fire a fresh full sync every time (#276).
    private val lastAutoSyncCompletedAt = mutableMapOf<String, Long>()
    private val autoSyncMutex = Mutex()

    /** True if enough time has elapsed since the last auto-sync to run another one. */
    suspend fun shouldAutoSync(serverId: String, minIntervalMs: Long = 15 * 60_000L): Boolean =
        autoSyncMutex.withLock {
            val last = lastAutoSyncCompletedAt[serverId] ?: return@withLock true
            System.currentTimeMillis() - last >= minIntervalMs
        }

    /** Records a successful auto-sync so [shouldAutoSync] throttles the next one. */
    suspend fun markAutoSyncCompleted(serverId: String) = autoSyncMutex.withLock {
        lastAutoSyncCompletedAt[serverId] = System.currentTimeMillis()
    }

    suspend fun syncBookmarks(server: Server): Int =
        executeSyncPipeline(SyncConfiguration.Full(server))

    /**
     * Full sync followed by a per-list membership pass. Each list is fetched exactly once
     * (Full no longer does the old O(lists) N+1 membership fetch). Used by callers that
     * lack MainScreenModel's own syncOtherLists loop — e.g. background sync.
     *
     * @param skipKey a list id already synced by the caller, skipped to avoid re-fetching.
     */
    suspend fun syncAllWithLists(server: Server, skipKey: SyncKey = null): Int {
        val newCount = syncBookmarks(server)
        val lists = listDao.getListsForServerOnce(server.id)
        val semaphore = kotlinx.coroutines.sync.Semaphore(3)
        kotlinx.coroutines.coroutineScope {
            lists.forEach { list ->
                val listId = list.remoteId
                if (listId == skipKey) return@forEach
                launch {
                    semaphore.acquire()
                    try {
                        syncBookmarksForList(server, listId)
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

    /**
     * Syncs only favorited bookmarks.
     */
    suspend fun syncFavorites(server: Server): Int =
        executeSyncPipeline(SyncConfiguration.Filtered(server, favourited = true))

    /**
     * Syncs only archived bookmarks.
     */
    suspend fun syncArchived(server: Server): Int =
        executeSyncPipeline(SyncConfiguration.Filtered(server, archived = true))

    /**
     * Syncs only bookmarks from a specific list.
     * Respects content sync mode (NEVER/PER_BOOKMARK/PER_LIST/ALL).
     */
    suspend fun syncBookmarksForList(server: Server, listId: String): Int =
        executeSyncPipeline(SyncConfiguration.ForList(server, listId))

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun createBookmark(url: String, onStatusChange: ((String) -> Unit)? = null): Result<BookmarkEntity> {
        onStatusChange?.invoke("Waiting for server response...")
        val serversList = serverRepository.servers.first()
        val server = serversList.firstOrNull() ?: return Result.failure(Exception("No server configured"))

        return try {
            var dto = remoteDataSource.createBookmark(server, url)

            // Polling for title/content (max 10 seconds)
            // Karakeep API takes some time to parse the URL
            var attempts = 0
            while (attempts < 15) { // Increased to 15 attempts (30 seconds total)
                val currentTitle = dto.title ?: dto.content?.title ?: ""
                if (currentTitle.isNotBlank() && currentTitle != "Untitled") break

                onStatusChange?.invoke("Waiting for bookmark to be parsed...")
                delay(2000) // Increased to 2 seconds
                try {
                    dto = remoteDataSource.fetchBookmark(server, dto.id ?: "")
                } catch (e: Exception) {
                    AppLogger.e("BookmarkRepository", "Polling fetch failed: ${e.message}")
                }
                attempts++
                AppLogger.d("BookmarkRepository", "Polling for bookmark parsing: attempt $attempts, title='${dto.title}', content.title='${dto.content?.title}'")
            }

            onStatusChange?.invoke("Finalizing bookmark...")

            // Initial map to entity
            val entity = BookmarkEntity(
                localId = 0L,
                remoteId = (dto.id ?: "").hashCode().toLong(),
                originalRemoteId = dto.id ?: "",
                serverId = server.id,
                title = dto.title ?: dto.content?.title ?: "Untitled",
                url = dto.content?.url ?: url,
                description = dto.content?.description,
                imageUrl = dto.content?.imageUrl,
                bannerImageAssetId = dto.assets?.find { it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.BANNER_IMAGE }?.id,
                screenshotAssetId = dto.assets?.find { it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.SCREENSHOT }?.id,
                tags = dto.tags?.joinToString(",") { it.name ?: "" } ?: "",
                listIds = "",
                isStarred = dto.favourited ?: false,
                isArchived = dto.archived ?: false,
                isRead = false,
                createdAt = try { Instant.parse(dto.createdAt ?: "").toEpochMilliseconds() } catch (e: Exception) { System.currentTimeMillis() },
                readingTimeMinutes = 0,
                content = "",
                crawlStatus = dto.content?.crawlStatus?.value,
                crawledAt = com.karakept.app.utils.parseIsoToEpochMillis(dto.content?.crawledAt)
            )

            // Insert into local DB
            bookmarkDao.insertBookmarks(listOf(entity))

            // Fetch the inserted entity to get the localId
            val inserted = bookmarkDao.getBookmarkByRemoteId(entity.remoteId, server.id)
                ?: entity

            // Trigger background sync for this single bookmark to get full content
            GlobalScope.launch(Dispatchers.Default) {
                try {
                    syncSingleBookmark(inserted.remoteId, server.id)
                } catch (e: Exception) {
                    AppLogger.e("BookmarkRepository", "Background sync failed for new bookmark: ${e.message}")
                }
            }

            Result.success(inserted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchBookmarkContent(bookmarkId: Long, serverId: String): String? {
        val server = serverRepository.servers.first().find { it.id == serverId } ?: return null

        // Find the ORIGINAL remote ID (string)
        val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkId, serverId) ?: return null

        return try {
            fetchRemoteContent(server, bookmark.originalRemoteId)
        } catch (e: Exception) {
            AppLogger.e("BookmarkRepo", "Failed to fetch content: ${e.message}", e)
            null
        }
    }

    suspend fun syncSingleBookmark(bookmarkId: Long, serverId: String) {
        val server = serverRepository.servers.first().find { it.id == serverId } ?: return
        val existing = bookmarkDao.getBookmarkByRemoteId(bookmarkId, serverId) ?: return

        try {
            // Note: intentionally NOT updating _syncProgress here. This method is called
            // from a background GlobalScope.launch after createBookmark and should not
            // interfere with the main sync progress state shown in the UI.
            val dto = remoteDataSource.fetchBookmark(server, existing.originalRemoteId)

            // Map DTO to entity, preserving localId and content if not provided in DTO
            val syncStrategy = settingsRepository.contentSyncStrategy.first()

            // We use the existing mapping logic but for a single bookmark
            // To reuse mapDtoToEntity, we need to wrap it in BookmarkSyncPipeline
            // Or just implement a simplified version here.

            val url = when (dto.content?.type) {
                com.karakept.api.model.BookmarkContent.Type.LINK -> dto.content?.url ?: ""
                com.karakept.api.model.BookmarkContent.Type.TEXT -> ""
                else -> dto.content?.url ?: ""
            }

            val title = dto.title ?: dto.content?.title ?: "Untitled"
            val incomingContent = dto.content?.htmlContent ?: dto.content?.text

            // Extract banner and screenshot asset IDs
            val bannerImageAssetId = dto.assets?.find { it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.BANNER_IMAGE }?.id
            val screenshotAssetId = dto.assets?.find { it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.SCREENSHOT }?.id

            // Fetch fresh list memberships from the server. Falls back to the existing
            // value on failure so a transient network issue doesn't wipe local state.
            val refreshedListIds = try {
                remoteDataSource.fetchListsForBookmark(server, existing.originalRemoteId)
                    .mapNotNull { it.id }
                    .joinToString(",")
            } catch (e: Exception) {
                AppLogger.e("BookmarkRepository", "Failed to fetch lists for bookmark ${existing.originalRemoteId}: ${e.message}")
                existing.listIds
            }

            // Update metadata — preserve local isRead (read status is client-side only)
            bookmarkDao.updateBookmarkMetadata(
                localId = existing.localId,
                title = title,
                url = url,
                description = dto.content?.description,
                imageUrl = dto.content?.imageUrl,
                bannerImageAssetId = bannerImageAssetId,
                screenshotAssetId = screenshotAssetId,
                tags = dto.tags?.joinToString(",") { it.name ?: "" } ?: "",
                listIds = refreshedListIds,
                isStarred = dto.favourited ?: false,
                isArchived = dto.archived ?: false,
                isRead = existing.isRead,
                readingTimeMinutes = existing.readingTimeMinutes, // Will update if content is fetched
                modifiedAt = dto.modifiedAt?.let {
                    try { Instant.parse(it).toEpochMilliseconds() } catch (e: Exception) { null }
                },
                crawlStatus = dto.content?.crawlStatus?.value ?: existing.crawlStatus,
                crawledAt = com.karakept.app.utils.parseIsoToEpochMillis(dto.content?.crawledAt)
                    ?: existing.crawledAt
            )

            // Now handle content if needed
            // For a force refresh, we should probably fetch content if it's available in the DTO
            // or if we should fetch it on demand.

            var finalContent = incomingContent

            if (finalContent.isNullOrBlank()) {
                val contentAsset = dto.assets?.find { it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.LINK_HTML_CONTENT }
                if (contentAsset != null) {
                    try {
                        val assetBytes = remoteDataSource.downloadAsset(server, contentAsset.id ?: "")
                        finalContent = assetBytes.decodeToString()
                    } catch (e: Exception) {
                        AppLogger.e("BookmarkRepository", "Failed to download content asset ${contentAsset.id}: ${e.message}")
                    }
                }
            }

            if (finalContent.isNullOrBlank()) {
                finalContent = dto.note ?: dto.content?.text
            }

            if (!finalContent.isNullOrBlank()) {
                // Cache images in HTML for offline reading
                val cachedContent = try {
                    imageCacheManager.cacheImagesInHtml(finalContent)
                } catch (e: Exception) {
                    AppLogger.e("BookmarkRepository", "Failed to cache images in HTML: ${e.message}")
                    finalContent
                }
                val readingTime = ReadingTimeCalculator.calculateReadingTime(cachedContent)
                bookmarkDao.updateContent(existing.localId, cachedContent, readingTime)
            }

            // Cache hero images (banner/screenshot)
            cacheHeroAssetsForBookmark(server, existing.remoteId, existing.serverId, bannerImageAssetId, screenshotAssetId)

            // Track server-side asset metadata (linkHtmlContent, fullPageArchive, precrawledArchive)
            // so the viewer knows what exists on the server even before downloading locally.
            insertContentAssetMetadata(dto, existing.remoteId, existing.serverId)

            // Cache fullPageArchive asset when user prefers it, so the viewer can switch
            // sources without touching the content field (which always stores extracted HTML).
            if (settingsRepository.preferFullPageHtml.first()) {
                val archiveAsset = dto.assets?.find {
                    it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.FULL_PAGE_ARCHIVE
                }
                if (archiveAsset?.id != null) {
                    cacheArchiveAsset(server, existing.remoteId, existing.serverId, archiveAsset)
                }
            }

            // Notify UI so the accumulated list refreshes this bookmark (e.g. with asset IDs)
            bookmarkActionsRepository.notifyBookmarkChanged(existing.remoteId)
        } catch (e: Exception) {
            AppLogger.e("BookmarkRepository", "syncSingleBookmark failed for $bookmarkId: ${e.message}", e)
            throw e
        }
    }

    // Pagination support
    suspend fun getBookmarksPaged(
        server: Server,
        status: FilterStatus,
        offset: Int,
        limit: Int,
        sort: SortOption = SortOption.NEWEST,
        listId: String? = null
    ): List<BookmarkEntity> {
        val query = buildPagedQuery(server.id, status, sort, listId, limit, offset)
        val result = bookmarkDao.getBookmarksPaged(query)
        AppLogger.d("BookmarkRepository", "getBookmarksPaged: status=$status, sort=$sort, listId=$listId, limit=$limit, offset=$offset -> returned ${result.size} bookmarks")
        return result
    }

    private fun buildPagedQuery(
        serverId: String,
        status: FilterStatus,
        sort: SortOption,
        listId: String?,
        limit: Int,
        offset: Int
    ): RoomRawQuery {
        val orderBy = sort.toOrderBySql()
        return if (listId != null) {
            RoomRawQuery(
                """SELECT $BOOKMARK_SELECT FROM bookmarks
                   WHERE serverId = ?
                   AND (listIds = ?
                        OR listIds LIKE ? || ',%'
                        OR listIds LIKE '%,' || ?
                        OR listIds LIKE '%,' || ? || ',%')
                   ORDER BY $orderBy
                   LIMIT ? OFFSET ?"""
            ) { stmt ->
                stmt.bindText(1, serverId)
                stmt.bindText(2, listId)
                stmt.bindText(3, listId)
                stmt.bindText(4, listId)
                stmt.bindText(5, listId)
                stmt.bindLong(6, limit.toLong())
                stmt.bindLong(7, offset.toLong())
            }
        } else {
            val whereClause = when (status) {
                FilterStatus.ALL -> "serverId = ? AND isArchived = 0"
                FilterStatus.ALL_INCLUDING_ARCHIVED -> "serverId = ?"
                FilterStatus.FAVORITES -> "serverId = ? AND isStarred = 1"
                FilterStatus.ARCHIVED -> "serverId = ? AND isArchived = 1"
                FilterStatus.OFFLINE -> "serverId = ? AND content IS NOT NULL AND length(content) > 0"
            }
            RoomRawQuery(
                "SELECT $BOOKMARK_SELECT FROM bookmarks WHERE $whereClause ORDER BY $orderBy LIMIT ? OFFSET ?"
            ) { stmt ->
                stmt.bindText(1, serverId)
                stmt.bindLong(2, limit.toLong())
                stmt.bindLong(3, offset.toLong())
            }
        }
    }

    companion object {
        private const val BOOKMARK_SELECT = """localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content"""

        private fun SortOption.toOrderBySql(): String = when (this) {
            SortOption.NEWEST             -> "createdAt DESC"
            SortOption.OLDEST             -> "createdAt ASC"
            SortOption.TITLE_AZ           -> "title COLLATE NOCASE ASC"
            SortOption.TITLE_ZA           -> "title COLLATE NOCASE DESC"
            SortOption.READING_TIME_SHORT -> "readingTimeMinutes ASC"
            SortOption.READING_TIME_LONG  -> "readingTimeMinutes DESC"
        }
    }

    /**
     * Fetches all bookmarks matching [status] and [listId] without pagination.
     * Used by selectAll() to select beyond the first page.
     */
    suspend fun getAllBookmarks(
        server: Server,
        status: com.karakept.app.data.model.FilterStatus,
        listId: String? = null
    ): List<BookmarkEntity> {
        return if (listId != null) {
            bookmarkDao.getAllBookmarksForList(server.id, listId)
        } else {
            when (status) {
                com.karakept.app.data.model.FilterStatus.ALL ->
                    bookmarkDao.getAllNotArchivedForServer(server.id)
                com.karakept.app.data.model.FilterStatus.ALL_INCLUDING_ARCHIVED ->
                    bookmarkDao.getAllBookmarksForServerSuspend(server.id)
                com.karakept.app.data.model.FilterStatus.FAVORITES ->
                    bookmarkDao.getAllFavoritesForServer(server.id)
                com.karakept.app.data.model.FilterStatus.ARCHIVED ->
                    bookmarkDao.getAllArchivedForServer(server.id)
                com.karakept.app.data.model.FilterStatus.OFFLINE ->
                    bookmarkDao.getAllOfflineForServer(server.id)
            }
        }
    }

    suspend fun getBookmarkCount(
        server: Server,
        status: com.karakept.app.data.model.FilterStatus,
        listId: String? = null
    ): Int {
        return if (listId != null) {
            // When filtering by list, use the list-specific count query
            bookmarkDao.getBookmarksForListCount(server.id, listId)
        } else {
            when (status) {
                // ALL shows non-archived bookmarks
                com.karakept.app.data.model.FilterStatus.ALL ->
                    bookmarkDao.getNotArchivedCount(server.id)
                // ALL_INCLUDING_ARCHIVED shows all bookmarks
                com.karakept.app.data.model.FilterStatus.ALL_INCLUDING_ARCHIVED ->
                    bookmarkDao.getTotalBookmarkCount(server.id)
                com.karakept.app.data.model.FilterStatus.FAVORITES ->
                    bookmarkDao.getFavoritesCount(server.id)
                com.karakept.app.data.model.FilterStatus.ARCHIVED ->
                    bookmarkDao.getArchivedCount(server.id)
                com.karakept.app.data.model.FilterStatus.OFFLINE ->
                    bookmarkDao.getOfflineCount(server.id)
            }
        }
    }

    /**
     * Shared logic for fetching bookmark content from server.
     * Tries: 1. Inline HTML -> 2. Asset HTML -> 3. Note/Text
     * The fullPageArchive asset is cached separately when preferFullPageHtml is enabled;
     * it is never stored in the content field so that source-switching works correctly.
     */
    internal suspend fun fetchRemoteContent(server: Server, remoteBookmarkId: String): String? {
        try {
            val fullBookmark = remoteDataSource.fetchBookmark(server, remoteBookmarkId)

            // Priority 1: inline htmlContent (full content)
            var content = fullBookmark.content?.htmlContent

            // Priority 2: asset-based content (prefer over description)
            if (content.isNullOrBlank()) {
                val contentAsset = fullBookmark.assets?.find { it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.LINK_HTML_CONTENT }
                if (contentAsset != null) {
                    try {
                        AppLogger.d("BookmarkRepository", "Downloading content from asset ${contentAsset.id}")
                        val assetBytes = remoteDataSource.downloadAsset(server, contentAsset.id ?: "")
                        content = assetBytes.decodeToString()
                        AppLogger.d("BookmarkRepository", "Downloaded ${content.length} chars from asset")
                    } catch (e: Exception) {
                        AppLogger.e("BookmarkRepository", "Failed to download content asset ${contentAsset.id}: ${e.message}")
                    }
                }
            }

            // Priority 3: fallback to note or text for non-link content
            if (content.isNullOrBlank()) {
                content = fullBookmark.note ?: fullBookmark.content?.text
                if (!content.isNullOrBlank()) {
                     AppLogger.d("BookmarkRepository", "Using note/text content (${content.length} chars)")
                }
            }

            return content
        } catch (e: Exception) {
            AppLogger.e("BookmarkRepository", "Error fetching remote content: ${e.message}")
            throw e
        }
    }

    /**
     * Executes the sync pipeline with the given configuration.
     * Uses per-key deduplication: if a pipeline for the same key is already running,
     * this call returns 0 immediately without starting a new pipeline.
     * Returns the number of new bookmarks inserted.
     */
    private suspend fun executeSyncPipeline(config: SyncConfiguration): Int {
        val key: SyncKey = when (config) {
            is SyncConfiguration.Full -> null
            is SyncConfiguration.Filtered -> if (config.favourited == true) SYNC_KEY_FAVORITES else SYNC_KEY_ARCHIVED
            is SyncConfiguration.ForList -> config.listId
        }

        if (!tryAcquireKey(key)) {
            // A skipped list pass is a silently missed membership reconcile — the caller
            // gets 0 back and cannot tell it from "nothing to do".
            AppLogger.d("BookmarkRepo", "SKIPPED sync for key=$key — already in progress")
            return 0
        }

        setKeyStatus(key, ListSyncStatus.FetchingMetadata())
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
                fetchRemoteContent = ::fetchRemoteContent,
                cacheHeroAssetsForBookmark = ::cacheHeroAssetsForBookmark,
                onProgress = { status -> setKeyStatus(key, status) },
                onPageCommitted = { _pageCommitted.tryEmit(key) },
                // Drop the busy indicator AND release the key once the visible rows have
                // landed. Holding it through enrichment made a pull-to-refresh during a long
                // content download hit the dedup check and silently do nothing.
                onForegroundComplete = {
                    setKeyStatus(key, ListSyncStatus.Idle)
                    releaseKey(key)
                    foregroundKeyReleased = true
                },
                shouldRunEnrichment = {
                    tryAcquireEnrichmentKey(key).also { holdsEnrichmentKey = it }
                }
            )
            val result = pipeline.execute()
            _lastSyncNewBookmarks = pipeline.newlyInsertedBookmarks
            if (pipeline.warnings.isNotEmpty()) {
                _syncReports.tryEmit(
                    com.karakept.app.data.model.SyncReport(key, result, pipeline.warnings)
                )
            }
            return result
        } catch (e: kotlinx.coroutines.CancellationException) {
            _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
            throw e
        } catch (e: Exception) {
            AppLogger.e("BookmarkRepo", "Failed to fetch bookmarks: ${e.message}", e)
            _syncProgress.value = com.karakept.app.data.model.SyncProgress.Error(e.message ?: "Unknown error")
            throw e
        } finally {
            if (!foregroundKeyReleased) releaseKey(key)
            if (holdsEnrichmentKey) releaseEnrichmentKey(key)
            setKeyStatus(key, ListSyncStatus.Idle)
            if (_syncProgress.value !is com.karakept.app.data.model.SyncProgress.Error) {
                _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
            }
        }
    }

    /**
     * After sync, finds lists with notifyOnNewBookmarks=true that received new bookmarks.
     * Returns list of (listId, listName, count) triples for notification dispatch.
     */
    suspend fun getListsNeedingNotification(serverId: String): List<Triple<String, String, Int>> {
        val allSettings = settingsRepository.allListSettings.first()
        val notifyListIds = allSettings.filter { it.value.notifyOnNewBookmarks }.keys
        if (notifyListIds.isEmpty()) return emptyList()

        val newBookmarks = _lastSyncNewBookmarks
        if (newBookmarks.isEmpty()) return emptyList()

        val allLists = listDao.getListsForServer(serverId).first()

        return findListsWithNewBookmarks(allSettings, newBookmarks, allLists)
    }

    /**
     * Downloads and caches hero images (banner and screenshot) for a bookmark.
     * Stores the local file paths in the AssetEntity for offline access.
     */
    internal suspend fun cacheHeroAssetsForBookmark(
        server: Server,
        bookmarkRemoteId: Long,
        serverId: String,
        bannerImageAssetId: String?,
        screenshotAssetId: String?
    ) {
        val authHeader = "Bearer ${server.apiKey}"

        // Cache banner image
        if (bannerImageAssetId != null) {
            try {
                val existingAsset = assetDao.getAssetsForBookmark(bookmarkRemoteId, serverId)
                    .find { it.id == bannerImageAssetId }

                if (existingAsset?.localPath == null) {
                    val bytes = remoteDataSource.downloadAsset(server, bannerImageAssetId)
                    val cacheDir = com.karakept.app.utils.FileUtils.getImageCacheDirectory()
                    val localPath = com.karakept.app.utils.FileUtils.saveFile(
                        cacheDir, "hero_banner_${bannerImageAssetId}", bytes
                    )
                    assetDao.insertAssets(
                        listOf(AssetEntity(
                            id = bannerImageAssetId,
                            bookmarkRemoteId = bookmarkRemoteId,
                            serverId = serverId,
                            assetType = "bannerImage",
                            fileName = "hero_banner_${bannerImageAssetId}",
                            contentType = null,
                            localPath = localPath
                        ))
                    )
                    AppLogger.d("BookmarkRepository", "Cached banner image for bookmark $bookmarkRemoteId: $localPath")
                }
            } catch (e: Exception) {
                AppLogger.e("BookmarkRepository", "Failed to cache banner image: ${e.message}")
            }
        }

        // Cache screenshot
        if (screenshotAssetId != null) {
            try {
                val existingAsset = assetDao.getAssetsForBookmark(bookmarkRemoteId, serverId)
                    .find { it.id == screenshotAssetId }

                if (existingAsset?.localPath == null) {
                    val bytes = remoteDataSource.downloadAsset(server, screenshotAssetId)
                    val cacheDir = com.karakept.app.utils.FileUtils.getImageCacheDirectory()
                    val localPath = com.karakept.app.utils.FileUtils.saveFile(
                        cacheDir, "hero_screenshot_${screenshotAssetId}", bytes
                    )
                    assetDao.insertAssets(
                        listOf(AssetEntity(
                            id = screenshotAssetId,
                            bookmarkRemoteId = bookmarkRemoteId,
                            serverId = serverId,
                            assetType = "screenshot",
                            fileName = "hero_screenshot_${screenshotAssetId}",
                            contentType = null,
                            localPath = localPath
                        ))
                    )
                    AppLogger.d("BookmarkRepository", "Cached screenshot for bookmark $bookmarkRemoteId: $localPath")
                }
            } catch (e: Exception) {
                AppLogger.e("BookmarkRepository", "Failed to cache screenshot: ${e.message}")
            }
        }
    }

    private suspend fun insertContentAssetMetadata(
        dto: com.karakept.api.model.Bookmark,
        bookmarkRemoteId: Long,
        serverId: String
    ) {
        val typeStrings = mapOf(
            com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.LINK_HTML_CONTENT to "linkHtmlContent",
            com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.FULL_PAGE_ARCHIVE to "fullPageArchive",
            com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.PRECRAWLED_ARCHIVE to "precrawledArchive",
            com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.PDF to "pdf"
        )
        val metadata = dto.assets?.mapNotNull { asset ->
            val typeStr = typeStrings[asset.assetType] ?: return@mapNotNull null
            val assetId = asset.id ?: return@mapNotNull null
            AssetEntity(
                id = assetId,
                bookmarkRemoteId = bookmarkRemoteId,
                serverId = serverId,
                assetType = typeStr,
                fileName = null,
                contentType = null,
                localPath = null
            )
        } ?: return
        if (metadata.isNotEmpty()) assetDao.insertAssetMetadataOnly(metadata)
    }

    private suspend fun cacheArchiveAsset(
        server: Server,
        bookmarkRemoteId: Long,
        serverId: String,
        asset: com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response
    ) {
        val assetId = asset.id ?: return
        try {
            val existing = assetDao.getAssetsForBookmark(bookmarkRemoteId, serverId)
                .find { it.id == assetId }
            if (existing?.localPath != null) return  // already cached

            val bytes = remoteDataSource.downloadAsset(server, assetId)
            val cacheDir = com.karakept.app.utils.FileUtils.getImageCacheDirectory()
            val localPath = com.karakept.app.utils.FileUtils.saveFile(
                cacheDir, "archive_${assetId}", bytes
            )
            assetDao.insertAssets(listOf(AssetEntity(
                id = assetId,
                bookmarkRemoteId = bookmarkRemoteId,
                serverId = serverId,
                assetType = "fullPageArchive",
                fileName = "archive_${assetId}",
                contentType = null,
                localPath = localPath
            )))
            AppLogger.d("BookmarkRepository", "Cached fullPageArchive for bookmark $bookmarkRemoteId: $localPath")
        } catch (e: Exception) {
            AppLogger.e("BookmarkRepository", "Failed to cache fullPageArchive: ${e.message}")
        }
    }

    /**
     * Reconciles a bookmark's list membership after a quick action.
     * Uses GET /bookmarks/{id}/lists — one API call.
     *
     * Smart list membership is server-computed and fully replaced with the server's answer.
     * Manual list membership is kept from the local DB — the optimistic update applied by
     * the action is already correct, and the queued server action may not have been
     * processed yet when this call is made.
     *
     * @param smartListIds the IDs of all known smart lists — only these are overwritten
     *                     by the server response; manual list IDs are preserved locally
     */
    /**
     * @return true if the server returned smart list membership (fresh data),
     *         false if potentially stale (no smart lists returned by the server).
     */
    suspend fun reconcileBookmarkSmartListMembership(
        server: Server,
        bookmarkLocalId: Long,
        smartListIds: Set<String>
    ): Boolean {
        val entity = bookmarkDao.getBookmarkById(bookmarkLocalId) ?: return true
        try {
            val serverListIds = remoteDataSource.fetchListsForBookmark(server, entity.originalRemoteId)
                .mapNotNull { it.id }.toSet()

            val currentIds = entity.listIds.split(",").filter { it.isNotEmpty() }.toSet()
            // Smart lists: server is authoritative (criteria are server-computed)
            // Manual lists: local DB is authoritative (optimistic update already applied)
            val updatedIds = ((currentIds - smartListIds) + (serverListIds intersect smartListIds))
                .joinToString(",")

            if (updatedIds != entity.listIds) {
                bookmarkDao.updateBookmarkMetadata(
                    localId = bookmarkLocalId,
                    title = entity.title,
                    url = entity.url,
                    description = entity.description,
                    imageUrl = entity.imageUrl,
                    bannerImageAssetId = entity.bannerImageAssetId,
                    screenshotAssetId = entity.screenshotAssetId,
                    tags = entity.tags,
                    listIds = updatedIds,
                    isStarred = entity.isStarred,
                    isArchived = entity.isArchived,
                    isRead = entity.isRead,
                    readingTimeMinutes = entity.readingTimeMinutes,
                    modifiedAt = entity.modifiedAt,
                    crawlStatus = entity.crawlStatus,
                    crawledAt = entity.crawledAt
                )
                AppLogger.d("BookmarkRepository", "Reconciled list membership for bookmark $bookmarkLocalId: $updatedIds")
            }

            // If the server returned no smart list membership, the response may be stale
            // (server's async smart list recalculation hasn't completed yet).
            val serverHasSmartLists = (serverListIds intersect smartListIds).isNotEmpty()
            return serverHasSmartLists
        } catch (e: Exception) {
            AppLogger.e("BookmarkRepository", "Failed to reconcile list membership for bookmark $bookmarkLocalId: ${e.message}", e)
            return false
        }
    }
}

/**
 * Determines which lists with notifyOnNewBookmarks=true received new bookmarks.
 * Returns list of (listId, listName) pairs for lists that should trigger a notification.
 *
 * Extracted as internal top-level function for testability in commonTest.
 */
internal fun findListsWithNewBookmarks(
    allListSettings: Map<String, ListSettings>,
    newBookmarks: List<BookmarkEntity>,
    allLists: List<ListEntity>
): List<Triple<String, String, Int>> {
    val notifyListIds = allListSettings
        .filter { it.value.notifyOnNewBookmarks }
        .keys
    if (notifyListIds.isEmpty()) return emptyList()

    val listNameMap = allLists.associate { it.remoteId to it.name }

    return notifyListIds.mapNotNull { listId ->
        val count = newBookmarks.count { bookmark ->
            bookmark.listIds.split(",").map { it.trim() }.contains(listId)
        }
        if (count > 0) {
            listNameMap[listId]?.let { name -> Triple(listId, name, count) }
        } else null
    }
}
