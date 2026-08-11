package com.karakept.app.data.local.dao

/**
 * The two ids a reading-progress pull needs, without dragging the article content along.
 * Reading progress has no batch endpoint on the server, so a backfill selects thousands of
 * these — reading whole [com.karakept.app.data.local.entity.BookmarkEntity] rows for that
 * would pull every cached article into memory.
 */
data class ProgressPullTarget(
    val localId: Long,
    val remoteId: Long
)
