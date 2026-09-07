package com.karakept.app.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.karakept.app.data.local.entity.AssetEntity

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE bookmarkRemoteId = :bookmarkRemoteId AND serverId = :serverId")
    suspend fun getAssetsForBookmark(bookmarkRemoteId: Long, serverId: String): List<AssetEntity>

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
}
