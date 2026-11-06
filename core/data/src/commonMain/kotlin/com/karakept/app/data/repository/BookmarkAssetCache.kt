package com.karakept.app.data.repository

import com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response
import com.karakept.app.data.cache.OfflineHeroImages
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.FileUtils

/**
 * Downloads a bookmark's assets for offline use and records them in the asset table: the hero
 * images (banner, screenshot), the full-page archive, and metadata-only rows for the content
 * assets the reader fetches on demand.
 *
 * Every download is skipped when the asset already has a local copy, and a failure is logged
 * rather than thrown — a missing asset only costs the offline copy, never the sync.
 */
internal class BookmarkAssetCache(
    private val assetDao: AssetDao,
    private val remoteDataSource: RemoteDataSource,
) {

    suspend fun cacheHeroAssets(
        server: Server,
        bookmarkRemoteId: String,
        serverId: String,
        bannerImageAssetId: String?,
        screenshotAssetId: String?
    ) {
        if (bannerImageAssetId != null) {
            cacheFile(
                server, bookmarkRemoteId, serverId,
                assetId = bannerImageAssetId,
                assetType = "bannerImage",
                fileName = OfflineHeroImages.bannerFileName(bannerImageAssetId)
            )
        }
        if (screenshotAssetId != null) {
            cacheFile(
                server, bookmarkRemoteId, serverId,
                assetId = screenshotAssetId,
                assetType = "screenshot",
                fileName = OfflineHeroImages.screenshotFileName(screenshotAssetId)
            )
        }
    }

    suspend fun cacheArchiveAsset(
        server: Server,
        bookmarkRemoteId: String,
        serverId: String,
        asset: BookmarksBookmarkIdAssetsPost201Response
    ) {
        val assetId = asset.id ?: return
        cacheFile(
            server, bookmarkRemoteId, serverId,
            assetId = assetId,
            assetType = "fullPageArchive",
            fileName = "archive_$assetId"
        )
    }

    suspend fun insertContentAssetMetadata(
        dto: com.karakept.api.model.Bookmark,
        bookmarkRemoteId: String,
        serverId: String
    ) {
        val metadata = dto.assets?.mapNotNull { asset ->
            val typeStr = CONTENT_ASSET_TYPES[asset.assetType] ?: return@mapNotNull null
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

    private suspend fun cacheFile(
        server: Server,
        bookmarkRemoteId: String,
        serverId: String,
        assetId: String,
        assetType: String,
        fileName: String
    ) {
        try {
            val existing = assetDao.getAssetsForBookmark(bookmarkRemoteId, serverId)
                .find { it.id == assetId }
            if (existing?.localPath != null) return

            val bytes = remoteDataSource.downloadAsset(server, assetId)
            val localPath = FileUtils.saveFile(FileUtils.getImageCacheDirectory(), fileName, bytes)
            assetDao.insertAssets(
                listOf(
                    AssetEntity(
                        id = assetId,
                        bookmarkRemoteId = bookmarkRemoteId,
                        serverId = serverId,
                        assetType = assetType,
                        fileName = fileName,
                        contentType = null,
                        localPath = localPath
                    )
                )
            )
            AppLogger.d("BookmarkAssetCache", "Cached $assetType for bookmark $bookmarkRemoteId: $localPath")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("BookmarkAssetCache", "Failed to cache $assetType: ${e.message}")
        }
    }

    private companion object {
        val CONTENT_ASSET_TYPES = mapOf(
            BookmarksBookmarkIdAssetsPost201Response.AssetType.LINK_HTML_CONTENT to "linkHtmlContent",
            BookmarksBookmarkIdAssetsPost201Response.AssetType.FULL_PAGE_ARCHIVE to "fullPageArchive",
            BookmarksBookmarkIdAssetsPost201Response.AssetType.PRECRAWLED_ARCHIVE to "precrawledArchive",
            BookmarksBookmarkIdAssetsPost201Response.AssetType.PDF to "pdf"
        )
    }
}
