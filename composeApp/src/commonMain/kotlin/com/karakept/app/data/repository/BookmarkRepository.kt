package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.ReadingTimeCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Sync configuration sealed class hierarchy
private sealed class SyncConfiguration {
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

private data class ApiFilters(
    val archived: Boolean? = null,
    val favourited: Boolean? = null
)

class BookmarkRepository(
    private val bookmarkDao: BookmarkDao,
    private val assetDao: AssetDao,
    private val remoteDataSource: RemoteDataSource,
    private val bookmarkActionsRepository: com.karakept.app.data.repository.BookmarkActionsRepository,
    private val settingsRepository: com.karakept.app.data.repository.SettingsRepository,
    private val serverRepository: com.karakept.app.data.repository.ServerRepository
) {
    fun getBookmarks(server: Server): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getBookmarksForServer(server.id)
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()
    
    private val _syncProgress = kotlinx.coroutines.flow.MutableStateFlow<com.karakept.app.data.model.SyncProgress>(com.karakept.app.data.model.SyncProgress.Idle)
    val syncProgress: kotlinx.coroutines.flow.StateFlow<com.karakept.app.data.model.SyncProgress> = _syncProgress.asStateFlow()

    suspend fun syncBookmarks(server: Server) {
        executeSyncPipeline(SyncConfiguration.Full(server))
    }

    /**
     * Syncs only favorited bookmarks.
     */
    suspend fun syncFavorites(server: Server) {
        executeSyncPipeline(SyncConfiguration.Filtered(server, favourited = true))
    }

    /**
     * Syncs only archived bookmarks.
     */
    suspend fun syncArchived(server: Server) {
        executeSyncPipeline(SyncConfiguration.Filtered(server, archived = true))
    }

    /**
     * Syncs only bookmarks from a specific list.
     * Respects content sync mode (NEVER/PER_BOOKMARK/PER_LIST/ALL).
     */
    suspend fun syncBookmarksForList(server: Server, listId: String) {
        executeSyncPipeline(SyncConfiguration.ForList(server, listId))
    }

    suspend fun fetchBookmarkContent(bookmarkId: Long, serverId: String): String? {
        val server = serverRepository.servers.first().find { it.id == serverId } ?: return null
        
        // Find the ORIGINAL remote ID (string)
        val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkId, serverId) ?: return null
        
        try {
            val dto = remoteDataSource.fetchBookmark(server, bookmark.originalRemoteId)
            
            println("📖 CONTENT: Fetching content for bookmark ${bookmark.originalRemoteId}")
            println("📖 CONTENT: htmlContent=${dto.content.htmlContent?.length ?: 0} chars")
            println("📖 CONTENT: assets=${dto.assets.size}, types=${dto.assets.map { it.assetType }}")
            
            // Try inline htmlContent first (full content)
            val htmlContent = dto.content.htmlContent
            if (!htmlContent.isNullOrBlank()) {
                println("📖 CONTENT: Using inline htmlContent (${htmlContent.length} chars)")
                return htmlContent
            }
            
            // Try asset-based content - prefer this over description/text
            val contentAsset = dto.assets.find { it.assetType == "linkHtmlContent" }
            if (contentAsset != null) {
                println("📖 CONTENT: Downloading content from asset ${contentAsset.id}")
                val assetBytes = remoteDataSource.downloadAsset(server, contentAsset.id)
                val content = assetBytes.decodeToString()
                println("📖 CONTENT: Downloaded ${content.length} chars from asset")
                return content
            }
            
            // Fallback to text for non-link types (text notes, etc.)
            val textContent = dto.content.text
            if (!textContent.isNullOrBlank()) {
                println("📖 CONTENT: Using text content (${textContent.length} chars)")
                return textContent
            }
            
            println("📖 CONTENT: No content available (neither inline nor asset)")
            return null
        } catch (e: Exception) {
            println("📖 CONTENT: ERROR - ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    // Pagination support
    suspend fun getBookmarksPaged(
        server: Server,
        status: com.karakept.app.data.model.FilterStatus,
        offset: Int,
        limit: Int,
        listId: String? = null
    ): List<BookmarkEntity> {
        val result = if (listId != null) {
            // When filtering by list, use the list-specific query
            println("getBookmarksPaged: Using list-filtered query for listId=$listId")
            bookmarkDao.getBookmarksForListPaged(server.id, listId, limit, offset)
        } else {
            when (status) {
                // ALL shows non-archived bookmarks
                com.karakept.app.data.model.FilterStatus.ALL ->
                    bookmarkDao.getNotArchivedPagedForServer(server.id, limit, offset)
                // ALL_INCLUDING_ARCHIVED shows all bookmarks (used for list views)
                com.karakept.app.data.model.FilterStatus.ALL_INCLUDING_ARCHIVED ->
                    bookmarkDao.getBookmarksPagedForServer(server.id, limit, offset)
                com.karakept.app.data.model.FilterStatus.FAVORITES ->
                    bookmarkDao.getFavoritesPagedForServer(server.id, limit, offset)
                com.karakept.app.data.model.FilterStatus.ARCHIVED ->
                    bookmarkDao.getArchivedPagedForServer(server.id, limit, offset)
            }
        }
        println("getBookmarksPaged: status=$status, listId=$listId, limit=$limit, offset=$offset -> returned ${result.size} bookmarks")
        result.forEach { bookmark ->
            println("  - ${bookmark.originalRemoteId} (${bookmark.title}) listIds='${bookmark.listIds}' createdAt=${bookmark.createdAt}")
        }
        return result
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
            }
        }
    }

    // ========== UNIFIED SYNC PIPELINE ==========

    /**
     * Unified sync pipeline that handles all sync modes (Full, Filtered, ForList)
     * through configuration, eliminating code duplication and fixing PER_LIST
     * strategy for filtered syncs.
     */
    private inner class BookmarkSyncPipeline(
        private val config: SyncConfiguration
    ) {
        suspend fun execute() {
            // Phase 1: Process pending actions
            _syncProgress.value = com.karakept.app.data.model.SyncProgress.Starting
            val processedIds = processPendingActions()

            // Phase 2: Fetch metadata
            val remoteBookmarks = fetchBookmarkMetadata()

            // Phase 3: Fetch list membership (conditional)
            val bookmarkListMap = fetchListMembership(remoteBookmarks)

            // Phase 4: Map DTOs to entities & perform differential sync
            _syncProgress.value = com.karakept.app.data.model.SyncProgress.ProcessingMetadata
            val entities = mapToEntities(remoteBookmarks, bookmarkListMap)
            val (entitiesWithLocalIds, newCount) = performDifferentialSync(entities, processedIds)

            // Phase 5: Content sync (uses entities with correct localIds)
            syncContent(entitiesWithLocalIds)

            // Emit completion with count before returning to Idle
            if (newCount > 0) {
                _syncProgress.value = com.karakept.app.data.model.SyncProgress.SyncComplete(newCount)
                kotlinx.coroutines.delay(100)  // Give UI time to process
            }
            _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
        }

        // Phase 1: Process Pending Actions
        private suspend fun processPendingActions(): Set<Long> {
            return try {
                val ids = bookmarkActionsRepository.processPendingActions(config.server)
                ids.toSet()
            } catch (e: Exception) {
                println("Error processing pending actions: ${e.message}")
                e.printStackTrace()
                emptySet()
            }
        }

        // Phase 2: Fetch Bookmark Metadata
        private suspend fun fetchBookmarkMetadata(): List<com.karakept.app.data.remote.model.BookmarkDto> {
            val allBookmarks = mutableListOf<com.karakept.app.data.remote.model.BookmarkDto>()
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
                _syncProgress.value = com.karakept.app.data.model.SyncProgress.FetchingMetadata(pageCount, allBookmarks.size)

                val response = remoteDataSource.fetchBookmarks(
                    server = config.server,
                    cursor = cursor,
                    includeContent = false,
                    archived = config.apiFilters.archived,
                    favourited = config.apiFilters.favourited
                )

                allBookmarks.addAll(response.bookmarks)
                cursor = response.nextCursor
            } while (cursor != null)

            return allBookmarks
        }

        // Phase 3: Fetch List Membership
        private suspend fun fetchListMembership(
            remoteBookmarks: List<com.karakept.app.data.remote.model.BookmarkDto>
        ): Map<String, List<String>> {
            if (!config.shouldFetchLists) {
                println("fetchListMembership: Skipping list fetch (shouldFetchLists=false)")
                return emptyMap() // Filtered syncs skip this
            }

            println("fetchListMembership: Config type = ${config::class.simpleName}, fetching lists for ${remoteBookmarks.size} bookmarks")

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
                    val listBookmarks = remoteDataSource.fetchBookmarksForList(config.server, list.id, includeContent = false)
                    listBookmarks.forEach { bookmark ->
                        bookmarkListMap.getOrPut(bookmark.id) { mutableListOf() }.add(list.id)
                    }
                } catch (e: Exception) {
                    // Skip failed lists
                }
            }

            return bookmarkListMap
        }

        private fun buildSingleListMap(
            remoteBookmarks: List<com.karakept.app.data.remote.model.BookmarkDto>,
            listId: String
        ): Map<String, List<String>> {
            val map = remoteBookmarks.associate { it.id to listOf(listId) }
            println("buildSingleListMap: Created map for list $listId with ${map.size} entries")
            return map
        }

        // Phase 4: Map to Entities & Differential Sync
        private suspend fun mapToEntities(
            dtos: List<com.karakept.app.data.remote.model.BookmarkDto>,
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
                    e.printStackTrace()
                    null
                }
            }
        }

        private suspend fun mapDtoToEntity(
            dto: com.karakept.app.data.remote.model.BookmarkDto,
            bookmarkListMap: Map<String, List<String>>,
            existingBookmarks: Map<String, BookmarkEntity>,
            syncStrategy: com.karakept.app.data.model.SyncStrategy
        ): BookmarkEntity {
            val existing = existingBookmarks[dto.id]

            val url = when (dto.content.type) {
                "link" -> dto.content.url ?: ""
                "text" -> ""
                else -> dto.content.url ?: ""
            }

            val createdAtMillis = try {
                Instant.parse(dto.createdAt).toEpochMilliseconds()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            val title = dto.title ?: dto.content.title ?: "Untitled"
            val incomingContent = dto.content.htmlContent ?: dto.content.text

            // CRITICAL FIX: Determine listIds - preserve from DB for filtered syncs
            val listIds = when {
                bookmarkListMap.isNotEmpty() -> {
                    val ids = bookmarkListMap[dto.id]?.joinToString(",") ?: ""
                    println("mapDtoToEntity: Bookmark ${dto.id} (${dto.title}) -> listIds: '$ids'")
                    ids
                }
                existing != null -> existing.listIds // Preserve for filtered syncs
                else -> ""
            }

            // Apply content strategy
            val newContent = when (syncStrategy) {
                com.karakept.app.data.model.SyncStrategy.NEVER,
                com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK -> null
                com.karakept.app.data.model.SyncStrategy.PER_LIST -> {
                    // Use listIds from either API or existing entity
                    val entityListIds = listIds.split(",").filter { it.isNotEmpty() }.toSet()
                    val syncConfig = settingsRepository.contentSyncConfig.first()
                    val targetLists = syncConfig.selectedLists.toSet()
                    if (entityListIds.intersect(targetLists).isNotEmpty()) incomingContent else null
                }
                com.karakept.app.data.model.SyncStrategy.ALL -> incomingContent
            }

            // Determine if existing bookmark has content (content field will be "HAS_CONTENT" or "")
            val existingHasContent = existing?.content == "HAS_CONTENT"
            val existingReadingTime = existing?.readingTimeMinutes ?: 0

            // Decide final content and reading time based on strategy
            val (finalContent, finalReadingTime) = when {
                // If we have new content from metadata sync, use it
                !newContent.isNullOrBlank() -> {
                    val time = ReadingTimeCalculator.calculateReadingTime(newContent)
                    Pair(newContent, time)
                }
                // If existing has content, preserve it (don't overwrite with empty)
                existingHasContent -> {
                    // Keep empty string as placeholder, but preserve reading time
                    // Content will remain in DB, we just don't load it during metadata sync
                    Pair("", existingReadingTime)
                }
                // No content at all
                else -> Pair("", 0)
            }

            // Extract banner and screenshot asset IDs for fallback image display
            println("📸 Bookmark ${dto.id} imageUrl='${dto.content.imageUrl}' has ${dto.assets.size} assets: ${dto.assets.map { "${it.assetType}:${it.id}" }}")
            val bannerImageAssetId = dto.assets
                .find { it.assetType == "bannerImage" }
                ?.id
            val screenshotAssetId = dto.assets
                .find { it.assetType == "screenshot" }
                ?.id
            if (bannerImageAssetId != null) {
                println("📸 Found bannerImage asset: $bannerImageAssetId for bookmark ${dto.id}")
            }
            if (screenshotAssetId != null) {
                println("📸 Found screenshot asset: $screenshotAssetId for bookmark ${dto.id}")
            }
            if (bannerImageAssetId == null && screenshotAssetId == null) {
                println("📸 No banner or screenshot assets found for bookmark ${dto.id}")
            }

            return BookmarkEntity(
                localId = existing?.localId ?: 0L,
                remoteId = dto.id.hashCode().toLong(),
                originalRemoteId = dto.id,
                serverId = config.server.id,
                title = title,
                url = url,
                description = dto.content.description,
                imageUrl = dto.content.imageUrl,
                bannerImageAssetId = bannerImageAssetId,
                screenshotAssetId = screenshotAssetId,
                tags = dto.tags.map { it.name }.joinToString(","),
                listIds = listIds,
                isStarred = dto.favourited,
                isArchived = dto.archived,
                isRead = existing?.isRead ?: false,
                createdAt = createdAtMillis,
                readingTimeMinutes = finalReadingTime,
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

            // Update bookmarks - use metadata-only update when content is empty (preserving existing content)
            // Use full update when we have new content from metadata sync
            if (toUpdate.isNotEmpty()) {
                println("performDifferentialSync: Updating ${toUpdate.size} bookmarks")
            }
            toUpdate.forEach { bookmark ->
                println("  - UPDATE: ${bookmark.originalRemoteId} (${bookmark.title}) listIds='${bookmark.listIds}' archived=${bookmark.isArchived}")
                if (!bookmark.content.isNullOrEmpty()) {
                    // We have new content, do full update
                    bookmarkDao.updateBookmarks(listOf(bookmark))
                } else {
                    // No content in this update, preserve existing content with metadata-only update
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
                println("performDifferentialSync: Inserting ${toInsert.size} bookmarks")
                toInsert.forEach { println("  - INSERT: ${it.originalRemoteId} (${it.title}) listIds='${it.listIds}' archived=${it.isArchived}") }
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
                com.karakept.app.data.model.SyncStrategy.ALL -> entities.filter { entity ->
                    // Only sync content if bookmark doesn't have it yet (readingTimeMinutes == 0)
                    entity.readingTimeMinutes == 0
                }
            }

            if (bookmarksToSync.isNotEmpty()) {
                fetchContentForBookmarks(bookmarksToSync)
            }
        }

        private suspend fun fetchContentForBookmarks(bookmarks: List<BookmarkEntity>) {
            var current = 0
            val total = bookmarks.size
            _syncProgress.value = com.karakept.app.data.model.SyncProgress.FetchingContent(current, total)

            bookmarks.forEach { entity ->
                try {
                    val fullBookmark = remoteDataSource.fetchBookmark(config.server, entity.originalRemoteId)
                    
                    // Priority 1: inline htmlContent (full content)
                    var content = fullBookmark.content.htmlContent
                    
                    // Priority 2: asset-based content (prefer over description)
                    if (content.isNullOrBlank()) {
                        val contentAsset = fullBookmark.assets.find { it.assetType == "linkHtmlContent" }
                        if (contentAsset != null) {
                            try {
                                val assetBytes = remoteDataSource.downloadAsset(config.server, contentAsset.id)
                                content = assetBytes.decodeToString()
                            } catch (e: Exception) {
                                println("Failed to download content asset ${contentAsset.id}: ${e.message}")
                            }
                        }
                    }
                    
                    // Priority 3: fallback to note or text for non-link content
                    if (content.isNullOrBlank()) {
                        content = fullBookmark.note ?: fullBookmark.content.text
                    }

                    if (!content.isNullOrBlank()) {
                        val readingTime = ReadingTimeCalculator.calculateReadingTime(content)
                        bookmarkDao.updateContent(entity.localId, content, readingTime)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                current++
                _syncProgress.value = com.karakept.app.data.model.SyncProgress.FetchingContent(current, total)
            }
        }
    }

    /**
     * Executes the sync pipeline with the given configuration.
     * Provides unified error handling and progress reporting.
     */
    private suspend fun executeSyncPipeline(config: SyncConfiguration) {
        mutex.withLock {
            try {
                val pipeline = BookmarkSyncPipeline(config)
                pipeline.execute()
            } catch (e: Exception) {
                e.printStackTrace()
                _syncProgress.value = com.karakept.app.data.model.SyncProgress.Error(e.message ?: "Unknown error")
                throw e
            } finally {
                if (_syncProgress.value !is com.karakept.app.data.model.SyncProgress.Error) {
                    _syncProgress.value = com.karakept.app.data.model.SyncProgress.Idle
                }
            }
        }
    }
}
