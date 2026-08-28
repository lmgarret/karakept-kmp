/** Bookmark action extension functions for MainScreenModel. */
package com.karakept.app.ui.screens

import androidx.lifecycle.viewModelScope
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.domain.action.BookmarkActionEvent
import com.karakept.app.data.remote.OfflineModeException
import com.karakept.app.data.remote.UnsupportedServerActionException
import com.karakept.app.data.repository.requestAiRetag
import com.karakept.app.data.repository.summarizeBookmark
import com.karakept.app.domain.action.AiAction
import com.karakept.app.data.repository.batchMarkRead
import com.karakept.app.data.repository.batchMarkUnread
import com.karakept.app.data.repository.flushPendingActions
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

fun MainScreenModel.toggleBookmarkArchive(bookmark: BookmarkEntity) {
    viewModelScope.launch {
        val position = lockedPositionOf(bookmark.remoteId)
        val event = if (bookmark.isArchived) {
            BookmarkActionEvent.Unarchive(bookmark)
        } else {
            BookmarkActionEvent.Archive(bookmark)
        }
        bookmarkActionController.executeAction(event, originalPosition = position)
        updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
    }
}

fun MainScreenModel.toggleBookmarkFavorite(bookmark: BookmarkEntity) {
    viewModelScope.launch {
        val position = lockedPositionOf(bookmark.remoteId)
        bookmarkActionController.executeAction(
            BookmarkActionEvent.ToggleFavorite(bookmark),
            originalPosition = position
        )
        if (bookmark.isStarred && _currentFilter.value.status == FilterStatus.FAVORITES) {
            updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
        }
    }
}

fun MainScreenModel.toggleBookmarkRead(bookmark: BookmarkEntity) {
    viewModelScope.launch {
        val markingUnread = bookmark.isRead
        val event = if (markingUnread) {
            BookmarkActionEvent.MarkUnread(bookmark)
        } else {
            BookmarkActionEvent.MarkRead(bookmark)
        }
        bookmarkActionController.executeAction(event)

        val resetProgress = markingUnread && settingsRepository.resetProgressOnMarkUnread.first()
        updateAccumulatedBookmarks { list ->
            list.map {
                if (it.remoteId == bookmark.remoteId) {
                    if (resetProgress) {
                        it.copy(
                            isRead = false,
                            readingProgress = 0f,
                            readingScrollIndex = 0,
                            readingScrollOffset = 0
                        )
                    } else {
                        it.copy(isRead = !bookmark.isRead)
                    }
                } else {
                    it
                }
            }
        }
    }
}

fun MainScreenModel.deleteBookmark(bookmark: BookmarkEntity) {
    viewModelScope.launch {
        val position = lockedPositionOf(bookmark.remoteId)
        bookmarkActionController.executeAction(
            BookmarkActionEvent.Delete(bookmark),
            originalPosition = position
        )
        updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
    }
}

fun MainScreenModel.updateBookmarkTags(bookmark: BookmarkEntity, newTags: List<String>) {
    viewModelScope.launch {
        val isOnline = !_isSyncing.value
        bookmarkActionsRepository.updateTags(
            bookmark.remoteId, bookmark.serverId, newTags, isOnline
        )
    }
}

/**
 * Reconciles a single bookmark's smart list membership after a list-membership action.
 * Uses GET /bookmarks/{id}/lists — one API call — instead of syncing entire smart lists.
 * Then updates the visible list surgically so the UI reflects the change immediately
 * without discarding the user's scroll position.
 */
internal fun MainScreenModel.reconcileBookmarkLists(bookmark: BookmarkEntity) {
    val server = _selectedServer.value ?: return
    val smartListIds = listRepository.lists.value
        .filter { it.type == KarakeepList.Type.SMART }
        .mapNotNull { it.id }
        .toSet()
    viewModelScope.launch {
        try {
            bookmarkActionsRepository.flushPendingActions(server)
            val serverHadSmartLists = bookmarkRepository.reconcileBookmarkSmartListMembership(server, bookmark.localId, smartListIds)
            if (!serverHadSmartLists && smartListIds.isNotEmpty()) {
                // Server's per-bookmark endpoint returned stale smart list data
                // (async recalculation not done yet). Mark smart lists as needing
                // a full sync when the user navigates to one of them.
                _smartListsNeedingRefresh.value += smartListIds
                AppLogger.d("MainScreenModel", "Marked ${smartListIds.size} smart lists for deferred refresh")
            }
            // Targeted update: fetch the reconciled bookmark from DB and apply a surgical
            // transform instead of resetPaginationAndLoad. resetPaginationAndLoad replaces
            // the whole accumulated list with only page 0 (≤20 items), which discards any
            // pages the user had scrolled through and causes the list to jump to a fixed
            // near-top position regardless of where the user was.
            val reloadServer = _selectedServer.value ?: server
            val updated = bookmarkRepository.getBookmarkByRemoteId(bookmark.remoteId, reloadServer.id)
            updateAccumulatedBookmarks { current ->
                applyReconcileBookmarkTransform(current, bookmark.remoteId, updated, _currentListContext.value)
            }
        } catch (e: Exception) {
            AppLogger.e("MainScreenModel", "List membership reconciliation failed for bookmark ${bookmark.localId}: ${e.message}", e)
        }
    }
}

/**
 * Pure transform applied after smart-list reconciliation.
 *
 * - [updated] == null  → bookmark was deleted server-side; remove it.
 * - [currentListContext] set and bookmark no longer in that list → remove it
 *   (e.g. a Feeds smart list that now excludes a bookmark added to Read Later).
 * - Otherwise → update the bookmark in place with fresh server data.
 *
 * Extracted as a top-level function so both production code and unit tests
 * exercise the same logic path.
 */
internal fun applyReconcileBookmarkTransform(
    current: List<BookmarkEntity>,
    remoteId: Long,
    updated: BookmarkEntity?,
    currentListContext: String?
): List<BookmarkEntity> {
    if (updated == null) return current.filter { it.remoteId != remoteId }
    val updatedListIds = updated.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val stillInContext = currentListContext == null || updatedListIds.contains(currentListContext)
    return if (stillInContext) {
        current.map { if (it.remoteId == remoteId) updated else it }
    } else {
        current.filter { it.remoteId != remoteId }
    }
}

fun MainScreenModel.moveBookmarkToList(bookmark: BookmarkEntity, listId: String) {
    _actedOnBookmarkIds.value += bookmark.remoteId
    val smartListIds = listRepository.lists.value
        .filter { it.type == KarakeepList.Type.SMART }
        .mapNotNull { it.id }
        .toSet()
    viewModelScope.launch {
        val isOnline = !_isSyncing.value
        bookmarkActionsRepository.moveToList(
            bookmark.remoteId, bookmark.serverId, listId, isOnline, smartListIds
        )
        updateAccumulatedBookmarks { list ->
            list.map {
                if (it.remoteId == bookmark.remoteId) {
                    val currentListIds = it.listIds
                        .split(",")
                        .map { id -> id.trim() }
                        .filter { id -> id.isNotBlank() }
                    // Add the target list and optimistically drop smart lists (see moveToList).
                    val newListIds = (currentListIds - smartListIds) + listId
                    if (newListIds.toSet() != currentListIds.toSet()) {
                        it.copy(listIds = newListIds.distinct().joinToString(","))
                    } else {
                        it
                    }
                } else {
                    it
                }
            }
        }
        reconcileBookmarkLists(bookmark)
    }
}

/**
 * Re-adds a bookmark to a list and reloads the UI from the database.
 * Used by undo lambdas where the bookmark was removed from the visible list.
 *
 * Skips [reconcileBookmarkLists] intentionally — calling it would launch a
 * separate coroutine with API calls + [resetPaginationAndLoad], which races
 * with the original [removeBookmarkFromList]'s reconciliation coroutine.
 * Smart list reconciliation will happen on the next sync.
 */
fun MainScreenModel.restoreAndMoveBookmarkToList(bookmark: BookmarkEntity, listId: String) {
    viewModelScope.launch {
        val isOnline = !_isSyncing.value
        bookmarkActionsRepository.moveToList(bookmark.remoteId, bookmark.serverId, listId, isOnline)
        val server = _selectedServer.value ?: return@launch
        resetPaginationAndLoad(server, effectiveFilterNow(), scrollToTop = false)
    }
}

/**
 * Removes a bookmark from a list and restores it in the visible accumulated list.
 * Used by undo lambdas when the bookmark may have been removed from the
 * accumulated list by smart list reconciliation (e.g. smart list that
 * excludes the target list — after adding to Read Later, the server
 * recalculates the smart list and strips its ID from the bookmark's local
 * listIds; undoing the add removes Read Later but the smart list ID is
 * still gone, so [resetPaginationAndLoad] won't find it).
 *
 * Optimistically re-inserts the bookmark at the top of the accumulated
 * list if it's missing, so the user sees it immediately. Smart list
 * membership will be fully reconciled on the next sync.
 */
fun MainScreenModel.accumulatedBookmarkPosition(bookmark: BookmarkEntity): Int =
    _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }

fun MainScreenModel.restoreAndRemoveBookmarkFromList(bookmark: BookmarkEntity, listId: String, originalPosition: Int = -1) {
    viewModelScope.launch {
        val isOnline = !_isSyncing.value
        bookmarkActionsRepository.removeFromList(bookmark.remoteId, bookmark.serverId, listId, isOnline)
        updateAccumulatedBookmarks { current ->
            applyRestoreAndRemoveFromListTransform(current, bookmark, listId, originalPosition)
        }
    }
}

/**
 * Pure function implementing the undo-add-to-list transform.
 *
 * If the bookmark is still in [currentBookmarks], strips [listId] from its listIds.
 * If the bookmark is missing (smart list reconciliation removed it), re-inserts it
 * at [originalPosition] (or index 0 if the position is out of bounds).
 */
fun applyRestoreAndRemoveFromListTransform(
    currentBookmarks: List<BookmarkEntity>,
    bookmark: BookmarkEntity,
    listId: String,
    originalPosition: Int = -1
): List<BookmarkEntity> {
    return if (currentBookmarks.any { it.remoteId == bookmark.remoteId }) {
        // Bookmark is still in the list — just strip the target listId
        currentBookmarks.map {
            if (it.remoteId == bookmark.remoteId) {
                val ids = it.listIds.split(",").map { id -> id.trim() }
                    .filter { id -> id.isNotBlank() && id != listId }
                it.copy(listIds = ids.joinToString(","))
            } else it
        }
    } else {
        // Bookmark was removed (smart list reconciliation stripped it).
        // Re-insert at original position with state from before the action.
        val mutable = currentBookmarks.toMutableList()
        if (originalPosition in 0..mutable.size) {
            mutable.add(originalPosition, bookmark)
        } else {
            mutable.add(0, bookmark)
        }
        mutable
    }
}

fun MainScreenModel.addBookmarkTag(bookmark: BookmarkEntity, tagName: String) {
    viewModelScope.launch {
        val currentTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (!currentTags.contains(tagName)) {
            val newTags = currentTags + tagName
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.updateTags(
                bookmark.remoteId, bookmark.serverId, newTags, isOnline
            )
            updateAccumulatedBookmarks { list ->
                list.map {
                    if (it.remoteId == bookmark.remoteId) it.copy(tags = newTags.joinToString(","))
                    else it
                }
            }
        }
    }
}

fun MainScreenModel.removeBookmarkTag(bookmark: BookmarkEntity, tagName: String) {
    viewModelScope.launch {
        val currentTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (currentTags.contains(tagName)) {
            val newTags = currentTags.filter { it != tagName }
            val isOnline = !_isSyncing.value
            bookmarkActionsRepository.updateTags(
                bookmark.remoteId, bookmark.serverId, newTags, isOnline
            )
            updateAccumulatedBookmarks { list ->
                list.map {
                    if (it.remoteId == bookmark.remoteId) it.copy(tags = newTags.joinToString(","))
                    else it
                }
            }
        }
    }
}

/**
 * Pure function implementing the conditional bookmark removal transform for LIST-01.
 *
 * When the user is viewing the target list (currentListContext == listId),
 * the bookmark is filtered out entirely (D-01).
 * When viewing a different context, the bookmark stays but its listIds are updated (D-02).
 *
 * Extracted as a top-level function so both production code and unit tests
 * exercise the same logic path.
 */
fun applyRemoveBookmarkTransform(
    currentListContext: String?,
    listId: String,
    bookmarks: List<BookmarkEntity>,
    bookmark: BookmarkEntity
): List<BookmarkEntity> {
    return if (currentListContext == listId) {
        bookmarks.filter { it.remoteId != bookmark.remoteId }
    } else {
        bookmarks.map {
            if (it.remoteId == bookmark.remoteId) {
                val newListIds = it.listIds
                    .split(",")
                    .map { id -> id.trim() }
                    .filter { id -> id.isNotBlank() && id != listId }
                it.copy(listIds = newListIds.joinToString(","))
            } else {
                it
            }
        }
    }
}

fun MainScreenModel.removeBookmarkFromList(bookmark: BookmarkEntity, listId: String) {
    _actedOnBookmarkIds.value += bookmark.remoteId
    viewModelScope.launch {
        val isOnline = !_isSyncing.value
        bookmarkActionsRepository.removeFromList(
            bookmark.remoteId, bookmark.serverId, listId, isOnline
        )
        updateAccumulatedBookmarks { currentBookmarks ->
            applyRemoveBookmarkTransform(
                currentListContext = _currentListContext.value,
                listId = listId,
                bookmarks = currentBookmarks,
                bookmark = bookmark
            )
        }
        reconcileBookmarkLists(bookmark)
    }
}

fun MainScreenModel.markAllBookmarksInListAsRead(listId: String) {
    val unreadInList = allBookmarks.value.filter { bookmark ->
        val bookmarkLists = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        bookmarkLists.contains(listId) && !bookmark.isRead
    }
    if (unreadInList.isEmpty()) return
    viewModelScope.launch {
        bookmarkActionsRepository.batchMarkRead(unreadInList)
        val ids = unreadInList.map { it.remoteId }.toSet()
        updateAccumulatedBookmarks { list ->
            list.map { if (it.remoteId in ids) it.copy(isRead = true) else it }
        }
        val count = unreadInList.size
        snackbarManager.showSnackbarWithUndo("Marked $count bookmark${if (count > 1) "s" else ""} as read", onUndo = {
            bookmarkActionsRepository.batchMarkUnread(unreadInList, false)
            updateAccumulatedBookmarks { list ->
                list.map { if (it.remoteId in ids) it.copy(isRead = false) else it }
            }
        })
    }
}

fun MainScreenModel.renameList(listId: String, newName: String, newIcon: String?) {
    viewModelScope.launch {
        val server = _selectedServer.value ?: return@launch
        listRepository.renameList(server, listId, newName, newIcon)
    }
}

fun MainScreenModel.executeScrollAction(
    bookmark: BookmarkEntity,
    action: com.karakept.app.data.model.SwipeAction,
    config: com.karakept.app.data.model.CustomSwipeActionConfig?
) {
    viewModelScope.launch {
        when (action) {
            com.karakept.app.data.model.SwipeAction.MARK_READ -> {
                if (!bookmark.isRead) {
                    bookmarkActionsRepository.markAsRead(bookmark.remoteId, bookmark.serverId)
                    updateAccumulatedBookmarks { list ->
                        list.map { if (it.remoteId == bookmark.remoteId) it.copy(isRead = true) else it }
                    }
                }
            }
            com.karakept.app.data.model.SwipeAction.ARCHIVE -> {
                if (!bookmark.isArchived) {
                    bookmarkActionsRepository.archiveBookmark(bookmark.remoteId, bookmark.serverId)
                    updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
                }
            }
            com.karakept.app.data.model.SwipeAction.FAVOURITE -> {
                bookmarkActionsRepository.toggleFavourite(
                    bookmark.remoteId, bookmark.serverId, bookmark.isStarred
                )
            }
            com.karakept.app.data.model.SwipeAction.ADD_TAG -> {
                config?.tagName?.let { addBookmarkTag(bookmark, it) }
            }
            com.karakept.app.data.model.SwipeAction.ADD_TO_LIST -> {
                config?.listId?.let { moveBookmarkToList(bookmark, it) }
            }
            else -> {}
        }
    }
}

fun MainScreenModel.createBookmark(url: String) {
    viewModelScope.launch {
        val server = _selectedServer.value ?: return@launch
        val tempRemoteId = kotlin.random.Random.nextLong(Long.MIN_VALUE, -1L)
        val placeholder = BookmarkEntity(
            remoteId = tempRemoteId,
            originalRemoteId = "pending-$tempRemoteId",
            serverId = server.id,
            url = url,
            title = url,
            content = null,
            imageUrl = null,
            bannerImageAssetId = null,
            screenshotAssetId = null,
            description = null,
            createdAt = 0L,
            isArchived = false,
            isStarred = false,
        )

        _pendingBookmarks.value = listOf(placeholder) + _pendingBookmarks.value
        _scrollToTopTrigger.emit(Unit)

        val result = bookmarkRepository.createBookmark(url)

        result.onSuccess { bookmark ->
            updateAccumulatedBookmarks { listOf(bookmark) + it }
            _pendingBookmarks.value = _pendingBookmarks.value.filter { it.remoteId != tempRemoteId }
            _createBookmarkResult.emit(Result.success(Unit))
        }.onFailure { e ->
            _pendingBookmarks.value = _pendingBookmarks.value.filter { it.remoteId != tempRemoteId }
            _createBookmarkResult.emit(Result.failure(e))
        }
    }
}

/**
 * Run a server-side AI job for one bookmark from the list.
 *
 * Feedback is a snackbar rather than a spinner on the row: these menus fire and dismiss, and the
 * updated row arrives on its own through `bookmarkChangedEvents` once the repository writes it.
 */
fun MainScreenModel.runAiAction(bookmark: BookmarkEntity, action: AiAction) {
    viewModelScope.launch {
        try {
            snackbarManager.showSnackbar("${action.runningLabel}\u2026")
            when (action) {
                AiAction.SUMMARIZE -> {
                    val summary = bookmarkActionsRepository.summarizeBookmark(bookmark)
                    snackbarManager.showSnackbar(
                        if (summary.isNullOrBlank()) "The server returned an empty summary"
                        else "Summary generated"
                    )
                }
                AiAction.RETAG -> {
                    val landed = bookmarkActionsRepository.requestAiRetag(bookmark)
                    snackbarManager.showSnackbar(
                        if (landed) "Tags updated"
                        else "Still tagging on the server \u2014 pull to refresh later"
                    )
                }
            }
        } catch (e: OfflineModeException) {
            snackbarManager.showSnackbar("Not available in offline mode")
        } catch (e: UnsupportedServerActionException) {
            snackbarManager.showSnackbar(e.message ?: "Not supported by this server")
        } catch (e: Exception) {
            AppLogger.e("MainScreenModel", "AI action ${action.name} failed: ${e.message}", e)
            snackbarManager.showErrorWithRetry("Couldn't reach the server") {
                runAiAction(bookmark, action)
            }
        }
    }
}
