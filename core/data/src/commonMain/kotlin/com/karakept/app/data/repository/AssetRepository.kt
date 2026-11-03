package com.karakept.app.data.repository

import com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response
import com.karakept.app.data.cache.OfflineHeroImages
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.FileUtils
import kotlinx.coroutines.withContext

/** An asset row stamped with its new `localPath`, and the bytes that were written there. */
class DownloadedAsset(val asset: AssetEntity, val bytes: ByteArray)

/** Where downloaded asset files live. A seam so tests don't write to the real cache directory. */
interface AssetFiles {
    /** Writes [bytes] as [fileName] and returns the absolute path it landed at. */
    fun save(fileName: String, bytes: ByteArray): String
    fun delete(path: String)
}

object CacheAssetFiles : AssetFiles {
    override fun save(fileName: String, bytes: ByteArray): String =
        FileUtils.saveFile(FileUtils.getImageCacheDirectory(), fileName, bytes)

    override fun delete(path: String) = FileUtils.deleteFile(path)
}

/** A bookmark's assets: the rows stored locally, the files they point at, and the server copies. */
class AssetRepository(
    private val assetDao: AssetDao,
    private val remoteDataSource: RemoteDataSource,
    private val appDispatchers: AppDispatchers,
    private val files: AssetFiles = CacheAssetFiles
) {
    suspend fun assetsFor(bookmark: Bookmark): List<AssetEntity> =
        assetDao.getAssetsForBookmark(bookmark.remoteId, bookmark.serverId)

    /**
     * Downloads [asset] and stores the local path against that exact row.
     * A copy already on disk is dropped first, so a failure can't leave both around.
     */
    suspend fun download(
        server: Server,
        asset: AssetEntity,
        onProgress: (Float?) -> Unit
    ): DownloadedAsset = withContext(appDispatchers.io) {
        asset.localPath?.let { previous ->
            deleteQuietly(previous)
            assetDao.clearLocalPath(asset.id)
        }
        val bytes = remoteDataSource.downloadAsset(server, asset.id, onProgress)
        val localPath = files.save(cacheFileNameFor(asset.id, asset.assetType), bytes)
        val downloaded = asset.copy(localPath = localPath)
        assetDao.insertAssets(listOf(downloaded))
        DownloadedAsset(downloaded, bytes)
    }

    suspend fun clearLocalCopy(asset: AssetEntity) = withContext(appDispatchers.io) {
        asset.localPath?.let { deleteQuietly(it) }
        assetDao.clearLocalPath(asset.id)
    }

    /** Deletes [asset] on the server, then locally. A failed detach leaves the local row alone. */
    suspend fun detachOnServer(server: Server, bookmark: Bookmark, asset: AssetEntity) =
        withContext(appDispatchers.io) {
            remoteDataSource.detachAsset(server, bookmark.remoteId, asset.id)
            asset.localPath?.let { deleteQuietly(it) }
            assetDao.deleteAsset(asset.id)
        }

    /**
     * Resolves the bookmark's archive from the server, downloads it and caches it locally.
     * Null when the server holds no archive for it.
     */
    suspend fun fetchArchive(server: Server, bookmark: Bookmark): DownloadedAsset? =
        withContext(appDispatchers.io) {
            val fullBookmark = remoteDataSource.fetchBookmark(server, bookmark.remoteId)
            val asset = fullBookmark.assets?.find {
                it.assetType == BookmarksBookmarkIdAssetsPost201Response.AssetType.FULL_PAGE_ARCHIVE ||
                    it.assetType == BookmarksBookmarkIdAssetsPost201Response.AssetType.PRECRAWLED_ARCHIVE
            } ?: return@withContext null
            val assetId = asset.id ?: ""
            val assetType = asset.assetType?.value ?: "fullPageArchive"
            val bytes = remoteDataSource.downloadAsset(server, assetId)
            val fileName = cacheFileNameFor(assetId, assetType)
            val localPath = files.save(fileName, bytes)
            val entity = AssetEntity(
                id = assetId,
                bookmarkRemoteId = bookmark.remoteId,
                serverId = bookmark.serverId,
                assetType = assetType,
                fileName = fileName,
                contentType = null,
                localPath = localPath
            )
            assetDao.insertAssets(listOf(entity))
            DownloadedAsset(entity, bytes)
        }

    private fun deleteQuietly(path: String) {
        try { files.delete(path) } catch (_: Exception) {}
    }

    companion object {
        /** Cache filename for an asset. PDFs keep the extension so the OS picks a reader. */
        fun cacheFileNameFor(assetId: String, assetType: String): String =
            when (assetType) {
                "fullPageArchive", "precrawledArchive" -> "archive_$assetId"
                "bannerImage" -> OfflineHeroImages.bannerFileName(assetId)
                "screenshot" -> OfflineHeroImages.screenshotFileName(assetId)
                "pdf" -> "asset_$assetId.pdf"
                else -> "asset_$assetId"
            }
    }
}
