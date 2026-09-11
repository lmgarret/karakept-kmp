package com.karakept.app.data.model

import com.karakept.app.data.local.entity.BookmarkEntity

/**
 * A row's position in a paged query's sort order, named by its sort keys rather than by
 * counting rows before it.
 *
 * OFFSET names a position only for as long as the rows above it stay put. A sync committing a
 * row above the read position shifts every later page down by one, so the next read starts a
 * row short: it re-returns rows the window already holds, and pushes an equal number *past* the
 * read position, where a forward-only walk never comes back for them. The window then sits
 * short of the table believing it is complete, and only a full reload — toggling a filter —
 * brings the displaced rows back (#333).
 *
 * A cursor cannot drift that way: `WHERE (key, localId) < (cursor)` selects the same rows
 * whatever is inserted above it. Every sort key is carried so one cursor answers for any
 * [SortOption] without its holder knowing which is active.
 */
data class BookmarkCursor(
    val createdAt: Long,
    val title: String,
    val readingTimeMinutes: Int,
    val localId: Long
) {
    companion object {
        fun of(bookmark: BookmarkEntity) = BookmarkCursor(
            createdAt = bookmark.createdAt,
            title = bookmark.title,
            readingTimeMinutes = bookmark.readingTimeMinutes,
            localId = bookmark.localId
        )
    }
}
