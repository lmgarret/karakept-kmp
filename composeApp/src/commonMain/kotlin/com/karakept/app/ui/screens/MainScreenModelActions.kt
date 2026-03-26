/** Bookmark action extension functions for MainScreenModel. */
package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.domain.action.BookmarkActionEvent
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

fun MainScreenModel.toggleBookmarkArchive(bookmark: BookmarkEntity) {
    screenModelScope.launch {
        val position = _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
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
    screenModelScope.launch {
        val position = _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
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
    screenModelScope.launch {
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
    screenModelScope.launch {
        val position = _accumulatedBookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
        bookmarkActionController.executeAction(
            BookmarkActionEvent.Delete(bookmark),
            originalPosition = position
        )
        updateAccumulatedBookmarks { it.filter { b -> b.remoteId != bookmark.remoteId } }
    }
}

fun MainScreenModel.updateBookmarkTags(bookmark: BookmarkEntity, newTags: List<String>) {
    screenModelScope.launch {
        val isOnline = !_isSyncing.value
        bookmarkActionsRepository.updateTags(
            bookmark.remoteId, bookmark.serverId, newTags, isOnline
        )
    }
}

/**
 * Triggers background sync for all smart lists after a list-membership action.
 * Smart list queries are server-owned, so we must re-fetch from server to get
 * accurate contents and drawer counts.
 */
private fun MainScreenModel.syncSmartLists() {
    val server = _selectedServer.value ?: return
    val smartLists = listRepository.lists.value.filter { it.type == KarakeepList.Type.SMART }
    if (smartLists.isEmpty()) return
    screenModelScope.launch {
        smartLists.forEach { smartList ->
            val listId = smartList.id ?: return@forEach
            launch {
                try {
                    bookmarkRepository.syncBookmarksForList(server, listId)
                } catch (e: Exception) {
                    AppLogger.e("MainScreenModel", "Smart list sync failed for $listId: ${e.message}", e)
                }
            }
        }
    }
}

fun MainScreenModel.moveBookmarkToList(bookmark: BookmarkEntity, listId: String) {
    screenModelScope.launch {
        val isOnline = !_isSyncing.value
        bookmarkActionsRepository.moveToList(
            bookmark.remoteId, bookmark.serverId, listId, isOnline
        )
        updateAccumulatedBookmarks { list ->
            list.map {
                if (it.remoteId == bookmark.remoteId) {
                    val currentListIds = it.listIds
                        .split(",")
                        .map { id -> id.trim() }
                        .filter { id -> id.isNotBlank() }
                    if (!currentListIds.contains(listId)) {
                        it.copy(listIds = (currentListIds + listId).joinToString(","))
                    } else {
                        it
                    }
                } else {
                    it
                }
            }
        }
        syncSmartLists()
    }
}

fun MainScreenModel.addBookmarkTag(bookmark: BookmarkEntity, tagName: String) {
    screenModelScope.launch {
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
    screenModelScope.launch {
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
    screenModelScope.launch {
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
        syncSmartLists()
    }
}

fun MainScreenModel.markAllBookmarksInListAsRead(listId: String) {
    screenModelScope.launch {
        val serverId = _selectedServer.value?.id ?: return@launch
        val unreadInList = allBookmarks.value.filter { bookmark ->
            val bookmarkLists = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            bookmarkLists.contains(listId) && !bookmark.isRead
        }
        unreadInList.forEach { bookmark ->
            bookmarkActionsRepository.markAsRead(bookmark.remoteId, serverId)
        }
        updateAccumulatedBookmarks { list ->
            list.map {
                val bookmarkLists = it.listIds.split(",").map { id -> id.trim() }.filter { id -> id.isNotEmpty() }
                if (bookmarkLists.contains(listId)) it.copy(isRead = true) else it
            }
        }
    }
}

fun MainScreenModel.renameList(listId: String, newName: String, newIcon: String?) {
    screenModelScope.launch {
        val server = _selectedServer.value ?: return@launch
        listRepository.renameList(server, listId, newName, newIcon)
    }
}

fun MainScreenModel.executeScrollAction(
    bookmark: BookmarkEntity,
    action: com.karakept.app.data.model.SwipeAction,
    config: com.karakept.app.data.model.CustomSwipeActionConfig?
) {
    screenModelScope.launch {
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
    screenModelScope.launch {
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
