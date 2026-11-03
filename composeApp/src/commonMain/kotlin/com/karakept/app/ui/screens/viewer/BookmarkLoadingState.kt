package com.karakept.app.ui.screens.viewer

import com.karakept.app.data.model.Bookmark

sealed class BookmarkLoadingState {
    data object Initial : BookmarkLoadingState()
    data class FullyLoaded(
        val bookmark: Bookmark
    ) : BookmarkLoadingState()
    data class Error(val message: String) : BookmarkLoadingState()
}
