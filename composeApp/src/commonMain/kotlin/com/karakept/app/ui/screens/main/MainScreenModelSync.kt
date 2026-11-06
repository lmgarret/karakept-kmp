package com.karakept.app.ui.screens.main

import androidx.lifecycle.viewModelScope
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.SYNC_KEY_ARCHIVED
import com.karakept.app.data.model.SYNC_KEY_FAVORITES
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncKey
import com.karakept.app.data.remote.hasHttpStatus
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Sync triggers for MainScreenModel: manual and startup syncs, the visible-row progress pull,
// and the smart-list refresh run when a view is opened.

/** Maps the current view (list context + filter) to a [SyncKey] for progress tracking. */
internal fun resolveCurrentKey(listId: String?, filter: FilterConfig): SyncKey = when {
    listId != null -> listId
    filter.status == FilterStatus.FAVORITES -> SYNC_KEY_FAVORITES
    filter.status == FilterStatus.ARCHIVED  -> SYNC_KEY_ARCHIVED
    else -> null
}

/**
 * Syncs the list just navigated to when a recent list-membership action may have left its
 * smart-list membership stale (GET /lists/{id}/bookmarks is always fresh), then folds the
 * result into the view in place so the correction lands without the list jumping.
 *
 * Called from the reload observer, so navigating away cancels it before it runs.
 */
internal suspend fun MainScreenModel.refreshStaleSmartList(server: Server, filter: FilterConfig) {
    val listId = _currentListContext.value ?: return
    if (listId !in _smartListsNeedingRefresh.value) return
    _smartListsNeedingRefresh.value -= listId
    try {
        // The list just navigated to, so it takes the same exemption as syncCurrentView.
        bookmarkRepository.syncBookmarksForList(server, listId, isCurrentView = true)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e("MainScreenModel", "Smart list refresh failed for $listId: ${e.message}", e)
    }
}

/**
 * Fills in reading progress for the rows currently on screen.
 *
 * The sync pass covers a bounded slice of the library, which leaves rows further down the
 * list showing nothing until a later pass reaches them. Scrolling to them asks for exactly
 * those, so what the user is looking at is right even when the rotation has not. Only rows
 * whose progress is missing or stale cost a request, so scrolling back and forth is free.
 */
fun MainScreenModel.onBookmarksVisible(remoteIds: List<String>) {
    val server = _selectedServer.value ?: return
    if (remoteIds.isEmpty()) return
    viewModelScope.launch {
        try {
            bookmarkRepository.pullReadingProgressForVisible(server.id, remoteIds)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("MainScreenModel", "Visible-row progress pull failed: ${e.message}")
        }
    }
}

fun MainScreenModel.syncBookmarks() {
    viewModelScope.launch {
        // Capture state BEFORE any suspension so the sync strategy and the
        // post-sync reload always use the same consistent snapshot.
        val capturedListContext = _currentListContext.value
        val capturedFilter = _currentFilter.value
        val capturedEffectiveFilter = effectiveFilterNow()

        val isOffline: Boolean = settingsRepository.offlineMode.first()
        if (isOffline) return@launch

        val server = selectedServer.value ?: return@launch

        try {
            // Step 1: Fetch lists first so the drawer updates immediately.
            listRepository.refreshLists(server)

            // Step 2: Sync the currently-rendered list/filter first so the user
            // sees their bookmarks as soon as possible.
            syncCurrentView(server, capturedListContext, capturedFilter)

            // Step 3: Sync all other named lists concurrently.
            // Each call is independently deduplicated by BookmarkRepository.
            val currentKey = resolveCurrentKey(capturedListContext, capturedFilter)
            syncOtherLists(server, currentKey)

            // Nothing to refresh at either step: every pass writes the rows it fetched,
            // and the list re-reads the pages on screen when the table changes. A bookmark
            // this view has never seen appears as soon as whichever pass returns it commits
            // it, rather than at the next refresh call someone remembered to make.

            // Record completion so the startup auto-sync is throttled next time (#276).
            bookmarkRepository.markAutoSyncCompleted(server.id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Scope cancelled (e.g. screen navigated away) — not a sync error.
            bookmarkRepository.resetSyncProgress()
            throw e
        } catch (e: Exception) {
            AppLogger.e("MainScreenModel", "Sync failed: ${e.message}", e)
            if (e.hasHttpStatus(401)) {
                // Retrying with the same credentials can't succeed — point the user
                // at re-authentication instead (#173).
                snackbarManager.showSnackbar(
                    "Authentication failed — check your API key in server settings",
                    androidx.compose.material3.SnackbarDuration.Long
                )
            } else {
                snackbarManager.showErrorWithRetry("Couldn't sync bookmarks") {
                    syncBookmarks()
                }
            }
        }
    }
}

/**
 * Dispatches the correct repository call for the currently-viewed list or filter.
 *
 * `isCurrentView` exempts this pass from the reading-progress ration, which is per server:
 * a list opened moments after another list's pass took the slot would otherwise skip its
 * own, leaving the one view the user is actually looking at as the place the counts stay
 * stale until scrolling fetched them row by row.
 */
private suspend fun MainScreenModel.syncCurrentView(
    server: com.karakept.app.data.model.Server,
    listContext: String?,
    filter: FilterConfig
) {
    when {
        listContext != null ->
            bookmarkRepository.syncBookmarksForList(server, listContext, isCurrentView = true)
        filter.status == FilterStatus.FAVORITES ->
            bookmarkRepository.syncFavorites(server, isCurrentView = true)
        filter.status == FilterStatus.ARCHIVED ->
            bookmarkRepository.syncArchived(server, isCurrentView = true)
        else -> bookmarkRepository.syncBookmarks(server)
    }
}

/**
 * Syncs all named lists except [skipKey] concurrently.
 * Failures of individual lists are logged but not re-thrown — they are non-fatal
 * and do not block the user from seeing already-synced data.
 */
private suspend fun MainScreenModel.syncOtherLists(
    server: com.karakept.app.data.model.Server,
    skipKey: SyncKey
) {
    // Bounded, like BookmarkRepository.syncAllWithLists. Unbounded fan-out here put one
    // paginated fetch per list on the wire at once, starving the list the user is
    // actually looking at (and its content downloads) of connections.
    val semaphore = kotlinx.coroutines.sync.Semaphore(3)
    val allLists = listRepository.lists.value
    AppLogger.d(
        "MainScreenModel",
        "syncOtherLists: ${allLists.size} known list(s), skipping key=$skipKey"
    )
    coroutineScope {
        allLists.forEach { list ->
            val key = list.id ?: return@forEach
            if (key == skipKey) return@forEach
            launch {
                semaphore.acquire()
                try {
                    bookmarkRepository.syncBookmarksForList(server, key)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w("MainScreenModel", "Other-list sync failed for list $key: ${e.message}")
                } finally {
                    semaphore.release()
                }
            }
        }
    }
}
