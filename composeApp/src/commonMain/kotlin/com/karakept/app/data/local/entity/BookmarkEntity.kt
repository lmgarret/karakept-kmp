package com.karakept.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.compose.runtime.Immutable

@Immutable
@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["remoteId", "serverId"], unique = true),
        // The offline view: which rows have an article body, in the default sort. See
        // [OFFLINE_PREDICATE] for why the view cannot ask about [content] itself.
        Index(value = ["serverId", "hasContent", "createdAt", "localId"]),
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    // The server's own bookmark id, verbatim. It is both the local identity key (with
    // [serverId]) and what every API call is keyed on.
    val remoteId: String,
    val serverId: String,
    val url: String,
    val title: String,
    val content: String?,
    // Whether [content] holds an article body, so the offline view can be answered without
    // reading one. Defaulted from [content], so constructing a bookmark cannot get it wrong;
    // the one place that writes [content] without rebuilding the row is
    // BookmarkDao.updateBookmarkContent, which sets both.
    val hasContent: Boolean = !content.isNullOrEmpty(),
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
    val readingScrollOffset: Int = 0, // LazyList firstVisibleItemScrollOffset for scroll restoration
    val modifiedAt: Long? = null, // Server modifiedAt (epoch millis); used to skip unchanged writes
    val progressSyncedAt: Long = 0, // Last time reading progress was pulled (epoch millis); rotating cursor
    val crawlStatus: String? = null, // Server crawl state: "success" | "failure" | "pending"
    val crawledAt: Long? = null, // When the server last crawled this bookmark
    // AI summary generated server-side. Distinct from [description], which the crawler reads from
    // the page's meta tags — Karakeep's inference worker writes this field and never touches that one.
    val summary: String? = null,
    val summarizationStatus: String? = null // "success" | "failure" | "pending"
)

/**
 * What the offline view selects.
 *
 * Not a test on [BookmarkEntity.content]: that column holds the whole article, and SQLite has to
 * load a value to compare it — `length(content) > 0` also decodes the UTF-8 to count characters.
 * Counting the offline view that way read every article body in the table, which measured 523ms
 * against 861 rows on a real library while the 4280-row archive counted in 15ms. Asking a boolean
 * column with an index over it is 0.1ms.
 */
const val OFFLINE_PREDICATE = "hasContent = 1"
