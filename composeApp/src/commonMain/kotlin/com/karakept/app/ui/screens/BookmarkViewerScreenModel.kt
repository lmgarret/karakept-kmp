package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.entity.BookmarkEntity

class BookmarkViewerScreenModel(
    private val bookmarkDao: BookmarkDao
) : ScreenModel {
    suspend fun getBookmark(id: Long): BookmarkEntity? {
        return bookmarkDao.getBookmarkById(id)
    }
}
