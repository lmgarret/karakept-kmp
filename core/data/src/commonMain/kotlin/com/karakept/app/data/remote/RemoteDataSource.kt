package com.karakept.app.data.remote

import com.karakept.api.model.*
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.Server
import io.ktor.client.HttpClient

/**
 * The data layer's one door to the Karakeep server. Every call is a one-line delegate to an
 * extension on [RemoteApiContext], grouped by concern:
 *
 * - bookmarks, lists, tags, assets, connection and version — `RemoteDataSourceBookmarks.kt`
 * - highlights — `RemoteDataSourceHighlights.kt`
 * - reading progress — `RemoteDataSourceReadingProgress.kt`
 * - recrawl, AI summary and retag, admin probe — `RemoteDataSourceServerActions.kt`
 *
 * The calls stay members here, not extensions on this class, so repository tests can keep
 * stubbing them with MockK's `coEvery`, which cannot reach a top-level extension without
 * `mockkStatic`. Defaults live on these members only; the implementations take every argument.
 */
internal class RemoteDataSource(
    client: HttpClient,
    offlineModeProvider: (suspend () -> Boolean)? = null
) {
    private val api = RemoteApiContext(client, offlineModeProvider)

    suspend fun fetchBookmarks(
        server: Server,
        cursor: String? = null,
        limit: Int = 50,
        includeContent: Boolean = false,
        archived: Boolean? = null,
        favourited: Boolean? = null
    ): PaginatedBookmarks = api.fetchBookmarks(server, cursor, limit, includeContent, archived, favourited)

    suspend fun fetchBookmark(server: Server, bookmarkId: String, includeContent: Boolean = true): Bookmark =
        api.fetchBookmark(server, bookmarkId, includeContent)

    suspend fun fetchListsForBookmark(server: Server, bookmarkId: String): List<KarakeepList> =
        api.fetchListsForBookmark(server, bookmarkId)

    suspend fun fetchLists(server: Server): List<KarakeepList> = api.fetchLists(server)

    suspend fun fetchBookmarksForList(server: Server, listId: String, includeContent: Boolean = false): List<Bookmark> =
        api.fetchBookmarksForList(server, listId, includeContent)

    suspend fun testConnection(url: String, apiKey: String): Boolean = api.testConnection(url, apiKey)

    /** See [RemoteApiContext.fetchServerVersion]: null when the server predates `/api/version`. */
    suspend fun fetchServerVersion(url: String): String? = api.fetchServerVersion(url)

    /** [onProgress] gets a 0f..1f fraction, or null when the response has no Content-Length. */
    suspend fun downloadAsset(
        server: Server,
        assetId: String,
        onProgress: ((Float?) -> Unit)? = null
    ): ByteArray = api.downloadAsset(server, assetId, onProgress)

    suspend fun updateBookmark(
        server: Server,
        bookmarkId: String,
        updates: BookmarksBookmarkIdPatchRequest
    ): Bookmark = api.updateBookmark(server, bookmarkId, updates)

    suspend fun fetchAllBookmarks(server: Server): List<Bookmark> = api.fetchAllBookmarks(server)

    suspend fun deleteBookmark(server: Server, bookmarkId: String) = api.deleteBookmark(server, bookmarkId)

    suspend fun detachAsset(server: Server, bookmarkId: String, assetId: String) =
        api.detachAsset(server, bookmarkId, assetId)

    suspend fun attachTags(
        server: Server,
        bookmarkId: String,
        tags: List<String>
    ): BookmarksBookmarkIdTagsPost200Response = api.attachTags(server, bookmarkId, tags)

    suspend fun detachTags(
        server: Server,
        bookmarkId: String,
        tags: List<String>
    ): BookmarksBookmarkIdTagsDelete200Response = api.detachTags(server, bookmarkId, tags)

    suspend fun addBookmarkToList(server: Server, listId: String, bookmarkId: String) =
        api.addBookmarkToList(server, listId, bookmarkId)

    suspend fun createBookmark(server: Server, url: String): Bookmark = api.createBookmark(server, url)

    suspend fun removeBookmarkFromList(server: Server, listId: String, bookmarkId: String) =
        api.removeBookmarkFromList(server, listId, bookmarkId)

    suspend fun updateList(server: Server, listId: String, name: String, icon: String?): KarakeepList =
        api.updateList(server, listId, name, icon)

    suspend fun fetchAllHighlights(server: Server): List<Highlight> = api.fetchAllHighlights(server)

    suspend fun fetchHighlightsForBookmark(server: Server, bookmarkId: String): List<Highlight> =
        api.fetchHighlightsForBookmark(server, bookmarkId)

    suspend fun createHighlight(
        server: Server,
        bookmarkId: String,
        text: String,
        startOffset: Int,
        endOffset: Int,
        note: String? = null,
        color: String? = null
    ): Highlight = api.createHighlight(server, bookmarkId, text, startOffset, endOffset, note, color)

    suspend fun updateHighlight(
        server: Server,
        highlightId: String,
        note: String? = null,
        color: String? = null
    ): Highlight = api.updateHighlight(server, highlightId, note, color)

    suspend fun deleteHighlight(server: Server, highlightId: String) = api.deleteHighlight(server, highlightId)

    /** See [RemoteApiContext.updateReadingProgress]: false only when the server will never keep it. */
    suspend fun updateReadingProgress(server: Server, bookmarkId: String, progressPercent: Int): Boolean =
        api.updateReadingProgress(server, bookmarkId, progressPercent)

    /** See [RemoteApiContext.getReadingProgressBatch]: an id missing from the map was never answered. */
    suspend fun getReadingProgressBatch(
        server: Server,
        bookmarkIds: List<String>,
        chunkSize: Int = READING_PROGRESS_BATCH_SIZE
    ): Map<String, Int?> = api.getReadingProgressBatch(server, bookmarkIds, chunkSize)

    suspend fun recrawlBookmark(
        server: Server,
        bookmarkId: String,
        archiveFullPage: Boolean = false,
        storePdf: Boolean = false
    ): Unit = api.recrawlBookmark(server, bookmarkId, archiveFullPage, storePdf)

    suspend fun summarizeBookmark(server: Server, bookmarkId: String): SummarizeResult =
        api.summarizeBookmark(server, bookmarkId)

    suspend fun requestAiRetag(server: Server, bookmarkId: String): Unit = api.requestAiRetag(server, bookmarkId)

    suspend fun isServerAdmin(server: Server): Boolean = api.isServerAdmin(server)
}
