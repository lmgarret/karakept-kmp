package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.karakept.app.utils.AppLogger

/**
 * Repository for handling bookmark actions with offline-first approach.
 * Actions are queued locally and synced when online.
 */
class BookmarkActionsRepository(
    internal val bookmarkDao: BookmarkDao,
    internal val pendingActionDao: PendingActionDao,
    internal val remoteDataSource: RemoteDataSource,
    internal val serverRepository: com.karakept.app.data.repository.ServerRepository,
    private val settingsRepository: com.karakept.app.data.repository.SettingsRepository
) {
    internal val jsonSerializer = Json { ignoreUnknownKeys = true }

    // Lazy injection to break circular dependency
    private var _bookmarkRepository: com.karakept.app.data.repository.BookmarkRepository? = null
    internal var highlightDao: com.karakept.app.data.local.dao.HighlightDao? = null
        private set

    // Track when actions are being performed
    private val _isPerformingAction = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isPerformingAction: kotlinx.coroutines.flow.StateFlow<Boolean> = _isPerformingAction

    // Emits the remoteId of a bookmark whenever it has been locally modified by an action.
    // Observers (e.g. MainScreenModel) can react to keep their lists up-to-date without a full sync.
    private val _bookmarkChangedEvents = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val bookmarkChangedEvents: SharedFlow<Long> = _bookmarkChangedEvents

    fun notifyBookmarkChanged(remoteId: Long) {
        _bookmarkChangedEvents.tryEmit(remoteId)
    }
    
    // Audit (Phase 02): No tag cache exists -- markAsRead/markAsUnread use direct DB writes.
    // The originally-feared read/unread race condition does not apply to the current implementation.
    
    fun setBookmarkRepository(repository: com.karakept.app.data.repository.BookmarkRepository) {
        _bookmarkRepository = repository
    }

    fun setHighlightDao(dao: com.karakept.app.data.local.dao.HighlightDao) {
        highlightDao = dao
    }

    // Scope for background operations like auto-sync
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    /**
     * Archive a bookmark. Updates locally and queues for sync.
     */
    suspend fun archiveBookmark(bookmarkRemoteId: Long, serverId: String) {
        performAction {
            withContext(Dispatchers.IO) {
                // Update local copy immediately (optimistic update)
                val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
                bookmark?.let {
                    bookmarkDao.insertBookmark(it.copy(isArchived = true))
                }

                // Queue action
                queueAction(
                    bookmarkRemoteId = bookmarkRemoteId,
                    serverId = serverId,
                    actionType = PendingActionType.ARCHIVE,
                    actionData = "{}"
                )

                _bookmarkChangedEvents.emit(bookmarkRemoteId)

                // Auto-sync if not in offline mode
                triggerAutoSync(serverId)
            }
        }
    }

    /**
     * Unarchive a bookmark.
     */
    suspend fun unarchiveBookmark(bookmarkRemoteId: Long, serverId: String) {
        performAction {
            withContext(Dispatchers.IO) {
                val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
                bookmark?.let {
                    bookmarkDao.insertBookmark(it.copy(isArchived = false))
                }

                queueAction(
                    bookmarkRemoteId = bookmarkRemoteId,
                    serverId = serverId,
                    actionType = PendingActionType.UNARCHIVE,
                    actionData = "{}"
                )

                _bookmarkChangedEvents.emit(bookmarkRemoteId)

                // Auto-sync if not in offline mode
                triggerAutoSync(serverId)
            }
        }
    }

    /**
     * Toggle favourite status of a bookmark.
     */
    suspend fun toggleFavourite(
        bookmarkRemoteId: Long,
        serverId: String,
        currentlyFavourited: Boolean
    ) {
        performAction {
            withContext(Dispatchers.IO) {
                val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
                bookmark?.let {
                    bookmarkDao.insertBookmark(it.copy(isStarred = !currentlyFavourited))
                }

                queueAction(
                    bookmarkRemoteId = bookmarkRemoteId,
                    serverId = serverId,
                    actionType = if (currentlyFavourited) PendingActionType.UNFAVOURITE else PendingActionType.FAVOURITE,
                    actionData = "{}"
                )

                _bookmarkChangedEvents.emit(bookmarkRemoteId)

                // Auto-sync if not in offline mode
                triggerAutoSync(serverId)
            }
        }
    }

    /**
     * Mark bookmark as read locally and sync the completed progress (100%) to server.
     * Read status is primarily driven by reading progress reaching 1.0,
     * but this allows manual override.
     */
    suspend fun markAsRead(bookmarkRemoteId: Long, serverId: String) {
        performAction {
            withContext(Dispatchers.IO) {
                val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
                bookmark?.let {
                    bookmarkDao.insertBookmark(it.copy(isRead = true, readingProgress = 1f))
                    queueReadingProgressUpdate(bookmarkRemoteId, serverId, progressPercent = 100)
                }
                _bookmarkChangedEvents.emit(bookmarkRemoteId)
                triggerAutoSync(serverId)
            }
        }
    }

    /**
     * Mark bookmark as unread locally and optionally reset server-side progress to 0%.
     * @param resetProgress If true, also resets reading progress and scroll position to zero.
     */
    suspend fun markAsUnread(bookmarkRemoteId: Long, serverId: String, resetProgress: Boolean = false) {
        performAction {
            withContext(Dispatchers.IO) {
                val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
                bookmark?.let {
                    val updated = if (resetProgress) {
                        it.copy(isRead = false, readingProgress = 0f, readingScrollIndex = 0, readingScrollOffset = 0)
                    } else {
                        it.copy(isRead = false)
                    }
                    bookmarkDao.insertBookmark(updated)
                    if (resetProgress) {
                        queueReadingProgressUpdate(bookmarkRemoteId, serverId, progressPercent = 0)
                        triggerAutoSync(serverId)
                    }
                }
                _bookmarkChangedEvents.emit(bookmarkRemoteId)
            }
        }
    }

    /**
     * Queue a reading progress update to be synced to the server.
     * Only one pending update per bookmark is kept (latest wins), so stale updates are discarded.
     */
    suspend fun queueReadingProgressUpdate(
        bookmarkRemoteId: Long,
        serverId: String,
        progressPercent: Int
    ) {
        withContext(Dispatchers.IO) {
            AppLogger.d("ReadProgressSync", "queueReadingProgressUpdate remoteId=$bookmarkRemoteId serverId=$serverId percent=$progressPercent")
            // Remove any stale pending update for this bookmark (keep only latest)
            pendingActionDao.deleteActionsForBookmarkByType(
                bookmarkRemoteId, serverId, PendingActionType.UPDATE_READING_PROGRESS
            )
            queueAction(
                bookmarkRemoteId = bookmarkRemoteId,
                serverId = serverId,
                actionType = PendingActionType.UPDATE_READING_PROGRESS,
                actionData = jsonSerializer.encodeToString(mapOf("progressPercent" to progressPercent.toString()))
            )
            AppLogger.d("ReadProgressSync", "queued pending action successfully")
        }
        triggerAutoSync(serverId)
    }

    /**
     * Delete a bookmark.
     */
    suspend fun deleteBookmark(bookmarkLocalId: Long, bookmarkRemoteId: Long, serverId: String) {
        performAction {
            withContext(Dispatchers.IO) {
                // Get the bookmark BEFORE deleting to preserve originalRemoteId for the API call
                val bookmark = bookmarkDao.getBookmarkById(bookmarkLocalId)
                val originalRemoteId = bookmark?.originalRemoteId

                // Delete locally
                bookmark?.let {
                    bookmarkDao.deleteBookmark(it)
                }

                // Queue action with the originalRemoteId stored in actionData
                queueAction(
                    bookmarkRemoteId = bookmarkRemoteId,
                    serverId = serverId,
                    actionType = PendingActionType.DELETE,
                    actionData = jsonSerializer.encodeToString(mapOf("originalRemoteId" to originalRemoteId))
                )

                _bookmarkChangedEvents.emit(bookmarkRemoteId)

                // Auto-sync if not in offline mode
                triggerAutoSync(serverId)
            }
        }
    }

    /**
     * Update tags on a bookmark.
     */
    suspend fun updateTags(
        bookmarkRemoteId: Long,
        serverId: String,
        newTags: List<String>,
        isOnline: Boolean
    ) {
        withContext(Dispatchers.IO) {
            val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
            bookmark?.let {
                bookmarkDao.insertBookmark(it.copy(
                    tags = newTags.joinToString(",")
                ))
            }

            queueAction(
                bookmarkRemoteId = bookmarkRemoteId,
                serverId = serverId,
                actionType = PendingActionType.UPDATE_TAGS,
                actionData = jsonSerializer.encodeToString(mapOf("tags" to newTags))
            )

            // Auto-sync if not in offline mode
            triggerAutoSync(serverId)
        }
    }

    /**
     * Move bookmark to a list.
     */
    suspend fun moveToList(
        bookmarkRemoteId: Long,
        serverId: String,
        listId: String,
        isOnline: Boolean
    ) {
        withContext(Dispatchers.IO) {
            // Update local DB immediately (optimistic update)
            val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
            bookmark?.let {
                val currentListIds = it.listIds.split(",").map { id -> id.trim() }.filter { id -> id.isNotBlank() }
                if (!currentListIds.contains(listId)) {
                    val newListIds = currentListIds + listId
                    bookmarkDao.insertBookmark(it.copy(listIds = newListIds.joinToString(",")))
                }
            }

            queueAction(
                bookmarkRemoteId = bookmarkRemoteId,
                serverId = serverId,
                actionType = PendingActionType.MOVE_TO_LIST,
                actionData = jsonSerializer.encodeToString(mapOf("listId" to listId))
            )

            _bookmarkChangedEvents.emit(bookmarkRemoteId)

            // Auto-sync if not in offline mode
            triggerAutoSync(serverId)
        }
    }

    /**
     * Remove bookmark from a list.
     */
    suspend fun removeFromList(
        bookmarkRemoteId: Long,
        serverId: String,
        listId: String,
        isOnline: Boolean
    ) {
        withContext(Dispatchers.IO) {
            // Update local DB immediately (optimistic update)
            val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
            bookmark?.let {
                val currentListIds = it.listIds.split(",").map { id -> id.trim() }.filter { id -> id.isNotBlank() }
                val newListIds = currentListIds.filter { id -> id != listId }
                bookmarkDao.insertBookmark(it.copy(listIds = newListIds.joinToString(",")))
            }

            queueAction(
                bookmarkRemoteId = bookmarkRemoteId,
                serverId = serverId,
                actionType = PendingActionType.REMOVE_FROM_LIST,
                actionData = jsonSerializer.encodeToString(mapOf("listId" to listId))
            )

            _bookmarkChangedEvents.emit(bookmarkRemoteId)

            // Auto-sync if not in offline mode
            triggerAutoSync(serverId)
        }
    }

    /**
     * Queue a highlight creation action.
     */
    suspend fun queueCreateHighlight(
        server: Server,
        bookmarkLocalId: Long,
        bookmarkRemoteId: String,
        text: String,
        startOffset: Int,
        endOffset: Int,
        note: String? = null,
        color: String? = null,
        tempId: String? = null
    ) {
        withContext(Dispatchers.IO) {
            queueAction(
                bookmarkRemoteId = bookmarkLocalId,
                serverId = server.id,
                actionType = PendingActionType.CREATE_HIGHLIGHT,
                actionData = jsonSerializer.encodeToString(mapOf(
                    "originalRemoteId" to bookmarkRemoteId,
                    "bookmarkRemoteId" to bookmarkRemoteId,
                    "text" to text,
                    "startOffset" to startOffset.toString(),
                    "endOffset" to endOffset.toString(),
                    "note" to note,
                    "color" to color,
                    "tempId" to tempId
                ))
            )

            // Auto-sync if not in offline mode
            triggerAutoSync(server.id)
        }
    }

    /**
     * Queue a highlight deletion action.
     */
    suspend fun queueDeleteHighlight(
        server: Server,
        bookmarkLocalId: Long,
        highlightRemoteId: String
    ) {
        withContext(Dispatchers.IO) {
            queueAction(
                bookmarkRemoteId = bookmarkLocalId,
                serverId = server.id,
                actionType = PendingActionType.DELETE_HIGHLIGHT,
                actionData = jsonSerializer.encodeToString(mapOf(
                    "highlightId" to highlightRemoteId
                ))
            )
            triggerAutoSync(server.id)
        }
    }

    /**
     * Queue a highlight update action.
     */
    suspend fun queueUpdateHighlight(
        server: Server,
        bookmarkLocalId: Long,
        highlightRemoteId: String,
        note: String? = null,
        color: String? = null
    ) {
        AppLogger.d("BookmarkActionsRepository", "queueUpdateHighlight called - highlightRemoteId=$highlightRemoteId, note=$note, color=$color")
        withContext(Dispatchers.IO) {
            val actionData = jsonSerializer.encodeToString(mapOf(
                "highlightId" to highlightRemoteId,
                "note" to note,
                "color" to color
            ))
            AppLogger.d("BookmarkActionsRepository", "queueUpdateHighlight - actionData=$actionData")
            queueAction(
                bookmarkRemoteId = bookmarkLocalId,
                serverId = server.id,
                actionType = PendingActionType.UPDATE_HIGHLIGHT,
                actionData = actionData
            )
            AppLogger.d("BookmarkActionsRepository", "queueUpdateHighlight - action queued, triggering sync")
            triggerAutoSync(server.id)
        }
    }
    
    /**
     * Helper function to wrap action execution with loading state
     */
    private suspend fun performAction(action: suspend () -> Unit) {
        try {
            _isPerformingAction.value = true
            action()
        } finally {
            _isPerformingAction.value = false
        }
    }

    /**
     * Queue an action for later sync.
     */
    internal suspend fun queueAction(
        bookmarkRemoteId: Long,
        serverId: String,
        actionType: String,
        actionData: String
    ) {
        pendingActionDao.insertAction(
            PendingActionEntity(
                bookmarkRemoteId = bookmarkRemoteId,
                serverId = serverId,
                actionType = actionType,
                actionData = actionData,
                createdAt = System.currentTimeMillis()
            )
        )
    }
    
    /**
     * Trigger auto-sync if not in offline mode.
     * Called after every action to sync changes to server automatically.
     */
    internal suspend fun triggerAutoSync(serverId: String) {
        repositoryScope.launch {
            try {
                // Check if offline mode is enabled
                if (!settingsRepository.offlineMode.first()) {
                    // Get server and trigger sync
                    val servers = serverRepository.servers.first()
                    val server = servers.find { it.id == serverId }
                    server?.let {
                        // Start processing pending actions immediately (Push only)
                        AppLogger.d("BookmarkActionsRepository", "Triggering push-only sync for server ${server.id}")
                        processPendingActions(it)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("BookmarkActionsRepository", "Error in auto-sync: ${e.message}")
                // Don't propagate error - auto-sync is a best-effort operation
            }
        }
    }

    // Mutex to ensure only one action processing happens at a time
    internal val actionMutex = kotlinx.coroutines.sync.Mutex()
}
