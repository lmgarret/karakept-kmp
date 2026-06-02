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
    private val onProgress: ((ListSyncStatus) -> Unit)? = null
) {
    /** Bookmarks inserted during the last execute() call, available after completion. */
    var newlyInsertedBookmarks: List<BookmarkEntity> = emptyList()
        private set

    suspend fun execute(): Int {
        // Phase 1: Process pending actions
        syncProgress.value = com.karakept.app.data.model.SyncProgress.Starting
        onProgress?.invoke(ListSyncStatus.FetchingMetadata())
        val processedIds = processPendingActions()

        // Phase 2: Fetch metadata
        val remoteBookmarks = fetchBookmarkMetadata()

        // Phase 2.5: Sync Highlights (skip for ForList — membership reconciliation only)
        if (config !is SyncConfiguration.ForList) {
            highlightRepository.syncHighlights(config.server)
        }

        // Phase 3: Fetch list membership (conditional)
        val bookmarkListMap = fetchListMembership(remoteBookmarks)

        // Phase 4: Map DTOs to entities & perform differential sync
        syncProgress.value = com.karakept.app.data.model.SyncProgress.ProcessingMetadata
        onProgress?.invoke(ListSyncStatus.FetchingMetadata(remoteBookmarks.size))
        val entities = mapToEntities(remoteBookmarks, bookmarkListMap)
        val (entitiesWithLocalIds, newCount) = performDifferentialSync(entities, processedIds)

        // Phase 4.5: Reconcile list membership for ForList syncs
        if (config is SyncConfiguration.ForList) {
            reconcileListMembership(remoteBookmarks, config.listId)
        }

        // Phase 4.6: Insert server-side asset metadata (linkHtmlContent, fullPageArchive,
        // precrawledArchive) so the viewer knows what exists on the server even before
        // downloading. Uses IGNORE conflict strategy to preserve existing localPath values.
        insertAssetMetadata(remoteBookmarks, entitiesWithLocalIds)

        // Phase 5: Content sync. ForList syncs also run this phase — syncContent() is gated
        // internally by each list's syncOffline setting, so content is only downloaded for
        // lists explicitly configured for offline reading.
        syncContent(entitiesWithLocalIds)

        // Phase 6: Sync reading progress for in-progress bookmarks
        syncReadingProgress(entitiesWithLocalIds)

        // Emit completion with count before returning to Idle
        if (newCount > 0) {
            syncProgress.value = com.karakept.app.data.model.SyncProgress.SyncComplete(newCount)
            kotlinx.coroutines.delay(100)  // Give UI time to process
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

    // Phase 2: Fetch Bookmark Metadata
    private suspend fun fetchBookmarkMetadata(): List<com.karakept.api.model.Bookmark> {
        val allBookmarks = mutableListOf<com.karakept.api.model.Bookmark>()
        var cursor: String? = null
        var pageCount = 0

        // Special case for list sync - uses dedicated endpoint
        if (config is SyncConfiguration.ForList) {
            val bookmarks = remoteDataSource.fetchBookmarksForList(config.server, config.listId, includeContent = false)
            return bookmarks
        }

        // Standard paginated fetch for Full and Filtered syncs
        do {
            pageCount++
            syncProgress.value = com.karakept.app.data.model.SyncProgress.FetchingMetadata(pageCount, allBookmarks.size)

            val response = remoteDataSource.fetchBookmarks(
                server = config.server,
                cursor = cursor,
                includeContent = false,
                archived = config.apiFilters.archived,
                favourited = config.apiFilters.favourited
            )

            allBookmarks.addAll(response.bookmarks ?: emptyList())
            cursor = response.nextCursor
        } while (cursor != null)

        return allBookmarks
    }

    // Phase 3: Fetch List Membership
    private suspend fun fetchListMembership(
        remoteBookmarks: List<com.karakept.api.model.Bookmark>
    ): Map<String, List<String>> {
        if (!config.shouldFetchLists) return emptyMap()

        return when (config) {
            is SyncConfiguration.Full -> fetchAllListMembership()
            is SyncConfiguration.ForList -> buildSingleListMap(remoteBookmarks, config.listId)
            else -> emptyMap()
        }
    }

    private suspend fun fetchAllListMembership(): Map<String, List<String>> {
        val bookmarkListMap = mutableMapOf<String, MutableList<String>>()
        val lists = remoteDataSource.fetchLists(config.server)

        lists.forEach { list ->
            try {
                val listBookmarks = remoteDataSource.fetchBookmarksForList(config.server, list.id ?: "", includeContent = false)
                listBookmarks.forEach { bookmark ->
                    bookmarkListMap.getOrPut(bookmark.id ?: "") { mutableListOf() }.add(list.id ?: "")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Skip failed lists
            }
        }

        return bookmarkListMap
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
        // Use special query that includes content existence info (reading time + content flag)
        val existingBookmarks = bookmarkDao.getBookmarksForServerWithContentInfo(config.server.id)
            .associateBy { it.originalRemoteId }
        val syncStrategy = settingsRepository.contentSyncStrategy.first()

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
            content = finalContent
        )
    }

    private suspend fun performDifferentialSync(
        entities: List<BookmarkEntity>,
        processedIds: Set<Long>
    ): Pair<List<BookmarkEntity>, Int> {
        val existing = bookmarkDao.getBookmarksForServer(config.server.id).first()
        val pendingIds = bookmarkActionsRepository.getPendingActionBookmarkIds(config.server.id).toSet()
        val ignoredIds = processedIds + pendingIds

        // Update existing - need to handle metadata vs full update
        val toUpdate = entities.filter { incoming ->
            existing.any { e -> e.remoteId == incoming.remoteId } &&
            !ignoredIds.contains(incoming.remoteId)
        }.map { incoming ->
            val localId = existing.first { e -> e.remoteId == incoming.remoteId }.localId
            incoming.copy(localId = localId)
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
                    readingTimeMinutes = bookmark.readingTimeMinutes
                )
            }
        }

        // Insert new
        val toInsert = entities.filter { incoming ->
            existing.none { e -> e.remoteId == incoming.remoteId }
        }

        val newBookmarksCount = toInsert.size

        if (toInsert.isNotEmpty()) {
            bookmarkDao.insertBookmarks(toInsert)

            // IMPORTANT: Re-query to get the generated localIds for newly inserted bookmarks
            // Room doesn't return IDs when inserting a list, so we need to fetch them
            // This is needed for content sync to work on new bookmarks
            val afterInsert = bookmarkDao.getBookmarksForServer(config.server.id).first()
            val insertedRemoteIds = toInsert.map { it.remoteId }.toSet()

            // Update the entities list with correct localIds for inserted bookmarks
            val updatedEntities = entities.map { entity ->
                if (insertedRemoteIds.contains(entity.remoteId)) {
                    val dbEntity = afterInsert.find { it.remoteId == entity.remoteId }
                    if (dbEntity != null) {
                        entity.copy(localId = dbEntity.localId)
                    } else {
                        entity
                    }
                } else {
                    entity
                }
            }

            // Expose newly inserted bookmarks for per-list notification counts
            newlyInsertedBookmarks = toInsert

            // Return the updated entities list for content sync
            return Pair(updatedEntities, newBookmarksCount)
        }

        // Delete removed (conditional)
        if (config.shouldDeleteRemoved) {
            val incomingIds = entities.map { it.remoteId }.toSet()
            val toDelete = existing.filter { it.remoteId !in incomingIds }
            if (toDelete.isNotEmpty()) {
                toDelete.forEach { bookmarkDao.deleteBookmark(it) }
            }
        }

        return Pair(entities, newBookmarksCount)
    }

    // Phase 6: Sync reading progress from server for all synced bookmarks.
    // The karakeep server stores reading progress in a separate table, only
    // accessible via per-bookmark tRPC calls (no batch endpoint). To keep
    // sync time reasonable we pull concurrently and cap the total count.
    private suspend fun syncReadingProgress(entities: List<BookmarkEntity>) {
        // Keep the bar visible during Phase 6 — isRead changes can alter the displayed count.
        // The executeSyncPipeline finally-block clears the key, no explicit reset needed.
        onProgress?.invoke(ListSyncStatus.FetchingMetadata(entities.size))
        val trackProgress = kotlinx.coroutines.withTimeoutOrNull(1000) {
            settingsRepository.trackReadingProgress.firstOrNull()
        } ?: true
        if (!trackProgress) return
        val isOffline = kotlinx.coroutines.withTimeoutOrNull(1000) {
            settingsRepository.offlineMode.firstOrNull()
        } ?: false
        if (isOffline) return

        val candidates = entities.take(50) // Cap to bound API cost

        if (candidates.isEmpty()) return

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
        remoteBookmarks: List<com.karakept.api.model.Bookmark>,
        listId: String
    ) {
        val serverRemoteIds = remoteBookmarks.mapNotNull { it.id }.toSet()
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
                readingTimeMinutes = bookmark.readingTimeMinutes
            )
        }

        if (removals.isNotEmpty()) {
            AppLogger.d("BookmarkRepo", "Reconciled list $listId: removed ${removals.size} stale bookmark(s)")
        }
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
