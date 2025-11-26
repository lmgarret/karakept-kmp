package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity

sealed class BookmarkLoadingState {
    data object Initial : BookmarkLoadingState()
    data class FullyLoaded(
        val bookmark: BookmarkEntity
    ) : BookmarkLoadingState()
    data class Error(val message: String) : BookmarkLoadingState()
}
