package com.karakept.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.karakept.app.data.local.entity.PendingActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingActionDao {
    /**
     * Get all pending actions for a specific server, ordered by creation time.
     */
    @Query("SELECT * FROM pending_actions WHERE serverId = :serverId ORDER BY createdAt ASC")
    fun getPendingActionsForServer(serverId: String): Flow<List<PendingActionEntity>>
    
    /**
     * Get all pending actions synchronously for sync operations.
     */
    @Query("SELECT * FROM pending_actions WHERE serverId = :serverId ORDER BY createdAt ASC")
    suspend fun getPendingActionsList(serverId: String): List<PendingActionEntity>
    
    /**
     * Insert a new pending action.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: PendingActionEntity): Long
    
    /**
     * Update an existing action (e.g., to increment retry count or update error).
     */
    @Update
    suspend fun updateAction(action: PendingActionEntity)
    
    /**
     * Delete a specific action (after successful sync).
     */
    @Delete
    suspend fun deleteAction(action: PendingActionEntity)
    
    /**
     * Delete all pending actions for a bookmark (e.g., if bookmark is deleted).
     */
    @Query("DELETE FROM pending_actions WHERE bookmarkRemoteId = :bookmarkRemoteId AND serverId = :serverId")
    suspend fun deleteActionsForBookmark(bookmarkRemoteId: Long, serverId: String)
    
    /**
     * Delete all pending actions for a server.
     */
    @Query("DELETE FROM pending_actions WHERE serverId = :serverId")
    suspend fun deleteAllActionsForServer(serverId: String)
    
    /**
     * Count pending actions for a server.
     */
    @Query("SELECT COUNT(*) FROM pending_actions WHERE serverId = :serverId")
    suspend fun countPendingActions(serverId: String): Int
}
