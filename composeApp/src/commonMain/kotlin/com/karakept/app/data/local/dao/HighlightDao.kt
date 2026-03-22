package com.karakept.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.karakept.app.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE serverId = :serverId ORDER BY createdAt DESC")
    fun getHighlightsForServer(serverId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE serverId = :serverId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getHighlightsPagedForServer(serverId: String, limit: Int, offset: Int): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE bookmarkRemoteId = :bookmarkRemoteId AND serverId = :serverId ORDER BY createdAt DESC")
    fun getHighlightsForBookmark(bookmarkRemoteId: String, serverId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE remoteId = :remoteId")
    suspend fun getHighlightByRemoteId(remoteId: String): HighlightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlights(highlights: List<HighlightEntity>)

    @Update
    suspend fun updateHighlight(highlight: HighlightEntity)

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE remoteId = :remoteId")
    suspend fun deleteHighlightByRemoteId(remoteId: String)

    @Query("DELETE FROM highlights WHERE serverId = :serverId")
    suspend fun deleteHighlightsForServer(serverId: String)

    @Query("DELETE FROM highlights WHERE bookmarkRemoteId = :bookmarkRemoteId AND serverId = :serverId AND remoteId NOT IN (:keepIds) AND remoteId NOT LIKE 'temp_%'")
    suspend fun deleteHighlightsNotIn(bookmarkRemoteId: String, serverId: String, keepIds: List<String>)

    @Query("DELETE FROM highlights WHERE bookmarkRemoteId = :bookmarkRemoteId AND serverId = :serverId AND remoteId NOT LIKE 'temp_%'")
    suspend fun deleteAllHighlightsForBookmark(bookmarkRemoteId: String, serverId: String)

    @Query("DELETE FROM highlights WHERE serverId = :serverId AND remoteId NOT IN (:keepIds) AND remoteId NOT LIKE 'temp_%'")
    suspend fun deleteHighlightsNotInList(serverId: String, keepIds: List<String>)

    @Query("DELETE FROM highlights WHERE serverId = :serverId AND remoteId NOT LIKE 'temp_%'")
    suspend fun deleteAllNonTempHighlightsForServer(serverId: String)
}
