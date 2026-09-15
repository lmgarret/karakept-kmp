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

/**
 * Selects every bookmark the view holds, loaded or not.
 *
 * Asks the database for the ids alone. It used to read the whole view into the list first — the
 * only way to have a row's id was to have the row — so selecting a four-thousand-row view
 * materialised four thousand rows to produce four thousand strings.
 */
fun MainScreenModel.selectAll() {
    _selectedViaSelectAll.value = true
    viewModelScope.launch {
        val server = _selectedServer.value ?: return@launch
        _selectedBookmarkIds.value =
            bookmarkRepository.getViewRemoteIds(server, effectiveFilterNow()).toSet()
    }
}

/**
 * The rows behind the current selection, read by id.
 *
 * A selection can cover rows the list has never shown — "select all" is exactly that — so it
 * cannot be a filter over what happens to be loaded.
 */
internal suspend fun MainScreenModel.getSelectedBookmarks(): List<BookmarkEntity> {
    val ids = _selectedBookmarkIds.value
    if (ids.isEmpty()) return emptyList()
    val server = _selectedServer.value ?: return emptyList()
    return bookmarkRepository.getBookmarksByRemoteIds(server.id, ids.toList())
}

/**
 * Runs [act] over the selection, then reports it with an undo that runs [undo].
 *
 * Neither half touches the list. Each repository call writes its rows to the local database, and
 * the list re-reads the pages on screen when the table changes — so a batch archive empties those
 * rows out of an unarchived view, and undoing it puts them back where the sort wants them.
 */
private fun MainScreenModel.batchAction(
    keep: (BookmarkEntity) -> Boolean = { true },
    message: (Int) -> String,
    undo: (suspend (List<BookmarkEntity>) -> Unit)? = null,
    act: suspend (List<BookmarkEntity>) -> Unit
) {
    viewModelScope.launch {
        val bookmarks = getSelectedBookmarks().filter(keep)
        if (bookmarks.isEmpty()) {
            clearSelection()
            return@launch
        }
        act(bookmarks)
        clearSelection()
        val text = message(bookmarks.size)
        if (undo == null) {
            snackbarManager.showSnackbar(text)
        } else {
            snackbarManager.showSnackbarWithUndo(text, onUndo = { undo(bookmarks) })
        }
    }
}

private fun plural(count: Int) = if (count > 1) "s" else ""

fun MainScreenModel.batchArchive() = batchAction(
    keep = { !it.isArchived },
    message = { "Archived $it bookmark${plural(it)}" },
    undo = { bookmarkActionsRepository.batchUnarchive(it) },
    act = { bookmarkActionsRepository.batchArchive(it) }
)

fun MainScreenModel.batchUnarchive() = batchAction(
    keep = { it.isArchived },
    message = { "Unarchived $it bookmark${plural(it)}" },
    undo = { bookmarkActionsRepository.batchArchive(it) },
    act = { bookmarkActionsRepository.batchUnarchive(it) }
)

fun MainScreenModel.batchMarkRead() = batchAction(
    keep = { !it.isRead },
    message = { "Marked $it bookmark${plural(it)} as read" },
    undo = { bookmarkActionsRepository.batchMarkUnread(it, false) },
    act = { bookmarkActionsRepository.batchMarkRead(it) }
)

fun MainScreenModel.batchMarkUnread() = batchAction(
    keep = { it.isRead },
    message = { "Marked $it bookmark${plural(it)} as unread" },
    undo = { bookmarkActionsRepository.batchMarkRead(it) },
    act = {
        val resetProgress = settingsRepository.resetProgressOnMarkUnread.first()
        bookmarkActionsRepository.batchMarkUnread(it, resetProgress)
    }
)

fun MainScreenModel.batchFavourite() = batchAction(
    keep = { !it.isStarred },
    message = { "Added $it bookmark${plural(it)} to favorites" },
    undo = { bookmarkActionsRepository.batchSetFavourite(it, makeFavourite = false) },
    act = { bookmarkActionsRepository.batchSetFavourite(it, makeFavourite = true) }
)

fun MainScreenModel.batchUnfavourite() = batchAction(
    keep = { it.isStarred },
    message = { "Removed $it bookmark${plural(it)} from favorites" },
    undo = { bookmarkActionsRepository.batchSetFavourite(it, makeFavourite = true) },
    act = { bookmarkActionsRepository.batchSetFavourite(it, makeFavourite = false) }
)

fun MainScreenModel.batchDelete() {
    viewModelScope.launch {
        val bookmarks = getSelectedBookmarks()
        if (bookmarks.isEmpty()) {
            clearSelection()
            return@launch
        }
        bookmarkActionsRepository.batchDelete(bookmarks)
        clearSelection()
    }
}

fun MainScreenModel.batchSetTags(newTags: List<String>) = batchAction(
    message = { "Tags updated for $it bookmark${plural(it)}" },
    act = { bookmarkActionsRepository.batchUpdateTags(it, newTags) }
)

fun MainScreenModel.batchMoveToList(listId: String) = batchAction(
    message = { "Moved $it bookmark${plural(it)} to list" },
    undo = { moved -> moved.forEach { restoreAndRemoveBookmarkFromList(it, listId) } },
    act = { moved ->
        bookmarkActionsRepository.batchMoveToList(moved, listId)
        moved.firstOrNull()?.let { reconcileBookmarkLists(it) }
    }
)

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
    if (aiBatchJob?.isActive == true) return

    aiBatchJob = viewModelScope.launch {
        val bookmarks = getSelectedBookmarks()
        if (bookmarks.isEmpty()) {
            clearSelection()
            return@launch
        }
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
