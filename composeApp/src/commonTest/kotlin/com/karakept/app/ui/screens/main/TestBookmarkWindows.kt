package com.karakept.app.ui.screens.main

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkWindow

/**
 * Builds the window a view of [rows] would produce, with only [loadedPages] actually read.
 *
 * The list is sized by the view and holds a few pages of it, so a slot is a position among
 * thousands while the rows in hand are a hundred-odd sliding along them. Tests that describe a
 * view as a plain list describe the one shape where those two happen to agree, and every bug this
 * helper exists for lives in the gap: an index that means a slot used to read a row, and an index
 * found among the rows used to name a slot.
 *
 * [loadedPages] left null loads everything, which is the shape a short view really does have.
 */
internal fun viewWindow(
    rows: List<BookmarkEntity>,
    pageSize: Int = BookmarkWindow.DEFAULT_PAGE_SIZE,
    loadedPages: Set<Int>? = null,
    generation: Int = 0,
    prepended: List<BookmarkEntity> = emptyList()
): BookmarkWindow = BookmarkWindow(
    viewTotal = rows.size,
    pageSize = pageSize,
    pages = rows.chunked(pageSize).withIndex()
        .filter { (page, _) -> loadedPages == null || page in loadedPages }
        .associate { (page, contents) -> page to contents },
    prepended = prepended,
    generation = generation,
    resolved = true
)
