package com.karakept.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to store bookmark actions that are pending sync with the server.
 * This enables offline-first functionality where actions are queued locally
 * and synchronized when network connectivity is restored.
 */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    /** Remote ID of the bookmark this action applies to */
    val bookmarkRemoteId: Long,
    
    /** Server ID where this bookmark lives */
    val serverId: String,
    
    /** Type of action: archive, favourite, delete, move_to_list, update_tags, mark_read */
    val actionType: String,
    
    /** JSON-encoded action parameters (e.g., list ID, tag names, etc.) */
    val actionData: String,
    
    /** Timestamp when action was created (milliseconds since epoch) */
    val createdAt: Long,
    
    /** Number of times this action has been retried */
    val retryCount: Int = 0,

    /** Last error message if action failed */
    val lastError: String? = null,

    /** [STATUS_PENDING] while retryable, [STATUS_FAILED] once given up — never silently dropped */
    val status: String = STATUS_PENDING,

    /** Earliest epoch-millis timestamp this action may be retried (exponential backoff) */
    val nextAttemptAt: Long = 0
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_FAILED = "failed"
    }
}

/** Action types for bookmark operations */
object PendingActionType {
    const val ARCHIVE = "archive"
    const val UNARCHIVE = "unarchive"
    const val FAVOURITE = "favourite"
    const val UNFAVOURITE = "unfavourite"
    const val DELETE = "delete"
    const val MOVE_TO_LIST = "move_to_list"
    const val REMOVE_FROM_LIST = "remove_from_list"
    const val UPDATE_TAGS = "update_tags"
    const val MARK_READ = "mark_read"
    const val MARK_UNREAD = "mark_unread"
    const val CREATE_HIGHLIGHT = "create_highlight"
    const val UPDATE_HIGHLIGHT = "update_highlight"
    const val DELETE_HIGHLIGHT = "delete_highlight"
    const val UPDATE_READING_PROGRESS = "update_reading_progress"
}
