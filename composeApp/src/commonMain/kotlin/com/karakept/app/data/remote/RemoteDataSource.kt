package com.karakept.app.data.remote

import com.karakept.api.client.*
import com.karakept.api.model.*
import com.karakept.api.model.KarakeepList
import com.karakept.api.infrastructure.ApiClient
import com.karakept.app.data.model.Server
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.client.request.headers

class RemoteDataSource(
    private val client: HttpClient
) {
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
    ): PaginatedBookmarks {
        return try {
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

    suspend fun fetchBookmark(server: Server, bookmarkId: String, includeContent: Boolean = true): Bookmark {
        return try {
            bookmarksApi(server).bookmarksBookmarkIdGet(bookmarkId, includeContent).body()
        } catch (e: Exception) {
            throw ApiException("Error fetching bookmark: ${e.message}", e)
        }
    }

    suspend fun fetchLists(server: Server): List<KarakeepList> {
        return try {
            val response = listsApi(server).listsGet().body()
            response.lists ?: emptyList()
        } catch (e: Exception) {
            throw ApiException("Error fetching lists: ${e.message}", e)
        }
    }

    suspend fun fetchBookmarksForList(server: Server, listId: String, includeContent: Boolean = false): List<Bookmark> {
        return try {
            listsApi(server).listsListIdBookmarksGet(listId, includeContent = includeContent).body().bookmarks ?: emptyList()
        } catch (e: Exception) {
            throw ApiException("Error fetching bookmarks for list $listId: ${e.message}", e)
        }
    }

    suspend fun testConnection(url: String, apiKey: String): Boolean {
        return try {
             BookmarksApi(getBaseUrl(Server(id = "", url = url, apiKey = apiKey, label = "")), client).apply {
                 setBearerToken(apiKey)
             }.bookmarksGet(limit = 1.0)
             true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadAsset(server: Server, assetId: String): ByteArray {
        return try {
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
    ): Bookmark {
        return try {
            val api = bookmarksApi(server)
            api.bookmarksBookmarkIdPatch(bookmarkId, updates)
            // Fetch updated bookmark to ensure we have full data (content, tags, assets)
            api.bookmarksBookmarkIdGet(bookmarkId, includeContent = true).body()
        } catch (e: Exception) {
            throw ApiException("Error updating bookmark: ${e.message}", e)
        }
    }

    /**
     * Delete a bookmark
     * DELETE /api/v1/bookmarks/:bookmarkId
     */
    suspend fun fetchAllBookmarks(server: Server): List<Bookmark> {
        return bookmarksApi(server).bookmarksGet(includeContent = false).body().bookmarks ?: emptyList()
    }

    suspend fun deleteBookmark(server: Server, bookmarkId: String) {
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
    ): BookmarksBookmarkIdTagsPost200Response {
        return try {
            val request = BookmarksBookmarkIdTagsPostRequest(
                tags = tags.map { BookmarksBookmarkIdTagsPostRequestTagsInner(tagName = it) }
            )
            bookmarksApi(server).bookmarksBookmarkIdTagsPost(bookmarkId, request).body()
        } catch (e: Exception) {
            throw ApiException("Error attaching tags: ${e.message}", e)
        }
    }

    /**
     * Detach a tag from a bookmark
     * DELETE /api/v1/bookmarks/:bookmarkId/tags
     */
    suspend fun detachTags(server: Server, bookmarkId: String, tags: List<String>) {
        try {
            // Treat tags as names if they don't look like UUIDs, but for safety with this API, 
            // we can just send them as tagId if they are from the repository's tagId cache.
            // Actually, the easiest is to just send them as tagId since the repository passes IDs here.
            val request = BookmarksBookmarkIdTagsPostRequest(
                tags = tags.map { BookmarksBookmarkIdTagsPostRequestTagsInner(tagId = it) }
            )
            bookmarksApi(server).bookmarksBookmarkIdTagsDelete(bookmarkId, request)
        } catch (e: Exception) {
            throw ApiException("Error detaching tags: ${e.message}", e)
        }
    }

    /**
     * Add a bookmark to a list
     * PUT /api/v1/lists/:listId/bookmarks/:bookmarkId
     */
    suspend fun addBookmarkToList(server: Server, listId: String, bookmarkId: String) {
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
    suspend fun createBookmark(server: Server, url: String): Bookmark {
        return try {
            val request = BookmarksPostRequest(
                type = BookmarksPostRequest.Type.ASSET,
                url = url,
                text = "", 
                assetType = BookmarksPostRequest.AssetType.IMAGE,
                assetId = ""
            )
            bookmarksApi(server).bookmarksPost(request).body()
        } catch (e: Exception) {
            throw ApiException("Error creating bookmark: ${e.message}", e)
        }
    }

    /**
     * Remove a bookmark from a list
     * DELETE /api/v1/lists/:listId/bookmarks/:bookmarkId
     */
    suspend fun removeBookmarkFromList(server: Server, listId: String, bookmarkId: String) {
        try {
            listsApi(server).listsListIdBookmarksBookmarkIdDelete(listId, bookmarkId)
        } catch (e: Exception) {
            throw ApiException("Error removing bookmark from list: ${e.message}", e)
        }
    }
}

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
