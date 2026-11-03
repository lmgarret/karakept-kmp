package com.karakept.app.data.local.entity

import com.karakept.app.domain.OfflineRetention

/** Whether sync should leave this bookmark's offline copy alone rather than download it. */
fun OfflineRetention.skipsContentSync(bookmark: BookmarkEntity, retentionDays: Int?, now: Long): Boolean =
    bookmark.offlineEvictedAt != null || isRetired(bookmark, retentionDays, now)

fun OfflineRetention.isRetired(bookmark: BookmarkEntity, retentionDays: Int?, now: Long): Boolean =
    isRetired(bookmark.isRead, bookmark.isArchived, bookmark.readOrArchivedAt, retentionDays, now)
