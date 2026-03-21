package com.karakept.app.ui.screens

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject

data class BookmarkViewerScreen(
    val bookmarkId: Long,
    val scrollToHighlightId: String? = null
) : Screen {
    // Each bookmark needs its own Voyager key so that koinScreenModel returns a fresh
    // ScreenModel per bookmark.  Without this, navigating from one viewer to another
    // (without popping the first) reuses the stale ScreenModel, leaving the screen blank.
    override val key = "BookmarkViewerScreen-$bookmarkId-${scrollToHighlightId.orEmpty()}"

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<BookmarkViewerScreenModel>()
        val mainScreenModel = koinInject<MainScreenModel>()

        BookmarkViewerContent(
            bookmarkId = bookmarkId,
            scrollToHighlightId = scrollToHighlightId,
            screenModel = screenModel,
            onBack = { navigator.pop() },
            onTagFilterApply = { tag ->
                mainScreenModel.applyTagFilter(tag, bookmarkId)
                navigator.pop()
            }
        )
    }
}
