package com.karakept.app.domain.action

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.repository.BookmarkActionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Event emitted when an undo operation completes
 */
data class UndoCompletedEvent(
    val restoredBookmark: BookmarkEntity,
    val originalPosition: Int
)

/**
 * Central controller for bookmark actions with undo support.
 * Coordinates action execution, snackbar display, and undo operations.
 */
class BookmarkActionController(
    private val bookmarkActionsRepository: BookmarkActionsRepository,
    private val bookmarkDao: BookmarkDao,
    private val pendingActionDao: PendingActionDao,
    private val snackbarManager: ActionSnackbarManager
) {
    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Undo cache: stores last executed action for 5 seconds
    private val undoCache = mutableMapOf<String, UndoableActionState>()
    private val UNDO_TIMEOUT_MS = 5000L

    // Event flow for undo completion - UI can listen to restore items to lists
    private val _undoCompletedEvents = MutableSharedFlow<UndoCompletedEvent>(extraBufferCapacity = 10)
    val undoCompletedEvents: SharedFlow<UndoCompletedEvent> = _undoCompletedEvents

    /**
     * Execute a bookmark action with automatic undo support
     * @param event The action to execute
     * @param originalPosition Optional position of the bookmark in the UI list for undo restoration
     */
    suspend fun executeAction(event: BookmarkActionEvent, originalPosition: Int = -1): BookmarkActionResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Capture current state for undo (before modification)
                val undoState = if (event.requiresUndo) {
                    captureUndoState(event, originalPosition)
                } else null

                // 2. Execute the action via repository
                val result = when (event) {
                    is BookmarkActionEvent.Archive -> {
                        bookmarkActionsRepository.archiveBookmark(
                            event.bookmark.remoteId,
                            event.bookmark.serverId
                        )
                        BookmarkActionResult.Success("Bookmark archived")
                    }

                    is BookmarkActionEvent.Unarchive -> {
                        bookmarkActionsRepository.unarchiveBookmark(
                            event.bookmark.remoteId,
                            event.bookmark.serverId
                        )
                        BookmarkActionResult.Success("Bookmark unarchived")
                    }

                    is BookmarkActionEvent.MarkRead -> {
                        bookmarkActionsRepository.markAsRead(
                            event.bookmark.remoteId,
                            event.bookmark.serverId
                        )
                        BookmarkActionResult.Success("Marked as read")
                    }

                    is BookmarkActionEvent.MarkUnread -> {
                        bookmarkActionsRepository.markAsUnread(
                            event.bookmark.remoteId,
                            event.bookmark.serverId
                        )
                        BookmarkActionResult.Success("Marked as unread")
                    }

                    is BookmarkActionEvent.ToggleFavorite -> {
                        bookmarkActionsRepository.toggleFavourite(
                            event.bookmark.remoteId,
                            event.bookmark.serverId,
                            event.bookmark.isStarred
                        )
                        val message = if (event.bookmark.isStarred) {
                            "Removed from favorites"
                        } else {
                            "Added to favorites"
                        }
                        BookmarkActionResult.Success(message)
                    }

                    is BookmarkActionEvent.Delete -> {
                        bookmarkActionsRepository.deleteBookmark(
                            event.bookmark.localId,
                            event.bookmark.remoteId,
                            event.bookmark.serverId
                        )
                        BookmarkActionResult.Success("Bookmark deleted")
                    }

                    is BookmarkActionEvent.UpdateTags -> {
                        val isOffline = false // Get from settings if needed
                        bookmarkActionsRepository.updateTags(
                            event.bookmark.remoteId,
                            event.bookmark.serverId,
                            event.newTags,
                            !isOffline
                        )
                        BookmarkActionResult.Success("Tags updated")
                    }

                    is BookmarkActionEvent.MoveToList -> {
                        val isOffline = false
                        bookmarkActionsRepository.moveToList(
                            event.bookmark.remoteId,
                            event.bookmark.serverId,
                            event.listId,
                            !isOffline
                        )
                        BookmarkActionResult.Success("Moved to list")
                    }

                    is BookmarkActionEvent.RemoveFromList -> {
                        val isOffline = false
                        bookmarkActionsRepository.removeFromList(
                            event.bookmark.remoteId,
                            event.bookmark.serverId,
                            event.listId,
                            !isOffline
                        )
                        BookmarkActionResult.Success("Removed from list")
                    }
                }

                // 3. Store undo state and show snackbar
                if (undoState != null && result is BookmarkActionResult.Success) {
                    storeUndoState(event.bookmark.serverId + "_" + event.bookmark.remoteId, undoState)

                    // Show snackbar with undo action
                    snackbarManager.showSnackbarWithUndo(
                        message = result.message,
                        onUndo = { undoAction(event.bookmark.serverId + "_" + event.bookmark.remoteId) }
                    )
                } else if (result is BookmarkActionResult.Success) {
                    snackbarManager.showSnackbar(result.message)
                }

                result
            } catch (e: Exception) {
                BookmarkActionResult.Error(e.message ?: "Action failed")
            }
        }
    }

    /**
     * Capture the current state before modification for undo
     */
    private suspend fun captureUndoState(event: BookmarkActionEvent, originalPosition: Int): UndoableActionState {
        // Get current pending actions for this bookmark (to remove on undo)
        val pendingActions = pendingActionDao.getPendingActionsList(event.bookmark.serverId)
            .filter { it.bookmarkRemoteId == event.bookmark.remoteId }
            .map { it.id }

        return UndoableActionState(
            originalBookmark = event.bookmark.copy(), // Deep copy
            action = event,
            timestamp = System.currentTimeMillis(),
            pendingActionIds = pendingActions,
            originalPosition = originalPosition
        )
    }

    /**
     * Store undo state with automatic cleanup after timeout
     */
    private fun storeUndoState(key: String, state: UndoableActionState) {
        undoCache[key] = state

        // Auto-cleanup after timeout
        controllerScope.launch {
            delay(UNDO_TIMEOUT_MS)
            undoCache.remove(key)
        }
    }

    /**
     * Execute undo operation
     */
    private suspend fun undoAction(key: String) {
        val undoState = undoCache[key] ?: return

        withContext(Dispatchers.IO) {
            try {
                // 1. Restore original bookmark state in DB
                bookmarkDao.insertBookmark(undoState.originalBookmark)

                // 2. Remove pending actions that were queued by the undone action
                // Get current pending actions
                val currentPendingActions = pendingActionDao.getPendingActionsList(
                    undoState.originalBookmark.serverId
                )

                // Find actions created AFTER our snapshot (these are the ones we queued)
                val actionsToRemove = currentPendingActions.filter { pending ->
                    pending.bookmarkRemoteId == undoState.originalBookmark.remoteId &&
                    pending.createdAt > undoState.timestamp
                }

                actionsToRemove.forEach { pendingActionDao.deleteAction(it) }

                // 3. Queue opposite action if original action was already synced
                // If there are no pending actions newer than our timestamp, the action was synced
                if (actionsToRemove.isEmpty()) {
                    queueOppositeAction(undoState.action, undoState.originalBookmark)
                }

                // 4. Remove from undo cache
                undoCache.remove(key)

                // 5. Emit event to notify UI to restore the bookmark to lists
                // Re-fetch the bookmark to get the updated state
                val restoredBookmark = bookmarkDao.getBookmarkByRemoteId(
                    undoState.originalBookmark.remoteId,
                    undoState.originalBookmark.serverId
                )
                if (restoredBookmark != null) {
                    _undoCompletedEvents.emit(UndoCompletedEvent(restoredBookmark, undoState.originalPosition))
                }

                // 6. Show confirmation
                snackbarManager.showSnackbar("Undone")

            } catch (e: Exception) {
                snackbarManager.showSnackbar("Undo failed: ${e.message}")
            }
        }
    }

    /**
     * Queue the opposite action when undoing a synced action
     */
    private suspend fun queueOppositeAction(
        originalAction: BookmarkActionEvent,
        bookmark: BookmarkEntity
    ) {
        when (originalAction) {
            is BookmarkActionEvent.Archive -> {
                bookmarkActionsRepository.unarchiveBookmark(bookmark.remoteId, bookmark.serverId)
            }
            is BookmarkActionEvent.Unarchive -> {
                bookmarkActionsRepository.archiveBookmark(bookmark.remoteId, bookmark.serverId)
            }
            is BookmarkActionEvent.MarkRead -> {
                bookmarkActionsRepository.markAsUnread(bookmark.remoteId, bookmark.serverId)
            }
            is BookmarkActionEvent.MarkUnread -> {
                bookmarkActionsRepository.markAsRead(bookmark.remoteId, bookmark.serverId)
            }
            is BookmarkActionEvent.ToggleFavorite -> {
                bookmarkActionsRepository.toggleFavourite(
                    bookmark.remoteId,
                    bookmark.serverId,
                    !bookmark.isStarred // Opposite of original state
                )
            }
            is BookmarkActionEvent.Delete -> {
                // For delete undo, we need to re-create the bookmark
                // This is complex and may require server API support
                // For now, show a message that delete cannot be undone after sync
                snackbarManager.showSnackbar("Cannot undo deleted bookmark after sync")
            }
            else -> {
                // Non-undoable actions
            }
        }
    }

    /**
     * Clear all undo states (e.g., on screen disposal)
     */
    fun clearUndoCache() {
        undoCache.clear()
    }
}
