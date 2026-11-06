package com.karakept.app.data.remote

import com.karakept.api.client.*
import com.karakept.api.model.*
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.Server
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal suspend fun RemoteApiContext.fetchBookmarks(
    server: Server,
    cursor: String?,
    limit: Int,
    includeContent: Boolean,
    archived: Boolean?,
    favourited: Boolean?
): PaginatedBookmarks = guardedCall {
    try {
        bookmarksApi(server).bookmarksGet(
            cursor = cursor,
            limit = limit.toDouble(),
            includeContent = includeContent,
            archived = archived,
            favourited = favourited
        ).checkedBody()
    } catch (e: Exception) {
        throw ApiException("Error fetching bookmarks: ${e.message}", e)
    }
}

internal suspend fun RemoteApiContext.fetchBookmark(server: Server, bookmarkId: String, includeContent: Boolean): Bookmark = guardedCall {
    try {
        bookmarksApi(server).bookmarksBookmarkIdGet(bookmarkId, includeContent).checkedBody()
    } catch (e: Exception) {
        throw ApiException("Error fetching bookmark: ${e.message}", e)
    }
}

internal suspend fun RemoteApiContext.fetchListsForBookmark(server: Server, bookmarkId: String): List<com.karakept.api.model.KarakeepList> = guardedCall {
    try {
        bookmarksApi(server).bookmarksBookmarkIdListsGet(bookmarkId).checkedBody().lists ?: emptyList()
    } catch (e: Exception) {
        throw ApiException("Error fetching lists for bookmark $bookmarkId: ${e.message}", e)
    }
}

internal suspend fun RemoteApiContext.fetchLists(server: Server): List<KarakeepList> = guardedCall {
    try {
        val response = listsApi(server).listsGet().checkedBody()
        response.lists ?: emptyList()
    } catch (e: Exception) {
        throw ApiException("Error fetching lists: ${e.message}", e)
    }
}

internal suspend fun RemoteApiContext.fetchBookmarksForList(server: Server, listId: String, includeContent: Boolean): List<Bookmark> = guardedCall {
    try {
        val allBookmarks = mutableListOf<Bookmark>()
        var cursor: String? = null
        do {
            val response = listsApi(server).listsListIdBookmarksGet(
                listId,
                includeContent = includeContent,
                cursor = cursor
            ).checkedBody()
            allBookmarks.addAll(response.bookmarks ?: emptyList())
            cursor = response.nextCursor
        } while (cursor != null)
        allBookmarks
    } catch (e: Exception) {
        throw ApiException("Error fetching bookmarks for list $listId: ${e.message}", e)
    }
}

internal suspend fun RemoteApiContext.testConnection(url: String, apiKey: String): Boolean = guardedCall {
    try {
         BookmarksApi(getBaseUrl(Server(id = "", url = url, apiKey = apiKey, label = "")), client).apply {
             setBearerToken(apiKey)
         }.bookmarksGet(limit = 1.0).checkedBody()
         true
    } catch (e: Exception) {
        false
    }
}

/**
 * The version string [url]'s server reports from `GET /api/version`, which needs no
 * authentication. Null when the route does not exist — the server predates it. Any other
 * failure throws, so a caller can leave the version unknown rather than call it outdated.
 */
internal suspend fun RemoteApiContext.fetchServerVersion(url: String): String? = guardedCall {
    val base = getTrpcBaseUrl(Server(id = "", url = url, apiKey = "", label = ""))
    val response: HttpResponse = try {
        client.get("$base/api/version")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ApiException("Error fetching server version: ${e.message}", e)
    }
    when {
        response.status.value == 404 -> null
        !response.status.isSuccess() ->
            throw ApiException("Error fetching server version: ${response.status}", statusCode = response.status.value)
        else -> try {
            trpcJson.parseToJsonElement(response.bodyAsText())
                .jsonObject["version"]?.jsonPrimitive?.content
                ?: throw ApiException("Server version response has no version field")
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Unreadable server version response: ${e.message}", e)
        }
    }
}

/**
 * Downloads an asset's bytes.
 *
 * [onProgress] is invoked with a 0f..1f fraction as bytes arrive, or with null when the
 * response carries no Content-Length and the fraction can't be known — callers should
 * show an indeterminate indicator in that case.
 */
internal suspend fun RemoteApiContext.downloadAsset(
    server: Server,
    assetId: String,
    onProgress: ((Float?) -> Unit)?
): ByteArray = guardedCall {
    try {
        // Reverting to direct Ktor client as the generated assetsApi returns HttpResponse<Unit> (void)
        // and doesn't seem to handle the binary download properly in this version.
        val baseUrl = if (server.url.endsWith("/")) server.url.removeSuffix("/") else server.url
        val url = if (baseUrl.endsWith("/api/v1")) baseUrl else "$baseUrl/api/v1"

        val response: HttpResponse = client.get("$url/assets/$assetId") {
            header("Authorization", getAuth(server))
            // Full-page archives can be large — allow more than the default budget.
            timeout {
                requestTimeoutMillis = ASSET_REQUEST_TIMEOUT_MS
                socketTimeoutMillis = ASSET_SOCKET_TIMEOUT_MS
            }
            if (onProgress != null) {
                onDownload { bytesSentTotal, contentLength ->
                    // contentLength is null for chunked responses — report indeterminate
                    // rather than inventing a fraction.
                    onProgress(
                        if (contentLength != null && contentLength > 0) {
                            (bytesSentTotal.toFloat() / contentLength).coerceIn(0f, 1f)
                        } else null
                    )
                }
            }
        }

        if (response.status.isSuccess()) {
            response.body<ByteArray>()
        } else {
            throw ApiException("Failed to download asset: ${response.status}", statusCode = response.status.value)
        }
    } catch (e: Exception) {
        throw ApiException("Error downloading asset: ${e.message}", e)
    }
}

/**
 * Update a bookmark (archive, favourite, etc.)
 * PATCH /api/v1/bookmarks/:bookmarkId
 */
internal suspend fun RemoteApiContext.updateBookmark(
    server: Server,
    bookmarkId: String,
    updates: BookmarksBookmarkIdPatchRequest
): Bookmark = guardedCall {
    try {
        val api = bookmarksApi(server)
        api.bookmarksBookmarkIdPatch(bookmarkId, updates)
        // Fetch updated bookmark to ensure we have full data (content, tags, assets)
        api.bookmarksBookmarkIdGet(bookmarkId, includeContent = true).checkedBody()
    } catch (e: Exception) {
        throw ApiException("Error updating bookmark: ${e.message}", e)
    }
}

/**
 * Fetch all bookmarks (without content)
 */
internal suspend fun RemoteApiContext.fetchAllBookmarks(server: Server): List<Bookmark> = guardedCall {
    bookmarksApi(server).bookmarksGet(includeContent = false).checkedBody().bookmarks ?: emptyList()
}

/**
 * Delete a bookmark
 * DELETE /api/v1/bookmarks/:bookmarkId
 */
internal suspend fun RemoteApiContext.deleteBookmark(server: Server, bookmarkId: String) = guardedCall {
    try {
        bookmarksApi(server).bookmarksBookmarkIdDelete(bookmarkId)
    } catch (e: Exception) {
        throw ApiException("Error deleting bookmark: ${e.message}", e)
    }
}

/**
 * Detach an asset from a bookmark, deleting it on the server.
 * DELETE /api/v1/bookmarks/:bookmarkId/assets/:assetId
 */
internal suspend fun RemoteApiContext.detachAsset(server: Server, bookmarkId: String, assetId: String) = guardedCall {
    try {
        val response = bookmarksApi(server).bookmarksBookmarkIdAssetsAssetIdDelete(bookmarkId, assetId)
        // 204 No Content, so there is no body to decode — check the status directly rather
        // than going through checkedBody(). A silent failure here would wrongly tell the
        // user their server-side copy is gone.
        if (!response.success) {
            throw ApiException("HTTP ${response.status}")
        }
    } catch (e: Exception) {
        throw ApiException("Error deleting asset on server: ${e.message}", e)
    }
}

/**
 * Attach tags to a bookmark
 * POST /api/v1/bookmarks/:bookmarkId/tags
 */
internal suspend fun RemoteApiContext.attachTags(
    server: Server,
    bookmarkId: String,
    tags: List<String>
): BookmarksBookmarkIdTagsPost200Response = guardedCall {
    try {
        val request = BookmarksBookmarkIdTagsPostRequest(
            tags = tags.map { BookmarksBookmarkIdTagsPostRequestTagsInner(tagName = it) }
        )
        bookmarksApi(server).bookmarksBookmarkIdTagsPost(bookmarkId, request).checkedBody()
    } catch (e: Exception) {
        throw ApiException("Error attaching tags: ${e.message}", e)
    }
}

/**
 * Detach tags from a bookmark by their IDs
 * DELETE /api/v1/bookmarks/:bookmarkId/tags
 */
internal suspend fun RemoteApiContext.detachTags(server: Server, bookmarkId: String, tags: List<String>): BookmarksBookmarkIdTagsDelete200Response = guardedCall {
    try {
        val request = BookmarksBookmarkIdTagsPostRequest(
            tags = tags.map { BookmarksBookmarkIdTagsPostRequestTagsInner(tagId = it) }
        )
        bookmarksApi(server).bookmarksBookmarkIdTagsDelete(bookmarkId, request).checkedBody()
    } catch (e: Exception) {
        throw ApiException("Error detaching tags: ${e.message}", e)
    }
}

/**
 * Add a bookmark to a list
 * PUT /api/v1/lists/:listId/bookmarks/:bookmarkId
 */
internal suspend fun RemoteApiContext.addBookmarkToList(server: Server, listId: String, bookmarkId: String) = guardedCall {
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
internal suspend fun RemoteApiContext.createBookmark(server: Server, url: String): Bookmark = guardedCall {
    try {
        val request = BookmarksPostRequest(
            type = BookmarksPostRequest.Type.LINK,
            url = url
        )
        val response = bookmarksApi(server).bookmarksPost(request)
        if (!response.success) {
            val errorBody = response.response.bodyAsText()
            throw ApiException("Bookmark creation failed with status ${response.status}: $errorBody", statusCode = response.status)
        }
        response.body()
    } catch (e: Exception) {
        throw ApiException("Error creating bookmark: ${e.message}", e)
    }
}

/**
 * Remove a bookmark from a list
 * DELETE /api/v1/lists/:listId/bookmarks/:bookmarkId
 */
internal suspend fun RemoteApiContext.removeBookmarkFromList(server: Server, listId: String, bookmarkId: String) = guardedCall {
    try {
        listsApi(server).listsListIdBookmarksBookmarkIdDelete(listId, bookmarkId)
    } catch (e: Exception) {
        throw ApiException("Error removing bookmark from list: ${e.message}", e)
    }
}

/**
 * Update a list's name and/or icon
 * PATCH /api/v1/lists/:listId
 */
internal suspend fun RemoteApiContext.updateList(
    server: Server,
    listId: String,
    name: String,
    icon: String?
): KarakeepList = guardedCall {
    try {
        val request = ListsListIdPatchRequest(name = name, icon = icon)
        listsApi(server).listsListIdPatch(listId, request).checkedBody()
    } catch (e: Exception) {
        throw ApiException("Error updating list: ${e.message}", e)
    }
}
