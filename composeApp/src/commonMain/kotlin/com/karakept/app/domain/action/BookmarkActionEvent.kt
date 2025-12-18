package com.karakept.app.domain.action

import com.karakept.app.data.local.entity.BookmarkEntity

/**
 * Sealed hierarchy representing all possible bookmark actions.
 * Each action contains the data needed to execute and potentially undo the operation.
 */
sealed class BookmarkActionEvent {
    abstract val bookmark: BookmarkEntity
    abstract val requiresUndo: Boolean

    data class Archive(
        override val bookmark: BookmarkEntity,
        override val requiresUndo: Boolean = true
    ) : BookmarkActionEvent()

    data class Unarchive(
        override val bookmark: BookmarkEntity,
        override val requiresUndo: Boolean = true
    ) : BookmarkActionEvent()

    data class MarkRead(
        override val bookmark: BookmarkEntity,
        override val requiresUndo: Boolean = true
    ) : BookmarkActionEvent()

    data class MarkUnread(
        override val bookmark: BookmarkEntity,
        override val requiresUndo: Boolean = true
    ) : BookmarkActionEvent()

    data class ToggleFavorite(
        override val bookmark: BookmarkEntity,
        override val requiresUndo: Boolean = true
    ) : BookmarkActionEvent()

    data class Delete(
        override val bookmark: BookmarkEntity,
        override val requiresUndo: Boolean = true
    ) : BookmarkActionEvent()

    // Non-undoable actions
    data class UpdateTags(
        override val bookmark: BookmarkEntity,
        val newTags: List<String>,
        override val requiresUndo: Boolean = false
    ) : BookmarkActionEvent()

    data class MoveToList(
        override val bookmark: BookmarkEntity,
        val listId: String,
        override val requiresUndo: Boolean = false
    ) : BookmarkActionEvent()

    data class RemoveFromList(
        override val bookmark: BookmarkEntity,
        val listId: String,
        override val requiresUndo: Boolean = false
    ) : BookmarkActionEvent()
}

/**
 * Result of executing a bookmark action
 */
sealed class BookmarkActionResult {
    data class Success(
        val message: String,
        val undoAction: BookmarkActionEvent? = null
    ) : BookmarkActionResult()

    data class Error(val message: String) : BookmarkActionResult()
}

/**
 * Snapshot of bookmark state for undo operations
 */
data class UndoableActionState(
    val originalBookmark: BookmarkEntity,
    val action: BookmarkActionEvent,
    val timestamp: Long,
    val pendingActionIds: List<Long> = emptyList(), // IDs of PendingActionEntity to remove
    val originalPosition: Int = -1 // Original position in the list for restoration
)
