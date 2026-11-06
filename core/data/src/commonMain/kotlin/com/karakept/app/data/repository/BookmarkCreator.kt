package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.toBookmark
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.AppLogger
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Creates a bookmark on the server, waits for its crawl to settle, stores the row, and then
 * refreshes its full content in the background on [scope] — the repository's own scope, so the
 * refresh outlives the screen that created the bookmark.
 */
internal class BookmarkCreator(
    private val bookmarkDao: BookmarkDao,
    private val remoteDataSource: RemoteDataSource,
    private val serverRepository: ServerRepository,
    private val contentSync: BookmarkContentSync,
    private val scope: CoroutineScope,
) {
    suspend fun createBookmark(url: String, onStatusChange: ((String) -> Unit)? = null): Result<Bookmark> {
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
            scope.launch {
                try {
                    contentSync.syncSingleBookmark(inserted.remoteId, server.id)
                } catch (e: Exception) {
                    AppLogger.e("BookmarkRepository", "Background sync failed for new bookmark: ${e.message}")
                }
            }

            Result.success(inserted.toBookmark())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
