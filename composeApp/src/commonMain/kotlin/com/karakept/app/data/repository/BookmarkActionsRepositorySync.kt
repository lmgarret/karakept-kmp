package com.karakept.app.data.repository

import com.karakept.app.utils.AppLogger
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.api.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Sync processing for BookmarkActionsRepository.
 * Handles executing pending actions against the remote server.
 * Extracted to reduce BookmarkActionsRepository file size.
 */

/**
 * Fetch reading progress from the server and apply it locally if it is higher than the
 * current local progress. Used when opening a bookmark on a new device to restore
 * cross-device reading position.
 *
 * @return true if local progress was updated from server data, false otherwise.
 */
suspend fun BookmarkActionsRepository.pullReadingProgressFromServer(bookmarkRemoteId: Long, serverId: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            println("ReadProgressSync: pullReadingProgressFromServer remoteId=$bookmarkRemoteId serverId=$serverId")
            val servers = serverRepository.servers.first()
            val server = servers.find { it.id == serverId }
            if (server == null) {
                println("ReadProgressSync: pull ABORT -- server not found for serverId=$serverId")
                return@withContext false
            }
            val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
            if (bookmark == null) {
                println("ReadProgressSync: pull ABORT -- bookmark not found in DB for remoteId=$bookmarkRemoteId")
                return@withContext false
            }

            println("ReadProgressSync: fetching server progress for originalRemoteId=${bookmark.originalRemoteId}")
            val serverPercent = remoteDataSource.getReadingProgress(server, bookmark.originalRemoteId)
            if (serverPercent == null) {
                println("ReadProgressSync: pull -- server returned null progress")
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
 * Process all pending actions for a specific bookmark.
 */
internal suspend fun BookmarkActionsRepository.processPendingActionsForBookmark(serverId: String, bookmarkRemoteId: Long) {
    val actions = pendingActionDao.getPendingActionsList(serverId)
        .filter { it.bookmarkRemoteId == bookmarkRemoteId }

    for (action in actions) {
        executeAction(action, serverId)
    }
}

/**
 * Process all pending actions for a server. Called during sync.
 * Returns list of bookmark remote IDs that were processed.
 */
suspend fun BookmarkActionsRepository.processPendingActions(server: Server): List<Long> {
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
suspend fun BookmarkActionsRepository.getPendingActionBookmarkIds(serverId: String): List<Long> {
    return pendingActionDao.getPendingActionsList(serverId).map { it.bookmarkRemoteId }.distinct()
}

/**
 * Execute a single pending action.
 */
internal suspend fun BookmarkActionsRepository.executeAction(action: PendingActionEntity, serverId: String) {
    println("BookmarkActionsRepository: executeAction START for ${action.actionType}, bookmark ${action.bookmarkRemoteId}")
    try {
        // Get server from repository - collect the flow
        val servers = serverRepository.servers.first()
        val server: Server? = servers.find { it.id == serverId }
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
            val data = jsonSerializer.decodeFromString<Map<String, String?>>(action.actionData)
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
                val data = jsonSerializer.decodeFromString<Map<String, List<String>>>(action.actionData)
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
                // Legacy actions -- reading progress is now synced via UPDATE_READING_PROGRESS.
                println("BookmarkActionsRepository: Skipping legacy ${action.actionType} action")
            }
            PendingActionType.UPDATE_READING_PROGRESS -> {
                val data = jsonSerializer.decodeFromString<Map<String, String>>(action.actionData)
                val progressPercent = data["progressPercent"]?.toIntOrNull() ?: 0
                println("ReadProgressSync: executing pending action -- pushing ${progressPercent}% for bookmark $bookmarkId to server")
                val success = remoteDataSource.updateReadingProgress(server, bookmarkId, progressPercent)
                println("ReadProgressSync: push result=$success")
            }
            PendingActionType.MOVE_TO_LIST -> {
                val data = jsonSerializer.decodeFromString<Map<String, String>>(action.actionData)
                val listId = data["listId"] ?: return
                println("BookmarkActionsRepository: Calling addBookmarkToList")
                remoteDataSource.addBookmarkToList(server, listId, bookmarkId)
            }
            PendingActionType.REMOVE_FROM_LIST -> {
                val data = jsonSerializer.decodeFromString<Map<String, String>>(action.actionData)
                val listId = data["listId"] ?: return
                println("BookmarkActionsRepository: Calling removeBookmarkFromList")
                remoteDataSource.removeBookmarkFromList(server, listId, bookmarkId)
            }
            PendingActionType.CREATE_HIGHLIGHT -> {
                val data = jsonSerializer.decodeFromString<Map<String, String?>>(action.actionData)
                val bId = data["bookmarkRemoteId"] ?: return
                val text = data["text"] ?: return
                val startOffset = data["startOffset"]?.toInt() ?: 0
                val endOffset = data["endOffset"]?.toInt() ?: 0
                val note = data["note"]
                val color = data["color"]
                val tempId = data["tempId"]  // Get the temp ID we stored
                println("BookmarkActionsRepository: Calling createHighlight for bookmark $bId, tempId=$tempId")
                val result = remoteDataSource.createHighlight(server, bId, text, startOffset, endOffset, note, color)

                val highlightServerId = result.id
                println("BookmarkActionsRepository: Highlight created successfully with server ID: $highlightServerId")

                // Update local DB: Replace temp ID with real server ID
                if (tempId != null && highlightServerId != null && highlightDao != null) {
                    try {
                        println("BookmarkActionsRepository: Replacing tempId=$tempId with serverId=$highlightServerId")
                        val existingHighlight = highlightDao?.getHighlightByRemoteId(tempId)
                        if (existingHighlight != null) {
                            // Update the highlight with the real server ID and any other server data
                            highlightDao?.updateHighlight(existingHighlight.copy(
                                remoteId = highlightServerId,
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
                val data = jsonSerializer.decodeFromString<Map<String, String>>(action.actionData)
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
                val data = jsonSerializer.decodeFromString<Map<String, String?>>(action.actionData)
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
