/** Selection and batch operation extension functions for MainScreenModel. */
package com.karakept.app.ui.screens

import androidx.lifecycle.viewModelScope
import com.karakept.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.karakept.app.data.remote.UnsupportedServerActionException
import com.karakept.app.data.repository.requestAiRetag
import com.karakept.app.data.repository.summarizeBookmark
import com.karakept.app.domain.action.AiAction
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.CancellationException
import com.karakept.app.data.repository.batchArchive
import com.karakept.app.data.repository.batchUnarchive
import com.karakept.app.data.repository.batchMarkRead
import com.karakept.app.data.repository.batchMarkUnread
import com.karakept.app.data.repository.batchSetFavourite
import com.karakept.app.data.repository.batchDelete
import com.karakept.app.data.repository.batchUpdateTags
import com.karakept.app.data.repository.batchMoveToList
import com.karakept.app.domain.BookmarkFilterUtils

/** Track the last clicked bookmark index (call on every normal click). */
fun MainScreenModel.trackLastClickedIndex(index: Int) {
    _lastSelectedIndex = index
}

fun MainScreenModel.enterSelectionMode(bookmark: BookmarkEntity) {
    _selectedViaSelectAll.value = false
    _selectedBookmarkIds.value = setOf(bookmark.remoteId)
    _lastSelectedIndex = bookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
}

/**
 * Enters selection mode and immediately selects a range from the last clicked
 * index (tracked outside selection mode via [trackLastClickedIndex]) to [toIndex].
 * If no anchor exists, just selects the single item at [toIndex].
 */
fun MainScreenModel.enterSelectionModeWithRange(toIndex: Int) {
    _selectedViaSelectAll.value = false
    val list = bookmarks.value
    val anchor = _lastSelectedIndex.takeIf { it >= 0 && it <= list.lastIndex }
    if (anchor != null) {
        val start = minOf(anchor, toIndex)
        val end = minOf(maxOf(anchor, toIndex), list.lastIndex)
        val rangeIds = (start..end).map { list[it].remoteId }.toSet()
        _selectedBookmarkIds.value = rangeIds
    } else {
        val bookmark = list.getOrNull(toIndex) ?: return
        _selectedBookmarkIds.value = setOf(bookmark.remoteId)
    }
    _lastSelectedIndex = toIndex
}

fun MainScreenModel.toggleBookmarkSelection(bookmark: BookmarkEntity) {
    _selectedViaSelectAll.value = false
    val current = _selectedBookmarkIds.value
    _selectedBookmarkIds.value = if (bookmark.remoteId in current) {
        current - bookmark.remoteId
    } else {
        current + bookmark.remoteId
    }
    _lastSelectedIndex = bookmarks.value.indexOfFirst { it.remoteId == bookmark.remoteId }
}

/**
 * Selects all bookmarks in the range [lastSelectedIndex, toIndex] (inclusive).
 * Used for Shift+Click range selection on desktop.
 */
fun MainScreenModel.selectRange(toIndex: Int) {
    _selectedViaSelectAll.value = false
    val fromIndex = _lastSelectedIndex.takeIf { it >= 0 } ?: return
    val list = bookmarks.value
    val start = minOf(fromIndex, toIndex)
    val end = minOf(maxOf(fromIndex, toIndex), list.lastIndex)
    val rangeIds = (start..end).map { list[it].remoteId }.toSet()
    _selectedBookmarkIds.value = _selectedBookmarkIds.value + rangeIds
    _lastSelectedIndex = toIndex
}

fun MainScreenModel.clearSelection() {
    _selectedViaSelectAll.value = false
    _selectedBookmarkIds.value = emptySet()
    _lastSelectedIndex = -1
}

fun MainScreenModel.selectAll() {
    _selectedViaSelectAll.value = true
    if (!_hasMoreItems.value) {
        // D-03: All pages already loaded -- current behavior is correct
        _selectedBookmarkIds.value = _accumulatedBookmarks.value.map { it.remoteId }.toSet()
        return
    }
    // D-01: Fetch all matching entities from DB in one query
    viewModelScope.launch {
        val server = _selectedServer.value ?: return@launch
        val expandedFilter = effectiveFilterNow()

        // D-04: Apply current filter to DB query
        val singleListId = expandedFilter.lists.singleOrNull()
        val allEntities = bookmarkRepository.getAllBookmarks(server, expandedFilter.status, singleListId)

        // Apply client-side filters (tags, multi-list) per Pitfall 1
        val filtered = BookmarkFilterUtils.applyClientSideFilters(
            allEntities, expandedFilter, skipListFilter = singleListId != null
        )
        val sorted = BookmarkFilterUtils.applySorting(filtered, expandedFilter.sort)

        // Use updateAccumulatedBookmarks for thread safety per Pitfall 3
        updateAccumulatedBookmarks { sorted }
        _hasMoreItems.value = false
        _selectedBookmarkIds.value = sorted.map { it.remoteId }.toSet()
    }
}

internal fun MainScreenModel.getSelectedBookmarks(): List<BookmarkEntity> {
    val ids = _selectedBookmarkIds.value
    return _accumulatedBookmarks.value.filter { it.remoteId in ids }
}

fun MainScreenModel.batchArchive() {
    val bookmarks = getSelectedBookmarks().filter { !it.isArchived }
    if (bookmarks.isEmpty()) { clearSelection(); return }
    viewModelScope.launch {
        bookmarkActionsRepository.batchArchive(bookmarks)
        val remoteIds = bookmarks.map { it.remoteId }.toSet()
        updateAccumulatedBookmarks { it.filter { b -> b.remoteId !in remoteIds } }
        clearSelection()
        val count = bookmarks.size
        snackbarManager.showSnackbarWithUndo("Archived $count bookmark${if (count > 1) "s" else ""}", onUndo = {
            bookmarkActionsRepository.batchUnarchive(bookmarks)
            updateAccumulatedBookmarks { it + bookmarks.map { b -> b.copy(isArchived = false) } }
        })
    }
}

fun MainScreenModel.batchUnarchive() {
    val bookmarks = getSelectedBookmarks().filter { it.isArchived }
    if (bookmarks.isEmpty()) { clearSelection(); return }
    viewModelScope.launch {
        bookmarkActionsRepository.batchUnarchive(bookmarks)
        val remoteIds = bookmarks.map { it.remoteId }.toSet()
        updateAccumulatedBookmarks { it.filter { b -> b.remoteId !in remoteIds } }
        clearSelection()
        val count = bookmarks.size
        snackbarManager.showSnackbarWithUndo("Unarchived $count bookmark${if (count > 1) "s" else ""}", onUndo = {
            bookmarkActionsRepository.batchArchive(bookmarks)
            updateAccumulatedBookmarks { it + bookmarks.map { b -> b.copy(isArchived = true) } }
        })
    }
}

fun MainScreenModel.batchMarkRead() {
    val bookmarks = getSelectedBookmarks().filter { !it.isRead }
    if (bookmarks.isEmpty()) { clearSelection(); return }
    viewModelScope.launch {
        bookmarkActionsRepository.batchMarkRead(bookmarks)
        val ids = bookmarks.map { it.remoteId }.toSet()
        updateAccumulatedBookmarks { list ->
            list.map { if (it.remoteId in ids) it.copy(isRead = true) else it }
        }
        clearSelection()
        val count = bookmarks.size
        snackbarManager.showSnackbarWithUndo("Marked $count bookmark${if (count > 1) "s" else ""} as read", onUndo = {
            bookmarkActionsRepository.batchMarkUnread(bookmarks, false)
            updateAccumulatedBookmarks { list ->
                list.map { if (it.remoteId in ids) it.copy(isRead = false) else it }
            }
        })
    }
}

fun MainScreenModel.batchMarkUnread() {
    val bookmarks = getSelectedBookmarks().filter { it.isRead }
    if (bookmarks.isEmpty()) { clearSelection(); return }
    viewModelScope.launch {
        val resetProgress = settingsRepository.resetProgressOnMarkUnread.first()
        bookmarkActionsRepository.batchMarkUnread(bookmarks, resetProgress)
        val ids = bookmarks.map { it.remoteId }.toSet()
        updateAccumulatedBookmarks { list ->
            list.map {
                if (it.remoteId in ids) {
                    if (resetProgress) it.copy(isRead = false, readingProgress = 0f, readingScrollIndex = 0, readingScrollOffset = 0)
                    else it.copy(isRead = false)
                } else it
            }
        }
        clearSelection()
        val count = bookmarks.size
        snackbarManager.showSnackbarWithUndo("Marked $count bookmark${if (count > 1) "s" else ""} as unread", onUndo = {
            bookmarkActionsRepository.batchMarkRead(bookmarks)
            updateAccumulatedBookmarks { list ->
                list.map { if (it.remoteId in ids) it.copy(isRead = true) else it }
            }
        })
    }
}

fun MainScreenModel.batchFavourite() {
    val bookmarks = getSelectedBookmarks().filter { !it.isStarred }
    if (bookmarks.isEmpty()) { clearSelection(); return }
    viewModelScope.launch {
        bookmarkActionsRepository.batchSetFavourite(bookmarks, makeFavourite = true)
        val ids = bookmarks.map { it.remoteId }.toSet()
        updateAccumulatedBookmarks { list ->
            list.map { if (it.remoteId in ids) it.copy(isStarred = true) else it }
        }
        clearSelection()
        val count = bookmarks.size
        snackbarManager.showSnackbarWithUndo("Added $count bookmark${if (count > 1) "s" else ""} to favorites", onUndo = {
            bookmarkActionsRepository.batchSetFavourite(bookmarks, makeFavourite = false)
            updateAccumulatedBookmarks { list ->
                list.map { if (it.remoteId in ids) it.copy(isStarred = false) else it }
            }
        })
    }
}

fun MainScreenModel.batchUnfavourite() {
    val bookmarks = getSelectedBookmarks().filter { it.isStarred }
    if (bookmarks.isEmpty()) { clearSelection(); return }
    viewModelScope.launch {
        bookmarkActionsRepository.batchSetFavourite(bookmarks, makeFavourite = false)
        val ids = bookmarks.map { it.remoteId }.toSet()
        updateAccumulatedBookmarks { list ->
            list.map { if (it.remoteId in ids) it.copy(isStarred = false) else it }
        }
        clearSelection()
        val count = bookmarks.size
        snackbarManager.showSnackbarWithUndo("Removed $count bookmark${if (count > 1) "s" else ""} from favorites", onUndo = {
            bookmarkActionsRepository.batchSetFavourite(bookmarks, makeFavourite = true)
            updateAccumulatedBookmarks { list ->
                list.map { if (it.remoteId in ids) it.copy(isStarred = true) else it }
            }
        })
    }
}

fun MainScreenModel.batchDelete() {
    val bookmarks = getSelectedBookmarks()
    if (bookmarks.isEmpty()) { clearSelection(); return }
    viewModelScope.launch {
        bookmarkActionsRepository.batchDelete(bookmarks)
        val ids = bookmarks.map { it.remoteId }.toSet()
        updateAccumulatedBookmarks { it.filter { b -> b.remoteId !in ids } }
        clearSelection()
    }
}

fun MainScreenModel.batchSetTags(newTags: List<String>) {
    val bookmarks = getSelectedBookmarks()
    if (bookmarks.isEmpty()) { clearSelection(); return }
    viewModelScope.launch {
        bookmarkActionsRepository.batchUpdateTags(bookmarks, newTags)
        val ids = bookmarks.map { it.remoteId }.toSet()
        val tagString = newTags.joinToString(",")
        updateAccumulatedBookmarks { list ->
            list.map { bookmark ->
                if (bookmark.remoteId in ids) bookmark.copy(tags = tagString) else bookmark
            }
        }
        clearSelection()
        val count = bookmarks.size
        snackbarManager.showSnackbar("Tags updated for $count bookmark${if (count > 1) "s" else ""}")
    }
}

fun MainScreenModel.batchMoveToList(listId: String) {
    val bookmarks = getSelectedBookmarks()
    if (bookmarks.isEmpty()) { clearSelection(); return }
    viewModelScope.launch {
        val positions = bookmarks.associate { it.remoteId to accumulatedBookmarkPosition(it) }
        bookmarkActionsRepository.batchMoveToList(bookmarks, listId)
        val ids = bookmarks.map { it.remoteId }.toSet()
        updateAccumulatedBookmarks { list ->
            list.map { bookmark ->
                if (bookmark.remoteId in ids) {
                    val currentListIds = bookmark.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    if (!currentListIds.contains(listId)) {
                        bookmark.copy(listIds = (currentListIds + listId).joinToString(","))
                    } else bookmark
                } else bookmark
            }
        }
        bookmarks.firstOrNull()?.let { reconcileBookmarkLists(it) }
        clearSelection()
        val count = bookmarks.size
        snackbarManager.showSnackbarWithUndo("Moved $count bookmark${if (count > 1) "s" else ""} to list", onUndo = {
            for (bookmark in bookmarks) {
                restoreAndRemoveBookmarkFromList(bookmark, listId, positions[bookmark.remoteId] ?: -1)
            }
        })
    }
}

/** How far a batch AI run has got. Drives the selection bar's title while one is in flight. */
data class AiBatchProgress(
    val action: AiAction,
    val done: Int,
    val total: Int
)

/**
 * Run [action] over the current selection, one bookmark at a time.
 *
 * Sequential on purpose: each item is an inference call on the server, and firing them in parallel
 * is how you get a self-hosted instance to start refusing them. A failure is counted rather than
 * fatal — the run finishes and reports how many landed — because giving up halfway leaves the user
 * with no idea which half worked. There is no undo: a generated summary has no previous value worth
 * restoring, and a re-tag is the server's own judgement.
 *
 * The caller is expected to have confirmed first; [selectedViaSelectAll] selections never reach here.
 */
fun MainScreenModel.batchAiAction(action: AiAction) {
    val bookmarks = getSelectedBookmarks()
    if (bookmarks.isEmpty()) {
        clearSelection()
        return
    }
    if (aiBatchJob?.isActive == true) return

    aiBatchJob = viewModelScope.launch {
        var succeeded = 0
        var failed = 0
        try {
            _aiBatchProgress.value = AiBatchProgress(action, done = 0, total = bookmarks.size)
            bookmarks.forEachIndexed { index, bookmark ->
                try {
                    when (action) {
                        AiAction.SUMMARIZE -> bookmarkActionsRepository.summarizeBookmark(bookmark)
                        // Fire and forget: polling each bookmark for its new tags would make a
                        // ten-item run take minutes. They arrive on the next sync.
                        AiAction.RETAG -> bookmarkActionsRepository.requestAiRetag(bookmark, awaitResult = false)
                    }
                    succeeded++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: UnsupportedServerActionException) {
                    // The server will refuse every remaining item for the same reason.
                    snackbarManager.showSnackbar(e.message ?: "Not supported by this server")
                    return@launch
                } catch (e: Exception) {
                    AppLogger.e("MainScreenModel", "Batch AI ${action.name} failed for ${bookmark.remoteId}: ${e.message}", e)
                    failed++
                }
                _aiBatchProgress.value = AiBatchProgress(action, done = index + 1, total = bookmarks.size)
            }

            // Re-tagging only queues jobs, so the message says what was asked for, not what landed.
            val verb = if (action == AiAction.SUMMARIZE) "Summarized" else "Requested AI tagging for"
            snackbarManager.showSnackbar(
                if (failed == 0) "$verb $succeeded of ${bookmarks.size}"
                else "$verb $succeeded of ${bookmarks.size} — $failed failed"
            )
        } finally {
            _aiBatchProgress.value = null
            clearSelection()
        }
    }
}

/** Stop a batch AI run after the item currently in flight. */
fun MainScreenModel.cancelAiBatchAction() {
    aiBatchJob?.cancel()
    aiBatchJob = null
}
