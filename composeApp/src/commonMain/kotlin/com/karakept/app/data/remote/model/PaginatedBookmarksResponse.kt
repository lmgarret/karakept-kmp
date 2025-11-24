package com.karakept.app.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedBookmarksResponse(
    val bookmarks: List<BookmarkDto>,
    val nextCursor: String? = null
)
