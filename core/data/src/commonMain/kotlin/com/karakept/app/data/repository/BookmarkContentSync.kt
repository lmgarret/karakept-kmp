package com.karakept.app.data.repository

import com.karakept.app.data.cache.ImageCacheManager
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.ReadingTimeCalculator
import kotlin.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Pulls one bookmark from the server: the body the reader fetches on demand, and the full
 * refresh of a single row (metadata, list membership, content, assets) after it is created or
 * force-refreshed.
 */
internal class BookmarkContentSync(
    private val bookmarkDao: BookmarkDao,
    private val remoteDataSource: RemoteDataSource,
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository,
    private val imageCacheManager: ImageCacheManager,
    private val assetCache: BookmarkAssetCache,
) {
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
            assetCache.cacheHeroAssets(server, existing.remoteId, existing.serverId, bannerImageAssetId, screenshotAssetId)

            // Track server-side asset metadata (linkHtmlContent, fullPageArchive, precrawledArchive)
            // so the viewer knows what exists on the server even before downloading locally.
            assetCache.insertContentAssetMetadata(dto, existing.remoteId, existing.serverId)

            // Cache fullPageArchive asset when user prefers it, so the viewer can switch
            // sources without touching the content field (which always stores extracted HTML).
            if (settingsRepository.preferFullPageHtml.first()) {
                val archiveAsset = dto.assets?.find {
                    it.assetType == com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType.FULL_PAGE_ARCHIVE
                }
                if (archiveAsset?.id != null) {
                    assetCache.cacheArchiveAsset(server, existing.remoteId, existing.serverId, archiveAsset)
                }
            }

        } catch (e: Exception) {
            AppLogger.e("BookmarkRepository", "syncSingleBookmark failed for $bookmarkId: ${e.message}", e)
            throw e
        }
    }

    /**
     * Shared logic for fetching bookmark content from server.
     * Tries: 1. Inline HTML -> 2. Asset HTML -> 3. Note/Text
     * The fullPageArchive asset is cached separately when preferFullPageHtml is enabled;
     * it is never stored in the content field so that source-switching works correctly.
     */
    suspend fun fetchRemoteContent(server: Server, remoteBookmarkId: String): String? {
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
}
