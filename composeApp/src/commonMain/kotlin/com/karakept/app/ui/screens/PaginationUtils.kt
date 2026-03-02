package com.karakept.app.ui.screens

/**
 * Advances through consecutive pages starting at [startPage] until either:
 * - At least one item survives the caller-supplied filter (non-empty filtered list), OR
 * - The underlying data source is truly exhausted (raw page count < [pageSize]).
 *
 * This is the core fix for the "missing bookmarks / broken infinite scroll" bug.
 *
 * **Why this is needed**: When multi-list client-side filtering is active
 * (e.g. `includeChildListBookmarks = true` expands one list ID into several), the DB
 * query fetches a full page of [pageSize] rows, but many — or all — of them may belong
 * to other lists and be discarded by the client-side filter.  If we stop as soon as one
 * page returns 0 visible items, we prematurely signal "no more data", hiding bookmarks
 * that would have appeared on the next DB page.
 *
 * The loop continues as long as the raw DB page was full ([rawCount] == [pageSize]) and
 * the filtered result is empty.  Only when the raw page is short ([rawCount] < [pageSize])
 * do we know the DB has no more rows to offer.
 *
 * @param startPage   0-based page index to begin from.
 * @param pageSize    Number of rows requested per DB page.
 * @param loadPage    Suspending function that fetches page [page] and returns
 *                    `Pair(filteredItems, rawDbRowCount)`.
 * @return Triple of:
 *   - `filteredItems`: the visible items found (may be empty if DB exhausted before any match)
 *   - `lastPage`: the page index of the last fetched page
 *   - `dbExhausted`: `true` when the raw page had fewer than [pageSize] rows
 */
internal suspend fun <T> advancePagesUntilItemsFound(
    startPage: Int,
    pageSize: Int,
    loadPage: suspend (page: Int) -> Pair<List<T>, Int>
): Triple<List<T>, Int, Boolean> {
    var page = startPage
    while (true) {
        val (items, rawCount) = loadPage(page)
        val dbExhausted = rawCount < pageSize
        if (items.isNotEmpty() || dbExhausted) {
            return Triple(items, page, dbExhausted)
        }
        page++
    }
}
