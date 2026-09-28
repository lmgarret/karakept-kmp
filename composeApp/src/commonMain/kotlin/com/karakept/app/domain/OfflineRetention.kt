package com.karakept.app.domain

import com.karakept.app.data.local.entity.BookmarkEntity

/**
 * When a read or archived bookmark stops being worth keeping offline.
 *
 * Mirrors [com.karakept.app.data.local.entity.RETIRED_PREDICATE]: cleanup evicts through the SQL,
 * while sync and the reader ask this for a row they already hold, so that content cleanup dropped
 * is not downloaded straight back.
 */
object OfflineRetention {
    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    const val MIN_DAYS = 1
    const val MAX_DAYS = 90
    const val DEFAULT_DAYS = 30

    fun cutoff(retentionDays: Int, now: Long): Long = now - retentionDays * DAY_MILLIS

    fun isRetired(bookmark: BookmarkEntity, retentionDays: Int, now: Long): Boolean =
        isRetired(bookmark.isRead, bookmark.isArchived, bookmark.readOrArchivedAt, retentionDays, now)

    fun isRetired(
        isRead: Boolean,
        isArchived: Boolean,
        readOrArchivedAt: Long?,
        retentionDays: Int,
        now: Long
    ): Boolean {
        if (retentionDays <= 0) return false
        if (!isRead && !isArchived) return false
        val since = readOrArchivedAt ?: return false
        return since < cutoff(retentionDays, now)
    }
}
