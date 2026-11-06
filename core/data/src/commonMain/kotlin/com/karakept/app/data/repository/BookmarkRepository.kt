package com.karakept.app.data.repository

import androidx.room3.RoomRawQuery
import com.karakept.app.data.cache.ImageCacheManager
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.toBookmark
import com.karakept.app.data.local.entity.toBookmarks
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.local.projection.ListMembershipGroup
import com.karakept.app.data.local.projection.QuickFilterCountRow
import com.karakept.app.data.local.projection.TagGroup
import com.karakept.app.data.model.BookmarkCursor
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.domain.OfflineRetention
import com.karakept.app.data.local.entity.isRetired
import com.karakept.app.data.model.SyncKey
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BookmarkRepository internal constructor(
    private val bookmarkDao: BookmarkDao,
    private val assetDao: AssetDao,
    private val remoteDataSource: RemoteDataSource,
    private val bookmarkActionsRepository: com.karakept.app.data.repository.BookmarkActionsRepository,
    private val settingsRepository: com.karakept.app.data.repository.SettingsRepository,
    private val serverRepository: com.karakept.app.data.repository.ServerRepository,
    private val highlightRepository: com.karakept.app.data.repository.HighlightRepository,
    private val imageCacheManager: ImageCacheManager,
    private val listDao: ListDao,
    private val appDispatchers: AppDispatchers,
    private val offlineCacheRepository: OfflineCacheRepository? = null
) {
    // Outlives any one caller: a bookmark created by the user must finish fetching its full
    // content even if the screen that created it goes away. Bound to the repository (a Koin
    // single) rather than GlobalScope so the work stays cancellable and test-drainable.
    private val repositoryScope = CoroutineScope(SupervisorJob() + appDispatchers.default)

    private val assetCache = BookmarkAssetCache(assetDao, remoteDataSource)

    /**
     * Every row for a server, as one list.
     *
     * Nothing in the app reads this to *render* with any more — the list is sized by a count and
     * reads the pages under the viewport, and the drawer's counts come from grouped queries. Kept
     * for the callers that genuinely want the whole table at once, and deliberately not held in a
     * StateFlow: residency is what made it expensive, not the query.
     */
    fun getBookmarks(server: Server): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksForServer(server.id).map { it.toBookmarks() }
    }

    fun getOfflineBookmarkCount(serverId: String): Flow<Int> {
        return bookmarkDao.getOfflineCountFlow(serverId)
    }

    suspend fun getBookmarkByRemoteId(remoteId: String, serverId: String): Bookmark? {
        return bookmarkDao.getBookmarkByRemoteId(remoteId, serverId)?.toBookmark()
    }

    fun observeBookmark(localId: Long): Flow<Bookmark?> =
        bookmarkDao.observeBookmarkById(localId).map { it?.toBookmark() }

    suspend fun saveReadingProgress(localId: Long, progress: Float, scrollIndex: Int, scrollOffset: Int) {
        withContext(appDispatchers.io) {
            bookmarkDao.writeReadingProgress(localId, progress, scrollIndex, scrollOffset)
        }
    }

    suspend fun markOpened(localId: Long, now: Long) {
        withContext(appDispatchers.io) { bookmarkDao.markOpened(localId, now) }
    }

    suspend fun saveContent(localId: Long, content: String, readingTimeMinutes: Int) {
        withContext(appDispatchers.io) { bookmarkDao.updateContent(localId, content, readingTimeMinutes) }
    }

    /**
     * Whether a body fetched on demand for the reader should be stored as the offline copy, or
     * only shown. Decided here because it rests on offline-cache bookkeeping the row carries and
     * [Bookmark] does not.
     */
    suspend fun shouldPersistFetchedContent(localId: Long, now: Long): Boolean {
        val row = withContext(appDispatchers.io) { bookmarkDao.getBookmarkById(localId) } ?: return false
        var persist = when (settingsRepository.contentSyncStrategy.first()) {
            SyncStrategy.PER_BOOKMARK -> true
            SyncStrategy.PER_LIST -> {
                val targetLists = settingsRepository.contentSyncTargetLists.first()
                row.listIds.split(",").filter { it.isNotBlank() }.any { targetLists.contains(it) }
            }
            else -> false
        }
        // The storage cap dropped a copy it once held; opening it is what brings it back.
        if (row.offlineEvictedAt != null) persist = true
        // Past its retention: shown, not stored again.
        val retentionDays = settingsRepository.activeOfflineRetentionDays.firstOrNull()
        if (OfflineRetention.isRetired(row, retentionDays, now)) persist = false
        return persist
    }

    private val contentSync = BookmarkContentSync(
        bookmarkDao, remoteDataSource, settingsRepository, serverRepository, imageCacheManager, assetCache
    )

    private val creator = BookmarkCreator(bookmarkDao, remoteDataSource, serverRepository, contentSync, repositoryScope)

    // A single instance: it holds the per-key sync locks and the reading-progress ration.
    private val syncRunner = BookmarkSyncRunner(
        bookmarkDao = bookmarkDao,
        assetDao = assetDao,
        remoteDataSource = remoteDataSource,
        bookmarkActionsRepository = bookmarkActionsRepository,
        settingsRepository = settingsRepository,
        highlightRepository = highlightRepository,
        imageCacheManager = imageCacheManager,
        listDao = listDao,
        appDispatchers = appDispatchers,
        assetCache = assetCache,
        contentSync = contentSync,
        offlineCacheRepository = offlineCacheRepository,
        scope = repositoryScope
    )

    private val listMembership = BookmarkListMembership(
        bookmarkDao, remoteDataSource, settingsRepository, listDao,
        lastSyncNewBookmarks = { syncRunner.lastSyncNewBookmarks }
    )

    val perKeyProgress: StateFlow<Map<SyncKey, ListSyncStatus>> = syncRunner.perKeyProgress

    // Kept for BackgroundSyncOrchestrator backward compatibility.
    val syncProgress: StateFlow<com.karakept.app.data.model.SyncProgress> = syncRunner.syncProgress

    // Emits a report whenever a pipeline finishes with non-fatal warnings, so partial
    // failures are surfaced to the user instead of silently reporting "sync complete" (Group H).
    val syncReports: kotlinx.coroutines.flow.SharedFlow<com.karakept.app.data.model.SyncReport> = syncRunner.syncReports

    // Emits once when a background full sync (syncAllWithLists) finishes, so a foreground screen
    // can refresh its currently-displayed list in place to surface newly synced bookmarks.
    val backgroundSyncCompleted: kotlinx.coroutines.flow.SharedFlow<Unit> = syncRunner.backgroundSyncCompleted

    // Emits the SyncKey of a pipeline that just committed a page of metadata. Lets a screen
    // refresh in place while a long sync is still running, instead of only at the end.
    val pageCommitted: kotlinx.coroutines.flow.SharedFlow<SyncKey> = syncRunner.pageCommitted

    /** Resets sync progress to Idle. Used when a sync is cancelled (not failed). */
    fun resetSyncProgress() {
        syncRunner.resetSyncProgress()
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
        syncRunner.tryAcquireReadingProgressPull(serverId, force)

    suspend fun syncBookmarks(server: Server): Int =
        syncRunner.executeSyncPipeline(SyncConfiguration.Full(server))

    /**
     * Full sync followed by a per-list membership pass. Each list is fetched exactly once
     * (Full no longer does the old O(lists) N+1 membership fetch). Used by callers that
     * lack MainScreenModel's own syncOtherLists loop — e.g. background sync.
     *
     * @param skipKey a list id already synced by the caller, skipped to avoid re-fetching.
     */
    suspend fun syncAllWithLists(server: Server, skipKey: SyncKey = null): Int =
        syncRunner.syncAllWithLists(server, skipKey)

    /**
     * Syncs only favorited bookmarks.
     *
     * @param isCurrentView true when this is the view on screen — see [syncBookmarksForList].
     */
    suspend fun syncFavorites(server: Server, isCurrentView: Boolean = false): Int =
        syncRunner.executeSyncPipeline(
            SyncConfiguration.Filtered(server, favourited = true),
            forceProgressPull = isCurrentView
        )

    /**
     * Syncs only archived bookmarks.
     *
     * @param isCurrentView true when this is the view on screen — see [syncBookmarksForList].
     */
    suspend fun syncArchived(server: Server, isCurrentView: Boolean = false): Int =
        syncRunner.executeSyncPipeline(
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
    ): Int = syncRunner.executeSyncPipeline(
        SyncConfiguration.ForList(server, listId),
        forceProgressPull = isCurrentView
    )

    suspend fun createBookmark(url: String, onStatusChange: ((String) -> Unit)? = null): Result<Bookmark> =
        creator.createBookmark(url, onStatusChange)

    suspend fun fetchBookmarkContent(bookmarkId: String, serverId: String): String? =
        contentSync.fetchBookmarkContent(bookmarkId, serverId)

    suspend fun syncSingleBookmark(bookmarkId: String, serverId: String) {
        contentSync.syncSingleBookmark(bookmarkId, serverId)
    }

    private val viewQueries = BookmarkViewQueries(bookmarkDao)

    suspend fun getBookmarksPaged(
        server: Server,
        status: FilterStatus,
        limit: Int,
        after: BookmarkCursor? = null,
        sort: SortOption = SortOption.NEWEST,
        listId: String? = null
    ): List<Bookmark> = viewQueries.getBookmarksPaged(server, status, limit, after, sort, listId).toBookmarks()

    suspend fun countBookmarksForView(server: Server, filter: FilterConfig): Int =
        viewQueries.countBookmarksForView(server, filter)

    fun listMembershipGroups(serverId: String): Flow<List<ListMembershipGroup>> =
        viewQueries.listMembershipGroups(serverId)

    fun tagGroups(serverId: String): Flow<List<TagGroup>> = viewQueries.tagGroups(serverId)

    fun quickFilterCounts(serverId: String): Flow<QuickFilterCountRow> = viewQueries.quickFilterCounts(serverId)

    suspend fun getUnreadInList(serverId: String, listId: String): List<Bookmark> =
        viewQueries.getUnreadInList(serverId, listId).toBookmarks()

    suspend fun getBookmarkAt(server: Server, filter: FilterConfig, index: Int): Bookmark? =
        viewQueries.getBookmarkAt(server, filter, index)?.toBookmark()

    suspend fun searchBookmarks(
        server: Server,
        filter: FilterConfig,
        query: String,
        limit: Int = SEARCH_RESULT_LIMIT
    ): List<Bookmark> = viewQueries.searchBookmarks(server, filter, query, limit).toBookmarks()

    suspend fun countBookmarksBefore(
        server: Server,
        filter: FilterConfig,
        before: BookmarkCursor,
        excludeRead: Boolean = false
    ): Int = viewQueries.countBookmarksBefore(server, filter, before, excludeRead)

    fun countBookmarksForViewFlow(server: Server, filter: FilterConfig): Flow<Int> =
        viewQueries.countBookmarksForViewFlow(server, filter)

    suspend fun getViewRemoteIds(server: Server, filter: FilterConfig): List<String> =
        viewQueries.getViewRemoteIds(server, filter)

    suspend fun getBookmarksByRemoteIds(serverId: String, remoteIds: List<String>): List<Bookmark> =
        viewQueries.getBookmarksByRemoteIds(serverId, remoteIds).toBookmarks()

    suspend fun getBookmarkPage(server: Server, filter: FilterConfig, offset: Int, limit: Int): List<Bookmark> =
        viewQueries.getBookmarkPage(server, filter, offset, limit).toBookmarks()

    suspend fun getAllBookmarks(server: Server, status: FilterStatus, listId: String? = null): List<Bookmark> =
        viewQueries.getAllBookmarks(server, status, listId).toBookmarks()

    suspend fun getBookmarkCount(server: Server, status: FilterStatus, listId: String? = null): Int =
        viewQueries.getBookmarkCount(server, status, listId)

    companion object {
        internal const val SEARCH_RESULT_LIMIT = BookmarkViewQueries.SEARCH_RESULT_LIMIT

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


        internal fun buildPagedQuery(
            serverId: String,
            filter: FilterConfig,
            limit: Int,
            after: BookmarkCursor? = null
        ): RoomRawQuery = BookmarkViewQueries.buildPagedQuery(serverId, filter, limit, after)

        internal fun buildViewPredicate(serverId: String, filter: FilterConfig): BookmarkViewQueries.Companion.SqlPredicate =
            BookmarkViewQueries.buildViewPredicate(serverId, filter)

        internal fun buildViewIdsQuery(serverId: String, filter: FilterConfig): RoomRawQuery =
            BookmarkViewQueries.buildViewIdsQuery(serverId, filter)

        internal fun buildSearchQuery(serverId: String, filter: FilterConfig, query: String, limit: Int): RoomRawQuery =
            BookmarkViewQueries.buildSearchQuery(serverId, filter, query, limit)

        internal fun buildPageQuery(serverId: String, filter: FilterConfig, offset: Int, limit: Int): RoomRawQuery =
            BookmarkViewQueries.buildPageQuery(serverId, filter, offset, limit)

        internal fun buildCountBeforeQuery(
            serverId: String,
            filter: FilterConfig,
            before: BookmarkCursor,
            excludeRead: Boolean
        ): RoomRawQuery = BookmarkViewQueries.buildCountBeforeQuery(serverId, filter, before, excludeRead)

        internal fun buildCountQuery(serverId: String, filter: FilterConfig): RoomRawQuery =
            BookmarkViewQueries.buildCountQuery(serverId, filter)
    }

    internal suspend fun fetchRemoteContent(server: Server, remoteBookmarkId: String): String? =
        contentSync.fetchRemoteContent(server, remoteBookmarkId)

    /**
     * After sync, finds lists with notifyOnNewBookmarks=true that received new bookmarks.
     * Returns list of (listId, listName, count) triples for notification dispatch.
     */
    suspend fun getListsNeedingNotification(serverId: String): List<Triple<String, String, Int>> =
        listMembership.getListsNeedingNotification(serverId)

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
     * @return true if the server returned smart list membership (fresh data),
     *         false if potentially stale (no smart lists returned by the server).
     */
    suspend fun reconcileBookmarkSmartListMembership(
        server: Server,
        bookmarkLocalId: Long,
        smartListIds: Set<String>
    ): Boolean = listMembership.reconcileBookmarkSmartListMembership(server, bookmarkLocalId, smartListIds)
}

// Shared with BookmarkActionsRepository.persistFinalReadingProgress, which must write on its own
// scope and reaches BookmarkRepository only through a late-bound circular reference.
internal suspend fun BookmarkDao.writeReadingProgress(
    localId: Long,
    progress: Float,
    scrollIndex: Int,
    scrollOffset: Int
) {
    updateReadingProgress(localId, progress, scrollIndex, scrollOffset)
}
