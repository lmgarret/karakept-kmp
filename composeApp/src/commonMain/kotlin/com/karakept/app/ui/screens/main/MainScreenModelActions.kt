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
package com.karakept.app.ui.screens.main

import androidx.lifecycle.viewModelScope
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.domain.action.BookmarkActionEvent
import com.karakept.app.data.remote.OfflineModeException
import com.karakept.app.data.remote.UnsupportedServerActionException
import com.karakept.app.data.repository.requestAiRetag
import com.karakept.app.data.repository.summarizeBookmark
import com.karakept.app.domain.action.AiAction
import com.karakept.app.data.repository.batchAddToList
import com.karakept.app.data.repository.batchMarkRead
import com.karakept.app.data.repository.batchMarkUnread
import com.karakept.app.data.repository.batchRemoveFromList
import com.karakept.app.domain.ListMembershipChange
import com.karakept.app.domain.ListMembershipOperation
import com.karakept.app.domain.ListMembershipUtils
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

fun MainScreenModel.toggleBookmarkArchive(bookmark: Bookmark) {
    viewModelScope.launch {
        val event = if (bookmark.isArchived) {
            BookmarkActionEvent.Unarchive(bookmark)
        } else {
            BookmarkActionEvent.Archive(bookmark)
        }
        bookmarkActionController.executeAction(event)
    }
}

fun MainScreenModel.toggleBookmarkFavorite(bookmark: Bookmark) {
    viewModelScope.launch {
        bookmarkActionController.executeAction(BookmarkActionEvent.ToggleFavorite(bookmark))
    }
}

fun MainScreenModel.toggleBookmarkRead(bookmark: Bookmark) {
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

fun MainScreenModel.deleteBookmark(bookmark: Bookmark) {
    viewModelScope.launch {
        bookmarkActionController.executeAction(BookmarkActionEvent.Delete(bookmark))
    }
}

fun MainScreenModel.updateBookmarkTags(bookmark: Bookmark, newTags: List<String>) {
    viewModelScope.launch {
        bookmarkActionsRepository.updateTags(
            bookmark.remoteId, bookmark.serverId, newTags
        )
    }
}

/**
 * Flags every smart list for a sync the next time it is opened, after an edit that may have
 * changed which of them hold the edited bookmarks.
 *
 * Nothing is written to the bookmarks themselves. Karakeep evaluates a smart list as a query when
 * its bookmarks are asked for and records no membership anywhere, so no per-bookmark request can
 * say whether a bookmark is still in one; only syncing the smart list can (GET /lists/{id}/bookmarks,
 * see [refreshStaleSmartList]). Until then the bookmark stays in every view it was in.
 */
internal fun MainScreenModel.markSmartListsStale() {
    val smartListIds = listRepository.lists.value
        .filter { it.type == KarakeepList.Type.SMART }
        .mapNotNull { it.id }
    _smartListsNeedingRefresh.value += smartListIds
}

fun MainScreenModel.addBookmarkToList(bookmark: Bookmark, listId: String) {
    _actedOnBookmarkIds.value += bookmark.remoteId
    viewModelScope.launch {
        bookmarkActionsRepository.addToList(bookmark.remoteId, bookmark.serverId, listId)
        markSmartListsStale()
    }
}

/**
 * Applies a list-membership edit to [bookmarks] and offers to undo it.
 *
 * Nothing is taken out of a list the edit does not name: a bookmark can be in several lists, so
 * joining one says nothing about the list on screen. What the edit does to a smart list is the
 * server's to say, and it says so when that list is next synced ([markSmartListsStale]).
 */
fun MainScreenModel.updateBookmarkLists(bookmarks: List<Bookmark>, change: ListMembershipChange) {
    val operations = ListMembershipUtils.operationsFor(bookmarks, change)
    if (operations.isEmpty()) return
    _actedOnBookmarkIds.value += operations.flatMap { op -> op.bookmarks.map { it.remoteId } }
    viewModelScope.launch {
        operations.forEach { applyListMembershipOperation(it) }
        markSmartListsStale()
        val names = listRepository.lists.value.associate { it.id to it.name }
        snackbarManager.showSnackbarWithUndo(
            ListMembershipUtils.describeChange(change, bookmarks.size, names::get),
            onUndo = {
                operations.forEach { applyListMembershipOperation(it.inverted()) }
                markSmartListsStale()
            }
        )
    }
}

private suspend fun MainScreenModel.applyListMembershipOperation(operation: ListMembershipOperation) {
    if (operation.add) {
        bookmarkActionsRepository.batchAddToList(operation.bookmarks, operation.listId)
    } else {
        bookmarkActionsRepository.batchRemoveFromList(operation.bookmarks, operation.listId)
    }
}

/** Undo for a swipe that took a bookmark out of a list. */
fun MainScreenModel.restoreAndAddBookmarkToList(bookmark: Bookmark, listId: String) {
    viewModelScope.launch {
        bookmarkActionsRepository.addToList(bookmark.remoteId, bookmark.serverId, listId)
    }
}

/** Undo for a swipe that added a bookmark to a list. */
fun MainScreenModel.restoreAndRemoveBookmarkFromList(bookmark: Bookmark, listId: String) {
    viewModelScope.launch {
        bookmarkActionsRepository.removeFromList(bookmark.remoteId, bookmark.serverId, listId)
    }
}

fun MainScreenModel.addBookmarkTag(bookmark: Bookmark, tagName: String) {
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

fun MainScreenModel.removeBookmarkTag(bookmark: Bookmark, tagName: String) {
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

fun MainScreenModel.removeBookmarkFromList(bookmark: Bookmark, listId: String) {
    _actedOnBookmarkIds.value += bookmark.remoteId
    viewModelScope.launch {
        bookmarkActionsRepository.removeFromList(
            bookmark.remoteId, bookmark.serverId, listId
        )
        markSmartListsStale()
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
    bookmark: Bookmark,
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
                config?.listId?.let { addBookmarkToList(bookmark, it) }
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
        val placeholder = Bookmark(
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
 * Feedback is a snackbar rather than a spinner on the row: these menus fire and dismiss.
 */
fun MainScreenModel.runAiAction(bookmark: Bookmark, action: AiAction) {
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
