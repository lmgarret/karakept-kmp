package com.karakept.app.ui.screens.viewer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.Bookmark
import com.karakept.app.ui.components.AnimatedVisibilityOrPlain
import com.karakept.app.ui.components.EinkAwareSmallFab
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.theme.LocalEinkMode
import getPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ViewerFab(
    visible: Boolean,
    animated: Boolean,
    loadingState: BookmarkLoadingState,
    ui: ViewerUiState,
    screenModel: BookmarkViewerScreenModel,
    onShareClick: (Bookmark) -> Unit,
    onOpenInBrowserClick: (Bookmark) -> Unit
) {
    AnimatedVisibilityOrPlain(visible = visible, animated = animated) {
        if (loadingState is BookmarkLoadingState.FullyLoaded) {
            val fullyLoadedState: BookmarkLoadingState.FullyLoaded = loadingState
            BookmarkFabMenu(
                expanded = ui.fabExpanded, onExpandedChange = { ui.fabExpanded = it },
                bookmark = fullyLoadedState.bookmark,
                onFavoriteClick = {
                    screenModel.toggleBookmarkFavorite(fullyLoadedState.bookmark)
                    ui.fabExpanded = false
                },
                onArchiveClick = {
                    screenModel.toggleBookmarkArchive(fullyLoadedState.bookmark)
                    ui.fabExpanded = false
                },
                onReadClick = {
                    screenModel.toggleBookmarkRead(fullyLoadedState.bookmark)
                    ui.fabExpanded = false
                },
                onShareClick = {
                    onShareClick(fullyLoadedState.bookmark)
                    ui.fabExpanded = false
                },
                onOpenInBrowserClick = {
                    onOpenInBrowserClick(fullyLoadedState.bookmark)
                    ui.fabExpanded = false
                }
            )
        }
    }
}

@Composable
internal fun BoxScope.ViewerScrollToTopButton(
    visible: Boolean,
    showSearch: Boolean,
    animationsDisabled: Boolean,
    onClick: () -> Unit
) {
    val scrollToTopTarget = if (showSearch && !getPlatform().isDesktop) 76.dp else 16.dp
    val animatedScrollToTopPadding by animateDpAsState(
        targetValue = scrollToTopTarget,
        animationSpec = tween(300),
        label = "scrollToTopBottomPadding"
    )
    val scrollToTopBottomPadding =
        if (animationsDisabled) scrollToTopTarget else animatedScrollToTopPadding
    AnimatedVisibilityOrPlain(
        visible = visible,
        animated = !animationsDisabled,
        modifier = Modifier.align(Alignment.BottomStart)
            .navigationBarsPadding()
            .padding(start = 16.dp, bottom = scrollToTopBottomPadding)
    ) {
        EinkAwareSmallFab(onClick = onClick) {
            Icon(
                imageVector = AppIcons.Default.ArrowUpward,
                contentDescription = "Scroll to top"
            )
        }
    }
}

@Composable
internal fun HighlightDimmingOverlay(ui: ViewerUiState) {
    var overlayRootOffset by remember { mutableStateOf(Offset.Zero) }
    // A 60% black scrim is a page-sized ink dump that ghosts for several turns,
    // and it leaves the rest of the article barely legible on a monochrome
    // panel. Outlining the selection says the same thing for a hundredth of
    // the ink.
    val outlineOnly = LocalEinkMode.current.highContrast
    val outlineColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { coords -> overlayRootOffset = coords.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { ui.selectedHighlightId = null } }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = 0.99f)) {
            if (!outlineOnly) drawRect(Color.Black.copy(alpha = 0.6f))
            val pos = ui.highlightPosition
            val posPath = pos?.path
            if (posPath != null) {
                withTransform({ translate(pos.rootOffset.x - overlayRootOffset.x, pos.rootOffset.y - overlayRootOffset.y) }) {
                    if (outlineOnly) {
                        drawPath(
                            path = posPath,
                            color = outlineColor,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    } else {
                        drawPath(path = posPath, color = Color.Transparent, blendMode = BlendMode.Clear)
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.ViewerSearchBarHost(ui: ViewerUiState, scope: CoroutineScope) {
    val isDesktop = getPlatform().isDesktop
    ReaderSearchBar(
        visible = ui.showSearch,
        query = ui.searchQuery,
        onQueryChange = { query ->
            ui.searchQuery = query
            ui.currentMatchIndex = 0
            ui.lastScrolledMatchIndex = -1
        },
        matchCount = ui.searchMatches.size,
        currentMatchIndex = ui.currentMatchIndex,
        onPrevious = {
            if (ui.searchMatches.isNotEmpty()) {
                ui.lastScrolledMatchIndex = -1
                ui.currentMatchIndex = if (ui.currentMatchIndex > 0) ui.currentMatchIndex - 1 else ui.searchMatches.size - 1
            }
        },
        onNext = {
            if (ui.searchMatches.isNotEmpty()) {
                ui.lastScrolledMatchIndex = -1
                ui.currentMatchIndex = if (ui.currentMatchIndex < ui.searchMatches.size - 1) ui.currentMatchIndex + 1 else 0
            }
        },
        onClose = {
            ui.showSearch = false
            ui.searchQuery = ""
            ui.searchMatches = emptyList()
            ui.lastScrolledMatchIndex = -1
            scope.launch { kotlinx.coroutines.delay(100); ui.contentFocusRequester.requestFocus() }
        },
        maxWidth = if (isDesktop) 520.dp else 360.dp,
        modifier = if (isDesktop)
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        else
            Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(end = 80.dp, bottom = 14.dp, start = 16.dp)
    )
}
