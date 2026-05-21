package com.karakept.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("DELETE FROM assets WHERE serverId = :serverId")
    suspend fun deleteAllAssetsForServer(serverId: String)
}
