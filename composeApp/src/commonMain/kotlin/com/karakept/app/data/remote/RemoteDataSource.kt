package com.karakept.app.data.remote

import com.karakept.api.client.*
import com.karakept.api.model.*
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.Server
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.OidcSignInUtils
import com.karakept.app.utils.TrpcPayloadUtils
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.karakept.api.infrastructure.HttpResponse as ApiHttpResponse

/**
 * Exception thrown when a network request is blocked due to offline mode being enabled.
 */
class OfflineModeException(message: String = "Offline mode is enabled - network request blocked") : Exception(message)

/**
 * Thrown when the server does not expose a tRPC route Karakept relies on. tRPC is Karakeep's
 * internal API, so routes can disappear or be renamed between versions — callers should report
 * this as "your server doesn't support this" rather than as a transient failure worth retrying.
 */
class UnsupportedServerActionException(message: String) : Exception(message)

/**
 * Karakeep's wording when reading progress is pushed for a bookmark that is not a link.
 * The only push rejection that is not worth retrying.
 */
private const val NON_LINK_PROGRESS_ERROR = "reading progress can only be saved"

/** Karakeep's wording when the instance has no LLM wired up at all. */
private const val NO_INFERENCE_CLIENT_ERROR = "no inference client configured"

/** tRPC's wording for an unknown procedure — a 404 that is not a missing bookmark. */
private const val UNKNOWN_TRPC_PROCEDURE_ERROR = "No procedure found"

/**
 * Bookmarks per batched reading-progress request. Conservative on purpose: the batch travels
 * in the query string and a default nginx refuses a request line much past 8KB, which is well
 * under what Karakeep's own web client allows itself. Twenty ids is ~2KB encoded and still
 * turns a thousand-bookmark pass from a thousand requests into fifty.
 */
private const val READING_PROGRESS_BATCH_SIZE = 20

/**
 * Batched progress requests in flight at once. A pass covers hundreds of bookmarks; issued one
 * after another the round trips are the whole cost of it, and this is the same ceiling the
 * per-bookmark pull used before batching.
 */
private const val PROGRESS_REQUEST_CONCURRENCY = 5

/** "URI too long" and "request header fields too large" — the proxy, not the server. */
private val URL_TOO_LONG_STATUSES = setOf(414, 431)

/**
 * Extension that checks the HTTP status of a generated API response before deserializing.
 * If the response is non-2xx, throws [ApiException] with the status code and response body,
 * preventing cryptic [io.ktor.client.call.NoTransformationFoundException] errors when the
 * server returns a non-JSON error response (e.g. 401 text/plain).
 */
private suspend fun <T : Any> ApiHttpResponse<T>.checkedBody(): T {
    if (!success) {
        val errorBody = try { response.bodyAsText() } catch (_: Exception) { "(unreadable)" }
        throw ApiException("HTTP $status: $errorBody", statusCode = status)
    }
    return body()
}

class RemoteDataSource(
    private val client: HttpClient,
    private val offlineModeProvider: (suspend () -> Boolean)? = null
) {
    /**
     * Guard function that blocks execution when the user has enabled offline mode.
     * Throws OfflineModeException when offline.
     */
    private suspend fun <T> guardedCall(block: suspend () -> T): T {
        if (offlineModeProvider?.invoke() == true) {
            throw OfflineModeException()
        }
        return block()
    }

    private val trpcJson = Json { ignoreUnknownKeys = true }

    private fun getBaseUrl(server: Server): String {
        val base = if (server.url.endsWith("/")) server.url.removeSuffix("/") else server.url
        return if (base.endsWith("/api/v1")) base else "$base/api/v1"
    }

    private fun getTrpcBaseUrl(server: Server): String {
        val base = if (server.url.endsWith("/")) server.url.removeSuffix("/") else server.url
        return if (base.endsWith("/api/v1")) base.removeSuffix("/api/v1") else base
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
            ).checkedBody()
        } catch (e: Exception) {
            throw ApiException("Error fetching bookmarks: ${e.message}", e)
        }
    }

    suspend fun fetchBookmark(server: Server, bookmarkId: String, includeContent: Boolean = true): Bookmark = guardedCall {
        try {
            bookmarksApi(server).bookmarksBookmarkIdGet(bookmarkId, includeContent).checkedBody()
        } catch (e: Exception) {
            throw ApiException("Error fetching bookmark: ${e.message}", e)
        }
    }

    suspend fun fetchListsForBookmark(server: Server, bookmarkId: String): List<com.karakept.api.model.KarakeepList> = guardedCall {
        try {
            bookmarksApi(server).bookmarksBookmarkIdListsGet(bookmarkId).checkedBody().lists ?: emptyList()
        } catch (e: Exception) {
            throw ApiException("Error fetching lists for bookmark $bookmarkId: ${e.message}", e)
        }
    }

    suspend fun fetchLists(server: Server): List<KarakeepList> = guardedCall {
        try {
            val response = listsApi(server).listsGet().checkedBody()
            response.lists ?: emptyList()
        } catch (e: Exception) {
            throw ApiException("Error fetching lists: ${e.message}", e)
        }
    }

    suspend fun fetchBookmarksForList(server: Server, listId: String, includeContent: Boolean = false): List<Bookmark> = guardedCall {
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

    suspend fun testConnection(url: String, apiKey: String): Boolean = guardedCall {
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
     * Mint an API key for whoever owns a web session — how SSO/OIDC sign-in ends up with a key,
     * since Karakeep's REST API has no token exchange of its own. [cookieHeader] is the web
     * session's cookies as a `Cookie` header, read out of the WebView the user signed in with.
     *
     * tRPC mutation: apiKeys.create
     * POST /api/trpc/apiKeys.create?batch=1
     *
     * @throws ApiException with [ApiException.statusCode] 401 when the session is not signed in.
     */
    suspend fun createApiKeyFromSession(
        serverUrl: String,
        cookieHeader: String,
        keyName: String = OidcSignInUtils.API_KEY_NAME
    ): String = guardedCall {
        val url = "${OidcSignInUtils.webBaseUrl(serverUrl)}/api/trpc/apiKeys.create?batch=1"
        val response: HttpResponse = try {
            client.post(url) {
                header(HttpHeaders.Cookie, cookieHeader)
                contentType(ContentType.Application.Json)
                setBody(TrpcPayloadUtils.createApiKey(keyName))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Error creating API key: ${e.message}", e)
        }

        if (!response.status.isSuccess()) {
            val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
            throw ApiException(
                "Error creating API key: HTTP ${response.status.value}: $errorBody",
                statusCode = response.status.value
            )
        }

        val key = try {
            trpcJson.parseToJsonElement(response.bodyAsText()).jsonArray
                .firstOrNull()
                ?.jsonObject?.get("result")
                ?.jsonObject?.get("data")
                ?.jsonObject?.get("json")
                ?.jsonObject?.get("key")
                ?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            throw ApiException("Error parsing API key response: ${e.message}", e)
        }
        key?.takeIf { it.isNotBlank() } ?: throw ApiException("Server returned no API key")
    }

    /**
     * Downloads an asset's bytes.
     *
     * [onProgress] is invoked with a 0f..1f fraction as bytes arrive, or with null when the
     * response carries no Content-Length and the fraction can't be known — callers should
     * show an indeterminate indicator in that case.
     */
    suspend fun downloadAsset(
        server: Server,
        assetId: String,
        onProgress: ((Float?) -> Unit)? = null
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
    suspend fun updateBookmark(
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
    suspend fun fetchAllBookmarks(server: Server): List<Bookmark> = guardedCall {
        bookmarksApi(server).bookmarksGet(includeContent = false).checkedBody().bookmarks ?: emptyList()
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
     * Detach an asset from a bookmark, deleting it on the server.
     * DELETE /api/v1/bookmarks/:bookmarkId/assets/:assetId
     */
    suspend fun detachAsset(server: Server, bookmarkId: String, assetId: String) = guardedCall {
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
    suspend fun attachTags(
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
    suspend fun detachTags(server: Server, bookmarkId: String, tags: List<String>): BookmarksBookmarkIdTagsDelete200Response = guardedCall {
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
    suspend fun removeBookmarkFromList(server: Server, listId: String, bookmarkId: String) = guardedCall {
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
    suspend fun updateList(
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

    /**
     * Get all highlights
     * GET /api/v1/highlights
     */
    suspend fun fetchAllHighlights(server: Server): List<Highlight> = guardedCall {
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
    suspend fun fetchHighlightsForBookmark(server: Server, bookmarkId: String): List<Highlight> = guardedCall {
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
            highlightsApi(server).highlightsPost(request).checkedBody()
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
            highlightsApi(server).highlightsHighlightIdPatch(highlightId, request).checkedBody()
        } catch (e: Exception) {
            throw ApiException("Error updating highlight: ${e.message}", e)
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

    /**
     * Push reading progress to server via tRPC.
     *
     * Returns true when the server stored the value, false only for the one rejection that
     * can never succeed on retry: Karakeep keeps reading progress for LINK bookmarks alone
     * and answers BAD_REQUEST for every other type. Any other non-2xx throws, so the pending
     * action survives for a retry instead of being dropped as if it had synced — a rejected
     * push used to leave the progress local forever with nothing to show for it.
     *
     * tRPC mutation: bookmarks.updateReadingProgress
     * POST /api/trpc/bookmarks.updateReadingProgress?batch=1
     */
    suspend fun updateReadingProgress(
        server: Server,
        bookmarkId: String,
        progressPercent: Int
    ): Boolean = guardedCall {
        val response: HttpResponse = try {
            val trpcBase = getTrpcBaseUrl(server)
            val url = "$trpcBase/api/trpc/bookmarks.updateReadingProgress?batch=1"
            val body = TrpcPayloadUtils.updateReadingProgress(bookmarkId, progressPercent)
            client.post(url) {
                header("Authorization", getAuth(server))
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Error updating reading progress: ${e.message}", e)
        }

        if (response.status.isSuccess()) return@guardedCall true

        val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
        if (response.status.value == 400 && errorBody.contains(NON_LINK_PROGRESS_ERROR, ignoreCase = true)) {
            AppLogger.d(
                "RemoteDataSource",
                "Server does not keep reading progress for bookmark $bookmarkId (not a link)"
            )
            return@guardedCall false
        }

        throw ApiException(
            "Error updating reading progress: HTTP ${response.status.value}: $errorBody",
            statusCode = response.status.value
        )
    }

    /**
     * Fetch reading progress for several bookmarks, in as few requests as the URL budget allows.
     *
     * Returns the progress percent per bookmark id. A `null` entry means the server holds no
     * progress for that bookmark; an id **missing** from the map was never answered — a chunk
     * that failed — and must be treated as "we never found out", not as "nothing stored", so
     * the caller holds its cursor rather than marking the row as asked.
     *
     * The batch travels in the query string, and how long a URL a deployment accepts is the
     * reverse proxy's business rather than the server's — Karakeep's own client caps itself at
     * 14000 characters, while a default nginx rejects rather less. [chunkSize] therefore starts
     * small and halves on the two statuses that mean "your request line is too long", so a
     * strict proxy costs a retry rather than the whole feature.
     *
     * Chunks run concurrently up to [PROGRESS_REQUEST_CONCURRENCY]: a pass covers hundreds of
     * bookmarks, and issued one after another the round trips are the whole cost of the pass.
     */
    suspend fun getReadingProgressBatch(
        server: Server,
        bookmarkIds: List<String>,
        chunkSize: Int = READING_PROGRESS_BATCH_SIZE
    ): Map<String, Int?> {
        if (bookmarkIds.isEmpty()) return emptyMap()
        var size = chunkSize.coerceAtLeast(1)
        while (true) {
            val chunks = bookmarkIds.chunked(size)
            val answers = fetchChunksConcurrently(server, chunks)
            // A proxy rejects on the request line alone, so every chunk of this size fails the
            // same way. Halving and starting over costs a round trip and gets the whole pass,
            // where retrying chunk by chunk would pay that cost once per chunk.
            if (answers == null && size > 1) {
                size /= 2
                AppLogger.d("RemoteDataSource", "Progress batch too long, retrying at $size")
                continue
            }
            return answers ?: emptyMap()
        }
    }

    /**
     * Runs [chunks] with bounded concurrency, merging the answers.
     *
     * Returns null when a chunk was refused for being too long — the caller retries smaller.
     * Any other failure omits just that chunk, leaving its ids out of the map: one unreachable
     * chunk must not cost the answers the rest of the pass already paid for.
     */
    private suspend fun fetchChunksConcurrently(
        server: Server,
        chunks: List<List<String>>
    ): Map<String, Int?>? = coroutineScope {
        val semaphore = Semaphore(PROGRESS_REQUEST_CONCURRENCY)
        val outcomes = chunks.map { chunk ->
            async {
                semaphore.withPermit {
                    try {
                        ChunkOutcome(chunk.zip(fetchReadingProgressChunk(server, chunk)).toMap())
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val tooLong = e is ApiException && e.statusCode in URL_TOO_LONG_STATUSES
                        if (!tooLong) {
                            AppLogger.d("RemoteDataSource", "Progress chunk failed: ${e.message}")
                        }
                        ChunkOutcome(emptyMap(), urlTooLong = tooLong)
                    }
                }
            }
        }.awaitAll()
        if (outcomes.any { it.urlTooLong }) null else outcomes.fold(emptyMap()) { acc, o -> acc + o.answers }
    }

    /** One chunk's answers, plus whether it was refused for being too long to send. */
    private data class ChunkOutcome(
        val answers: Map<String, Int?>,
        val urlTooLong: Boolean = false
    )

    /** One batched request. Returns the answers positionally; entries may be null. */
    private suspend fun fetchReadingProgressChunk(
        server: Server,
        bookmarkIds: List<String>
    ): List<Int?> = guardedCall {
        val response: HttpResponse = try {
            val trpcBase = getTrpcBaseUrl(server)
            val path = TrpcPayloadUtils.batchPath("bookmarks.getReadingProgress", bookmarkIds.size)
            client.get("$trpcBase/api/trpc/$path") {
                header("Authorization", getAuth(server))
                parameter("batch", "1")
                parameter("input", TrpcPayloadUtils.getReadingProgressBatch(bookmarkIds))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Error fetching reading progress: ${e.message}", e)
        }

        if (!response.status.isSuccess()) {
            val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
            throw ApiException(
                "Error fetching reading progress: HTTP ${response.status.value}: $errorBody",
                statusCode = response.status.value
            )
        }

        try {
            val entries = trpcJson.parseToJsonElement(response.bodyAsText()).jsonArray
            // A batch answers positionally. An entry that carried an error rather than a
            // result reads as null here, which the caller treats as "not answered".
            bookmarkIds.indices.map { position ->
                entries.getOrNull(position)
                    ?.jsonObject?.get("result")
                    ?.jsonObject?.get("data")
                    ?.jsonObject?.get("json")
                    ?.jsonObject?.get("readingProgressPercent")
                    ?.jsonPrimitive?.intOrNull
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Error parsing reading progress response: ${e.message}", e)
        }
    }

    /**
     * Ask the server to re-crawl a link bookmark. The server enqueues a background job, so
     * success here only means the request was accepted — the resulting metadata and assets
     * appear on a later sync.
     *
     * [archiveFullPage] additionally stores a `fullPageArchive` asset, [storePdf] a `pdf` asset.
     * These map to the "Refresh" / "Preserve offline archive" / "Preserve as PDF" actions in
     * the Karakeep web UI.
     *
     * tRPC mutation: bookmarks.recrawlBookmark
     * POST /api/trpc/bookmarks.recrawlBookmark?batch=1
     *
     * @throws UnsupportedServerActionException if the server has no such tRPC route.
     */
    suspend fun recrawlBookmark(
        server: Server,
        bookmarkId: String,
        archiveFullPage: Boolean = false,
        storePdf: Boolean = false
    ): Unit = guardedCall {
        val trpcBase = getTrpcBaseUrl(server)
        val url = "$trpcBase/api/trpc/bookmarks.recrawlBookmark?batch=1"
        val response: HttpResponse = try {
            client.post(url) {
                header("Authorization", getAuth(server))
                contentType(ContentType.Application.Json)
                setBody(TrpcPayloadUtils.recrawlBookmark(bookmarkId, archiveFullPage, storePdf))
            }
        } catch (e: Exception) {
            throw ApiException("Error requesting recrawl: ${e.message}", e)
        }

        if (response.status.isSuccess()) return@guardedCall

        val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
        // tRPC answers an unknown procedure with 404 "No procedure found on path …". A missing
        // bookmark is also a 404, so match on the message rather than the status alone.
        if (errorBody.contains("No procedure found", ignoreCase = true)) {
            throw UnsupportedServerActionException(
                "This Karakeep server doesn't support re-crawling from the app"
            )
        }
        throw ApiException("HTTP ${response.status}: $errorBody")
    }

    /**
     * Ask the server to generate an AI summary for a bookmark.
     *
     * Unlike the crawl jobs above this is **synchronous** — Karakeep runs the inference inline and
     * answers with the updated record — so the call can block for as long as the model takes.
     *
     * The summary lands in the bookmark's `summary` field, which is separate from `description`:
     * the crawler reads that one from the page's meta tags and the inference worker never writes it.
     *
     * POST /bookmarks/{bookmarkId}/summarize
     *
     * @throws UnsupportedServerActionException if the instance has no inference client configured.
     */
    suspend fun summarizeBookmark(server: Server, bookmarkId: String): SummarizeResult = guardedCall {
        try {
            val dto = bookmarksApi(server).bookmarksBookmarkIdSummarizePost(bookmarkId).checkedBody()
            SummarizeResult(
                summary = dto.summary,
                summarizationStatus = dto.summarizationStatus?.value
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.message?.contains(NO_INFERENCE_CLIENT_ERROR, ignoreCase = true) == true) {
                throw UnsupportedServerActionException(
                    "This Karakeep server has no AI model configured"
                )
            }
            throw ApiException("Error summarizing bookmark: ${e.message}", e)
        }
    }

    /**
     * Ask the server to re-run AI tagging for a bookmark. The server enqueues an inference job,
     * so success here only means the request was accepted — the new tags appear on a later sync.
     *
     * tRPC mutation: admin.adminRetagBookmark
     * POST /api/trpc/admin.adminRetagBookmark?batch=1
     *
     * @throws UnsupportedServerActionException if the route is missing or the key is not an admin's.
     */
    suspend fun requestAiRetag(server: Server, bookmarkId: String): Unit = guardedCall {
        val trpcBase = getTrpcBaseUrl(server)
        val url = "$trpcBase/api/trpc/admin.adminRetagBookmark?batch=1"
        val response: HttpResponse = try {
            client.post(url) {
                header("Authorization", getAuth(server))
                contentType(ContentType.Application.Json)
                setBody(TrpcPayloadUtils.adminRetagBookmark(bookmarkId))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Error requesting AI tagging: ${e.message}", e)
        }

        if (response.status.isSuccess()) return@guardedCall

        val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
        // Two distinct "this server won't do it" cases: the route is absent, or the key belongs to
        // a plain user. Neither is worth retrying, and both read the same to the person asking.
        if (errorBody.contains(UNKNOWN_TRPC_PROCEDURE_ERROR, ignoreCase = true)) {
            throw UnsupportedServerActionException(
                "This Karakeep server doesn't support re-running AI tagging from the app"
            )
        }
        if (response.status.value == 401 || response.status.value == 403) {
            throw UnsupportedServerActionException(
                "Re-running AI tagging needs an admin account on this server"
            )
        }
        throw ApiException("HTTP ${response.status}: $errorBody", statusCode = response.status.value)
    }

    /**
     * Whether this server's API key belongs to an admin.
     *
     * Neither `GET /users/me` nor tRPC `users.whoami` reports the user's role, so the only way to
     * find out is to call something only an admin may call. `admin.getAdminNoticies` is the
     * cheapest such route — its handler returns an empty object and touches no data.
     *
     * A rejected probe answers false; a transport failure throws, so a caller can leave the
     * capability unknown rather than caching a wrong "not an admin".
     */
    suspend fun isServerAdmin(server: Server): Boolean = guardedCall {
        val trpcBase = getTrpcBaseUrl(server)
        val response: HttpResponse = try {
            client.get("$trpcBase/api/trpc/admin.getAdminNoticies") {
                header("Authorization", getAuth(server))
                parameter("batch", "1")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Error checking admin status: ${e.message}", e)
        }
        response.status.isSuccess()
    }
}

/** What [RemoteDataSource.summarizeBookmark] got back from the server. */
data class SummarizeResult(
    val summary: String?,
    val summarizationStatus: String?
)

class ApiException(
    message: String,
    cause: Throwable? = null,
    statusCode: Int? = null
) : Exception(message, cause) {
    /** HTTP status of the failed response, preserved through re-wraps via [cause]. */
    val statusCode: Int? = statusCode ?: (cause as? ApiException)?.statusCode
}

/** True when this failure chain contains an HTTP response with [code]. */
fun Throwable.hasHttpStatus(code: Int): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is ApiException && current.statusCode == code) return true
        current = current.cause
    }
    return false
}
