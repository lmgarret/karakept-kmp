package com.karakept.app.data.repository

import androidx.room3.RoomRawQuery
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.BookmarkCursor
import com.karakept.app.data.model.ContentFilter
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.ReadFilter
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
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
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
    private val listDao: ListDao,
    private val appDispatchers: AppDispatchers
) {
    // Outlives any one caller: a bookmark created by the user must finish fetching its full
    // content even if the screen that created it goes away. Bound to the repository (a Koin
    // single) rather than GlobalScope so the work stays cancellable and test-drainable.
    private val repositoryScope = CoroutineScope(SupervisorJob() + appDispatchers.default)

    fun getBookmarks(server: Server): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getBookmarksForServer(server.id)
    }

    fun getOfflineBookmarkCount(serverId: String): Flow<Int> {
        return bookmarkDao.getOfflineCountFlow(serverId)
    }

    suspend fun getBookmarkByRemoteId(remoteId: String, serverId: String): BookmarkEntity? {
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

    // Rationing for the reading-progress pass. It is per server rather than per sync flavour:
    // a fan-out of list syncs would otherwise each run their own pass over the same library.
    private val lastProgressPullAt = mutableMapOf<String, Long>()
    private val progressPullMutex = Mutex()

    /**
     * Pulls reading progress for bookmarks the user is currently looking at, ahead of the
     * rotating cursor reaching them. Only rows whose progress is missing or older than
     * [VISIBLE_PROGRESS_STALE_AFTER_MS] are fetched, so scrolling a list back and forth
     * costs nothing while still picking up what another device changed.
     *
     * Best-effort and silent: this runs off scrolling, and a failed pull here is picked up
     * by the next sync.
     */
    suspend fun pullReadingProgressForVisible(serverId: String, remoteIds: List<String>) {
        if (remoteIds.isEmpty()) return
        if (settingsRepository.offlineMode.first()) return
        if (!settingsRepository.trackReadingProgress.first()) return

        val targets = bookmarkDao.getStaleProgressTargetsIn(
            serverId = serverId,
            remoteIds = remoteIds,
            staleBefore = System.currentTimeMillis() - VISIBLE_PROGRESS_STALE_AFTER_MS
        )
        if (targets.isEmpty()) return

        try {
            bookmarkActionsRepository.pullReadingProgressForTargets(targets, serverId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("BookmarkRepo", "Visible-row progress pull failed: ${e.message}")
        }
    }

    internal suspend fun tryAcquireReadingProgressPull(serverId: String, force: Boolean): Boolean =
        progressPullMutex.withLock {
            val now = System.currentTimeMillis()
            val last = lastProgressPullAt[serverId] ?: 0L
            if (!force && now - last < PROGRESS_PULL_MIN_INTERVAL_MS) return@withLock false
            lastProgressPullAt[serverId] = now
            true
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
     *
     * @param isCurrentView true when this is the view on screen — see [syncBookmarksForList].
     */
    suspend fun syncFavorites(server: Server, isCurrentView: Boolean = false): Int =
        executeSyncPipeline(
            SyncConfiguration.Filtered(server, favourited = true),
            forceProgressPull = isCurrentView
        )

    /**
     * Syncs only archived bookmarks.
     *
     * @param isCurrentView true when this is the view on screen — see [syncBookmarksForList].
     */
    suspend fun syncArchived(server: Server, isCurrentView: Boolean = false): Int =
        executeSyncPipeline(
            SyncConfiguration.Filtered(server, archived = true),
            forceProgressPull = isCurrentView
        )

    /**
     * Syncs only bookmarks from a specific list.
     * Respects content sync mode (NEVER/PER_BOOKMARK/PER_LIST/ALL).
     *
     * @param isCurrentView true when this list is the one on screen. The reading-progress
     *   ration is per server, so a list opened moments after another list's pass took the
     *   slot would silently skip its own — leaving the view the user is actually looking at
     *   as the one place the counts stay stale until scrolling fetched them row by row.
     *   The view on screen is the one that must not be rationed.
     */
    suspend fun syncBookmarksForList(
        server: Server,
        listId: String,
        isCurrentView: Boolean = false
    ): Int = executeSyncPipeline(
        SyncConfiguration.ForList(server, listId),
        forceProgressPull = isCurrentView
    )

    suspend fun createBookmark(url: String, onStatusChange: ((String) -> Unit)? = null): Result<BookmarkEntity> {
        onStatusChange?.invoke("Waiting for server response...")
        val serversList = serverRepository.servers.first()
        val server = serversList.firstOrNull() ?: return Result.failure(Exception("No server configured"))

        return try {
            var dto = remoteDataSource.createBookmark(server, url)

            // Polling for title/content, waiting for the crawl to reach a terminal status.
            // Karakeep API takes some time to parse the URL, and can pass through an interim
            // state (e.g. an anti-bot interstitial page) whose title looks valid but isn't final -
            // only accept a title once the server itself reports the crawl as settled.
            var attempts = 0
            while (attempts < 30) { // 30 attempts, 2s apart = 60 seconds total
                val currentTitle = dto.title ?: dto.content?.title ?: ""
                val crawlStatusValue = dto.content?.crawlStatus?.value
                val crawlSettled = crawlStatusValue == null || crawlStatusValue == "success" || crawlStatusValue == "failure"
                if (currentTitle.isNotBlank() && currentTitle != "Untitled" && crawlSettled) break

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
                remoteId = dto.id ?: "",
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
            repositoryScope.launch {
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

    suspend fun fetchBookmarkContent(bookmarkId: String, serverId: String): String? {
        val server = serverRepository.servers.first().find { it.id == serverId } ?: return null

        val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkId, serverId) ?: return null

        return try {
            fetchRemoteContent(server, bookmark.remoteId)
        } catch (e: Exception) {
            AppLogger.e("BookmarkRepo", "Failed to fetch content: ${e.message}", e)
            null
        }
    }

    suspend fun syncSingleBookmark(bookmarkId: String, serverId: String) {
        val server = serverRepository.servers.first().find { it.id == serverId } ?: return
        val existing = bookmarkDao.getBookmarkByRemoteId(bookmarkId, serverId) ?: return

        try {
            // Note: intentionally NOT updating _syncProgress here. This method is called
            // from a background repositoryScope.launch after createBookmark and should not
            // interfere with the main sync progress state shown in the UI.
            val dto = remoteDataSource.fetchBookmark(server, existing.remoteId)

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
                remoteDataSource.fetchListsForBookmark(server, existing.remoteId)
                    .mapNotNull { it.id }
                    .joinToString(",")
            } catch (e: Exception) {
                AppLogger.e("BookmarkRepository", "Failed to fetch lists for bookmark ${existing.remoteId}: ${e.message}")
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
                    ?: existing.crawledAt,
                summary = dto.summary ?: existing.summary,
                summarizationStatus = dto.summarizationStatus?.value ?: existing.summarizationStatus
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
    /**
     * Reads the [limit] rows that follow [after] in the sort order, or the first [limit] rows
     * when it is null.
     *
     * Paging by cursor rather than by OFFSET is what keeps a walk whole while a sync commits
     * underneath it — see [BookmarkCursor].
     */
    suspend fun getBookmarksPaged(
        server: Server,
        status: FilterStatus,
        limit: Int,
        after: BookmarkCursor? = null,
        sort: SortOption = SortOption.NEWEST,
        listId: String? = null
    ): List<BookmarkEntity> {
        val query = buildPagedQuery(
            server.id,
            FilterConfig(status = status, sort = sort, lists = listOfNotNull(listId)),
            limit,
            after
        )
        val result = bookmarkDao.getBookmarksPaged(query)
        AppLogger.d("BookmarkRepository", "getBookmarksPaged: status=$status, sort=$sort, listId=$listId, limit=$limit, after=${after?.localId} -> returned ${result.size} bookmarks")
        return result
    }

    /**
     * How many rows [filter] admits, asked of the database rather than counted in memory.
     *
     * This is the list's total: the number of slots it renders, whether or not their rows have
     * been read.
     */
    suspend fun countBookmarksForView(server: Server, filter: FilterConfig): Int =
        bookmarkDao.countBookmarks(buildCountQuery(server.id, filter))

    /** [countBookmarksForView], re-read whenever the table changes. */
    fun countBookmarksForViewFlow(server: Server, filter: FilterConfig): Flow<Int> =
        bookmarkDao.countBookmarksFlow(buildCountQuery(server.id, filter))

    /**
     * The [limit] rows of [filter]'s view starting at [offset] — a page named by its position.
     *
     * Addressed by offset rather than by cursor because the list addresses rows by index: it
     * renders the view's whole length and asks for the page under the viewport, which may be
     * anywhere. A cursor names a position by the row at it, which is what a walk needs and what a
     * jump does not have.
     *
     * The drift that made OFFSET unusable for the walk (#333) needed a position *accumulated* by
     * that walk: a row committed above it shifted every page below, and a forward-only walk never
     * came back for what the shift displaced. Nothing accumulates here. Each read states the
     * offset it wants, and every write re-reads the pages on screen, so a row committed above
     * them moves the rows down and the next read reports them where they now are.
     */
    /**
     * Every row [filter] admits, by identity only.
     *
     * What "select all" needs: the ids to act on, without reading the rows to get them. It used
     * to read the whole view into the list first, because the only way to have a row's id was to
     * have the row.
     */
    suspend fun getViewRemoteIds(server: Server, filter: FilterConfig): List<String> {
        val where = buildViewPredicate(server.id, filter)
        return bookmarkDao.selectRemoteIds(
            rawQuery(
                "SELECT remoteId FROM bookmarks WHERE ${where.sql} " +
                    "ORDER BY ${filter.sort.toOrderBySql()}",
                where.binds
            )
        )
    }

    /** The rows behind [remoteIds] — for acting on a selection that outruns what is loaded. */
    suspend fun getBookmarksByRemoteIds(
        serverId: String,
        remoteIds: List<String>
    ): List<BookmarkEntity> =
        if (remoteIds.isEmpty()) emptyList()
        else remoteIds.chunked(SQLITE_MAX_BIND_ARGS).flatMap {
            bookmarkDao.getBookmarksByRemoteIds(serverId, it)
        }

    suspend fun getBookmarkPage(
        server: Server,
        filter: FilterConfig,
        offset: Int,
        limit: Int
    ): List<BookmarkEntity> =
        bookmarkDao.getBookmarksPaged(buildPageQuery(server.id, filter, offset, limit))

    companion object {
        /**
         * SQLite's default limit on host parameters, which a selection can exceed: `IN (?, ?, …)`
         * binds one per id, and selecting a four-thousand-row view is four thousand of them.
         */
        private const val SQLITE_MAX_BIND_ARGS = 900

        /**
         * Minimum gap between two reading-progress passes triggered by list/filter syncs.
         *
         * Was 30s, when a pass cost one request per bookmark and a fan-out of list syncs
         * could put thousands on the wire. Batched, a pass is a few dozen requests, and the
         * gap is what decides how long a stale count stays on screen — so it is now short
         * enough that a burst of list syncs runs a handful of passes (each covering the next
         * rows in the rotation, so the work compounds rather than repeating) instead of
         * exactly one, and the list the user opens is not left waiting on a slot another
         * list took a moment earlier.
         */
        internal const val PROGRESS_PULL_MIN_INTERVAL_MS = 5_000L

        /**
         * How old a row's reading progress may be before looking at it in the list refetches
         * it. Bounds the cost of scrolling — roughly one request per visible row per window —
         * while keeping the rows on screen current with the other devices.
         */
        internal const val VISIBLE_PROGRESS_STALE_AFTER_MS = 5 * 60_000L

        private const val BOOKMARK_SELECT = """localId, remoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content"""

        /**
         * Builds the paged query. Lives in the companion so a test can page a real SQLite
         * table with it rather than restating its SQL — the ORDER BY is what makes paging
         * well-defined, and a copy of it in a test proves nothing about the query the app runs.
         */
        internal fun buildPagedQuery(
            serverId: String,
            filter: FilterConfig,
            limit: Int,
            after: BookmarkCursor? = null
        ): RoomRawQuery {
            val where = buildViewPredicate(serverId, filter)
            val conditions = StringBuilder(where.sql)
            val binds = where.binds.toMutableList()

            // The keyset clause narrows the same view, so it is appended to its predicate rather
            // than folded into it — the view is what a count has to agree with, and "after this
            // row" is not part of the view.
            if (after != null) {
                conditions.append(" AND ").append(filter.sort.keysetPredicateSql())
                binds.addAll(filter.sort.keysetBinds(after))
            }
            binds += limit.toLong()

            val sql = "SELECT $BOOKMARK_SELECT FROM bookmarks " +
                "WHERE $conditions " +
                "ORDER BY ${filter.sort.toOrderBySql()} LIMIT ?"

            return rawQuery(sql, binds)
        }

        /**
         * The whole view as one predicate, shared by the rows and by anything counting them.
         *
         * Every clause the view is defined by lives here, including the four that used to be
         * applied in Kotlin after the read (tags, a multi-list selection, the read filter, the
         * content filter). Splitting them across the two was what made a row's position in the
         * view and its position in the query two different numbers — a read had to over-fetch and
         * guess how much the Kotlin side would discard. With the whole view in one `WHERE`, the
         * nth row the query returns is the nth row of the view, which is what lets the list ask
         * for a page by its index.
         */
        /** A `WHERE` body and the values its placeholders bind, in order. */
        internal data class SqlPredicate(val sql: String, val binds: List<Any>)

        internal fun buildViewPredicate(serverId: String, filter: FilterConfig): SqlPredicate {
            val binds = mutableListOf<Any>()
            val conditions = mutableListOf<String>()

            conditions += "serverId = ?"
            binds += serverId

            val singleListId = filter.lists.singleOrNull()
            if (singleListId != null) {
                // Membership in the comma-separated listIds column. A single-list view applies
                // no status clause, which is what it has always done.
                conditions += csvContainsSql("listIds")
                binds += singleListId
            } else {
                when (filter.status) {
                    FilterStatus.ALL -> conditions += "isArchived = 0"
                    FilterStatus.ALL_INCLUDING_ARCHIVED -> Unit
                    FilterStatus.FAVORITES -> conditions += "isStarred = 1"
                    FilterStatus.ARCHIVED -> conditions += "isArchived = 1"
                    FilterStatus.OFFLINE ->
                        conditions += "content IS NOT NULL AND length(content) > 0"
                }
                if (filter.lists.isNotEmpty()) {
                    // Several lists select a row that is in any of them.
                    conditions += filter.lists.joinToString(" OR ", "(", ")") {
                        csvContainsSql("listIds")
                    }
                    binds.addAll(filter.lists)
                }
            }

            if (filter.tags.isNotEmpty()) {
                conditions += filter.tags.joinToString(" OR ", "(", ")") { csvContainsSql("tags") }
                binds.addAll(filter.tags)
            }

            when (filter.readFilter) {
                ReadFilter.ALL -> Unit
                ReadFilter.UNREAD -> conditions += "isRead = 0"
                ReadFilter.READ -> conditions += "isRead = 1"
                ReadFilter.IN_PROGRESS -> conditions += "(readingProgress > 0 AND isRead = 0)"
            }

            when (filter.contentFilter) {
                ContentFilter.ALL -> Unit
                ContentFilter.DOWNLOADED -> conditions += "readingTimeMinutes > 0"
                ContentFilter.NOT_DOWNLOADED -> conditions += "readingTimeMinutes = 0"
            }

            return SqlPredicate(conditions.joinToString(" AND "), binds)
        }

        /** The page of [filter]'s view at [offset], addressed by position. */
        internal fun buildPageQuery(
            serverId: String,
            filter: FilterConfig,
            offset: Int,
            limit: Int
        ): RoomRawQuery {
            val where = buildViewPredicate(serverId, filter)
            val sql = "SELECT $BOOKMARK_SELECT FROM bookmarks " +
                "WHERE ${where.sql} " +
                "ORDER BY ${filter.sort.toOrderBySql()} LIMIT ? OFFSET ?"
            return rawQuery(sql, where.binds + limit.toLong() + offset.toLong())
        }

        /** How many rows [filter] admits — the view's size, without reading any of it. */
        internal fun buildCountQuery(serverId: String, filter: FilterConfig): RoomRawQuery {
            val where = buildViewPredicate(serverId, filter)
            return rawQuery("SELECT COUNT(*) FROM bookmarks WHERE ${where.sql}", where.binds)
        }

        /**
         * Membership of a bind value in a comma-separated [column].
         *
         * `instr` on the delimited column rather than four `LIKE` patterns: a tag is free text
         * and may hold `%` or `_`, which `LIKE` reads as wildcards, so `LIKE` would match tags
         * the user never selected. Wrapping both sides in commas makes it an exact element
         * match — `,kotlin,` is in `,kotlin,android,` and `,lin,` is not.
         */
        private fun csvContainsSql(column: String): String =
            "instr(',' || $column || ',', ',' || ? || ',') > 0"

        private fun rawQuery(sql: String, binds: List<Any>): RoomRawQuery =
            RoomRawQuery(sql) { stmt ->
                binds.forEachIndexed { index, value ->
                    when (value) {
                        is Long -> stmt.bindLong(index + 1, value)
                        is String -> stmt.bindText(index + 1, value)
                        else -> error("unbindable value $value")
                    }
                }
            }

        /**
         * "Strictly after the cursor" in this sort's own order — the same comparison
         * [toOrderBySql] sorts by, so the two cannot disagree about which rows are still to
         * come. The tie-break on `localId` is what makes it strict: without it a page boundary
         * falling inside a run of equal keys either repeats that run or skips it.
         *
         * The title comparison carries `COLLATE NOCASE` for the same reason — a predicate
         * comparing case-sensitively against a case-insensitive ORDER BY drops rows.
         */
        private fun SortOption.keysetPredicateSql(): String = when (this) {
            SortOption.NEWEST ->
                "(createdAt < ? OR (createdAt = ? AND localId < ?))"
            SortOption.OLDEST ->
                "(createdAt > ? OR (createdAt = ? AND localId > ?))"
            SortOption.TITLE_AZ ->
                "(title COLLATE NOCASE > ? OR (title COLLATE NOCASE = ? AND localId > ?))"
            SortOption.TITLE_ZA ->
                "(title COLLATE NOCASE < ? OR (title COLLATE NOCASE = ? AND localId < ?))"
            SortOption.READING_TIME_SHORT ->
                "(readingTimeMinutes > ? OR (readingTimeMinutes = ? AND localId > ?))"
            SortOption.READING_TIME_LONG ->
                "(readingTimeMinutes < ? OR (readingTimeMinutes = ? AND localId < ?))"
        }

        /** The three values [keysetPredicateSql] binds, in order. */
        private fun SortOption.keysetBinds(after: BookmarkCursor): List<Any> {
            val key: Any = when (this) {
                SortOption.NEWEST, SortOption.OLDEST -> after.createdAt
                SortOption.TITLE_AZ, SortOption.TITLE_ZA -> after.title
                SortOption.READING_TIME_SHORT, SortOption.READING_TIME_LONG ->
                    after.readingTimeMinutes.toLong()
            }
            return listOf(key, key, after.localId)
        }

        /**
         * Every sort ends on `localId`, which is unique, so the row order is total.
         *
         * Without it rows sharing a sort key are placed relative to one another by nothing:
         * two LIMIT/OFFSET reads of the same table are free to disagree about where a tied
         * row sits, and a row the two reads disagree about lands on both sides of a page
         * boundary or on neither. Ties are the norm — a feed imports a batch of bookmarks
         * carrying one timestamp, and `readingTimeMinutes` is 0 across most of a library.
         */
        private fun SortOption.toOrderBySql(): String = when (this) {
            SortOption.NEWEST             -> "createdAt DESC, localId DESC"
            SortOption.OLDEST             -> "createdAt ASC, localId ASC"
            SortOption.TITLE_AZ           -> "title COLLATE NOCASE ASC, localId ASC"
            SortOption.TITLE_ZA           -> "title COLLATE NOCASE DESC, localId DESC"
            SortOption.READING_TIME_SHORT -> "readingTimeMinutes ASC, localId ASC"
            SortOption.READING_TIME_LONG  -> "readingTimeMinutes DESC, localId DESC"
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
    private suspend fun executeSyncPipeline(
        config: SyncConfiguration,
        forceProgressPull: Boolean = false
    ): Int = withContext(appDispatchers.io) {
        val key: SyncKey = when (config) {
            is SyncConfiguration.Full -> null
            is SyncConfiguration.Filtered -> if (config.favourited == true) SYNC_KEY_FAVORITES else SYNC_KEY_ARCHIVED
            is SyncConfiguration.ForList -> config.listId
        }

        if (!tryAcquireKey(key)) {
            // A skipped list pass is a silently missed membership reconcile — the caller
            // gets 0 back and cannot tell it from "nothing to do".
            AppLogger.d("BookmarkRepo", "SKIPPED sync for key=$key — already in progress")
            return@withContext 0
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
            _lastSyncNewBookmarks = pipeline.newlyInsertedBookmarks
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
        bookmarkRemoteId: String,
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
        bookmarkRemoteId: String,
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
        bookmarkRemoteId: String,
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
            val serverListIds = remoteDataSource.fetchListsForBookmark(server, entity.remoteId)
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
                    crawledAt = entity.crawledAt,
                    summary = entity.summary,
                    summarizationStatus = entity.summarizationStatus
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
