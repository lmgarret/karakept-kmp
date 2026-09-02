package com.karakept.app.utils

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builders for the tRPC batch request envelope used by the Karakeep web app.
 *
 * Karakeep exposes a handful of operations only over tRPC (never through the documented
 * REST API), so [com.karakept.app.data.remote.RemoteDataSource] posts to the `api/trpc`
 * routes directly for those. The envelope is always `{"0":{"json":{…}}}`; these helpers
 * exist so the payload shape can be unit-tested without standing up an HTTP client.
 */
object TrpcPayloadUtils {

    fun envelope(input: JsonObject): String = envelope(listOf(input))

    /**
     * tRPC mutation `bookmarks.recrawlBookmark`.
     *
     * Both flags default to false server-side; passing them explicitly keeps the three
     * request variants (refresh / full page archive / PDF) readable at the call site.
     */
    fun recrawlBookmark(
        bookmarkId: String,
        archiveFullPage: Boolean,
        storePdf: Boolean
    ): String = envelope(
        buildJsonObject {
            put("bookmarkId", bookmarkId)
            put("archiveFullPage", archiveFullPage)
            put("storePdf", storePdf)
        }
    )

    /** tRPC mutation `bookmarks.updateReadingProgress`. */
    fun updateReadingProgress(
        bookmarkId: String,
        progressPercent: Int
    ): String = envelope(
        buildJsonObject {
            put("bookmarkId", bookmarkId)
            put("readingProgressOffset", 0)
            put("readingProgressAnchor", JsonPrimitive(null as String?))
            put("readingProgressPercent", progressPercent)
        }
    )

    /**
     * tRPC mutation `admin.adminRetagBookmark`.
     *
     * Karakeep exposes no user-level route that re-runs AI tagging on one bookmark — the only
     * alternative, `bookmarks.recrawlBookmark`, re-downloads the page as a side effect. This one
     * is an `adminProcedure`, so it answers UNAUTHORIZED for a non-admin API key.
     */
    fun adminRetagBookmark(bookmarkId: String): String = envelope(
        buildJsonObject { put("bookmarkId", bookmarkId) }
    )

    /** tRPC query `bookmarks.getReadingProgress` (sent as the `input` query parameter). */
    fun getReadingProgress(bookmarkId: String): String = getReadingProgressBatch(listOf(bookmarkId))

    /**
     * The same query for several bookmarks in one request: `{"0":{…},"1":{…},…}`.
     *
     * Karakeep has no procedure that returns progress for many bookmarks, but tRPC batches at
     * the transport, which is what its own web client does (`httpBatchLink`). One call per
     * bookmark therefore does not have to mean one request per bookmark — the difference
     * between a library converging over dozens of syncs and converging in one.
     *
     * Results come back as an array in the same order the inputs were given.
     */
    fun getReadingProgressBatch(bookmarkIds: List<String>): String = envelope(
        bookmarkIds.map { id -> buildJsonObject { put("bookmarkId", id) } }
    )

    /** Multi-entry envelope. Indices are the batch's ordering, so insertion order matters. */
    fun envelope(inputs: List<JsonObject>): String = JsonObject(
        inputs.mapIndexed { index, input ->
            index.toString() to JsonObject(mapOf("json" to input))
        }.toMap()
    ).toString()

    /**
     * The batched-query URL path: one copy of [procedure] per input, comma separated, which is
     * how tRPC names the procedures a batch is made of.
     */
    fun batchPath(procedure: String, count: Int): String =
        List(count) { procedure }.joinToString(",")
}
