package com.karakept.app.data.model

import androidx.compose.runtime.Immutable

/**
 * A bookmark as everything above the data layer sees it.
 *
 * The Room row (`BookmarkEntity`) also carries bookkeeping only the data layer reads — sync
 * cursors, offline-cache stamps, the indexed `hasContent` flag — which is deliberately absent
 * here, so the entity can stay private to the data layer.
 */
@Immutable
data class Bookmark(
    val localId: Long = 0,
    val remoteId: String,
    val serverId: String,
    val url: String,
    val title: String,
    val content: String?,
    val imageUrl: String?,
    val bannerImageAssetId: String?,
    val screenshotAssetId: String?,
    val description: String?,
    val createdAt: Long,
    val isArchived: Boolean,
    val isStarred: Boolean,
    val isRead: Boolean = false,
    val tags: String = "", // Comma-separated tags
    val listIds: String = "", // Comma-separated list IDs
    val readingTimeMinutes: Int = 0,
    val readingProgress: Float = 0f, // 0.0–1.0
    val readingScrollIndex: Int = 0,
    val readingScrollOffset: Int = 0,
    val crawlStatus: String? = null, // "success" | "failure" | "pending"
    val crawledAt: Long? = null,
    // AI summary generated server-side; distinct from [description], read from the page's meta tags.
    val summary: String? = null,
    val summarizationStatus: String? = null // "success" | "failure" | "pending"
)
