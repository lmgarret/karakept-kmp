package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.utils.AppLogger
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.api.model.*
import com.karakept.api.infrastructure.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Repository for handling bookmark actions with offline-first approach.
 * Actions are queued locally and synced when online.
 */
class BookmarkActionsRepository(
    private val bookmarkDao: BookmarkDao,
    private val pendingActionDao: PendingActionDao,
    private val remoteDataSource: RemoteDataSource,
    private val serverRepository: com.karakept.app.data.repository.ServerRepository,
    private val settingsRepository: com.karakept.app.data.repository.SettingsRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Lazy injection to break circular dependency
    private var _bookmarkRepository: com.karakept.app.data.repository.BookmarkRepository? = null
    private var _highlightDao: com.karakept.app.data.local.dao.HighlightDao? = null

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
    
    // Cache for tag IDs to handle read/unread toggling race conditions
    
    fun setBookmarkRepository(repository: com.karakept.app.data.repository.BookmarkRepository) {
        _bookmarkRepository = repository
    }

    fun setHighlightDao(dao: com.karakept.app.data.local.dao.HighlightDao) {
        _highlightDao = dao
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
            println("ReadProgressSync: queueReadingProgressUpdate remoteId=$bookmarkRemoteId serverId=$serverId percent=$progressPercent")
            // Remove any stale pending update for this bookmark (keep only latest)
            pendingActionDao.deleteActionsForBookmarkByType(
                bookmarkRemoteId, serverId, PendingActionType.UPDATE_READING_PROGRESS
            )
            queueAction(
                bookmarkRemoteId = bookmarkRemoteId,
                serverId = serverId,
                actionType = PendingActionType.UPDATE_READING_PROGRESS,
                actionData = json.encodeToString(mapOf("progressPercent" to progressPercent.toString()))
            )
            println("ReadProgressSync: queued pending action successfully")
        }
        triggerAutoSync(serverId)
    }

    /**
     * Fetch reading progress from the server and apply it locally if it is higher than the
     * current local progress. Used when opening a bookmark on a new device to restore
     * cross-device reading position.
     *
     * @return true if local progress was updated from server data, false otherwise.
     */
    suspend fun pullReadingProgressFromServer(bookmarkRemoteId: Long, serverId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                println("ReadProgressSync: pullReadingProgressFromServer remoteId=$bookmarkRemoteId serverId=$serverId")
                val servers = serverRepository.servers.first()
                val server = servers.find { it.id == serverId }
                if (server == null) {
                    println("ReadProgressSync: pull ABORT — server not found for serverId=$serverId")
                    return@withContext false
                }
                val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
                if (bookmark == null) {
                    println("ReadProgressSync: pull ABORT — bookmark not found in DB for remoteId=$bookmarkRemoteId")
                    return@withContext false
                }

                println("ReadProgressSync: fetching server progress for originalRemoteId=${bookmark.originalRemoteId}")
                val serverPercent = remoteDataSource.getReadingProgress(server, bookmark.originalRemoteId)
                if (serverPercent == null) {
                    println("ReadProgressSync: pull — server returned null progress")
                    return@withContext false
                }

                println("ReadProgressSync: server has ${serverPercent}%, local has ${(bookmark.readingProgress * 100).toInt()}%")
                val serverProgress = serverPercent / 100f
                // Only apply server progress if it's higher than local (avoid overwriting newer local data)
                if (serverProgress > bookmark.readingProgress) {
                    bookmarkDao.updateReadingProgress(
                        localId = bookmark.localId,
                        progress = serverProgress,
                        scrollIndex = 0,
                        scrollOffset = 0
                    )
                    println("ReadProgressSync: restored from server: ${serverPercent}%")
                    true
                } else {
                    println("ReadProgressSync: server progress not higher, keeping local")
                    false
                }
            } catch (e: Exception) {
                println("BookmarkActionsRepository: Failed to pull reading progress: ${e.message}")
                false
            }
        }
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
                    actionData = json.encodeToString(mapOf("originalRemoteId" to originalRemoteId))
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
                actionData = json.encodeToString(mapOf("tags" to newTags))
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
                actionData = json.encodeToString(mapOf("listId" to listId))
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
                actionData = json.encodeToString(mapOf("listId" to listId))
            )

            _bookmarkChangedEvents.emit(bookmarkRemoteId)

            // Auto-sync if not in offline mode
            triggerAutoSync(serverId)
        }
    }

    // =========================================================================
    // Batch operations (no snackbar — callers handle UI feedback)
    // =========================================================================

    /**
     * Archive a list of bookmarks without showing individual snackbars.
     */
    suspend fun batchArchive(bookmarks: List<BookmarkEntity>) {
        withContext(Dispatchers.IO) {
            bookmarks.forEach { bookmark ->
                val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
                current?.let { bookmarkDao.insertBookmark(it.copy(isArchived = true)) }
                queueAction(
                    bookmarkRemoteId = bookmark.remoteId,
                    serverId = bookmark.serverId,
                    actionType = PendingActionType.ARCHIVE,
                    actionData = "{}"
                )
                _bookmarkChangedEvents.emit(bookmark.remoteId)
            }
            bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
        }
    }

    /**
     * Unarchive a list of bookmarks without showing individual snackbars.
     */
    suspend fun batchUnarchive(bookmarks: List<BookmarkEntity>) {
        withContext(Dispatchers.IO) {
            bookmarks.forEach { bookmark ->
                val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
                current?.let { bookmarkDao.insertBookmark(it.copy(isArchived = false)) }
                queueAction(
                    bookmarkRemoteId = bookmark.remoteId,
                    serverId = bookmark.serverId,
                    actionType = PendingActionType.UNARCHIVE,
                    actionData = "{}"
                )
                _bookmarkChangedEvents.emit(bookmark.remoteId)
            }
            bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
        }
    }

    /**
     * Mark a list of bookmarks as read without showing individual snackbars.
     */
    suspend fun batchMarkRead(bookmarks: List<BookmarkEntity>) {
        withContext(Dispatchers.IO) {
            bookmarks.forEach { bookmark ->
                val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
                current?.let {
                    bookmarkDao.insertBookmark(it.copy(isRead = true, readingProgress = 1f))
                    queueReadingProgressUpdate(bookmark.remoteId, bookmark.serverId, progressPercent = 100)
                }
                _bookmarkChangedEvents.emit(bookmark.remoteId)
            }
            bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
        }
    }

    /**
     * Mark a list of bookmarks as unread without showing individual snackbars.
     */
    suspend fun batchMarkUnread(bookmarks: List<BookmarkEntity>, resetProgress: Boolean = false) {
        withContext(Dispatchers.IO) {
            bookmarks.forEach { bookmark ->
                val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
                current?.let {
                    val updated = if (resetProgress) {
                        it.copy(isRead = false, readingProgress = 0f, readingScrollIndex = 0, readingScrollOffset = 0)
                    } else {
                        it.copy(isRead = false)
                    }
                    bookmarkDao.insertBookmark(updated)
                }
                _bookmarkChangedEvents.emit(bookmark.remoteId)
            }
        }
    }

    /**
     * Set favourite status for a list of bookmarks without showing individual snackbars.
     */
    suspend fun batchSetFavourite(bookmarks: List<BookmarkEntity>, makeFavourite: Boolean) {
        withContext(Dispatchers.IO) {
            bookmarks.forEach { bookmark ->
                val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
                current?.let { bookmarkDao.insertBookmark(it.copy(isStarred = makeFavourite)) }
                queueAction(
                    bookmarkRemoteId = bookmark.remoteId,
                    serverId = bookmark.serverId,
                    actionType = if (makeFavourite) PendingActionType.FAVOURITE else PendingActionType.UNFAVOURITE,
                    actionData = "{}"
                )
                _bookmarkChangedEvents.emit(bookmark.remoteId)
            }
            bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
        }
    }

    /**
     * Delete a list of bookmarks without showing individual snackbars.
     */
    suspend fun batchDelete(bookmarks: List<BookmarkEntity>) {
        withContext(Dispatchers.IO) {
            bookmarks.forEach { bookmark ->
                val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
                if (current != null) {
                    val originalRemoteId = current.originalRemoteId
                    bookmarkDao.deleteBookmark(current)
                    queueAction(
                        bookmarkRemoteId = bookmark.remoteId,
                        serverId = bookmark.serverId,
                        actionType = PendingActionType.DELETE,
                        actionData = json.encodeToString(mapOf("originalRemoteId" to originalRemoteId))
                    )
                    _bookmarkChangedEvents.emit(bookmark.remoteId)
                }
            }
            bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
        }
    }

    /**
     * Set the same tag list on all selected bookmarks without showing individual snackbars.
     */
    suspend fun batchUpdateTags(bookmarks: List<BookmarkEntity>, newTags: List<String>) {
        withContext(Dispatchers.IO) {
            bookmarks.forEach { bookmark ->
                val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
                current?.let { bookmarkDao.insertBookmark(it.copy(tags = newTags.joinToString(","))) }
                queueAction(
                    bookmarkRemoteId = bookmark.remoteId,
                    serverId = bookmark.serverId,
                    actionType = PendingActionType.UPDATE_TAGS,
                    actionData = json.encodeToString(mapOf("tags" to newTags))
                )
                _bookmarkChangedEvents.emit(bookmark.remoteId)
            }
            bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
        }
    }

    /**
     * Add a list of bookmarks to a list without showing individual snackbars.
     */
    suspend fun batchMoveToList(bookmarks: List<BookmarkEntity>, listId: String) {
        withContext(Dispatchers.IO) {
            bookmarks.forEach { bookmark ->
                val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
                current?.let {
                    val currentListIds = it.listIds.split(",").map { id -> id.trim() }.filter { id -> id.isNotBlank() }
                    if (!currentListIds.contains(listId)) {
                        bookmarkDao.insertBookmark(it.copy(listIds = (currentListIds + listId).joinToString(",")))
                    }
                }
                queueAction(
                    bookmarkRemoteId = bookmark.remoteId,
                    serverId = bookmark.serverId,
                    actionType = PendingActionType.MOVE_TO_LIST,
                    actionData = json.encodeToString(mapOf("listId" to listId))
                )
                _bookmarkChangedEvents.emit(bookmark.remoteId)
            }
            bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
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
                actionData = json.encodeToString(mapOf(
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
                actionData = json.encodeToString(mapOf(
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
        println("BookmarkActionsRepository: queueUpdateHighlight called - highlightRemoteId=$highlightRemoteId, note=$note, color=$color")
        withContext(Dispatchers.IO) {
            val actionData = json.encodeToString(mapOf(
                "highlightId" to highlightRemoteId,
                "note" to note,
                "color" to color
            ))
            println("BookmarkActionsRepository: queueUpdateHighlight - actionData=$actionData")
            queueAction(
                bookmarkRemoteId = bookmarkLocalId,
                serverId = server.id,
                actionType = PendingActionType.UPDATE_HIGHLIGHT,
                actionData = actionData
            )
            println("BookmarkActionsRepository: queueUpdateHighlight - action queued, triggering sync")
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
    private suspend fun queueAction(
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
    private suspend fun triggerAutoSync(serverId: String) {
        repositoryScope.launch {
            try {
                // Check if offline mode is enabled
                if (!settingsRepository.offlineMode.first()) {
                    // Get server and trigger sync
                    val servers = serverRepository.servers.first()
                    val server = servers.find { it.id == serverId }
                    server?.let {
                        // Start processing pending actions immediately (Push only)
                        println("BookmarkActionsRepository: Triggering push-only sync for server ${server.id}")
                        processPendingActions(it)
                    }
                }
            } catch (e: Exception) {
                println("BookmarkActionsRepository: Error in auto-sync: ${e.message}")
                // Don't propagate error - auto-sync is a best-effort operation
            }
        }
    }

    /**
     * Process all pending actions for a specific bookmark.
     */
    private suspend fun processPendingActionsForBookmark(serverId: String, bookmarkRemoteId: Long) {
        val actions = pendingActionDao.getPendingActionsList(serverId)
            .filter { it.bookmarkRemoteId == bookmarkRemoteId }
        
        for (action in actions) {
            executeAction(action, serverId)
        }
    }

    // Mutex to ensure only one action processing happens at a time
    private val actionMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Process all pending actions for a server. Called during sync.
     * Returns list of bookmark remote IDs that were processed.
     */
    suspend fun processPendingActions(server: Server): List<Long> {
        return actionMutex.withLock {
            withContext(Dispatchers.IO) {
                val actions = pendingActionDao.getPendingActionsList(server.id)
                val processedIds = mutableListOf<Long>()
                println("BookmarkActionsRepository: Found ${actions.size} pending actions for server ${server.id}")
                
                for (action in actions) {
                    println("BookmarkActionsRepository: Processing action ${action.actionType} for bookmark ${action.bookmarkRemoteId}")
                    executeAction(action, server.id)
                    processedIds.add(action.bookmarkRemoteId)
                }
                
                println("BookmarkActionsRepository: Finished processing all pending actions")
                processedIds
            }
        }
    }

    /**
     * Get list of bookmark remote IDs that have pending actions.
     */
    suspend fun getPendingActionBookmarkIds(serverId: String): List<Long> {
        return pendingActionDao.getPendingActionsList(serverId).map { it.bookmarkRemoteId }.distinct()
    }

    /**
     * Execute a single pending action.
     */
    private suspend fun executeAction(action: PendingActionEntity, serverId: String) {
        println("BookmarkActionsRepository: executeAction START for ${action.actionType}, bookmark ${action.bookmarkRemoteId}")
        try {
            // Get server from repository - collect the flow
            val servers = serverRepository.servers.first()
            val server: com.karakept.app.data.model.Server? = servers.find { it.id == serverId }
            if (server == null) {
                println("BookmarkActionsRepository: Server not found for action: $serverId")
                return
            }
            
            // Get the bookmark ID to use for the API call
            // For DELETE, CREATE_HIGHLIGHT, DELETE_HIGHLIGHT, and UPDATE_HIGHLIGHT actions, the bookmark may be deleted/not yet synced,
            // or we don't need the bookmark ID at all (highlight actions use highlightId directly)
            // For other actions, we can get it from the bookmark entity
            val highlightActions = listOf(PendingActionType.DELETE_HIGHLIGHT, PendingActionType.UPDATE_HIGHLIGHT)
            val bookmarkId: String = if (action.actionType == PendingActionType.DELETE || action.actionType == PendingActionType.CREATE_HIGHLIGHT) {
                // For DELETE and CREATE_HIGHLIGHT, get originalRemoteId from actionData
                val data = json.decodeFromString<Map<String, String?>>(action.actionData)
                val id = data["originalRemoteId"]
                if (id == null) {
                    println("BookmarkActionsRepository: ${action.actionType} action missing originalRemoteId in actionData")
                    pendingActionDao.deleteAction(action)
                    return
                }
                id
            } else if (action.actionType in highlightActions) {
                // For DELETE_HIGHLIGHT and UPDATE_HIGHLIGHT, we don't need the bookmark ID
                // The highlight ID is in the actionData and that's all we need
                ""  // Placeholder - not used for these actions
            } else {
                // For other actions, get the bookmark to retrieve its REAL string ID from the server
                // The bookmarkRemoteId stored in pending_actions is a hashed long, but the API needs the original string ID
                val bookmark = bookmarkDao.getBookmarkByRemoteId(action.bookmarkRemoteId, serverId)
                if (bookmark == null) {
                    println("BookmarkActionsRepository: Bookmark not found locally: ${action.bookmarkRemoteId}")
                    // Delete the orphaned action
                    pendingActionDao.deleteAction(action)
                    return
                }
                bookmark.originalRemoteId
            }

            println("BookmarkActionsRepository: Executing ${action.actionType} on server with bookmark ID $bookmarkId")

            when (action.actionType) {
                PendingActionType.ARCHIVE -> {
                    println("BookmarkActionsRepository: Calling updateBookmark with archived=true")
                    remoteDataSource.updateBookmark(server, bookmarkId, BookmarksBookmarkIdPatchRequest(archived = true))
                }
                PendingActionType.UNARCHIVE -> {
                    println("BookmarkActionsRepository: Calling updateBookmark with archived=false")
                    remoteDataSource.updateBookmark(server, bookmarkId, BookmarksBookmarkIdPatchRequest(archived = false))
                }
                PendingActionType.FAVOURITE -> {
                    println("BookmarkActionsRepository: Calling updateBookmark with favourited=true")
                    remoteDataSource.updateBookmark(server, bookmarkId, BookmarksBookmarkIdPatchRequest(favourited = true))
                }
                PendingActionType.UNFAVOURITE -> {
                    println("BookmarkActionsRepository: Calling updateBookmark with favourited=false")
                    remoteDataSource.updateBookmark(server, bookmarkId, BookmarksBookmarkIdPatchRequest(favourited = false))
                }
                PendingActionType.DELETE -> {
                    println("BookmarkActionsRepository: Calling deleteBookmark")
                    remoteDataSource.deleteBookmark(server, bookmarkId)
                }
                PendingActionType.UPDATE_TAGS -> {
                    val data = json.decodeFromString<Map<String, List<String>>>(action.actionData)
                    val newTags = data["tags"] ?: emptyList()
                    println("BookmarkActionsRepository: UPDATE_TAGS - Replacing tags with ${newTags.size} tags: $newTags")

                    // Fetch current bookmark to get existing tags
                    val bookmarkDto = remoteDataSource.fetchBookmark(server, bookmarkId, includeContent = false)
                    val currentTags = bookmarkDto.tags?.map { it.name ?: "" }?.filter { it.isNotBlank() } ?: emptyList()
                    val currentTagIds = bookmarkDto.tags?.associate { (it.name ?: "") to (it.id ?: "") } ?: emptyMap()

                    println("BookmarkActionsRepository: Current tags: $currentTags")

                    // Determine which tags to add and remove
                    val tagsToAdd = newTags.filter { it !in currentTags }
                    val tagsToRemove = currentTags.filter { it !in newTags }

                    println("BookmarkActionsRepository: Tags to add: $tagsToAdd")
                    println("BookmarkActionsRepository: Tags to remove: $tagsToRemove")

                    // Remove tags that are no longer in the list
                    if (tagsToRemove.isNotEmpty()) {
                        val tagIdsToRemove = tagsToRemove.mapNotNull { currentTagIds[it] }
                        println("BookmarkActionsRepository: Tags to remove mapped to IDs: ${tagsToRemove.zip(tagIdsToRemove)}")
                        if (tagIdsToRemove.isNotEmpty()) {
                            println("BookmarkActionsRepository: Detaching ${tagIdsToRemove.size} tag IDs: $tagIdsToRemove")
                            val detachResponse = remoteDataSource.detachTags(server, bookmarkId, tagIdsToRemove)
                            println("BookmarkActionsRepository: Detach response: ${detachResponse.detached}")
                        } else {
                            println("BookmarkActionsRepository: Warning: Could not find tag IDs for tags to remove: $tagsToRemove")
                        }
                    }

                    // Add new tags
                    if (tagsToAdd.isNotEmpty()) {
                        println("BookmarkActionsRepository: Attaching ${tagsToAdd.size} new tags: $tagsToAdd")
                        val attachResponse = remoteDataSource.attachTags(server, bookmarkId, tagsToAdd)
                        println("BookmarkActionsRepository: Attach response: ${attachResponse.attached}")
                    }
                }
                PendingActionType.MARK_READ, PendingActionType.MARK_UNREAD -> {
                    // Legacy actions – reading progress is now synced via UPDATE_READING_PROGRESS.
                    println("BookmarkActionsRepository: Skipping legacy ${action.actionType} action")
                }
                PendingActionType.UPDATE_READING_PROGRESS -> {
                    val data = json.decodeFromString<Map<String, String>>(action.actionData)
                    val progressPercent = data["progressPercent"]?.toIntOrNull() ?: 0
                    println("ReadProgressSync: executing pending action — pushing ${progressPercent}% for bookmark $bookmarkId to server")
                    val success = remoteDataSource.updateReadingProgress(server, bookmarkId, progressPercent)
                    println("ReadProgressSync: push result=$success")
                }
                PendingActionType.MOVE_TO_LIST -> {
                    val data = json.decodeFromString<Map<String, String>>(action.actionData)
                    val listId = data["listId"] ?: return
                    println("BookmarkActionsRepository: Calling addBookmarkToList")
                    remoteDataSource.addBookmarkToList(server, listId, bookmarkId)
                }
                PendingActionType.REMOVE_FROM_LIST -> {
                    val data = json.decodeFromString<Map<String, String>>(action.actionData)
                    val listId = data["listId"] ?: return
                    println("BookmarkActionsRepository: Calling removeBookmarkFromList")
                    remoteDataSource.removeBookmarkFromList(server, listId, bookmarkId)
                }
                PendingActionType.CREATE_HIGHLIGHT -> {
                    val data = json.decodeFromString<Map<String, String?>>(action.actionData)
                    val bId = data["bookmarkRemoteId"] ?: return
                    val text = data["text"] ?: return
                    val startOffset = data["startOffset"]?.toInt() ?: 0
                    val endOffset = data["endOffset"]?.toInt() ?: 0
                    val note = data["note"]
                    val color = data["color"]
                    val tempId = data["tempId"]  // Get the temp ID we stored
                    println("BookmarkActionsRepository: Calling createHighlight for bookmark $bId, tempId=$tempId")
                    val result = remoteDataSource.createHighlight(server, bId, text, startOffset, endOffset, note, color)

                    val serverId = result.id
                    println("BookmarkActionsRepository: Highlight created successfully with server ID: $serverId")

                    // Update local DB: Replace temp ID with real server ID
                    if (tempId != null && serverId != null && _highlightDao != null) {
                        try {
                            println("BookmarkActionsRepository: Replacing tempId=$tempId with serverId=$serverId")
                            val existingHighlight = _highlightDao?.getHighlightByRemoteId(tempId)
                            if (existingHighlight != null) {
                                // Update the highlight with the real server ID and any other server data
                                _highlightDao?.updateHighlight(existingHighlight.copy(
                                    remoteId = serverId,
                                    text = result.text ?: existingHighlight.text,
                                    startOffset = result.startOffset?.toInt() ?: existingHighlight.startOffset,
                                    endOffset = result.endOffset?.toInt() ?: existingHighlight.endOffset,
                                    note = result.note,
                                    color = result.color?.value,
                                    createdAt = try {
                                        kotlinx.datetime.Instant.parse(result.createdAt ?: "").toEpochMilliseconds()
                                    } catch (e: Exception) { existingHighlight.createdAt }
                                ))
                                println("BookmarkActionsRepository: Successfully updated highlight with server ID")
                            } else {
                                println("BookmarkActionsRepository: Could not find highlight with tempId=$tempId")
                            }
                        } catch (e: Exception) {
                            println("BookmarkActionsRepository: Error updating highlight with server ID: ${e.message}")
                        }
                    }
                }
                PendingActionType.DELETE_HIGHLIGHT -> {
                    println("BookmarkActionsRepository: DELETE_HIGHLIGHT actionData=${action.actionData}")
                    val data = json.decodeFromString<Map<String, String>>(action.actionData)
                    println("BookmarkActionsRepository: DELETE_HIGHLIGHT parsed data=$data")
                    val hId = data["highlightId"]
                    if (hId == null) {
                        println("BookmarkActionsRepository: ERROR - highlightId is null in action data")
                        return
                    }
                    println("BookmarkActionsRepository: Calling deleteHighlight for highlightId=$hId")

                    // Check if this is a temp ID (starts with "temp_")
                    if (hId.startsWith("temp_")) {
                        println("BookmarkActionsRepository: Skipping delete for temp highlight ID: $hId")
                        // Skip deleting temp IDs - they don't exist on the server
                        // The highlight was never synced or was already deleted optimistically
                        return
                    }

                    try {
                        remoteDataSource.deleteHighlight(server, hId)
                    } catch (e: Exception) {
                        // If highlight doesn't exist on server (404), treat as success
                        // The desired end state (highlight deleted) is already achieved
                        val errorMsg = e.message?.lowercase() ?: ""
                        if (errorMsg.contains("404") || errorMsg.contains("not found")) {
                            println("BookmarkActionsRepository: Highlight $hId not found on server, treating as already deleted")
                            // Don't rethrow - let it proceed to delete the action from queue
                        } else {
                            throw e
                        }
                    }
                }
                PendingActionType.UPDATE_HIGHLIGHT -> {
                    val data = json.decodeFromString<Map<String, String?>>(action.actionData)
                    val hId = data["highlightId"] ?: return
                    val note = data["note"]
                    val color = data["color"]
                    println("BookmarkActionsRepository: Calling updateHighlight $hId")
                    remoteDataSource.updateHighlight(server, hId, note, color)
                }
            }
            
            println("BookmarkActionsRepository: API call succeeded, deleting action from queue")
            // Delete action after successful execution
            pendingActionDao.deleteAction(action)
            println("BookmarkActionsRepository: Action deleted successfully")
            
        } catch (e: Exception) {
            AppLogger.e("BookmarkActionsRepo", "Failed to execute pending action: ${e.message}", e)
            // Update retry count and error message
            pendingActionDao.updateAction(
                action.copy(
                    retryCount = action.retryCount + 1,
                    lastError = e.message
                )
            )
            
            // If too many retries, we might want to delete it or alert user
            if (action.retryCount >= 5) {
                // Could delete or mark as failed
                println("Action failed after 5 retries: ${action.actionType} for bookmark ${action.bookmarkRemoteId}")
                pendingActionDao.deleteAction(action) // Give up after 5 retries to prevent blocking
            }
        }
    }
}
