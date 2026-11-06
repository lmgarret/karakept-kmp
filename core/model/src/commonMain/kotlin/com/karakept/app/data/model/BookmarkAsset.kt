package com.karakept.app.data.model

import androidx.compose.runtime.Immutable

/** A file the server holds for a bookmark — archive, banner, screenshot, PDF — and its local copy. */
@Immutable
data class BookmarkAsset(
    val id: String,
    val bookmarkRemoteId: String,
    val serverId: String,
    val assetType: String,
    val fileName: String?,
    val contentType: String?,
    val localPath: String? // Path to the downloaded file
)
