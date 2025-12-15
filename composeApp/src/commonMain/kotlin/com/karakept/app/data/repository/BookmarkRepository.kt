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

class BookmarkRepository(
    private val bookmarkDao: BookmarkDao,
    private val assetDao: AssetDao,
    private val remoteDataSource: RemoteDataSource,
    private val bookmarkActionsRepository: com.karakept.app.data.repository.BookmarkActionsRepository
) {
    fun getBookmarks(server: Server): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getBookmarksForServer(server.id)
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun syncBookmarks(server: Server) {
        mutex.withLock {
            try {
                // IMPORTANT: Process pending actions FIRST, before fetching fresh data
                val processedIds = mutableSetOf<Long>()
            
            println("BookmarkRepository: About to process pending actions for server ${server.id}")
            try {
                val ids = bookmarkActionsRepository.processPendingActions(server)
                processedIds.addAll(ids)
                println("BookmarkRepository: Successfully processed pending actions for ${ids.size} bookmarks")
            } catch (e: Exception) {
                println("BookmarkRepository: Error processing pending actions: ${e.message}")
                e.printStackTrace()
            }
            
            // 1. Fetch all bookmarks
            val remoteBookmarks = remoteDataSource.fetchBookmarks(server)
            
            // 2. Fetch all lists to map bookmark membership
            val lists = remoteDataSource.fetchLists(server)
            val bookmarkListMap = mutableMapOf<String, MutableList<String>>() // BookmarkID -> List<ListID>
            
            lists.forEach { list ->
                try {
                    val listBookmarks = remoteDataSource.fetchBookmarksForList(server, list.id)
                    listBookmarks.forEach { bookmark ->
                        bookmarkListMap.getOrPut(bookmark.id) { mutableListOf() }.add(list.id)
                    }
                } catch (e: Exception) {
                    // Ignore errors for individual lists
                }
            }

            val entities = remoteBookmarks.mapNotNull { dto ->
                try {
                    // Extract URL from content based on type
                    val url = when (dto.content.type) {
                        "link" -> dto.content.url ?: ""
                        "text" -> "" // Text notes don't have URLs
                        else -> dto.content.url ?: ""
                    }
                    
                    // Parse timestamp - API returns ISO 8601 string
                    val createdAtMillis = try {
                        Instant.parse(dto.createdAt).toEpochMilliseconds()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                    
                    // Use content title if bookmark title is null
                    val title = dto.title ?: dto.content.title ?: "Untitled"

                    // Get HTML content - prioritize htmlContent, then fall back to other fields
                    val content = dto.content.htmlContent ?: dto.note ?: dto.content.description ?: dto.content.text

                    // Calculate reading time from content
                    val readingTimeMinutes = ReadingTimeCalculator.calculateReadingTime(content)

                    // Process tags
                    val tagsString = dto.tags.joinToString(",") { it.name }

                    // Check if bookmark has "karakept:read" tag to set isRead flag
                    val isRead = dto.tags.any { it.name == "karakept:read" }

                    // Process lists
                    val listIdsString = bookmarkListMap[dto.id]?.joinToString(",") ?: ""

                    BookmarkEntity(
                        remoteId = dto.id.hashCode().toLong(), // Convert string ID to long (Note: this might cause collisions but keeping existing logic)
                        originalRemoteId = dto.id, // Store ORIGINAL string ID for API calls
                        serverId = server.id,
                        url = url,
                        title = title,
                        content = content,
                        imageUrl = dto.content.imageUrl,
                        description = dto.content.description,
                        createdAt = createdAtMillis,
                        isArchived = dto.archived,
                        isStarred = dto.favourited,
                        isRead = isRead,
                        tags = tagsString,
                        listIds = listIdsString,
                        readingTimeMinutes = readingTimeMinutes
                    )
                } catch (e: Exception) {
                    // Log and skip malformed bookmarks
                    e.printStackTrace()
                    null
                }
            }

            // Differential sync: update existing, insert new, delete removed
            val existingBookmarks = bookmarkDao.getBookmarksForServer(server.id).first()
            val existingRemoteIds = existingBookmarks.map { it.remoteId }.toSet()
            val incomingRemoteIds = entities.map { it.remoteId }.toSet()

            // Phase 1: Update existing bookmarks (preserves localId)
            // Filter out bookmarks that have pending actions (from before sync start) OR were just processed
            // This prevents overwriting local optimistic updates with potentially stale server data
            val pendingActionBookmarkIds = bookmarkActionsRepository.getPendingActionBookmarkIds(server.id).toSet()
            val ignoredIds = processedIds + pendingActionBookmarkIds
            
            val toUpdate = entities.filter { incoming ->
                existingRemoteIds.contains(incoming.remoteId) && !ignoredIds.contains(incoming.remoteId)
            }.map { incoming ->
                // Preserve the existing localId
                val existingLocalId = existingBookmarks
                    .find { it.remoteId == incoming.remoteId }?.localId ?: 0
                incoming.copy(localId = existingLocalId)
            }

            // Phase 2: Insert new bookmarks
            val toInsert = entities.filter { incoming ->
                !existingRemoteIds.contains(incoming.remoteId)
            }

            // Phase 3: Delete removed bookmarks
            val toDeleteRemoteIds = existingRemoteIds - incomingRemoteIds
            val toDelete = existingBookmarks.filter {
                toDeleteRemoteIds.contains(it.remoteId)
            }

            // Execute updates
            if (toUpdate.isNotEmpty()) {
                bookmarkDao.updateBookmarks(toUpdate)
            }
            if (toInsert.isNotEmpty()) {
                bookmarkDao.insertBookmarks(toInsert)
            }
            if (toDelete.isNotEmpty()) {
                toDelete.forEach { bookmarkDao.deleteBookmark(it) }
            }

            // Sync Assets
            // First delete existing assets for this server
            assetDao.deleteAllAssetsForServer(server.id)
            
            val assetsDir = com.karakept.app.utils.FileUtils.getAssetsDirectory()
            val assetEntities = mutableListOf<com.karakept.app.data.local.entity.AssetEntity>()

            remoteBookmarks.forEach { bookmark ->
                bookmark.assets.forEach { asset ->
                    if (asset.assetType == "precrawledArchive") {
                        try {
                            val content = remoteDataSource.downloadAsset(server, asset.id)
                            // Use fileName if available, otherwise use asset ID with .html extension
                            val fileName = if (asset.fileName != null) {
                                "${asset.id}_${asset.fileName}"
                            } else {
                                "${asset.id}.html"
                            }
                            val localPath = com.karakept.app.utils.FileUtils.saveFile(assetsDir, fileName, content)
                            
                            assetEntities.add(
                                com.karakept.app.data.local.entity.AssetEntity(
                                    id = asset.id,
                                    bookmarkRemoteId = bookmark.id.hashCode().toLong(),
                                    serverId = server.id,
                                    assetType = asset.assetType,
                                    fileName = asset.fileName,
                                    contentType = asset.contentType,
                                    localPath = localPath
                                )
                            )
                        } catch (e: Exception) {
                            // Log error but continue syncing other assets
                            e.printStackTrace()
                        }
                    }
                }
            }
            
            if (assetEntities.isNotEmpty()) {
                assetDao.insertAssets(assetEntities)
            }

            } catch (e: Exception) {
                // Handle error (log, etc.)
                e.printStackTrace()
                throw e
            }
        }
    }
}
