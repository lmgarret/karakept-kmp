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
     *
     * Ties break on `localId`, matching the ORDER BY the paged query uses (see
     * BookmarkRepository.toOrderBySql). Both ends must agree: a page appended to the loaded
     * window is re-sorted here, and a comparator that ordered ties differently from the query
     * would interleave the new page into the old rows in an order no page boundary matches.
     */
    fun applySorting(
        bookmarks: List<BookmarkEntity>,
        sort: SortOption
    ): List<BookmarkEntity> = when (sort) {
        SortOption.NEWEST ->
            bookmarks.sortedWith(compareByDescending<BookmarkEntity> { it.createdAt }.thenByDescending { it.localId })
        SortOption.OLDEST ->
            bookmarks.sortedWith(compareBy<BookmarkEntity> { it.createdAt }.thenBy { it.localId })
        SortOption.TITLE_AZ ->
            bookmarks.sortedWith(compareBy<BookmarkEntity> { it.title.lowercase() }.thenBy { it.localId })
        SortOption.TITLE_ZA ->
            bookmarks.sortedWith(compareByDescending<BookmarkEntity> { it.title.lowercase() }.thenByDescending { it.localId })
        SortOption.READING_TIME_SHORT ->
            bookmarks.sortedWith(compareBy<BookmarkEntity> { it.readingTimeMinutes }.thenBy { it.localId })
        SortOption.READING_TIME_LONG ->
            bookmarks.sortedWith(compareByDescending<BookmarkEntity> { it.readingTimeMinutes }.thenByDescending { it.localId })
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
            // In search mode allBookmarks strips content, so OFFLINE falls back to all non-archived.
            // Paged browsing uses DB-level filtering which correctly checks content length.
            FilterStatus.OFFLINE              -> all.filter { !it.isArchived }
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
