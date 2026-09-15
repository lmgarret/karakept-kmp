package com.karakept.app.data.model

import androidx.compose.runtime.Immutable
import com.karakept.app.data.local.entity.BookmarkEntity

/** What the list renders at one index. */
sealed interface BookmarkSlot {
    data class Loaded(val bookmark: BookmarkEntity) : BookmarkSlot

    /** Not read yet — renders as a placeholder and asks for the rows around it. */
    data object Placeholder : BookmarkSlot
}

/**
 * The list as the UI addresses it: [total] slots, of which a contiguous run is loaded.
 *
 * The LazyColumn used to be indexed by the loaded rows themselves, so it could only scroll to a
 * row the window already held. Everything that wanted to reach an arbitrary position — the
 * fast-scroll cursor above all — had to first *make the window reach it*, which meant a database
 * read between the thumb moving and the list following. Sizing the list by [total] instead makes
 * an index mean the same thing whether or not its row has arrived, so a jump is arithmetic rather
 * than a read.
 *
 * The loaded rows are a contiguous run rather than a scattered set because that is what the
 * forward walk produces and what a page read extends. An index outside it is a
 * [BookmarkSlot.Placeholder]; there are none while [total] equals the run's length, which is the
 * state [dense] describes and the only one the walk can currently reach.
 *
 * [generation] is what makes a stale window droppable: it changes whenever the rows stop being a
 * view of the same thing — a filter or server switch — so a page in flight for the previous view
 * can be recognised on arrival rather than published on top of the new one.
 */
@Immutable
data class BookmarkWindow(
    val total: Int,
    /** The loaded run, in view order, starting at [loadedFrom]. */
    val rows: List<BookmarkEntity>,
    val loadedFrom: Int = 0,
    val generation: Int = 0
) {
    /** Indices whose row is on hand. */
    val loadedRange: IntRange = loadedFrom until (loadedFrom + rows.size)

    /** Rows on hand, however many slots the list has. */
    val loadedCount: Int get() = rows.size

    val isEmpty: Boolean get() = total == 0

    operator fun get(index: Int): BookmarkSlot =
        bookmarkAt(index)?.let(BookmarkSlot::Loaded) ?: BookmarkSlot.Placeholder

    /** The row at [index], or null when it has not been read yet. */
    fun bookmarkAt(index: Int): BookmarkEntity? =
        if (index in loadedRange) rows[index - loadedFrom] else null

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
        val offset = rows.indexOfFirst { it.remoteId == remoteId }
        return if (offset < 0) -1 else loadedFrom + offset
    }

    companion object {
        val EMPTY = BookmarkWindow(total = 0, rows = emptyList())

        /** Every slot loaded — the whole view on hand, with nothing left to read. */
        fun dense(rows: List<BookmarkEntity>, generation: Int = 0): BookmarkWindow =
            BookmarkWindow(
                total = rows.size,
                rows = rows,
                loadedFrom = 0,
                generation = generation
            )
    }
}
