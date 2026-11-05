package com.karakept.app.data.local

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.FileUtils

/**
 * Drop rows for an earlier copy of a named asset, and the files they cached.
 *
 * Needed because an asset resolved by name has no stable id: when a sidecar re-cleans an article,
 * Karakeep replaces the asset by deleting the old row and pointing a **new** id at the new blob.
 * Without this the viewer sees two rows with the same name and can pick the dead one.
 */
internal suspend fun AssetDao.pruneSupersededNamedAsset(
    bookmarkRemoteId: String,
    serverId: String,
    fileName: String,
    keepAssetId: String
) {
    val superseded = try {
        getSupersededNamedAssets(bookmarkRemoteId, serverId, fileName, keepAssetId)
    } catch (e: Exception) {
        AppLogger.d("AssetPruning", "Looking up superseded assets failed: ${e.message}")
        return
    }
    superseded.forEach { asset ->
        asset.localPath?.let { path ->
            try {
                FileUtils.deleteFile(path)
            } catch (_: Exception) {
                // A file we cannot delete is cache: the row goes either way.
            }
        }
        try {
            deleteAsset(asset.id)
        } catch (e: Exception) {
            AppLogger.d("AssetPruning", "Deleting superseded asset row failed: ${e.message}")
        }
    }
}
