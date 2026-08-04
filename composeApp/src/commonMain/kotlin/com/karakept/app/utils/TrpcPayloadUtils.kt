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

    fun envelope(input: JsonObject): String =
        JsonObject(mapOf("0" to JsonObject(mapOf("json" to input)))).toString()

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

    /** tRPC query `bookmarks.getReadingProgress` (sent as the `input` query parameter). */
    fun getReadingProgress(bookmarkId: String): String = envelope(
        buildJsonObject { put("bookmarkId", bookmarkId) }
    )
}
