package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity

/**
 * The pages one view has in hand, between one read of the window and the next.
 *
 * The window is re-read on two signals that arrive down the same flow and look identical there:
 * the viewport crossing a page boundary, where every page already held is still exactly right,
 * and a write to the table, where none of them are. Room re-emits the view's count once per
 * invalidation, so that flow's emission index separates the two — a scroll carries the same
 * [revision] as the read before it, a write carries a new one.
 *
 * Told apart, a scroll reads only the pages it uncovered. Told together — which is what re-reading
 * the whole span every time amounts to — an ordinary scroll of one page costs two page queries
 * instead of one, and the page the user is looking at is queried again to produce the rows already
 * on screen.
 *
 * Pages outside the span asked for are dropped, so what this holds is bounded by the window and
 * not by how far the view has been scrolled.
 */
internal class BookmarkPageCache {
    private var revision = Int.MIN_VALUE
    private var pages: Map<Int, List<BookmarkEntity>> = emptyMap()

    /**
     * The pages of [span] still valid at [revision].
     *
     * Empty when [revision] has moved on, which is the whole of the staleness rule: a write to the
     * table can change any row of any page, including how many rows there are, so a page read
     * before it says nothing about the view after it.
     */
    fun held(revision: Int, span: IntRange): Map<Int, List<BookmarkEntity>> {
        if (revision != this.revision) {
            this.revision = revision
            pages = emptyMap()
        } else {
            pages = pages.filterKeys { it in span }
        }
        return pages
    }

    /** Records [read] as what the window holds at [revision]. */
    fun store(revision: Int, read: Map<Int, List<BookmarkEntity>>) {
        this.revision = revision
        pages = read
        if (read.isNotEmpty()) primed = true
    }

    /**
     * Whether this view has ever put rows on screen.
     *
     * The span is read in two passes so a view switch does not wait for pages nobody is looking
     * at, and that is only ever an improvement while the screen is empty. On a re-read it is the
     * opposite: publishing the viewport's pages alone takes the margin pages *away* from a list
     * that already had them, and any of them on screen — a scroll the read has not caught up
     * with — blinks back to a skeleton. A write to the table invalidates every page, so with a
     * sync running that is continuous.
     */
    fun isPrimed(): Boolean = primed

    private var primed = false
}
