package com.karakept.app.data.repository

import com.karakept.app.utils.AppLogger
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.hasHttpStatus
import com.karakept.api.model.*
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
 * Outcome of a reading-progress pull. [FAILED] has to stay distinguishable from [SKIPPED]:
 * the sync pipeline advances its rotating cursor on the strength of a pull having actually
 * reached the server, and a failed request that looked like "nothing to apply" would send
 * the bookmark to the back of the queue without anyone having asked the server.
 */
enum class ReadingProgressPullResult {
    /** Server progress was ahead of local and has been written to the DB. */
    APPLIED,

    /** The server answered, but there was nothing newer to apply. */
    SKIPPED,

    /** The server was never asked, or the request failed. */
    FAILED
}

/**
 * Fetch reading progress from the server and apply it locally. Used when opening a bookmark
 * on another device to restore the cross-device reading position.
 *
 * The server value wins unless this device has a progress push of its own still queued, which
 * is the one signal that local state is newer. The older rule — apply only a *higher* value —
 * protected the furthest-read position, but it also made "mark as unread" structurally unable
 * to travel: read state is local-only in this app, so the percentage is the only thing
 * carrying it, and a reset to 0% is by definition lower than what the other device holds.
 * Karakeep's `getReadingProgress` returns no timestamp, so there is no way to have both; the
 * cost is that two devices reading the same article settle on the last one to push.
 */
suspend fun BookmarkActionsRepository.pullReadingProgressFromServer(
    bookmarkRemoteId: Long,
    serverId: String
): ReadingProgressPullResult {
    return withContext(appDispatchers.io) {
        try {
            AppLogger.d("ReadProgressSync", "pullReadingProgressFromServer remoteId=$bookmarkRemoteId serverId=$serverId")
            val servers = serverRepository.servers.first()
            val server = servers.find { it.id == serverId }
            if (server == null) {
                AppLogger.w("ReadProgressSync", "pull ABORT -- server not found for serverId=$serverId")
                return@withContext ReadingProgressPullResult.FAILED
            }
            val bookmark = bookmarkDao.getBookmarkByRemoteId(bookmarkRemoteId, serverId)
            if (bookmark == null) {
                AppLogger.w("ReadProgressSync", "pull ABORT -- bookmark not found in DB for remoteId=$bookmarkRemoteId")
                return@withContext ReadingProgressPullResult.FAILED
            }

            // A queued push is this device saying "my value is newer and on its way" —
            // taking the server's answer would undo it before it was ever sent.
            val hasUnsyncedLocalProgress = pendingActionDao.countActionsForBookmarkByType(
                bookmarkRemoteId, serverId, PendingActionType.UPDATE_READING_PROGRESS
            ) > 0
            if (hasUnsyncedLocalProgress) {
                AppLogger.d("ReadProgressSync", "pull -- local progress not pushed yet, keeping it")
                return@withContext ReadingProgressPullResult.SKIPPED
            }

            AppLogger.d("ReadProgressSync", "fetching server progress for originalRemoteId=${bookmark.originalRemoteId}")
            val serverPercent = remoteDataSource.getReadingProgress(server, bookmark.originalRemoteId)
            if (serverPercent == null) {
                AppLogger.d("ReadProgressSync", "pull -- server has no progress stored")
                return@withContext ReadingProgressPullResult.SKIPPED
            }

            AppLogger.d("ReadProgressSync", "server has ${serverPercent}%, local has ${(bookmark.readingProgress * 100).toInt()}%")
            val serverProgress = serverPercent / 100f
            if (serverProgress != bookmark.readingProgress) {
                bookmarkDao.applyServerReadingProgress(
                    localId = bookmark.localId,
                    progress = serverProgress,
                    scrollIndex = 0,
                    scrollOffset = 0
                )
                // This write moves the read flag — below 100% it clears it — so the row the
                // list is holding is now wrong. The list keeps a snapshot of its rows while
                // the drawer's unread count reads the table live, so without this the two
                // disagree: the count reports a bookmark this pull turned back to unread and
                // the list goes on drawing it as read, with nothing on screen to scroll to.
                // Re-reading the view is what reconciled them, which is why the bookmarks
                // "appeared" only after a filter was applied and taken off again (#333).
                notifyBookmarkChanged(bookmarkRemoteId)
                AppLogger.d("ReadProgressSync", "applied from server: ${serverPercent}%")
                ReadingProgressPullResult.APPLIED
            } else {
                AppLogger.d("ReadProgressSync", "server matches local, nothing to apply")
                ReadingProgressPullResult.SKIPPED
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("BookmarkActionsRepositorySync", "Failed to pull reading progress: ${e.message}")
            ReadingProgressPullResult.FAILED
        }
    }
}

/**
 * Process all pending actions for a specific bookmark.
 */
internal suspend fun BookmarkActionsRepository.processPendingActionsForBookmark(serverId: String, bookmarkRemoteId: Long) {
    val actions = pendingActionDao.getProcessableActions(serverId, System.currentTimeMillis())
        .filter { it.bookmarkRemoteId == bookmarkRemoteId }

    for (action in actions) {
        executeAction(action, serverId)
    }
}

/**
 * Flush all pending actions to the server and wait for completion.
 * Call this before reading back server state to ensure the server has processed recent actions.
 */
suspend fun BookmarkActionsRepository.flushPendingActions(server: Server) {
    processPendingActions(server)
}

/**
 * Process all pending actions for a server. Called during sync.
 * Returns list of bookmark remote IDs that were processed.
 */
suspend fun BookmarkActionsRepository.processPendingActions(server: Server): List<Long> {
    return actionMutex.withLock {
        withContext(appDispatchers.io) {
            val actions = pendingActionDao.getProcessableActions(server.id, System.currentTimeMillis())
            val processedIds = mutableListOf<Long>()
            AppLogger.d("BookmarkActionsRepositorySync", "Found ${actions.size} processable actions for server ${server.id}")

            for (action in actions) {
                executeAction(action, server.id)
                processedIds.add(action.bookmarkRemoteId)
            }

            AppLogger.d("BookmarkActionsRepositorySync", "Finished processing all pending actions")
            processedIds
        }
    }
}

/**
 * Requeue failed actions and immediately attempt to process them.
 */
suspend fun BookmarkActionsRepository.retryFailedActions(server: Server) {
    pendingActionDao.requeueFailedActions(server.id)
    processPendingActions(server)
}

/**
 * Discard all failed actions for a server (explicit user choice).
 */
suspend fun BookmarkActionsRepository.discardFailedActions(serverId: String) {
    pendingActionDao.deleteFailedActions(serverId)
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
    AppLogger.d("BookmarkActionsRepositorySync", "executeAction START ${action.actionType} bookmark=${action.bookmarkRemoteId}")
    try {
        // Get server from repository - collect the flow
        val servers = serverRepository.servers.first()
        val server: Server? = servers.find { it.id == serverId }
        if (server == null) {
            AppLogger.e("BookmarkActionsRepositorySync", "Server not found for action: $serverId")
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
                AppLogger.e("BookmarkActionsRepositorySync", "${action.actionType} action missing originalRemoteId in actionData")
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
                AppLogger.w("BookmarkActionsRepositorySync", "Bookmark not found locally: ${action.bookmarkRemoteId}")
                // Delete the orphaned action
                pendingActionDao.deleteAction(action)
                return
            }
            bookmark.originalRemoteId
        }

        AppLogger.d("BookmarkActionsRepositorySync", "Executing ${action.actionType} on server with bookmark ID $bookmarkId")

        when (action.actionType) {
            PendingActionType.ARCHIVE -> {
                remoteDataSource.updateBookmark(server, bookmarkId, BookmarksBookmarkIdPatchRequest(archived = true))
            }
            PendingActionType.UNARCHIVE -> {
                remoteDataSource.updateBookmark(server, bookmarkId, BookmarksBookmarkIdPatchRequest(archived = false))
            }
            PendingActionType.FAVOURITE -> {
                remoteDataSource.updateBookmark(server, bookmarkId, BookmarksBookmarkIdPatchRequest(favourited = true))
            }
            PendingActionType.UNFAVOURITE -> {
                remoteDataSource.updateBookmark(server, bookmarkId, BookmarksBookmarkIdPatchRequest(favourited = false))
            }
            PendingActionType.DELETE -> {
                try {
                    remoteDataSource.deleteBookmark(server, bookmarkId)
                } catch (e: Exception) {
                    // Already gone on the server — the desired end state is achieved.
                    if (e.hasHttpStatus(404)) {
                        AppLogger.w("BookmarkActionsRepositorySync", "Bookmark $bookmarkId not found on server, treating delete as done")
                    } else {
                        throw e
                    }
                }
            }
            PendingActionType.UPDATE_TAGS -> {
                val data = jsonSerializer.decodeFromString<Map<String, List<String>>>(action.actionData)
                val newTags = data["tags"] ?: emptyList()

                // Fetch current bookmark to get existing tags
                val bookmarkDto = remoteDataSource.fetchBookmark(server, bookmarkId, includeContent = false)
                val currentTags = bookmarkDto.tags?.map { it.name ?: "" }?.filter { it.isNotBlank() } ?: emptyList()
                val currentTagIds = bookmarkDto.tags?.associate { (it.name ?: "") to (it.id ?: "") } ?: emptyMap()

                // Determine which tags to add and remove
                val tagsToAdd = newTags.filter { it !in currentTags }
                val tagsToRemove = currentTags.filter { it !in newTags }

                // Remove tags that are no longer in the list
                if (tagsToRemove.isNotEmpty()) {
                    val tagIdsToRemove = tagsToRemove.mapNotNull { currentTagIds[it] }
                    if (tagIdsToRemove.isNotEmpty()) {
                        remoteDataSource.detachTags(server, bookmarkId, tagIdsToRemove)
                    } else {
                        AppLogger.w("BookmarkActionsRepositorySync", "Could not find tag IDs for tags to remove: $tagsToRemove")
                    }
                }

                // Add new tags
                if (tagsToAdd.isNotEmpty()) {
                    remoteDataSource.attachTags(server, bookmarkId, tagsToAdd)
                }
            }
            PendingActionType.MARK_READ, PendingActionType.MARK_UNREAD -> {
                // Legacy actions -- reading progress is now synced via UPDATE_READING_PROGRESS.
                AppLogger.d("BookmarkActionsRepositorySync", "Skipping legacy ${action.actionType} action")
            }
            PendingActionType.UPDATE_READING_PROGRESS -> {
                val data = jsonSerializer.decodeFromString<Map<String, String>>(action.actionData)
                val progressPercent = data["progressPercent"]?.toIntOrNull() ?: 0
                AppLogger.d("ReadProgressSync", "executing pending action -- pushing ${progressPercent}% for bookmark $bookmarkId to server")
                // false means the server will never keep progress for this bookmark (not a
                // link), so the action is dropped below like a successful one. Every other
                // rejection throws and lands in recordActionFailure.
                val stored = remoteDataSource.updateReadingProgress(server, bookmarkId, progressPercent)
                AppLogger.d("ReadProgressSync", "push stored=$stored")
            }
            PendingActionType.MOVE_TO_LIST -> {
                val data = jsonSerializer.decodeFromString<Map<String, String>>(action.actionData)
                val listId = data["listId"] ?: return
                remoteDataSource.addBookmarkToList(server, listId, bookmarkId)
            }
            PendingActionType.REMOVE_FROM_LIST -> {
                val data = jsonSerializer.decodeFromString<Map<String, String>>(action.actionData)
                val listId = data["listId"] ?: return
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
                val result = remoteDataSource.createHighlight(server, bId, text, startOffset, endOffset, note, color)

                val highlightServerId = result.id

                // Update local DB: Replace temp ID with real server ID
                if (tempId != null && highlightServerId != null && highlightDao != null) {
                    try {
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
                                    kotlin.time.Instant.parse(result.createdAt ?: "").toEpochMilliseconds()
                                } catch (e: Exception) { existingHighlight.createdAt }
                            ))
                        } else {
                            AppLogger.w("BookmarkActionsRepositorySync", "Could not find highlight with tempId=$tempId")
                        }
                    } catch (e: Exception) {
                        AppLogger.e("BookmarkActionsRepositorySync", "Error updating highlight with server ID: ${e.message}")
                    }
                }
            }
            PendingActionType.DELETE_HIGHLIGHT -> {
                val data = jsonSerializer.decodeFromString<Map<String, String>>(action.actionData)
                val hId = data["highlightId"]
                if (hId == null) {
                    AppLogger.e("BookmarkActionsRepositorySync", "highlightId is null in DELETE_HIGHLIGHT action data")
                    return
                }

                // Check if this is a temp ID (starts with "temp_")
                if (hId.startsWith("temp_")) {
                    AppLogger.d("BookmarkActionsRepositorySync", "Skipping delete for temp highlight ID: $hId")
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
                        AppLogger.w("BookmarkActionsRepositorySync", "Highlight $hId not found on server, treating as already deleted")
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
                remoteDataSource.updateHighlight(server, hId, note, color)
            }
        }

        // Delete action after successful execution
        pendingActionDao.deleteAction(action)
        AppLogger.d("BookmarkActionsRepositorySync", "executeAction SUCCESS ${action.actionType} bookmark=${action.bookmarkRemoteId}")

    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e("BookmarkActionsRepo", "Failed to execute pending action: ${e.message}", e)
        recordActionFailure(action, e)
    }
}

private const val MAX_ACTION_RETRIES = 5
private val PERMANENT_HTTP_STATUSES = listOf(400, 404, 409, 422)

/**
 * Classifies a pending-action failure. Permanent server rejections and exhausted
 * retries mark the action failed — preserved for the user to retry or discard —
 * instead of the old behavior of silently deleting it. Transient failures get an
 * exponential backoff window so a flaky network can't burn through the retry budget.
 */
internal suspend fun BookmarkActionsRepository.recordActionFailure(action: PendingActionEntity, e: Exception) {
    val permanent = PERMANENT_HTTP_STATUSES.any { e.hasHttpStatus(it) }
    // A 401 can't succeed until the user re-authenticates — park the action without
    // burning retries; retryFailedActions() requeues it after re-auth.
    val unauthorized = e.hasHttpStatus(401)

    if (permanent || unauthorized) {
        pendingActionDao.updateAction(
            action.copy(status = PendingActionEntity.STATUS_FAILED, lastError = e.message)
        )
        AppLogger.e(
            "BookmarkActionsRepositorySync",
            "Action ${action.actionType} for bookmark ${action.bookmarkRemoteId} marked failed (${if (unauthorized) "auth" else "permanent"}): ${e.message}"
        )
        return
    }

    val newRetryCount = action.retryCount + 1
    if (newRetryCount >= MAX_ACTION_RETRIES) {
        pendingActionDao.updateAction(
            action.copy(
                retryCount = newRetryCount,
                lastError = e.message,
                status = PendingActionEntity.STATUS_FAILED
            )
        )
        AppLogger.e(
            "BookmarkActionsRepositorySync",
            "Action failed after $newRetryCount retries: ${action.actionType} for bookmark ${action.bookmarkRemoteId}"
        )
    } else {
        val backoffMinutes = minOf(1L shl newRetryCount, 60L)
        pendingActionDao.updateAction(
            action.copy(
                retryCount = newRetryCount,
                lastError = e.message,
                nextAttemptAt = System.currentTimeMillis() + backoffMinutes * 60_000L
            )
        )
    }
}
