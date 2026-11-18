package com.karakept.app.domain

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** One row of a `GET /lists/{id}/bookmarks` page, as far as a membership probe reads it. */
data class ProbeRow(val id: String?, val createdAt: String?)

/**
 * Pure parts of the per-bookmark smart-list membership probe: which smart lists a list edit can
 * affect, the cursor that starts a list's page at a given bookmark, and what that page says.
 *
 * Karakeep stores no smart-list membership; it evaluates the list's query on every fetch. Its
 * list route pages by `createdAt` descending, then `id` descending, and applies a cursor
 * `{id}_{createdAt}` inclusively (`createdAt < c OR (createdAt = c AND id <= c.id)`), so a page
 * of one starting at a bookmark's own cursor holds that bookmark first exactly when the list does.
 */
object SmartListProbeUtils {

    /**
     * Whether a smart list's [query] can change when a bookmark joins or leaves a list: it holds
     * a `list:` qualifier (which also names smart lists, resolved recursively by the server) or
     * `is:inlist`, negated or not. A substring test, so false positives (a title containing
     * "list:") cost one request each, and nothing the parser accepts slips through. A list whose
     * query is missing is assumed to be affected.
     */
    fun canDependOnListMembership(query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        return query.contains("list:", ignoreCase = true) || query.contains("inlist", ignoreCase = true)
    }

    /**
     * The cursor starting a list page at the bookmark [bookmarkId] created at [createdAtMillis],
     * formatted as the server's own `nextCursor` (`Date.toISOString()`), or null when no cursor
     * can be trusted to land on it.
     *
     * Karakeep stores `createdAt` in whole seconds, so a value with a millisecond part did not
     * come from the server — the sync falls back to the local clock on a date it cannot parse —
     * and a cursor built from it would start the page somewhere else. The server splits the
     * cursor on `_`, so an id holding one cannot be expressed.
     */
    fun cursorFor(bookmarkId: String, createdAtMillis: Long): String? {
        if (bookmarkId.isBlank() || '_' in bookmarkId) return null
        if (createdAtMillis <= 0L || createdAtMillis % 1000L != 0L) return null
        return "${bookmarkId}_${toIsoString(createdAtMillis)}"
    }

    /**
     * Reads the first page of a probe started at [cursorFor]: true when the bookmark is in the
     * list, false when the page proves it is not, null when the page cannot be trusted.
     *
     * A first row that is not the bookmark proves absence only if it sorts after it — older, or
     * as old with a smaller id. Anything sorting before it means the server did not apply the
     * cursor as expected, and the answer is unknown.
     */
    fun classifyPage(bookmarkId: String, createdAtMillis: Long, rows: List<ProbeRow>): Boolean? {
        val first = rows.firstOrNull() ?: return false
        if (first.id == bookmarkId) return true
        val firstId = first.id ?: return null
        val firstCreatedAt = parseMillis(first.createdAt) ?: return null
        val sortsAfter = firstCreatedAt < createdAtMillis ||
            (firstCreatedAt == createdAtMillis && firstId < bookmarkId)
        return if (sortsAfter) false else null
    }

    private fun parseMillis(iso: String?): Long? =
        iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

    // kotlinx-datetime drops a zero fraction ("…:00Z"); JavaScript's toISOString never does.
    internal fun toIsoString(epochMillis: Long): String {
        val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC)
        fun Int.pad(n: Int) = toString().padStart(n, '0')
        val millis = (epochMillis % 1000L).toInt().let { if (it < 0) it + 1000 else it }
        return "${t.year.pad(4)}-${t.month.number.pad(2)}-${t.day.pad(2)}" +
            "T${t.hour.pad(2)}:${t.minute.pad(2)}:${t.second.pad(2)}.${millis.pad(3)}Z"
    }
}
