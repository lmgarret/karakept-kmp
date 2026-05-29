package com.karakept.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable

@Immutable
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["remoteId", "serverId"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Long, // Hashed ID for local indexing 
    val originalRemoteId: String, // ORIGINAL string ID from API (used for API calls)
    val serverId: String,
    val url: String,
    val title: String,
    val content: String?,
    val type: String = "link", // BookmarkType storage value: link / text / video / asset / unknown
    val sourceUrl: String? = null, // External link for notes (url is "") and videos
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
    val readingTimeMinutes: Int = 0, // Estimated reading time in minutes
    val readingProgress: Float = 0f, // Reading progress (0.0–1.0) for visual indicator
    val readingScrollIndex: Int = 0, // LazyList firstVisibleItemIndex for scroll restoration
    val readingScrollOffset: Int = 0 // LazyList firstVisibleItemScrollOffset for scroll restoration
)
