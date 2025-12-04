package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

class BookmarkRepository(
    private val bookmarkDao: BookmarkDao,
    private val assetDao: AssetDao,
    private val remoteDataSource: RemoteDataSource
) {
    fun getBookmarks(server: Server): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getBookmarksForServer(server.id)
    }

    suspend fun syncBookmarks(server: Server) {
        try {
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
                    
                    // Process tags
                    val tagsString = dto.tags.joinToString(",") { it.name }
                    
                    // Process lists
                    val listIdsString = bookmarkListMap[dto.id]?.joinToString(",") ?: ""

                    BookmarkEntity(
                        remoteId = dto.id.hashCode().toLong(), // Convert string ID to long (Note: this might cause collisions but keeping existing logic)
                        serverId = server.id,
                        url = url,
                        title = title,
                        content = content,
                        imageUrl = dto.content.imageUrl,
                        description = dto.content.description,
                        createdAt = createdAtMillis,
                        isArchived = dto.archived,
                        isStarred = dto.favourited,
                        tags = tagsString,
                        listIds = listIdsString
                    )
                } catch (e: Exception) {
                    // Log and skip malformed bookmarks
                    e.printStackTrace()
                    null
                }
            }
            
            // Simple sync: delete all for this server and re-insert
            bookmarkDao.deleteAllBookmarksForServer(server.id)
            bookmarkDao.insertBookmarks(entities)

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
