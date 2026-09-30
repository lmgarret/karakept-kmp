package com.karakept.app.domain

import com.karakept.app.data.local.entity.BookmarkEntity
import kotlin.math.abs

/**
 * When a read or archived bookmark stops being worth keeping offline.
 *
 * A retention period is a number of days, null meaning retention is off. [isRetired] mirrors
 * [com.karakept.app.data.local.entity.RETIRED_PREDICATE]: cleanup evicts through the SQL, while
 * sync and the reader ask this for a row they already hold, so that content cleanup dropped is
 * not downloaded straight back.
 */
object OfflineRetention {
    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    const val MIN_DAYS = 1
    const val MAX_DAYS = 999
    const val DEFAULT_DAYS = 30

    /**
     * Where the slider can land: each day of the first week, weekly up to a month, then two and
     * three months. Any other value is typed into the field beside it.
     */
    val SLIDER_STOPS = listOf(1, 2, 3, 4, 5, 6, 7, 14, 21, 30, 60, 90)

    /** The slider stop closest to [days]; the lower one on a tie. */
    fun nearestStopIndex(days: Int): Int =
        SLIDER_STOPS.indices.minBy { abs(SLIDER_STOPS[it] - days) }

    fun cutoff(retentionDays: Int, now: Long): Long = now - retentionDays * DAY_MILLIS

    fun isRetired(bookmark: BookmarkEntity, retentionDays: Int?, now: Long): Boolean =
        isRetired(bookmark.isRead, bookmark.isArchived, bookmark.readOrArchivedAt, retentionDays, now)

    fun isRetired(
        isRead: Boolean,
        isArchived: Boolean,
        readOrArchivedAt: Long?,
        retentionDays: Int?,
        now: Long
    ): Boolean {
        if (retentionDays == null) return false
        if (!isRead && !isArchived) return false
        val since = readOrArchivedAt ?: return false
        return since <= cutoff(retentionDays, now)
    }
}
