/**
 * Bookmark action extension functions for MainScreenModel.
 *
 * None of these patch the list. Every action writes the row to the local database before it
 * queues anything for the server (see BookmarkActionsRepository — its own comment calls that the
 * optimistic update), and the list re-reads the pages on screen whenever the table changes. So
 * archiving a bookmark removes it from an unarchived view because the query stops returning it,
 * and undoing the archive brings it back *where the sort puts it* rather than where a remembered
 * index said it was.
 *
 * They used to patch it, because the list held whatever rows had been read and nothing would have
 * re-read them. Each patch was a second, hand-maintained answer to a question the query already
 * answers — which is how they came to disagree with it: a bookmark dropped from a list view was
 * removed outright, while one dropped from a smart list was re-inserted at index 0.
 */
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
        val event = if (bookmark.isArchived) {
            BookmarkActionEvent.Unarchive(bookmark)
        } else {
            BookmarkActionEvent.Archive(bookmark)
        }
        bookmarkActionController.executeAction(event)
    }
}

fun MainScreenModel.toggleBookmarkFavorite(bookmark: BookmarkEntity) {
    viewModelScope.launch {
        bookmarkActionController.executeAction(BookmarkActionEvent.ToggleFavorite(bookmark))
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
    }
}

fun MainScreenModel.deleteBookmark(bookmark: BookmarkEntity) {
    viewModelScope.launch {
        bookmarkActionController.executeAction(BookmarkActionEvent.Delete(bookmark))
    }
}

fun MainScreenModel.updateBookmarkTags(bookmark: BookmarkEntity, newTags: List<String>) {
    viewModelScope.launch {
        bookmarkActionsRepository.updateTags(
            bookmark.remoteId, bookmark.serverId, newTags
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
            // The reconcile writes the bookmark's corrected listIds to the database, and that
            // write is what updates the list: a smart list that no longer admits the row stops
            // returning it, and one that does returns it with its new memberships.
        } catch (e: Exception) {
            AppLogger.e("MainScreenModel", "List membership reconciliation failed for bookmark ${bookmark.localId}: ${e.message}", e)
        }
    }
}

fun MainScreenModel.moveBookmarkToList(bookmark: BookmarkEntity, listId: String) {
    _actedOnBookmarkIds.value += bookmark.remoteId
    val smartListIds = listRepository.lists.value
        .filter { it.type == KarakeepList.Type.SMART }
        .mapNotNull { it.id }
        .toSet()
    viewModelScope.launch {
        bookmarkActionsRepository.moveToList(
            bookmark.remoteId, bookmark.serverId, listId, smartListIds
        )
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
        bookmarkActionsRepository.moveToList(bookmark.remoteId, bookmark.serverId, listId)
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
fun MainScreenModel.restoreAndRemoveBookmarkFromList(bookmark: BookmarkEntity, listId: String) {
    viewModelScope.launch {
        bookmarkActionsRepository.removeFromList(bookmark.remoteId, bookmark.serverId, listId)
    }
}

fun MainScreenModel.addBookmarkTag(bookmark: BookmarkEntity, tagName: String) {
    viewModelScope.launch {
        val currentTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (!currentTags.contains(tagName)) {
            val newTags = currentTags + tagName
            bookmarkActionsRepository.updateTags(
                bookmark.remoteId, bookmark.serverId, newTags
            )
        }
    }
}

fun MainScreenModel.removeBookmarkTag(bookmark: BookmarkEntity, tagName: String) {
    viewModelScope.launch {
        val currentTags = bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (currentTags.contains(tagName)) {
            val newTags = currentTags.filter { it != tagName }
            bookmarkActionsRepository.updateTags(
                bookmark.remoteId, bookmark.serverId, newTags
            )
        }
    }
}

fun MainScreenModel.removeBookmarkFromList(bookmark: BookmarkEntity, listId: String) {
    _actedOnBookmarkIds.value += bookmark.remoteId
    viewModelScope.launch {
        bookmarkActionsRepository.removeFromList(
            bookmark.remoteId, bookmark.serverId, listId
        )
        reconcileBookmarkLists(bookmark)
    }
}

fun MainScreenModel.markAllBookmarksInListAsRead(listId: String) {
    viewModelScope.launch {
        val server = _selectedServer.value ?: return@launch
        // Asked of the database: the list's unread rows are not necessarily rows the list has
        // shown, and there is no resident copy of the table to filter.
        val unreadInList = bookmarkRepository.getUnreadInList(server.id, listId)
        if (unreadInList.isEmpty()) return@launch
        bookmarkActionsRepository.batchMarkRead(unreadInList)
        val count = unreadInList.size
        snackbarManager.showSnackbarWithUndo("Marked $count bookmark${if (count > 1) "s" else ""} as read", onUndo = {
            bookmarkActionsRepository.batchMarkUnread(unreadInList, false)
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
                }
            }
            com.karakept.app.data.model.SwipeAction.ARCHIVE -> {
                if (!bookmark.isArchived) {
                    bookmarkActionsRepository.archiveBookmark(bookmark.remoteId, bookmark.serverId)
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
        // Distinct from any server id, so the placeholder can never collide with a real row.
        val tempRemoteId = "pending-${kotlin.random.Random.nextLong()}"
        val placeholder = BookmarkEntity(
            remoteId = tempRemoteId,
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

        result.onSuccess {
            // The created row is in the database, so the list reads it in on its own; the
            // placeholder standing in for it can go.
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
