package com.karakept.app.data.local.dao

/**
 * What a reading-progress pull needs about a bookmark, without dragging the article content
 * along: a backfill selects thousands of these, and reading whole
 * [com.karakept.app.data.local.entity.BookmarkEntity] rows for that would pull every cached
 * article into memory.
 *
 * [originalRemoteId] is the server's own id, which the pull is keyed on, and [readingProgress]
 * is what the server's answer is compared against — so a pass decides what to apply without
 * going back to the table row by row.
 */
data class ProgressPullTarget(
    val localId: Long,
    val remoteId: Long,
    val originalRemoteId: String,
    val readingProgress: Float
)
