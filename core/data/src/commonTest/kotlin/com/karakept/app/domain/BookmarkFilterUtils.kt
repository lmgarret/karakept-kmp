package com.karakept.app.domain

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ContentFilter
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ReadFilter
import com.karakept.app.data.model.SortOption
import com.karakept.app.utils.NoCaseCollationUtils

/**
 * The view, as the app used to compute it in memory.
 *
 * This is no longer how the app decides what a view holds — `BookmarkRepository.buildViewPredicate`
 * is, and the list reads its rows from that. It lives in the test source set because that is the
 * only thing it is still for: an independent statement of what a filter *means*, written before
 * the SQL and not derived from it, which `BookmarkViewIndexAgreementTest` checks the predicate
 * against row by row for every sort, status, list and client-side filter.
 *
 * Keeping it is what makes that test worth anything — a predicate checked only against itself
 * proves nothing. Keeping it *here* is what stops it drifting back into a second definition of
 * the view, which is how the two came to disagree about title order in the first place.
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
     * Every row [filter] admits, in the order the paged query returns them.
     *
     * The loaded window is a *prefix* of this: the same rows, as far as paging has got. Holding
     * the whole of it costs almost nothing — these are references to rows already resident in
     * `allBookmarks`, which the app reads for its counts anyway — and it answers the two
     * questions a fast-scroll cursor asks that the window cannot. How many rows are there, so a
     * thumb maps over the list rather than over the pages loaded so far (#273); and which row is
     * at absolute position N, so the tooltip can name the row under the thumb without waiting for
     * a read to reach it.
     *
     * Mirrors what a page goes through on its way to the screen — the status clause
     * `BookmarkRepository.buildPagedQuery` applies, then [applyClientSideFilters], then
     * [applySorting], whose tie-break matches the query's `ORDER BY`. A view holding exactly one
     * list is the one that applies no status clause: it is queried by membership instead, and
     * admits archived rows like the query does.
     *
     * The rows are nearly in order already (the query behind them is `createdAt DESC`), so for
     * the default sort this is a near-linear pass rather than a sort in earnest.
     */
    fun orderedViewFor(all: List<BookmarkEntity>, filter: FilterConfig): List<BookmarkEntity> =
        applySorting(viewFor(all, filter), filter.sort)

    /** [orderedViewFor] without the ordering, for callers that only need to count. */
    fun viewFor(all: List<BookmarkEntity>, filter: FilterConfig): List<BookmarkEntity> {
        val base = if (filter.lists.singleOrNull() != null) {
            all
        } else {
            applyStatusFilter(all, filter.status)
        }
        return applyClientSideFilters(base, filter)
    }

    /**
     * How many rows the view holds in total, loaded or not — [view]'s own size, except offline.
     *
     * `FilterStatus.OFFLINE` selects on a non-empty `content` column, and the queries behind an
     * in-memory row strip that column, so [view] can only approximate it through the reading-time
     * proxy [ContentFilter.DOWNLOADED] stands on. [offlineCount] is the database's own answer for
     * the unnarrowed offline view and is preferred where it applies; a narrowed one has no exact
     * answer here and keeps the proxy, which at least agrees with the rows [view] can name.
     */
    fun countForView(
        view: List<BookmarkEntity>,
        filter: FilterConfig,
        offlineCount: Int = 0
    ): Int {
        val narrowed = filter.tags.isNotEmpty() ||
            filter.lists.isNotEmpty() ||
            filter.readFilter != ReadFilter.ALL ||
            filter.contentFilter != ContentFilter.ALL
        if (filter.status == FilterStatus.OFFLINE && !narrowed) return offlineCount
        return view.size
    }

    /** The rows [status] admits, matching the clause the paged query applies for it. */
    private fun applyStatusFilter(
        bookmarks: List<BookmarkEntity>,
        status: FilterStatus
    ): List<BookmarkEntity> = when (status) {
        FilterStatus.ALL -> bookmarks.filter { !it.isArchived }
        FilterStatus.ALL_INCLUDING_ARCHIVED -> bookmarks
        FilterStatus.FAVORITES -> bookmarks.filter { it.isStarred }
        FilterStatus.ARCHIVED -> bookmarks.filter { it.isArchived }
        // The column this really selects on is stripped from in-memory rows; see [countForView].
        FilterStatus.OFFLINE -> bookmarks.filter { it.readingTimeMinutes > 0 }
    }

    /**
     * Sorts [bookmarks] according to [sort].
     *
     * Ties break on `localId`, matching the ORDER BY the paged query uses (see
     * BookmarkRepository.toOrderBySql). Both ends must agree: a page appended to the loaded
     * window is re-sorted here, and a comparator that ordered ties differently from the query
     * would interleave the new page into the old rows in an order no page boundary matches.
     *
     * Agreeing on the *key* matters just as much as agreeing on the tie-break, which is why
     * titles go through [NoCaseCollationUtils] rather than `lowercase()` — the query compares
     * them with `COLLATE NOCASE`, and the two fold different alphabets.
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
            bookmarks.sortedWith(
                compareBy<BookmarkEntity, String>(NoCaseCollationUtils.ascending) { it.title }
                    .thenBy { it.localId }
            )
        SortOption.TITLE_ZA ->
            bookmarks.sortedWith(
                compareByDescending<BookmarkEntity, String>(NoCaseCollationUtils.ascending) { it.title }
                    .thenByDescending { it.localId }
            )
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
