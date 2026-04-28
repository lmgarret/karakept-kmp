package com.karakept.app.domain

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ContentFilter
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ReadFilter
import com.karakept.app.data.model.SortOption

/**
 * Pure, stateless utility functions for applying filters and sorting to
 * in-memory bookmark lists.
 *
 * All functions here are free of side-effects and require no dependencies,
 * making them straightforward to unit-test.
 */
object BookmarkFilterUtils {

    /**
     * Applies tag and list filters from [filter] to [bookmarks].
     *
     * Status filtering is intentionally excluded because it is already applied
     * at the DB level by [BookmarkRepository.getBookmarksPaged].
     *
     * @param skipListFilter Pass `true` when the list was already filtered at
     *   the DB level (single-list query) to avoid redundant client-side work.
     */
    fun applyClientSideFilters(
        bookmarks: List<BookmarkEntity>,
        filter: FilterConfig,
        skipListFilter: Boolean = false
    ): List<BookmarkEntity> {
        var result = bookmarks

        // Tag filter (OR logic: bookmark must have AT LEAST ONE selected tag)
        if (filter.tags.isNotEmpty()) {
            result = result.filter { bookmark ->
                val bookmarkTags = bookmark.tags
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                bookmarkTags.any { tag -> filter.tags.contains(tag) }
            }
        }

        // List filter (OR logic) — skipped when already applied at DB level
        if (filter.lists.isNotEmpty() && !skipListFilter) {
            result = result.filter { bookmark ->
                val bookmarkLists = bookmark.listIds
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                bookmarkLists.any { listId -> filter.lists.contains(listId) }
            }
        }

        if (filter.readFilter != ReadFilter.ALL) {
            result = result.filter { bm ->
                when (filter.readFilter) {
                    ReadFilter.UNREAD      -> !bm.isRead
                    ReadFilter.READ        -> bm.isRead
                    ReadFilter.IN_PROGRESS -> bm.readingProgress > 0f && !bm.isRead
                    ReadFilter.ALL         -> true
                }
            }
        }

        if (filter.contentFilter != ContentFilter.ALL) {
            result = result.filter { bm ->
                when (filter.contentFilter) {
                    ContentFilter.DOWNLOADED     -> bm.readingTimeMinutes > 0
                    ContentFilter.NOT_DOWNLOADED -> bm.readingTimeMinutes == 0
                    ContentFilter.ALL            -> true
                }
            }
        }

        return result
    }

    /**
     * Sorts [bookmarks] according to [sort].
     */
    fun applySorting(
        bookmarks: List<BookmarkEntity>,
        sort: SortOption
    ): List<BookmarkEntity> = when (sort) {
        SortOption.NEWEST             -> bookmarks.sortedByDescending { it.createdAt }
        SortOption.OLDEST             -> bookmarks.sortedBy { it.createdAt }
        SortOption.TITLE_AZ           -> bookmarks.sortedBy { it.title.lowercase() }
        SortOption.TITLE_ZA           -> bookmarks.sortedByDescending { it.title.lowercase() }
        SortOption.READING_TIME_SHORT -> bookmarks.sortedBy { it.readingTimeMinutes }
        SortOption.READING_TIME_LONG  -> bookmarks.sortedByDescending { it.readingTimeMinutes }
    }

    /**
     * Applies a full-text [query] on top of status/tag/list [filter].
     *
     * Used in search mode, where the entire DB cache is searched locally.
     * The query is matched against title, URL, and description fields.
     */
    fun applySearchFilter(
        all: List<BookmarkEntity>,
        filter: FilterConfig,
        query: String
    ): List<BookmarkEntity> {
        val q = query.lowercase()

        var result = when (filter.status) {
            FilterStatus.FAVORITES            -> all.filter { it.isStarred }
            FilterStatus.ARCHIVED             -> all.filter { it.isArchived }
            FilterStatus.ALL                  -> all.filter { !it.isArchived }
            FilterStatus.ALL_INCLUDING_ARCHIVED -> all
        }

        if (filter.tags.isNotEmpty()) {
            result = result.filter { bookmark ->
                val tags = bookmark.tags
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                filter.tags.any { tag -> tags.contains(tag) }
            }
        }

        if (filter.lists.isNotEmpty()) {
            result = result.filter { bookmark ->
                val lists = bookmark.listIds
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                filter.lists.any { listId -> lists.contains(listId) }
            }
        }

        if (filter.readFilter != ReadFilter.ALL) {
            result = result.filter { bm ->
                when (filter.readFilter) {
                    ReadFilter.UNREAD      -> !bm.isRead
                    ReadFilter.READ        -> bm.isRead
                    ReadFilter.IN_PROGRESS -> bm.readingProgress > 0f && !bm.isRead
                    ReadFilter.ALL         -> true
                }
            }
        }

        if (filter.contentFilter != ContentFilter.ALL) {
            result = result.filter { bm ->
                when (filter.contentFilter) {
                    ContentFilter.DOWNLOADED     -> bm.readingTimeMinutes > 0
                    ContentFilter.NOT_DOWNLOADED -> bm.readingTimeMinutes == 0
                    ContentFilter.ALL            -> true
                }
            }
        }

        return result.filter { bookmark ->
            bookmark.title.lowercase().contains(q) ||
                bookmark.url.lowercase().contains(q) ||
                bookmark.description?.lowercase()?.contains(q) == true
        }
    }
}
