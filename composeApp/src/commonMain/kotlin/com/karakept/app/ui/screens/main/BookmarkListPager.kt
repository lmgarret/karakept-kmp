package com.karakept.app.ui.screens.main

import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.BookmarkCursor
import com.karakept.app.data.model.BookmarkWindow
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.withIndex

/**
 * Reads the bookmark list's view a page at a time: the pages under the viewport and a margin
 * either side, or — while a search is on — every match at once.
 *
 * The server and the effective filter come in from the screen model that owns them, so this holds
 * nothing but the visible span it is told about.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class BookmarkListPager(
    private val bookmarkRepository: BookmarkRepository,
    private val selectedServer: StateFlow<Server?>,
    private val effectiveFilter: StateFlow<FilterConfig>,
    private val effectiveFilterNow: () -> FilterConfig,
    private val listVersion: StateFlow<Int>,
    private val pageSize: Int = PAGE_SIZE
) {

    // Which slots the list is showing, reported by it so their pages can be read. The list is
    // sized by the view, so this is a position in the view and not in what has been loaded.
    private val _visibleSlots = MutableStateFlow(0..0)
    val visibleSlots: StateFlow<IntRange> = _visibleSlots

    /**
     * How many pages either side of the visible span to keep loaded, so an ordinary scroll
     * reaches rows that are already there rather than placeholders.
     */
    private val pageMargin = 1

    /**
     * Names the row at [index] in the view on screen, for the fast-scroll cursor's tooltip.
     *
     * One row, read at its position. The tooltip has to answer "where am I" for a thumb that can
     * be anywhere, including over rows the list has not read — and the old answer was to keep the
     * whole view ordered in memory so any index could be looked up. A row is a query.
     */
    suspend fun bookmarkAtIndex(index: Int): Bookmark? {
        val server = selectedServer.value ?: return null
        return runCatching {
            bookmarkRepository.getBookmarkAt(server, effectiveFilterNow(), index)
        }.getOrNull()
    }

    /** Reports the slots on screen. Cheap to call often — only a page crossing reads anything. */
    fun reportVisibleSlots(range: IntRange) {
        _visibleSlots.value = range
    }

    /** Where the user is standing in [window], as a sort position the count can be taken against. */
    fun topVisibleCursor(window: BookmarkWindow): BookmarkCursor? =
        window.bookmarkAt(_visibleSlots.value.first)?.let(BookmarkCursor::of)

    /**
     * The view as the list addresses it: every slot it holds, with the pages under the viewport
     * read and the rest left as placeholders — or, for a non-blank [query], the search results.
     *
     * Sized by a `COUNT(*)` rather than by what has been read, so an index means a position in
     * the view from the first frame. A jump therefore needs no read to *arrive* — the slot is
     * already there — and the page under it is fetched by its offset while the list sits where it
     * was put. Nothing walks, so there is no accumulated position to drift (#333) and no window
     * to grow: the rows on screen are re-read from their offsets, and rows scrolled away from are
     * dropped rather than carried.
     *
     * Two things drive it, and both have to. The count is re-emitted by Room on every write to
     * the table, which is also what makes a row edited elsewhere — or a page a sync has just
     * committed — reappear correctly here: the pages on screen are read again. The visible span
     * only asks for anything when it crosses a page boundary.
     */
    fun window(query: String): Flow<BookmarkWindow> =
        if (query.isBlank()) pagedWindow() else searchWindow(query)

    private fun pagedWindow(): Flow<BookmarkWindow> =
        combine(selectedServer, effectiveFilter) { server, filter -> server?.let { it to filter } }
            .flatMapLatest { request ->
                if (request == null) return@flatMapLatest flowOf(BookmarkWindow.EMPTY)
                val (server, filter) = request
                // Bucketed to pages before de-duplicating: scrolling within a page asks for
                // nothing, and only crossing into a new one does. The count is deliberately not
                // de-duplicated — a write that edits a row without changing how many there are
                // still has to re-read the rows on screen.
                val visiblePages = _visibleSlots
                    .map { (it.first / pageSize)..(it.last / pageSize) }
                    .distinctUntilChanged()

                // What the view already holds, so a scroll re-reads nothing. Per request: a
                // switch arrives as a new inner flow and starts with nothing in hand.
                val cache = BookmarkPageCache()

                bookmarkRepository.countBookmarksForViewFlow(server, filter)
                    // Indexed, not de-duplicated: Room emits once per invalidation whether or not
                    // the count moved, and the index is what tells the cache a write happened.
                    .withIndex()
                    .combine(visiblePages) { counted, pages -> counted to pages }
                    .transformLatest { (counted, pages) ->
                        readWindow(server, filter, counted.value, counted.index, pages, cache)
                    }
                    // A re-read that produced the same rows is not news. Sync invalidates the
                    // table continuously, and without this every one of those re-reads costs a
                    // publish and a recomposition to say nothing changed.
                    .distinctUntilChanged()
                    .catch { e ->
                        AppLogger.e("MainScreenModel", "Failed to read bookmarks: ${e.message}", e)
                        emit(BookmarkWindow.EMPTY)
                    }
            }
            .combine(listVersion) { window, version -> window.copy(generation = version) }

    /**
     * Emits the pages [pageSpan] touches, then those plus [pageMargin] either side.
     *
     * Two emissions rather than one **on a first load only**. The pages under the viewport are
     * what the user is waiting for; the margin pages are what a scroll will want next, and
     * holding the first back until the second has been read makes a view switch cost every page
     * in the span when only one of them is being looked at. Once the view has rows on screen the
     * split stops paying: the viewport's pages are fewer rows than the list already holds, so
     * publishing them on their own takes the margins away and blinks them back to skeletons.
     *
     * Only pages [cache] does not already hold are read. On a scroll that is the page just
     * uncovered; on a write to the table it is all of them, because a write can change any row.
     */
    private suspend fun FlowCollector<BookmarkWindow>.readWindow(
        server: Server,
        filter: FilterConfig,
        total: Int,
        revision: Int,
        pageSpan: IntRange,
        cache: BookmarkPageCache
    ) {
        // Resolved: the database has answered, so an empty view here means an empty view.
        val shape = BookmarkWindow(viewTotal = total, pageSize = pageSize, resolved = true)
        if (total <= 0) {
            cache.store(revision, emptyMap())
            emit(shape)
            return
        }
        val lastPage = (total - 1) / pageSize
        val core = pageSpan.first.coerceIn(0, lastPage)..pageSpan.last.coerceIn(0, lastPage)
        val span = (core.first - pageMargin).coerceAtLeast(0)..
            (core.last + pageMargin).coerceAtMost(lastPage)

        val pages = cache.held(revision, span).toMutableMap()
        // Only while the screen is still empty. On a re-read the viewport's pages alone are
        // fewer rows than the list already has, so publishing them first blanks the margins.
        val marginsPending = !cache.isPrimed() && span.any { it !in core && it !in pages }

        readMissing(server, filter, pages, core)
        if (marginsPending) {
            cache.store(revision, pages.toMap())
            emit(shape.copy(pages = pages.toMap()))
        }
        readMissing(server, filter, pages, span)
        cache.store(revision, pages.toMap())
        emit(shape.copy(pages = pages.toMap()))
    }

    /** Reads whichever pages of [range] are not in [pages] yet, into [pages]. */
    private suspend fun readMissing(
        server: Server,
        filter: FilterConfig,
        pages: MutableMap<Int, List<Bookmark>>,
        range: IntRange
    ) {
        range.filterNot { it in pages }.forEach { page ->
            pages[page] = bookmarkRepository.getBookmarkPage(
                server,
                filter,
                page * pageSize,
                pageSize
            )
        }
    }

    /**
     * Search results, which are not a paged view: they are a bounded list the user scans, so
     * every row is on hand at once.
     *
     * Matched by the database. It used to be matched in Kotlin over a resident copy of the whole
     * table, which is the copy this work removes; see BookmarkRepository.searchBookmarks for the
     * one narrowing that comes with the move.
     */
    private fun searchWindow(query: String): Flow<BookmarkWindow> =
        combine(selectedServer, effectiveFilter) { server, filter -> server?.let { it to filter } }
            .flatMapLatest { request ->
                if (request == null) return@flatMapLatest flowOf(BookmarkWindow.EMPTY)
                val (server, filter) = request
                // Re-run when the table changes, so a row edited while a search is on screen
                // updates there too.
                bookmarkRepository.countBookmarksForViewFlow(server, filter).mapLatest {
                    val matches = bookmarkRepository.searchBookmarks(server, filter, query)
                    BookmarkWindow.dense(matches, pageSize = pageSize)
                }
            }
}
