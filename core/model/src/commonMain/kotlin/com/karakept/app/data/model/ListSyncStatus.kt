package com.karakept.app.data.model

/** Per-list sync progress, used for drawer indicators and the adaptive top progress bar. */
sealed class ListSyncStatus {
    data object Idle : ListSyncStatus()
    data class FetchingMetadata(val bookmarksCount: Int = 0) : ListSyncStatus()
    data class FetchingContent(val current: Int, val total: Int) : ListSyncStatus()
}

/**
 * Key that identifies which unit of work is being tracked.
 * - `null`                  → "All Bookmarks" / full sync
 * - [SYNC_KEY_FAVORITES]    → Favorites filter
 * - [SYNC_KEY_ARCHIVED]     → Archived filter
 * - any other string        → a named list's remote ID
 */
typealias SyncKey = String?

const val SYNC_KEY_FAVORITES = "__FAVORITES__"
const val SYNC_KEY_ARCHIVED  = "__ARCHIVED__"
