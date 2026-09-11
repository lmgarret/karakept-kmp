package com.karakept.app.ui.screens

/**
 * What a walk of consecutive pages found.
 *
 * @param items       the visible items found — empty only when the source ran out before any
 *                    page yielded a match.
 * @param lastPage    index of the last page fetched. The window spans `0..lastPage`, which is
 *                    what a whole-window re-read needs to know; it no longer addresses rows.
 * @param dbExhausted the last raw page came back short, so the source has nothing further.
 * @param nextCursor  position of the last raw row read, where the next walk resumes. Null when
 *                    the last page was empty, leaving the previous position standing.
 */
internal data class PageWalk<T, C>(
    val items: List<T>,
    val lastPage: Int,
    val dbExhausted: Boolean,
    val nextCursor: C?
)

/**
 * Advances through consecutive pages starting after [startCursor] until either:
 * - At least one item survives the caller-supplied filter (non-empty filtered list), OR
 * - The underlying data source is truly exhausted (raw page count < [pageSize]).
 *
 * **Why this is needed**: when client-side filtering is active (e.g.
 * `includeChildListBookmarks = true` expands one list ID into several), the DB query fetches a
 * full page of [pageSize] rows, but many — or all — of them may be discarded by the
 * client-side filter. Stopping as soon as one page returns 0 visible items would prematurely
 * signal "no more data", hiding bookmarks that the next DB page would have shown.
 *
 * Each read resumes from the previous read's own last row rather than from a row count, so a
 * row committed above the walk cannot shift the ground under it (see `BookmarkCursor`).
 *
 * @param startPage   0-based index the window is already [startCursor] rows deep at.
 * @param startCursor position to resume after; null starts at the first row.
 * @param pageSize    number of rows requested per DB page.
 * @param loadPage    fetches the page following a cursor.
 */
internal suspend fun <T, C> advancePagesUntilItemsFound(
    startPage: Int,
    startCursor: C?,
    pageSize: Int,
    loadPage: suspend (after: C?) -> PageRead<T, C>
): PageWalk<T, C> {
    var page = startPage
    var cursor = startCursor
    while (true) {
        val read = loadPage(cursor)
        val dbExhausted = read.rawCount < pageSize
        // A page that yielded nothing still moves the cursor on, so the next iteration asks for
        // rows it has not seen. Holding the old cursor would re-read the same page forever.
        cursor = read.nextCursor ?: cursor
        if (read.items.isNotEmpty() || dbExhausted) {
            return PageWalk(read.items, page, dbExhausted, cursor)
        }
        page++
    }
}

/**
 * One page as the source returned it.
 *
 * @param items      rows surviving the client-side filter.
 * @param rawCount   rows the query returned, before that filter. Short of the page size means
 *                   the source is exhausted — the filtered count cannot answer that.
 * @param nextCursor position of the last raw row, or null if the page was empty.
 */
internal data class PageRead<T, C>(
    val items: List<T>,
    val rawCount: Int,
    val nextCursor: C?
)
