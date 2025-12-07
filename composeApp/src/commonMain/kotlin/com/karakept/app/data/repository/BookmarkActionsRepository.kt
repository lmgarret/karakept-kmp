package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.remote.model.UpdateBookmarkDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.first

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
    
    // Track when actions are being performed
    private val _isPerformingAction = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isPerformingAction: kotlinx.coroutines.flow.StateFlow<Boolean> = _isPerformingAction
    
    fun setBookmarkRepository(repository: com.karakept.app.data.repository.BookmarkRepository) {
        _bookmarkRepository = repository
    }

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

                // Auto-sync if not in offline mode
                triggerAutoSync(serverId)
            }
        }
    }

    /**
     * Mark bookmark as read using the "karakept:read" tag.
     */
    suspend fun markAsRead(bookmarkRemoteId: Long, serverId: String) {
        performAction {
            withContext(Dispatchers.IO) {
                val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
                bookmark?.let {
                    bookmarkDao.insertBookmark(it.copy(isRead = true))
                }

                queueAction(
                    bookmarkRemoteId = bookmarkRemoteId,
                    serverId = serverId,
                    actionType = PendingActionType.MARK_READ,
                    actionData = json.encodeToString(mapOf("tags" to listOf("karakept:read")))
                )

                // Auto-sync if not in offline mode
                triggerAutoSync(serverId)
            }
        }
    }

    /**
     * Mark bookmark as unread by removing the "karakept:read" tag.
     */
    suspend fun markAsUnread(bookmarkRemoteId: Long, serverId: String, existingTags: List<String>) {
        performAction {
            withContext(Dispatchers.IO) {
                val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
                bookmark?.let {
                    bookmarkDao.insertBookmark(it.copy(isRead = false))
                }

                // Find the tag ID for "karakept:read" from existing tags
                queueAction(
                    bookmarkRemoteId = bookmarkRemoteId,
                    serverId = serverId,
                    actionType = PendingActionType.MARK_UNREAD,
                    actionData = json.encodeToString(mapOf("tagName" to "karakept:read"))
                )

                // Auto-sync if not in offline mode
                triggerAutoSync(serverId)
            }
        }
    }

    /**
     * Delete a bookmark.
     */
    suspend fun deleteBookmark(bookmarkLocalId: Long, bookmarkRemoteId: Long, serverId: String) {
        performAction {
            withContext(Dispatchers.IO) {
                // Delete locally
                val bookmark = bookmarkDao.getBookmarkById(bookmarkLocalId)
                bookmark?.let {
                    bookmarkDao.deleteBookmark(it)
                }

                // Queue action
                queueAction(
                    bookmarkRemoteId = bookmarkRemoteId,
                    serverId = serverId,
                    actionType = PendingActionType.DELETE,
                    actionData = "{}"
                )

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
                bookmarkDao.insertBookmark(it.copy(tags = newTags.joinToString(",")))
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
            queueAction(
                bookmarkRemoteId = bookmarkRemoteId,
                serverId = serverId,
                actionType = PendingActionType.MOVE_TO_LIST,
                actionData = json.encodeToString(mapOf("listId" to listId))
            )

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
            queueAction(
                bookmarkRemoteId = bookmarkRemoteId,
                serverId = serverId,
                actionType = PendingActionType.REMOVE_FROM_LIST,
                actionData = json.encodeToString(mapOf("listId" to listId))
            )

            // Auto-sync if not in offline mode
            triggerAutoSync(serverId)
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
        try {
            // Check if offline mode is enabled
            if (!settingsRepository.offlineMode.first()) {
                // Get server and trigger sync
                val servers = serverRepository.servers.first()
                val server = servers.find { it.id == serverId }
                server?.let {
                    // Sync all bookmarks (which will process pending actions first)
                    _bookmarkRepository?.syncBookmarks(it)
                }
            }
        } catch (e: Exception) {
            println("BookmarkActionsRepository: Error in auto-sync: ${e.message}")
            // Don't propagate error - auto-sync is a best-effort operation
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

    /**
     * Process all pending actions for a server. Called during sync.
     */
    suspend fun processPendingActions(server: Server) {
        withContext(Dispatchers.IO) {
            val actions = pendingActionDao.getPendingActionsList(server.id)
            println("BookmarkActionsRepository: Found ${actions.size} pending actions for server ${server.id}")
            
            for (action in actions) {
                println("BookmarkActionsRepository: Processing action ${action.actionType} for bookmark ${action.bookmarkRemoteId}")
                executeAction(action, server.id)
            }
            
            println("BookmarkActionsRepository: Finished processing all pending actions")
        }
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
            
            // CRITICAL FIX: Get the actual bookmark to retrieve its REAL string ID from the server
            // The bookmarkRemoteId stored in pending_actions is a hashed long, but the API needs the original string ID
            val bookmark = bookmarkDao.getBookmarkByRemoteId(action.bookmarkRemoteId, serverId)
            if (bookmark == null) {
                println("BookmarkActionsRepository: Bookmark not found locally: ${action.bookmarkRemoteId}")
                // Delete the orphaned action
                pendingActionDao.deleteAction(action) 
                return
            }
            
            // Use the ORIGINAL string ID from the bookmark entity for API calls
            val bookmarkId = bookmark.originalRemoteId
            println("BookmarkActionsRepository: Executing ${action.actionType} on server with bookmark ID $bookmarkId (original from entity)")
            
            when (action.actionType) {
                PendingActionType.ARCHIVE -> {
                    println("BookmarkActionsRepository: Calling updateBookmark with archived=true")
                    remoteDataSource.updateBookmark(server, bookmarkId, com.karakept.app.data.remote.model.UpdateBookmarkDto(archived = true))
                }
                PendingActionType.UNARCHIVE -> {
                    println("BookmarkActionsRepository: Calling updateBookmark with archived=false")
                    remoteDataSource.updateBookmark(server, bookmarkId, com.karakept.app.data.remote.model.UpdateBookmarkDto(archived = false))
                }
                PendingActionType.FAVOURITE -> {
                    println("BookmarkActionsRepository: Calling updateBookmark with favourited=true")
                    remoteDataSource.updateBookmark(server, bookmarkId, com.karakept.app.data.remote.model.UpdateBookmarkDto(favourited = true))
                }
                PendingActionType.UNFAVOURITE -> {
                    println("BookmarkActionsRepository: Calling updateBookmark with favourited=false")
                    remoteDataSource.updateBookmark(server, bookmarkId, com.karakept.app.data.remote.model.UpdateBookmarkDto(favourited = false))
                }
                PendingActionType.DELETE -> {
                    println("BookmarkActionsRepository: Calling deleteBookmark")
                    remoteDataSource.deleteBookmark(server, bookmarkId)
                }
                PendingActionType.UPDATE_TAGS -> {
                    val data = json.decodeFromString<Map<String, List<String>>>(action.actionData)
                    val tags = data["tags"] ?: emptyList()
                    println("BookmarkActionsRepository: Calling attachTags with ${tags.size} tags")
                    remoteDataSource.attachTags(server, bookmarkId, tags)
                }
                PendingActionType.MARK_READ -> {
                    val data = json.decodeFromString<Map<String, List<String>>>(action.actionData)
                    val tags = data["tags"] ?: emptyList()
                    println("BookmarkActionsRepository: Calling attachTags for mark_read with tags: $tags")
                    remoteDataSource.attachTags(server, bookmarkId, tags)
                }
                PendingActionType.MARK_UNREAD -> {
                    // Find and detach the karakept:read tag
                    val bookmark = bookmarkDao.getBookmarkById(action.bookmarkRemoteId)
                    val readTag = bookmark?.tags?.split(",")?.find { it == "karakept:read" }
                    if (readTag != null) {
                        println("BookmarkActionsRepository: Would detach read tag (not implemented)")
                        // Would need tag IDs, for now just skip
                        // remoteDataSource.detachTag(server, bookmarkId, tagId)
                    }
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
            }
            
            println("BookmarkActionsRepository: API call succeeded, deleting action from queue")
            // Delete action after successful execution
            pendingActionDao.deleteAction(action)
            println("BookmarkActionsRepository: Action deleted successfully")
            
        } catch (e: Exception) {
            println("BookmarkActionsRepository: ERROR in executeAction: ${e.message}")
            e.printStackTrace()
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
            }
        }
    }
}
