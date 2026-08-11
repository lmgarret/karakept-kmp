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
     * Actions eligible for processing: still pending and past their backoff window.
     */
    @Query("SELECT * FROM pending_actions WHERE serverId = :serverId AND status = 'pending' AND nextAttemptAt <= :now ORDER BY createdAt ASC")
    suspend fun getProcessableActions(serverId: String, now: Long): List<PendingActionEntity>

    /**
     * Actions that exhausted their retries (or hit a permanent error) — kept for the
     * user to retry or discard rather than silently dropped.
     */
    @Query("SELECT * FROM pending_actions WHERE serverId = :serverId AND status = 'failed' ORDER BY createdAt ASC")
    fun getFailedActionsForServer(serverId: String): Flow<List<PendingActionEntity>>

    @Query("SELECT COUNT(*) FROM pending_actions WHERE serverId = :serverId AND status = 'failed'")
    fun countFailedActions(serverId: String): Flow<Int>

    /**
     * Requeue all failed actions for another round of attempts.
     */
    @Query("UPDATE pending_actions SET status = 'pending', retryCount = 0, nextAttemptAt = 0 WHERE serverId = :serverId AND status = 'failed'")
    suspend fun requeueFailedActions(serverId: String)

    @Query("DELETE FROM pending_actions WHERE serverId = :serverId AND status = 'failed'")
    suspend fun deleteFailedActions(serverId: String)
    
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
     * Delete all pending actions of a specific type for a bookmark.
     * Used to deduplicate actions (e.g., keep only the latest reading progress update).
     */
    @Query("DELETE FROM pending_actions WHERE bookmarkRemoteId = :bookmarkRemoteId AND serverId = :serverId AND actionType = :actionType")
    suspend fun deleteActionsForBookmarkByType(bookmarkRemoteId: Long, serverId: String, actionType: String)
    
    /**
     * Whether this bookmark has an unsynced action of [actionType] waiting. Used by the
     * reading-progress pull to tell "the server is authoritative" from "we have a newer
     * local value that simply has not been pushed yet".
     */
    @Query("SELECT COUNT(*) FROM pending_actions WHERE bookmarkRemoteId = :bookmarkRemoteId AND serverId = :serverId AND actionType = :actionType")
    suspend fun countActionsForBookmarkByType(
        bookmarkRemoteId: Long,
        serverId: String,
        actionType: String
    ): Int

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
