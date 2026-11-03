package com.karakept.app.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.local.entity.RETIRED_PREDICATE
import com.karakept.app.data.local.projection.AssetPathRow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE bookmarkRemoteId = :bookmarkRemoteId AND serverId = :serverId")
    suspend fun getAssetsForBookmark(bookmarkRemoteId: String, serverId: String): List<AssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<AssetEntity>)

    // Insert server-side asset metadata without overwriting an existing localPath
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssetMetadataOnly(assets: List<AssetEntity>)

    // Remove the local file reference while keeping the server-side metadata row
    @Query("UPDATE assets SET localPath = NULL WHERE id = :assetId")
    suspend fun clearLocalPath(assetId: String)

    // Drop the row entirely — used when the asset is deleted on the server, not just locally
    @Query("DELETE FROM assets WHERE id = :assetId")
    suspend fun deleteAsset(assetId: String)

    @Query("DELETE FROM assets WHERE serverId = :serverId")
    suspend fun deleteAllAssetsForServer(serverId: String)

    @Query("SELECT localPath FROM assets WHERE localPath IS NOT NULL")
    suspend fun getAllLocalPaths(): List<String>

    @Query("SELECT bookmarkRemoteId, serverId, localPath FROM assets WHERE localPath IS NOT NULL")
    suspend fun getLocalPathOwners(): List<AssetPathRow>

    @Query("UPDATE assets SET localPath = NULL WHERE bookmarkRemoteId = :bookmarkRemoteId AND serverId = :serverId")
    suspend fun clearLocalPathsForBookmark(bookmarkRemoteId: String, serverId: String)

    // The rows stay, so the viewer still lists the asset as available on the server.
    @Query(
        "UPDATE assets SET localPath = NULL WHERE localPath IS NOT NULL AND EXISTS (" +
            "SELECT 1 FROM bookmarks b WHERE b.remoteId = assets.bookmarkRemoteId " +
            "AND b.serverId = assets.serverId AND " + RETIRED_PREDICATE + ")"
    )
    suspend fun clearLocalPathsOfRetired(cutoff: Long): Int

    @Query("UPDATE assets SET localPath = NULL WHERE localPath IS NOT NULL")
    suspend fun clearAllLocalPaths(): Int

    // Rows left behind by bookmarks deleted locally or on the server.
    @Query(
        "DELETE FROM assets WHERE NOT EXISTS (" +
            "SELECT 1 FROM bookmarks b WHERE b.remoteId = assets.bookmarkRemoteId " +
            "AND b.serverId = assets.serverId)"
    )
    suspend fun deleteOrphaned(): Int
}
