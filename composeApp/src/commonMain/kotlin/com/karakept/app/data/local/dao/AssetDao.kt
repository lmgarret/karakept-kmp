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

    @Query("DELETE FROM assets WHERE serverId = :serverId")
    suspend fun deleteAllAssetsForServer(serverId: String)
}
