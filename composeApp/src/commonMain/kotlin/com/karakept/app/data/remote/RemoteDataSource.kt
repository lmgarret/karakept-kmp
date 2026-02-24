package com.karakept.app.data.remote

import com.karakept.api.client.*
import com.karakept.api.model.*
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.Server
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Exception thrown when a network request is blocked due to offline mode being enabled.
 */
class OfflineModeException(message: String = "Offline mode is enabled - network request blocked") : Exception(message)

class RemoteDataSource(
    private val client: HttpClient,
    private val offlineModeProvider: (suspend () -> Boolean)? = null
) {
    /**
     * Guard function that blocks execution if offline mode is enabled.
     * Throws OfflineModeException when offline.
     */
    private suspend fun <T> guardedCall(block: suspend () -> T): T {
        if (offlineModeProvider?.invoke() == true) {
            throw OfflineModeException()
        }
        return block()
    }
    private fun getBaseUrl(server: Server): String {
        val base = if (server.url.endsWith("/")) server.url.removeSuffix("/") else server.url
        return if (base.endsWith("/api/v1")) base else "$base/api/v1"
    }

    private fun bookmarksApi(server: Server) = BookmarksApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }

    private fun listsApi(server: Server) = ListsApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }

    private fun tagsApi(server: Server) = TagsApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }

    private fun highlightsApi(server: Server) = HighlightsApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }

    private fun usersApi(server: Server) = UsersApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }
    private fun getAuth(server: Server) = "Bearer ${server.apiKey}"

    suspend fun fetchBookmarks(
        server: Server,
        cursor: String? = null,
        limit: Int = 50,
        includeContent: Boolean = false,
        archived: Boolean? = null,
        favourited: Boolean? = null
    ): PaginatedBookmarks = guardedCall {
        try {
            bookmarksApi(server).bookmarksGet(
                cursor = cursor,
                limit = limit.toDouble(),
                includeContent = includeContent,
                archived = archived,
                favourited = favourited
            ).body()
        } catch (e: Exception) {
            throw ApiException("Error fetching bookmarks: ${e.message}", e)
        }
    }

    suspend fun fetchBookmark(server: Server, bookmarkId: String, includeContent: Boolean = true): Bookmark = guardedCall {
        try {
            bookmarksApi(server).bookmarksBookmarkIdGet(bookmarkId, includeContent).body()
        } catch (e: Exception) {
            throw ApiException("Error fetching bookmark: ${e.message}", e)
        }
    }

    suspend fun fetchLists(server: Server): List<KarakeepList> = guardedCall {
        try {
            val response = listsApi(server).listsGet().body()
            response.lists ?: emptyList()
        } catch (e: Exception) {
            throw ApiException("Error fetching lists: ${e.message}", e)
        }
    }

    suspend fun fetchBookmarksForList(server: Server, listId: String, includeContent: Boolean = false): List<Bookmark> = guardedCall {
        try {
            listsApi(server).listsListIdBookmarksGet(listId, includeContent = includeContent).body().bookmarks ?: emptyList()
        } catch (e: Exception) {
            throw ApiException("Error fetching bookmarks for list $listId: ${e.message}", e)
        }
    }

    suspend fun testConnection(url: String, apiKey: String): Boolean = guardedCall {
        try {
             BookmarksApi(getBaseUrl(Server(id = "", url = url, apiKey = apiKey, label = "")), client).apply {
                 setBearerToken(apiKey)
             }.bookmarksGet(limit = 1.0).body()
             true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadAsset(server: Server, assetId: String): ByteArray = guardedCall {
        try {
            // Reverting to direct Ktor client as the generated assetsApi returns HttpResponse<Unit> (void)
            // and doesn't seem to handle the binary download properly in this version.
            val baseUrl = if (server.url.endsWith("/")) server.url.removeSuffix("/") else server.url
            val url = if (baseUrl.endsWith("/api/v1")) baseUrl else "$baseUrl/api/v1"

            val response: HttpResponse = client.get("$url/assets/$assetId") {
                header("Authorization", getAuth(server))
            }

            if (response.status.isSuccess()) {
                response.body<ByteArray>()
            } else {
                throw ApiException("Failed to download asset: ${response.status}")
            }
        } catch (e: Exception) {
            throw ApiException("Error downloading asset: ${e.message}", e)
        }
    }

    /**
     * Update a bookmark (archive, favourite, etc.)
     * PATCH /api/v1/bookmarks/:bookmarkId
     */
    suspend fun updateBookmark(
        server: Server,
        bookmarkId: String,
        updates: BookmarksBookmarkIdPatchRequest
    ): Bookmark = guardedCall {
        try {
            val api = bookmarksApi(server)
            api.bookmarksBookmarkIdPatch(bookmarkId, updates)
            // Fetch updated bookmark to ensure we have full data (content, tags, assets)
            api.bookmarksBookmarkIdGet(bookmarkId, includeContent = true).body()
        } catch (e: Exception) {
            throw ApiException("Error updating bookmark: ${e.message}", e)
        }
    }

    /**
     * Fetch all bookmarks (without content)
     */
    suspend fun fetchAllBookmarks(server: Server): List<Bookmark> = guardedCall {
        bookmarksApi(server).bookmarksGet(includeContent = false).body().bookmarks ?: emptyList()
    }

    /**
     * Delete a bookmark
     * DELETE /api/v1/bookmarks/:bookmarkId
     */
    suspend fun deleteBookmark(server: Server, bookmarkId: String) = guardedCall {
        try {
            bookmarksApi(server).bookmarksBookmarkIdDelete(bookmarkId)
        } catch (e: Exception) {
            throw ApiException("Error deleting bookmark: ${e.message}", e)
        }
    }

    /**
     * Attach tags to a bookmark
     * POST /api/v1/bookmarks/:bookmarkId/tags
     */
    suspend fun attachTags(
        server: Server,
        bookmarkId: String,
        tags: List<String>
    ): BookmarksBookmarkIdTagsPost200Response = guardedCall {
        try {
            val request = BookmarksBookmarkIdTagsPostRequest(
                tags = tags.map { BookmarksBookmarkIdTagsPostRequestTagsInner(tagName = it) }
            )
            bookmarksApi(server).bookmarksBookmarkIdTagsPost(bookmarkId, request).body()
        } catch (e: Exception) {
            throw ApiException("Error attaching tags: ${e.message}", e)
        }
    }

    /**
     * Detach tags from a bookmark by their IDs
     * DELETE /api/v1/bookmarks/:bookmarkId/tags
     */
    suspend fun detachTags(server: Server, bookmarkId: String, tags: List<String>): BookmarksBookmarkIdTagsDelete200Response = guardedCall {
        try {
            val request = BookmarksBookmarkIdTagsPostRequest(
                tags = tags.map { BookmarksBookmarkIdTagsPostRequestTagsInner(tagId = it) }
            )
            bookmarksApi(server).bookmarksBookmarkIdTagsDelete(bookmarkId, request).body()
        } catch (e: Exception) {
            throw ApiException("Error detaching tags: ${e.message}", e)
        }
    }

    /**
     * Add a bookmark to a list
     * PUT /api/v1/lists/:listId/bookmarks/:bookmarkId
     */
    suspend fun addBookmarkToList(server: Server, listId: String, bookmarkId: String) = guardedCall {
        try {
            listsApi(server).listsListIdBookmarksBookmarkIdPut(listId, bookmarkId)
        } catch (e: Exception) {
            throw ApiException("Error adding bookmark to list: ${e.message}", e)
        }
    }

    /**
     * Create a new bookmark
     * POST /api/v1/bookmarks
     */
    suspend fun createBookmark(server: Server, url: String): Bookmark = guardedCall {
        try {
            val request = BookmarksPostRequest(
                type = BookmarksPostRequest.Type.LINK,
                url = url
            )
            val response = bookmarksApi(server).bookmarksPost(request)
            if (!response.success) {
                val errorBody = response.response.bodyAsText()
                throw ApiException("Bookmark creation failed with status ${response.status}: $errorBody")
            }
            response.body() ?: throw ApiException("Empty success response from server")
        } catch (e: Exception) {
            throw ApiException("Error creating bookmark: ${e.message}", e)
        }
    }

    /**
     * Remove a bookmark from a list
     * DELETE /api/v1/lists/:listId/bookmarks/:bookmarkId
     */
    suspend fun removeBookmarkFromList(server: Server, listId: String, bookmarkId: String) = guardedCall {
        try {
            listsApi(server).listsListIdBookmarksBookmarkIdDelete(listId, bookmarkId)
        } catch (e: Exception) {
            throw ApiException("Error removing bookmark from list: ${e.message}", e)
        }
    }

    /**
     * Get all highlights
     * GET /api/v1/highlights
     */
    suspend fun fetchAllHighlights(server: Server): List<Highlight> = guardedCall {
        try {
            highlightsApi(server).highlightsGet(limit = 100.0).body().highlights ?: emptyList()
        } catch (e: Exception) {
            throw ApiException("Error fetching all highlights: ${e.message}", e)
        }
    }

    /**
     * Get highlights of a bookmark
     * GET /api/v1/bookmarks/:bookmarkId/highlights
     */
    suspend fun fetchHighlightsForBookmark(server: Server, bookmarkId: String): List<Highlight> = guardedCall {
        try {
            bookmarksApi(server).bookmarksBookmarkIdHighlightsGet(bookmarkId).body().highlights ?: emptyList()
        } catch (e: Exception) {
            throw ApiException("Error fetching highlights for bookmark $bookmarkId: ${e.message}", e)
        }
    }

    /**
     * Create a new highlight
     * POST /api/v1/highlights
     */
    suspend fun createHighlight(
        server: Server,
        bookmarkId: String,
        text: String,
        startOffset: Int,
        endOffset: Int,
        note: String? = null,
        color: String? = null
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
            highlightsApi(server).highlightsPost(request).body()
        } catch (e: Exception) {
            throw ApiException("Error creating highlight: ${e.message}", e)
        }
    }

    /**
     * Update a highlight
     * PATCH /api/v1/highlights/:highlightId
     */
    suspend fun updateHighlight(
        server: Server,
        highlightId: String,
        note: String? = null,
        color: String? = null
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
            highlightsApi(server).highlightsHighlightIdPatch(highlightId, request).body()
        } catch (e: Exception) {
            throw ApiException("Error updating highlight: ${e.message}", e)
        }
    }

    /**
     * Trigger a server-side recrawl of a bookmark.
     * POST /api/v1/bookmarks/:bookmarkId/recrawl
     *
     * Note: this endpoint is not in the generated OpenAPI client, so it uses a direct HTTP call.
     */
    suspend fun recrawlBookmark(server: Server, bookmarkId: String) = guardedCall {
        try {
            val baseUrl = getBaseUrl(server)
            val response: HttpResponse = client.post("$baseUrl/bookmarks/$bookmarkId/recrawl") {
                header("Authorization", getAuth(server))
            }
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw ApiException("Failed to trigger recrawl: ${response.status} - $errorBody")
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Error triggering recrawl: ${e.message}", e)
        }
    }

    /**
     * Delete a highlight
     * DELETE /api/v1/highlights/:highlightId
     */
    suspend fun deleteHighlight(server: Server, highlightId: String) = guardedCall {
        try {
            highlightsApi(server).highlightsHighlightIdDelete(highlightId)
        } catch (e: Exception) {
            throw ApiException("Error deleting highlight $highlightId: ${e.message}", e)
        }
    }
}

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
