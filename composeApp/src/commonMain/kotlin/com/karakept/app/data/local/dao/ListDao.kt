package com.karakept.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karakept.app.data.local.entity.ListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Query("SELECT * FROM lists WHERE serverId = :serverId ORDER BY name ASC")
    fun getListsForServer(serverId: String): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists WHERE serverId = :serverId")
    suspend fun getListsForServerOnce(serverId: String): List<ListEntity>

    @Query("SELECT * FROM lists WHERE remoteId = :remoteId AND serverId = :serverId")
    suspend fun getListByRemoteId(remoteId: String, serverId: String): ListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLists(lists: List<ListEntity>)

    @Query("DELETE FROM lists WHERE serverId = :serverId AND remoteId NOT IN (:keepIds)")
    suspend fun deleteRemovedLists(serverId: String, keepIds: List<String>)

    @Query("DELETE FROM lists WHERE serverId = :serverId")
    suspend fun deleteAllForServer(serverId: String)
}
