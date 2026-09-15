package com.karakept.app.data.model

import androidx.compose.runtime.Immutable
import com.karakept.app.data.local.entity.BookmarkEntity

/** What the list renders at one index. */
sealed interface BookmarkSlot {
    data class Loaded(val bookmark: BookmarkEntity) : BookmarkSlot

    /** Not read yet — renders as a placeholder and asks for the page holding it. */
    data object Placeholder : BookmarkSlot
}

/**
 * The list as the UI addresses it: one slot per row the view holds, of which some are loaded.
 *
 * The LazyColumn used to be indexed by the loaded rows, so it could only scroll to a row already
 * read. Anything wanting an arbitrary position had to first make the window reach it — a database
 * read between the input and the list following. Sizing by [viewTotal] instead makes an index mean
 * a position in the view whether or not its row has arrived, so a jump is arithmetic.
 *
 * Rows are held by **page**, because a page is what a read returns and what a request asks for.
 * Pages need not be contiguous: a jump loads the page it lands on and nothing in between.
 *
 * [prepended] are rows sitting above the view because they are not in it yet — a bookmark the user
 * has just added, held on screen while the request to create it is in flight. They are not in the
 * database, so [viewTotal] does not count them, and they occupy the first indices.
 *
 * [generation] changes when the rows stop being a view of the same thing — a filter or server
 * switch — so a read in flight for the previous view can be recognised on arrival rather than
 * published over the new one.
 */
@Immutable
data class BookmarkWindow(
    /** How many rows the view holds in the database, loaded or not. */
    val viewTotal: Int,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    /** Page index to that page's rows, in view order. */
    val pages: Map<Int, List<BookmarkEntity>> = emptyMap(),
    val prepended: List<BookmarkEntity> = emptyList(),
    val generation: Int = 0,
    /**
     * Whether the database has said how big the view is.
     *
     * Until it has, a window is not a view with nothing in it — it is a view nobody has counted.
     * The two look identical from [total] alone, which is how switching lists came to flash the
     * empty state: the list is rebuilt for the new view, and for a moment that window is empty
     * because no answer has arrived, not because there is nothing there.
     */
    val resolved: Boolean = false
) {
    /** Slots the list renders. */
    val total: Int = prepended.size + viewTotal

    /** The view holds nothing — and is known to, which [resolved] is what settles. */
    val isEmpty: Boolean get() = resolved && total == 0

    /** Rows on hand, however many slots there are. */
    val loadedCount: Int get() = prepended.size + pages.values.sumOf { it.size }

    operator fun get(index: Int): BookmarkSlot =
        bookmarkAt(index)?.let(BookmarkSlot::Loaded) ?: BookmarkSlot.Placeholder

    /** The row at [index], or null when its page has not been read yet. */
    fun bookmarkAt(index: Int): BookmarkEntity? {
        if (index < 0 || index >= total) return null
        if (index < prepended.size) return prepended[index]
        val viewIndex = index - prepended.size
        return pages[viewIndex / pageSize]?.getOrNull(viewIndex % pageSize)
    }

    /**
     * The page holding [index], or null for a prepended row — which is on hand by definition and
     * belongs to no page.
     */
    fun pageOf(index: Int): Int? {
        if (index < prepended.size || index >= total) return null
        return (index - prepended.size) / pageSize
    }

    /** Every page [range] touches, clamped to the pages the view actually has. */
    fun pagesCovering(range: IntRange): List<Int> {
        if (viewTotal <= 0 || range.isEmpty()) return emptyList()
        val lastPage = (viewTotal - 1) / pageSize
        val first = pageOf(range.first.coerceIn(0, total - 1)) ?: 0
        val last = pageOf(range.last.coerceIn(0, total - 1)) ?: lastPage
        return (first.coerceAtLeast(0)..last.coerceAtMost(lastPage)).toList()
    }

    /**
     * The LazyColumn key for [index].
     *
     * A loaded slot keys on its `remoteId`, so a row keeps its identity as the list is re-indexed
     * around it — and so the effects that read the visible items back (`key as? String`) go on
     * seeing bookmarks and nothing else. A placeholder keys on its index, which is not a String
     * and so is skipped by those effects for free; when its row arrives the key changes with it.
     */
    fun keyAt(index: Int): Any = bookmarkAt(index)?.remoteId ?: index

    fun indexOfRemoteId(remoteId: String): Int {
        val prependedAt = prepended.indexOfFirst { it.remoteId == remoteId }
        if (prependedAt >= 0) return prependedAt
        for ((pageIndex, rows) in pages) {
            val offset = rows.indexOfFirst { it.remoteId == remoteId }
            if (offset >= 0) return prepended.size + pageIndex * pageSize + offset
        }
        return -1
    }

    /** Every loaded row, in view order — for the callers that want the rows and not the slots. */
    fun loadedRows(): List<BookmarkEntity> =
        prepended + pages.entries.sortedBy { it.key }.flatMap { it.value }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50

        val EMPTY = BookmarkWindow(viewTotal = 0)

        /** Every slot loaded — the whole view on hand, with nothing left to read. */
        fun dense(
            rows: List<BookmarkEntity>,
            generation: Int = 0,
            pageSize: Int = DEFAULT_PAGE_SIZE
        ): BookmarkWindow = BookmarkWindow(
            viewTotal = rows.size,
            pageSize = pageSize,
            pages = rows.chunked(pageSize).withIndex().associate { (index, page) -> index to page },
            generation = generation,
            resolved = true
        )
    }
}
