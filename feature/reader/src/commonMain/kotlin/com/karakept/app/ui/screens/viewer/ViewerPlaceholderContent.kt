package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.karakept.app.ui.components.LoadingDotsIndicator
import com.karakept.app.ui.components.reader.BookmarkContentLoader
import getPlatform

@Composable
internal fun ViewerInitialContent(
    state: BookmarkLoadingState,
    initialTitle: String?,
    initialUrl: String?,
    toolbarHeight: Dp,
    padding: PaddingValues,
    onBack: () -> Unit
) {
    // The caller (bookmark list, highlights list, ...) often already has the title
    // and url in memory — passed through as initialTitle/initialUrl — so the top bar
    // can render immediately instead of waiting on this screen's own DB query to
    // resolve. Without it there is nothing to show up top, so fall back to the
    // full-screen skeleton/dots as before.
    if (initialTitle != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            ViewerTopBar(
                title = initialTitle,
                url = initialUrl ?: "",
                showStickyTitle = true,
                showMenu = false,
                toolbarHeight = toolbarHeight,
                readingProgress = 0f,
                onBackClick = onBack,
                onMenuToggle = {},
                onAppearanceClick = {},
                onMoveToListClick = {},
                onEditTagsClick = {},
                onRefreshClick = {},
                onDeleteClick = {},
                isDesktop = getPlatform().isDesktop,
                bookmark = null
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                LoadingDotsIndicator(label = "Loading…")
            }
        }
    } else {
        // fillMaxSize so the e-ink dots land centered like every other loading slot —
        // without a height the Box wraps them and they sit pinned to the top, then
        // jump to the middle as soon as the next loading state takes over.
        BookmarkContentLoader(
            loadingState = state,
            modifier = Modifier.padding(padding).fillMaxSize()
        )
    }
}

@Composable
internal fun ViewerErrorContent(message: String, padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text(text = "Error: $message", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
}
