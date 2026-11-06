package com.karakept.app.data.repository

import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.local.entity.PendingActionType
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/**
 * Batch operations for BookmarkActionsRepository.
 * These operate on multiple bookmarks at once without individual snackbar feedback.
 * Extracted to reduce BookmarkActionsRepository file size.
 */

/**
 * Archive a list of bookmarks without showing individual snackbars.
 */
suspend fun BookmarkActionsRepository.batchArchive(bookmarks: List<Bookmark>) {
    withContext(appDispatchers.io) {
        bookmarks.forEach { bookmark ->
            val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
            current?.let { bookmarkDao.insertBookmark(it.copy(isArchived = true)) }
            queueAction(
                bookmarkRemoteId = bookmark.remoteId,
                serverId = bookmark.serverId,
                actionType = PendingActionType.ARCHIVE,
                actionData = "{}"
            )
        }
        bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
    }
}

/**
 * Unarchive a list of bookmarks without showing individual snackbars.
 */
suspend fun BookmarkActionsRepository.batchUnarchive(bookmarks: List<Bookmark>) {
    withContext(appDispatchers.io) {
        bookmarks.forEach { bookmark ->
            val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
            current?.let { bookmarkDao.insertBookmark(it.copy(isArchived = false)) }
            queueAction(
                bookmarkRemoteId = bookmark.remoteId,
                serverId = bookmark.serverId,
                actionType = PendingActionType.UNARCHIVE,
                actionData = "{}"
            )
        }
        bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
    }
}

/**
 * Mark a list of bookmarks as read without showing individual snackbars.
 */
suspend fun BookmarkActionsRepository.batchMarkRead(bookmarks: List<Bookmark>) {
    withContext(appDispatchers.io) {
        bookmarks.forEach { bookmark ->
            val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
            current?.let {
                bookmarkDao.insertBookmark(it.copy(isRead = true, readingProgress = 1f))
                queueReadingProgressUpdate(bookmark.remoteId, bookmark.serverId, progressPercent = 100)
            }
        }
        bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
    }
}

/**
 * Mark a list of bookmarks as unread without showing individual snackbars.
 */
suspend fun BookmarkActionsRepository.batchMarkUnread(bookmarks: List<Bookmark>, resetProgress: Boolean = false) {
    withContext(appDispatchers.io) {
        bookmarks.forEach { bookmark ->
            val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
            current?.let {
                val updated = if (resetProgress) {
                    it.copy(isRead = false, readingProgress = 0f, readingScrollIndex = 0, readingScrollOffset = 0)
                } else {
                    it.copy(isRead = false)
                }
                bookmarkDao.insertBookmark(updated)
                if (resetProgress) {
                    queueReadingProgressUpdate(bookmark.remoteId, bookmark.serverId, progressPercent = 0)
                }
            }
        }
        if (resetProgress) {
            bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
        }
    }
}

/**
 * Set favourite status for a list of bookmarks without showing individual snackbars.
 */
suspend fun BookmarkActionsRepository.batchSetFavourite(bookmarks: List<Bookmark>, makeFavourite: Boolean) {
    withContext(appDispatchers.io) {
        bookmarks.forEach { bookmark ->
            val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
            current?.let { bookmarkDao.insertBookmark(it.copy(isStarred = makeFavourite)) }
            queueAction(
                bookmarkRemoteId = bookmark.remoteId,
                serverId = bookmark.serverId,
                actionType = if (makeFavourite) PendingActionType.FAVOURITE else PendingActionType.UNFAVOURITE,
                actionData = "{}"
            )
        }
        bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
    }
}

/**
 * Delete a list of bookmarks without showing individual snackbars.
 */
suspend fun BookmarkActionsRepository.batchDelete(bookmarks: List<Bookmark>) {
    withContext(appDispatchers.io) {
        bookmarks.forEach { bookmark ->
            val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
            if (current != null) {
                bookmarkDao.deleteBookmark(current)
                queueAction(
                    bookmarkRemoteId = bookmark.remoteId,
                    serverId = bookmark.serverId,
                    actionType = PendingActionType.DELETE,
                    actionData = "{}"
                )
            }
        }
        bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
    }
}

/**
 * Set the same tag list on all selected bookmarks without showing individual snackbars.
 */
suspend fun BookmarkActionsRepository.batchUpdateTags(bookmarks: List<Bookmark>, newTags: List<String>) {
    withContext(appDispatchers.io) {
        bookmarks.forEach { bookmark ->
            val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
            current?.let { bookmarkDao.insertBookmark(it.copy(tags = newTags.joinToString(","))) }
            queueAction(
                bookmarkRemoteId = bookmark.remoteId,
                serverId = bookmark.serverId,
                actionType = PendingActionType.UPDATE_TAGS,
                actionData = jsonSerializer.encodeToString(mapOf("tags" to newTags))
            )
        }
        bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
    }
}

/**
 * Add a list of bookmarks to a list without showing individual snackbars.
 */
suspend fun BookmarkActionsRepository.batchMoveToList(bookmarks: List<Bookmark>, listId: String) {
    withContext(appDispatchers.io) {
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
                actionData = jsonSerializer.encodeToString(mapOf("listId" to listId))
            )
        }
        bookmarks.firstOrNull()?.serverId?.let { triggerAutoSync(it) }
    }
}
