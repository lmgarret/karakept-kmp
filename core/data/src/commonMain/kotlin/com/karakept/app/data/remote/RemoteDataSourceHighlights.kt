package com.karakept.app.data.remote

import com.karakept.api.model.*
import com.karakept.app.data.model.Server

/**
 * Get all highlights
 * GET /api/v1/highlights
 */
internal suspend fun RemoteApiContext.fetchAllHighlights(server: Server): List<Highlight> = guardedCall {
    try {
        val allHighlights = mutableListOf<Highlight>()
        var cursor: String? = null
        do {
            val page = highlightsApi(server).highlightsGet(limit = 100.0, cursor = cursor).checkedBody()
            allHighlights.addAll(page.highlights ?: emptyList())
            cursor = page.nextCursor
        } while (cursor != null)
        allHighlights
    } catch (e: Exception) {
        throw ApiException("Error fetching all highlights: ${e.message}", e)
    }
}

/**
 * Get highlights of a bookmark
 * GET /api/v1/bookmarks/:bookmarkId/highlights
 */
internal suspend fun RemoteApiContext.fetchHighlightsForBookmark(server: Server, bookmarkId: String): List<Highlight> = guardedCall {
    try {
        bookmarksApi(server).bookmarksBookmarkIdHighlightsGet(bookmarkId).checkedBody().highlights ?: emptyList()
    } catch (e: Exception) {
        throw ApiException("Error fetching highlights for bookmark $bookmarkId: ${e.message}", e)
    }
}

/**
 * Create a new highlight
 * POST /api/v1/highlights
 */
internal suspend fun RemoteApiContext.createHighlight(
    server: Server,
    bookmarkId: String,
    text: String,
    startOffset: Int,
    endOffset: Int,
    note: String?,
    color: String?
): Highlight = guardedCall {
    try {
        val colorEnum = when (color?.lowercase()) {
            "red" -> HighlightsPostRequest.Color.RED
            "green" -> HighlightsPostRequest.Color.GREEN
            "blue" -> HighlightsPostRequest.Color.BLUE
            else -> HighlightsPostRequest.Color.YELLOW
        }
        val request = HighlightsPostRequest(
            bookmarkId = bookmarkId,
            text = text,
            startOffset = startOffset.toDouble(),
            endOffset = endOffset.toDouble(),
            note = note ?: "",
            color = colorEnum
        )
        highlightsApi(server).highlightsPost(request).checkedBody()
    } catch (e: Exception) {
        throw ApiException("Error creating highlight: ${e.message}", e)
    }
}

/**
 * Update a highlight
 * PATCH /api/v1/highlights/:highlightId
 */
internal suspend fun RemoteApiContext.updateHighlight(
    server: Server,
    highlightId: String,
    note: String?,
    color: String?
): Highlight = guardedCall {
    try {
        val colorEnum = if (color != null) {
            when (color.lowercase()) {
                "red" -> HighlightsHighlightIdPatchRequest.Color.RED
                "green" -> HighlightsHighlightIdPatchRequest.Color.GREEN
                "blue" -> HighlightsHighlightIdPatchRequest.Color.BLUE
                else -> HighlightsHighlightIdPatchRequest.Color.YELLOW
            }
        } else null
        val request = HighlightsHighlightIdPatchRequest(
            note = note,
            color = colorEnum
        )
        highlightsApi(server).highlightsHighlightIdPatch(highlightId, request).checkedBody()
    } catch (e: Exception) {
        throw ApiException("Error updating highlight: ${e.message}", e)
    }
}

/**
 * Delete a highlight
 * DELETE /api/v1/highlights/:highlightId
 */
internal suspend fun RemoteApiContext.deleteHighlight(server: Server, highlightId: String) = guardedCall {
    try {
        highlightsApi(server).highlightsHighlightIdDelete(highlightId)
    } catch (e: Exception) {
        throw ApiException("Error deleting highlight $highlightId: ${e.message}", e)
    }
}
