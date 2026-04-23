package com.karakept.app.data.repository

import androidx.room.RoomRawQuery
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.ReadingTimeCalculator
import com.karakept.app.utils.ImageCacheManager
import com.karakept.app.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

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

    private val mutex = Mutex()

    private val _syncProgress = MutableStateFlow<com.karakept.app.data.model.SyncProgress>(com.karakept.app.data.model.SyncProgress.Idle)
    val syncProgress: StateFlow<com.karakept.app.data.model.SyncProgress> = _syncProgress.asStateFlow()

    /** Bookmarks inserted during the last sync, used for per-list notification counts. */
    private var _lastSyncNewBookmarks: List<BookmarkEntity> = emptyList()

    /** Resets sync progress to Idle. Used when a sync is cancelled (not failed). */
    fun resetSyncProgress() {
        _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
    }

    suspend fun syncBookmarks(server: Server): Int =
        executeSyncPipeline(SyncConfiguration.Full(server))

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
                content = ""
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
                listIds = existing.listIds, // Preserve existing listIds as fetching them is expensive for single sync
                isStarred = dto.favourited ?: false,
                isArchived = dto.archived ?: false,
                isRead = existing.isRead,
                readingTimeMinutes = existing.readingTimeMinutes // Will update if content is fetched
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
     * Provides unified error handling and progress reporting.
     * Returns the number of new bookmarks inserted.
     */
    private suspend fun executeSyncPipeline(config: SyncConfiguration): Int {
        return mutex.withLock {
            try {
                val pipeline = BookmarkSyncPipeline(
                    config = config,
                    bookmarkDao = bookmarkDao,
                    remoteDataSource = remoteDataSource,
                    bookmarkActionsRepository = bookmarkActionsRepository,
                    settingsRepository = settingsRepository,
                    highlightRepository = highlightRepository,
                    imageCacheManager = imageCacheManager,
                    listDao = listDao,
                    syncProgress = _syncProgress,
                    fetchRemoteContent = ::fetchRemoteContent,
                    cacheHeroAssetsForBookmark = ::cacheHeroAssetsForBookmark
                )
                val result = pipeline.execute()
                _lastSyncNewBookmarks = pipeline.newlyInsertedBookmarks
                result
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Scope cancelled — not a real error, reset to Idle
                _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
                throw e
            } catch (e: Exception) {
                AppLogger.e("BookmarkRepo", "Failed to fetch bookmarks: ${e.message}", e)
                _syncProgress.value = com.karakept.app.data.model.SyncProgress.Error(e.message ?: "Unknown error")
                throw e
            } finally {
                if (_syncProgress.value !is com.karakept.app.data.model.SyncProgress.Error) {
                    _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
                }
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
                    readingTimeMinutes = entity.readingTimeMinutes
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
