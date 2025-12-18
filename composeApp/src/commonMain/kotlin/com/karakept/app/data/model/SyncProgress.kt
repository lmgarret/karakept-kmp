package com.karakept.app.data.model

sealed class SyncProgress {
    data object Idle : SyncProgress()
    data object Starting : SyncProgress()
    data class FetchingMetadata(val page: Int, val bookmarksCount: Int) : SyncProgress()
    data object ProcessingMetadata : SyncProgress()
    data class FetchingContent(val current: Int, val total: Int) : SyncProgress()
    data class SyncComplete(val newBookmarksCount: Int) : SyncProgress()
    data class Error(val message: String) : SyncProgress()
}
