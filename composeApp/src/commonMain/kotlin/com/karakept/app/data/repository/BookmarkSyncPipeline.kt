package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.ReadingTimeCalculator
import com.karakept.app.utils.ImageCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.time.Instant

// Sync configuration sealed class hierarchy
internal sealed class SyncConfiguration {
    abstract val server: Server
    abstract val shouldFetchLists: Boolean
    abstract val shouldDeleteRemoved: Boolean
    abstract val apiFilters: ApiFilters

    data class Full(
        override val server: Server
    ) : SyncConfiguration() {
        override val shouldFetchLists = true
        override val shouldDeleteRemoved = true
        override val apiFilters = ApiFilters()
    }

    data class Filtered(
        override val server: Server,
        val archived: Boolean? = null,
        val favourited: Boolean? = null
    ) : SyncConfiguration() {
        override val shouldFetchLists = false // Keep lightweight
        override val shouldDeleteRemoved = false // Only upsert
        override val apiFilters = ApiFilters(archived, favourited)
    }

    data class ForList(
        override val server: Server,
        val listId: String
    ) : SyncConfiguration() {
        override val shouldFetchLists = true // For validation
        override val shouldDeleteRemoved = false // Only upsert
        override val apiFilters = ApiFilters()
    }
}

internal data class ApiFilters(
    val archived: Boolean? = null,
    val favourited: Boolean? = null
)

/**
 * Unified sync pipeline that handles all sync modes (Full, Filtered, ForList)
 * through configuration, eliminating code duplication and fixing PER_LIST
 * strategy for filtered syncs.
 *
 * Extracted from BookmarkRepository to reduce file size and isolate sync logic.
 */
internal class BookmarkSyncPipeline(
    private val config: SyncConfiguration,
    private val bookmarkDao: BookmarkDao,
    private val assetDao: AssetDao,
    private val remoteDataSource: RemoteDataSource,
    private val bookmarkActionsRepository: BookmarkActionsRepository,
    private val settingsRepository: SettingsRepository,
    private val highlightRepository: HighlightRepository,
    private val imageCacheManager: ImageCacheManager,
    private val listDao: ListDao,
    private val syncProgress: MutableStateFlow<com.karakept.app.data.model.SyncProgress>,
    private val fetchRemoteContent: suspend (Server, String) -> String?,
    private val cacheHeroAssetsForBookmark: suspend (Server, Long, String, String?, String?) -> Unit,
    private val onProgress: ((ListSyncStatus) -> Unit)? = null,
    /** Invoked after each page of metadata has been committed to the DB. */
    private val onPageCommitted: (suspend () -> Unit)? = null,
    /**
     * Invoked once the rows the user can see are in the DB, before the enrichment phases
     * (highlights, content download, reading progress) run. Lets the caller drop the
     * "syncing" indicator instead of holding it for work that doesn't affect the list.
     */
    private val onForegroundComplete: (() -> Unit)? = null
) {
    /** Bookmarks inserted during the last execute() call, available after completion. */
    var newlyInsertedBookmarks: List<BookmarkEntity> = emptyList()
        private set

    // Local state for this server, read once and maintained across pages. Streaming the
    // fetch would otherwise re-read the whole table for every page. Indexed both ways
    // because mapping needs originalRemoteId and diffing needs remoteId — rebuilding
    // either index per page would put back the O(rows) cost this is here to avoid.
    private var existingByOriginalId: MutableMap<String, BookmarkEntity> = mutableMapOf()
    private var existingByRemoteId: MutableMap<Long, BookmarkEntity> = mutableMapOf()
    private var ignoredIds: Set<Long> = emptySet()
    private var syncStrategy: SyncStrategy = SyncStrategy.NEVER

    /** Non-fatal problems accumulated during the last execute() call (Group H). */
    private val _warnings = mutableListOf<com.karakept.app.data.model.SyncWarning>()
    val warnings: List<com.karakept.app.data.model.SyncWarning> get() = _warnings

    private fun recordWarning(phase: String, message: String) {
        _warnings.add(com.karakept.app.data.model.SyncWarning(phase, message))
    }

    suspend fun execute(): Int {
        // Phase 1: Process pending actions
        syncProgress.value = com.karakept.app.data.model.SyncProgress.Starting
        onProgress?.invoke(ListSyncStatus.FetchingMetadata())
        val processedIds = processPendingActions()

        // Snapshot local state once. Each page is diffed against this map and the map is
        // updated in place, so streaming doesn't turn one table read into one per page.
        val localRows = bookmarkDao.getBookmarksForServerWithContentInfo(config.server.id)
        existingByOriginalId = localRows.associateByTo(mutableMapOf()) { it.originalRemoteId }
        existingByRemoteId = localRows.associateByTo(mutableMapOf()) { it.remoteId }
        ignoredIds = processedIds +
            bookmarkActionsRepository.getPendingActionBookmarkIds(config.server.id).toSet()
        syncStrategy = settingsRepository.contentSyncStrategy.first()

        // Phases 2 + 4 + 4.6, fused and streamed: each page is committed as it arrives so
        // the list on screen converges after one round trip instead of after the whole
        // library has been downloaded.
        val syncedEntities = mutableListOf<BookmarkEntity>()
        val inserted = mutableListOf<BookmarkEntity>()
        val seenRemoteIds = mutableSetOf<String>()

        fetchBookmarkMetadata { page, pageNumber ->
            page.forEach { dto -> dto.id?.let(seenRemoteIds::add) }
            syncProgress.value =
                com.karakept.app.data.model.SyncProgress.FetchingMetadata(pageNumber, seenRemoteIds.size)

            val entities = mapToEntities(page, fetchListMembership(page))
            val (withLocalIds, pageInserted) = commitPage(entities)
            syncedEntities += withLocalIds
            inserted += pageInserted

            insertAssetMetadata(page, withLocalIds)
            onProgress?.invoke(ListSyncStatus.FetchingMetadata(seenRemoteIds.size))
            onPageCommitted?.invoke()
        }

        newlyInsertedBookmarks = inserted
        val newCount = inserted.size
        syncProgress.value = com.karakept.app.data.model.SyncProgress.ProcessingMetadata

        // Deletion reconciliation runs only once every page has landed. A fetch that fails
        // part-way must never be read as "the server dropped everything we didn't see" —
        // throwing out of the loop above skips this entirely and keeps the committed pages.
        if (config.shouldDeleteRemoved) {
            deleteRemoved(seenRemoteIds)
        }

        // Phase 4.5: Reconcile list membership for ForList syncs
        if (config is SyncConfiguration.ForList) {
            reconcileListMembership(seenRemoteIds, config.listId)
        }

        // Everything the user can see is now in the DB. Release the sync indicator here so
        // it doesn't stay lit through the enrichment phases below, which can run for
        // minutes on a large library and change nothing about the rendered list.
        if (newCount > 0) {
            syncProgress.value = com.karakept.app.data.model.SyncProgress.SyncComplete(newCount)
            kotlinx.coroutines.delay(100)  // Give UI time to process
        }
        syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
        onForegroundComplete?.invoke()

        // Phase 2.5: Sync Highlights (skip for ForList — membership reconciliation only)
        if (config !is SyncConfiguration.ForList) {
            if (!highlightRepository.syncHighlights(config.server)) {
                recordWarning("highlights", "Couldn't sync highlights")
            }
        }

        // Phase 5: Content sync. ForList syncs also run this phase — syncContent() is gated
        // internally by each list's syncOffline setting, so content is only downloaded for
        // lists explicitly configured for offline reading.
        syncContent(syncedEntities)

        // Phase 6: Sync reading progress. Scoped to Full sync only — running it for
        // every ForList pass multiplied the per-bookmark tRPC calls (up to lists×50).
        if (config is SyncConfiguration.Full) {
            syncReadingProgress()
        }

        syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle

        return newCount
    }

    // Phase 1: Process Pending Actions
    private suspend fun processPendingActions(): Set<Long> {
        return try {
            val ids = bookmarkActionsRepository.processPendingActions(config.server)
            ids.toSet()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("BookmarkRepo", "Sync pipeline failed: ${e.message}", e)
            emptySet()
        }
    }

    // Phase 2: Fetch Bookmark Metadata, one page at a time.
    // [onPage] is invoked for every page as soon as it arrives, so the caller can commit it
    // instead of waiting for the last cursor. Cursor pagination is inherently serial, so a
    // library of N bookmarks otherwise costs N/100 round trips before the first row lands.
    private suspend fun fetchBookmarkMetadata(
        onPage: suspend (page: List<com.karakept.api.model.Bookmark>, pageNumber: Int) -> Unit
    ) {
        // Special case for list sync - uses dedicated endpoint, which paginates internally
        // and returns the whole list. A single list is small enough that the extra
        // complexity of streaming it isn't worth it.
        if (config is SyncConfiguration.ForList) {
            val bookmarks = remoteDataSource.fetchBookmarksForList(config.server, config.listId, includeContent = false)
            onPage(bookmarks, 1)
            return
        }

        // Standard paginated fetch for Full and Filtered syncs
        var cursor: String? = null
        var pageCount = 0
        do {
            pageCount++
            val response = remoteDataSource.fetchBookmarks(
                server = config.server,
                cursor = cursor,
                limit = 100, // server max — halves request count vs the previous 50
                includeContent = false,
                archived = config.apiFilters.archived,
                favourited = config.apiFilters.favourited
            )

            onPage(response.bookmarks ?: emptyList(), pageCount)
            cursor = response.nextCursor
        } while (cursor != null)
    }

    // Phase 3: Fetch List Membership.
    // Full sync no longer fetches every list's bookmarks here (the old O(lists) N+1).
    // Membership is authored by the ForList passes that BookmarkRepository.syncAllWithLists
    // (and MainScreenModel's syncOtherLists) run afterwards, so each list is fetched once.
    // mapDtoToEntity preserves existing.listIds when the map is empty, so a standalone Full
    // sync keeps membership intact for already-known bookmarks.
    private suspend fun fetchListMembership(
        remoteBookmarks: List<com.karakept.api.model.Bookmark>
    ): Map<String, List<String>> {
        return when (config) {
            is SyncConfiguration.ForList -> buildSingleListMap(remoteBookmarks, config.listId)
            else -> emptyMap()
        }
    }

    private fun buildSingleListMap(
        remoteBookmarks: List<com.karakept.api.model.Bookmark>,
        listId: String
    ): Map<String, List<String>> {
        return remoteBookmarks.associate { (it.id ?: "") to listOf(listId) }
    }

    // Phase 4: Map to Entities & Differential Sync
    private suspend fun mapToEntities(
        dtos: List<com.karakept.api.model.Bookmark>,
        bookmarkListMap: Map<String, List<String>>
    ): List<BookmarkEntity> {
        val existingBookmarks = existingByOriginalId

        return dtos.mapNotNull { dto ->
            try {
                mapDtoToEntity(dto, bookmarkListMap, existingBookmarks, syncStrategy)
            } catch (e: Exception) {
                AppLogger.e("BookmarkRepo", "Failed to parse bookmark: ${e.message}", e)
                null
            }
        }
    }

    private suspend fun mapDtoToEntity(
        dto: com.karakept.api.model.Bookmark,
        bookmarkListMap: Map<String, List<String>>,
        existingBookmarks: Map<String, BookmarkEntity>,
        syncStrategy: com.karakept.app.data.model.SyncStrategy
    ): BookmarkEntity {
        val existing = existingBookmarks[dto.id ?: ""]

        val url = when (dto.content?.type) {
            com.karakept.api.model.BookmarkContent.Type.LINK -> dto.content?.url ?: ""
            com.karakept.api.model.BookmarkContent.Type.TEXT -> ""
            else -> dto.content?.url ?: ""
        }

        val createdAtMillis = try {
            Instant.parse(dto.createdAt ?: "").toEpochMilliseconds()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        val modifiedAtMillis = dto.modifiedAt?.let {
            try { Instant.parse(it).toEpochMilliseconds() } catch (e: Exception) { null }
        }

        val title = dto.title ?: dto.content?.title ?: "Untitled"
        val incomingContent = dto.content?.htmlContent ?: dto.content?.text

        val listIds = when {
            bookmarkListMap.isNotEmpty() -> {
                val ids = bookmarkListMap[dto.id ?: ""]?.joinToString(",") ?: ""
                if (config is SyncConfiguration.ForList && existing != null && existing.listIds.isNotEmpty()) {
                    // ForList sync: merge list membership to avoid stripping other lists
                    val existingIds = existing.listIds.split(",").filter { it.isNotEmpty() }.toMutableSet()
                    existingIds.addAll(ids.split(",").filter { it.isNotEmpty() })
                    existingIds.joinToString(",")
                } else {
                    ids
                }
            }
            existing != null -> existing.listIds // Preserve for filtered syncs
            else -> ""
        }

        val newContent = when (syncStrategy) {
            com.karakept.app.data.model.SyncStrategy.NEVER,
            com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK -> null
            com.karakept.app.data.model.SyncStrategy.PER_LIST -> {
                val entityListIds = listIds.split(",").filter { it.isNotEmpty() }.toSet()
                val syncConfig = settingsRepository.contentSyncConfig.first()
                val targetLists = syncConfig.selectedLists.toSet()
                if (entityListIds.intersect(targetLists).isNotEmpty()) incomingContent else null
            }
            com.karakept.app.data.model.SyncStrategy.ALL -> incomingContent
        }

        val existingHasContent = existing?.content == "HAS_CONTENT"
        val existingReadingTime = existing?.readingTimeMinutes ?: 0
        val (finalContent, finalReadingTime) = when {
            !newContent.isNullOrBlank() -> Pair(newContent, ReadingTimeCalculator.calculateReadingTime(newContent))
            existingHasContent -> Pair("", existingReadingTime) // preserve existing content in DB
            else -> Pair("", 0)
        }

        val bannerImageAssetId = dto.assets
            ?.find { it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.BANNER_IMAGE }
            ?.id
        val screenshotAssetId = dto.assets
            ?.find { it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.SCREENSHOT }
            ?.id

        return BookmarkEntity(
            localId = existing?.localId ?: 0L,
            remoteId = (dto.id ?: "").hashCode().toLong(),
            originalRemoteId = dto.id ?: "",
            serverId = config.server.id,
            title = title,
            url = url,
            description = dto.content?.description,
            imageUrl = dto.content?.imageUrl,
            bannerImageAssetId = bannerImageAssetId,
            screenshotAssetId = screenshotAssetId,
            tags = dto.tags?.joinToString(",") { it.name ?: "" } ?: "",
            listIds = listIds,
            isStarred = dto.favourited ?: false,
            isArchived = dto.archived ?: false,
            isRead = existing?.isRead ?: false,
            createdAt = createdAtMillis,
            readingTimeMinutes = finalReadingTime,
            readingProgress = existing?.readingProgress ?: 0f,
            readingScrollIndex = existing?.readingScrollIndex ?: 0,
            readingScrollOffset = existing?.readingScrollOffset ?: 0,
            modifiedAt = modifiedAtMillis,
            progressSyncedAt = existing?.progressSyncedAt ?: 0,
            content = finalContent
        )
    }

    /**
     * True when the incoming metadata matches what is already stored, so the DAO write
     * (which invalidates every Room observer and recomposes the list) can be skipped.
     * Requires a known server modifiedAt AND equal user-visible fields, because server
     * modifiedAt does not always change on membership/flag-only edits.
     */
    private fun isUnchanged(current: BookmarkEntity, incoming: BookmarkEntity): Boolean {
        val incomingModified = incoming.modifiedAt ?: return false
        return current.modifiedAt == incomingModified &&
            current.listIds == incoming.listIds &&
            current.tags == incoming.tags &&
            current.isArchived == incoming.isArchived &&
            current.isStarred == incoming.isStarred &&
            current.title == incoming.title
    }

    /**
     * Diffs one page of freshly fetched entities against the in-memory snapshot of local
     * state and writes it. Returns the page's entities with real localIds attached, plus
     * the subset that was newly inserted.
     *
     * Deletion is deliberately NOT handled here — see [deleteRemoved].
     */
    private suspend fun commitPage(
        entities: List<BookmarkEntity>
    ): Pair<List<BookmarkEntity>, List<BookmarkEntity>> {
        // Update existing - need to handle metadata vs full update
        val toUpdate = entities.filter { incoming ->
            val current = existingByRemoteId[incoming.remoteId]
            current != null &&
                !ignoredIds.contains(incoming.remoteId) &&
                !isUnchanged(current, incoming)
        }.map { incoming ->
            incoming.copy(localId = existingByRemoteId.getValue(incoming.remoteId).localId)
        }

        toUpdate.forEach { bookmark ->
            if (!bookmark.content.isNullOrEmpty()) {
                bookmarkDao.updateBookmarks(listOf(bookmark))
            } else {
                bookmarkDao.updateBookmarkMetadata(
                    localId = bookmark.localId,
                    title = bookmark.title,
                    url = bookmark.url,
                    description = bookmark.description,
                    imageUrl = bookmark.imageUrl,
                    bannerImageAssetId = bookmark.bannerImageAssetId,
                    screenshotAssetId = bookmark.screenshotAssetId,
                    tags = bookmark.tags,
                    listIds = bookmark.listIds,
                    isStarred = bookmark.isStarred,
                    isArchived = bookmark.isArchived,
                    isRead = bookmark.isRead,
                    readingTimeMinutes = bookmark.readingTimeMinutes,
                    modifiedAt = bookmark.modifiedAt
                )
            }
        }

        // Insert new
        val toInsert = entities.filter { incoming ->
            existingByRemoteId[incoming.remoteId] == null
        }

        var resultEntities = entities
        var insertedWithIds = emptyList<BookmarkEntity>()
        if (toInsert.isNotEmpty()) {
            // @Insert returns the generated rowIds in argument order, which for this table
            // are the localIds. Recovering them this way avoids re-reading every row for
            // the server — which, once pages are committed one at a time, would otherwise
            // cost a full table scan per page.
            val rowIds = bookmarkDao.insertBookmarks(toInsert)
            insertedWithIds = toInsert.mapIndexed { index, entity ->
                val rowId = rowIds.getOrNull(index) ?: -1L
                if (rowId > 0) entity.copy(localId = rowId) else entity
            }

            val insertedByRemoteId = insertedWithIds.associateBy { it.remoteId }
            resultEntities = entities.map { entity ->
                insertedByRemoteId[entity.remoteId] ?: entity
            }
        }

        // Fold this page into the snapshot so later pages diff against current state.
        for (entity in resultEntities) {
            existingByOriginalId[entity.originalRemoteId] = entity
            existingByRemoteId[entity.remoteId] = entity
        }

        return Pair(resultEntities, insertedWithIds)
    }

    /**
     * Reconciles server-side deletions once the full fetch has completed.
     *
     * Split out of the per-page commit on purpose: with a streamed fetch, "not in this
     * page" says nothing about whether the server still has a bookmark. Only the union of
     * every page does. Bookmarks with pending local actions are kept so optimistic state
     * isn't wiped before it syncs.
     */
    private suspend fun deleteRemoved(seenRemoteIds: Set<String>) {
        val local = bookmarkDao.getBookmarksForServer(config.server.id).first()
        val toDelete = local.filter {
            it.originalRemoteId !in seenRemoteIds && it.remoteId !in ignoredIds
        }
        if (toDelete.isNotEmpty()) {
            bookmarkDao.deleteBookmarks(toDelete)
            AppLogger.d("BookmarkRepo", "Reconciled deletions: removed ${toDelete.size} bookmark(s)")
        }
    }

    // Phase 6: Sync reading progress from server for all synced bookmarks.
    // The karakeep server stores reading progress in a separate table, only
    // accessible via per-bookmark tRPC calls (no batch endpoint). To keep
    // sync time reasonable we pull concurrently and cap the total count.
    private suspend fun syncReadingProgress() {
        val trackProgress = kotlinx.coroutines.withTimeoutOrNull(1000) {
            settingsRepository.trackReadingProgress.firstOrNull()
        } ?: true
        if (!trackProgress) return
        val isOffline = kotlinx.coroutines.withTimeoutOrNull(1000) {
            settingsRepository.offlineMode.firstOrNull()
        } ?: false
        if (isOffline) return

        // Rotating cursor: pull the least-recently-synced bookmarks first (then most
        // recently modified) so large libraries converge across successive syncs
        // instead of forever re-pulling the same arbitrary first 50.
        val candidates = bookmarkDao.getReadingProgressPullCandidates(config.server.id, limit = 50)
        if (candidates.isEmpty()) return
        onProgress?.invoke(ListSyncStatus.FetchingMetadata(candidates.size))

        val now = System.currentTimeMillis()
        // Pull concurrently (up to 5 at a time) to avoid blocking sync too long
        val semaphore = kotlinx.coroutines.sync.Semaphore(5)
        kotlinx.coroutines.coroutineScope {
            for (bookmark in candidates) {
                launch {
                    semaphore.acquire()
                    try {
                        bookmarkActionsRepository.pullReadingProgressFromServer(
                            bookmark.remoteId, config.server.id
                        )
                        bookmarkDao.updateProgressSyncedAt(bookmark.localId, now)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
    }

    // Phase 5: Content Sync
    private suspend fun syncContent(entitiesParam: List<BookmarkEntity>) {
        // Note: entities may have updated localIds after insertion
        val entities = entitiesParam
        val syncStrategy = settingsRepository.contentSyncStrategy.first()

        val bookmarksToSync = when (syncStrategy) {
            com.karakept.app.data.model.SyncStrategy.NEVER,
            com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK -> emptyList()
            com.karakept.app.data.model.SyncStrategy.PER_LIST -> {
                val syncConfig = settingsRepository.contentSyncConfig.first()
                val targetLists = if (config.shouldFetchLists) {
                    // Full/List sync: get effective sync lists with hierarchy
                    val lists = remoteDataSource.fetchLists(config.server)
                    syncConfig.getEffectiveSyncLists(lists).toSet()
                } else {
                    // Filtered sync: use configured lists directly
                    syncConfig.selectedLists.toSet()
                }

                entities.filter { entity ->
                    // Use listIds from entity (either fresh from API or preserved from DB)
                    val entityListIds = entity.listIds.split(",").filter { it.isNotEmpty() }.toSet()
                    // Check if bookmark needs content: is in target list AND doesn't have content yet
                    // We use readingTimeMinutes as indicator (0 = no content)
                    entityListIds.intersect(targetLists).isNotEmpty() && entity.readingTimeMinutes == 0
                }
            }
            SyncStrategy.ALL -> entities.filter { entity ->
                // Only sync content if bookmark doesn't have it yet (readingTimeMinutes == 0)
                entity.readingTimeMinutes == 0
            }
            else -> emptyList()
        }

        if (bookmarksToSync.isNotEmpty()) {
            fetchContentForBookmarks(bookmarksToSync)
        }

        // Per-list offline sync: fetch content for bookmarks in lists with syncOffline = true
        val allListSettings = settingsRepository.allListSettings.first()
        val offlineListIds = allListSettings
            .filter { (_, settings) -> settings.syncOffline }
            .keys
            .toMutableSet()

        // Expand with descendant list IDs for lists with includeChildListBookmarks = true
        val listsWithChildren = allListSettings
            .filter { (listId, settings) -> settings.syncOffline && settings.includeChildListBookmarks && listId in offlineListIds }
            .keys
        if (listsWithChildren.isNotEmpty()) {
            val allLists = listDao.getListsForServerOnce(config.server.id)
            for (parentId in listsWithChildren) {
                addDescendantListIds(parentId, allLists, offlineListIds)
            }
        }

        if (offlineListIds.isNotEmpty()) {
            val alreadySyncedIds = bookmarksToSync.map { it.remoteId }.toSet()
            val offlineBookmarks = entities.filter { entity ->
                if (alreadySyncedIds.contains(entity.remoteId)) return@filter false
                val entityListIds = entity.listIds.split(",").filter { it.isNotEmpty() }.toSet()
                entityListIds.intersect(offlineListIds).isNotEmpty() && entity.readingTimeMinutes == 0
            }
            if (offlineBookmarks.isNotEmpty()) {
                fetchContentForBookmarks(offlineBookmarks)
            }
        }
    }

    private suspend fun insertAssetMetadata(
        dtos: List<com.karakept.api.model.Bookmark>,
        entities: List<BookmarkEntity>
    ) {
        val entityByOriginalId = entities.associateBy { it.originalRemoteId }
        val metadata = mutableListOf<AssetEntity>()
        val trackableTypes = setOf(
            com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.LINK_HTML_CONTENT,
            com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.FULL_PAGE_ARCHIVE,
            com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.PRECRAWLED_ARCHIVE
        )
        val typeStrings = mapOf(
            com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.LINK_HTML_CONTENT to "linkHtmlContent",
            com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.FULL_PAGE_ARCHIVE to "fullPageArchive",
            com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.PRECRAWLED_ARCHIVE to "precrawledArchive"
        )
        for (dto in dtos) {
            val entity = entityByOriginalId[dto.id ?: ""] ?: continue
            dto.assets?.forEach { asset ->
                val type = asset.assetType ?: return@forEach
                if (type !in trackableTypes) return@forEach
                val assetId = asset.id ?: return@forEach
                metadata.add(AssetEntity(
                    id = assetId,
                    bookmarkRemoteId = entity.remoteId,
                    serverId = config.server.id,
                    assetType = typeStrings[type] ?: return@forEach,
                    fileName = null,
                    contentType = null,
                    localPath = null
                ))
            }
        }
        if (metadata.isNotEmpty()) {
            assetDao.insertAssetMetadataOnly(metadata)
        }
    }

    private suspend fun fetchContentForBookmarks(bookmarks: List<BookmarkEntity>) {
        var current = 0
        val total = bookmarks.size
        syncProgress.value = com.karakept.app.data.model.SyncProgress.FetchingContent(current, total)
        onProgress?.invoke(ListSyncStatus.FetchingContent(current, total))

        bookmarks.forEach { entity ->
            try {
                val content = fetchRemoteContent(config.server, entity.originalRemoteId)

                if (!content.isNullOrBlank()) {
                    // Cache images in HTML for offline reading
                    val cachedContent = try {
                        imageCacheManager.cacheImagesInHtml(content)
                    } catch (_: Exception) { content }
                    val readingTime = ReadingTimeCalculator.calculateReadingTime(cachedContent)
                    bookmarkDao.updateContent(entity.localId, cachedContent, readingTime)
                }

                // Cache hero images (banner/screenshot)
                cacheHeroAssetsForBookmark(
                    config.server, entity.remoteId, entity.serverId,
                    entity.bannerImageAssetId, entity.screenshotAssetId
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BookmarkRepo", "Failed to sync bookmark: ${e.message}", e)
                recordWarning("content", "Couldn't download content for \"${entity.title}\"")
            }
            current++
            syncProgress.value = com.karakept.app.data.model.SyncProgress.FetchingContent(current, total)
            onProgress?.invoke(ListSyncStatus.FetchingContent(current, total))
        }
    }

    /**
     * After a ForList sync, removes the listId from any local bookmark that the server
     * no longer returns for that list. This handles smart list eviction -- when a bookmark
     * no longer matches a smart list's query, it must be removed from local membership.
     */
    private suspend fun reconcileListMembership(
        serverRemoteIds: Set<String>,
        listId: String
    ) {
        val localBookmarksInList = bookmarkDao.getAllBookmarksForList(config.server.id, listId)

        val removals = computeStaleListRemovals(localBookmarksInList, serverRemoteIds, listId)

        for ((localId, newListIds) in removals) {
            val bookmark = localBookmarksInList.first { it.localId == localId }
            bookmarkDao.updateBookmarkMetadata(
                localId = localId,
                title = bookmark.title,
                url = bookmark.url,
                description = bookmark.description,
                imageUrl = bookmark.imageUrl,
                bannerImageAssetId = bookmark.bannerImageAssetId,
                screenshotAssetId = bookmark.screenshotAssetId,
                tags = bookmark.tags,
                listIds = newListIds,
                isStarred = bookmark.isStarred,
                isArchived = bookmark.isArchived,
                isRead = bookmark.isRead,
                readingTimeMinutes = bookmark.readingTimeMinutes,
                modifiedAt = bookmark.modifiedAt
            )
        }

        // Logged unconditionally, including the zero case. "No removals" and "reconcile
        // never ran for this list" are indistinguishable otherwise, and telling them apart
        // is the first question worth asking when a list shows bookmarks the server
        // doesn't return for it.
        AppLogger.d(
            "BookmarkRepo",
            "Reconciled list $listId: server=${serverRemoteIds.size}, " +
                "local=${localBookmarksInList.size}, removed=${removals.size}"
        )
    }

    /**
     * Recursively adds all descendant list IDs to the target set.
     * Same pattern as ListSyncConfig.addAllDescendants().
     */
    private fun addDescendantListIds(parentId: String, allLists: List<ListEntity>, target: MutableSet<String>) {
        val children = allLists.filter { it.parentId == parentId }
        for (child in children) {
            target.add(child.remoteId)
            addDescendantListIds(child.remoteId, allLists, target)
        }
    }
}

/**
 * Computes which bookmarks need their list membership updated after a ForList sync.
 *
 * Compares local bookmarks that claim membership in [listId] against the set of
 * bookmark IDs returned by the server. Any local bookmark NOT in the server response
 * has [listId] stripped from its listIds (but retains other list memberships).
 *
 * @param localBookmarksInList all local BookmarkEntity rows whose listIds contain [listId]
 * @param serverRemoteIds the set of originalRemoteId values the server returned for [listId]
 * @param listId the list being reconciled
 * @return list of Pair(localId, newListIds) for bookmarks that need updating
 */
internal fun computeStaleListRemovals(
    localBookmarksInList: List<BookmarkEntity>,
    serverRemoteIds: Set<String>,
    listId: String
): List<Pair<Long, String>> {
    return localBookmarksInList
        .filter { it.originalRemoteId !in serverRemoteIds }
        .map { entity ->
            val updatedIds = entity.listIds
                .split(",")
                .filter { it.isNotEmpty() && it != listId }
                .joinToString(",")
            Pair(entity.localId, updatedIds)
        }
}
