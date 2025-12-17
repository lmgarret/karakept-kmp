package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Remembers FAB visibility state based on scroll direction and auto-mark-as-read functionality
 */
@Composable
internal fun rememberFabVisibilityState(
    scrollState: LazyListState,
    fabExpanded: Boolean,
    autoMarkReadOnScroll: Boolean,
    isBookmarkRead: Boolean,
    onMarkAsRead: () -> Unit,
    onSnackbarMessage: (String) -> Unit
): Boolean {
    var previousScrollOffset by remember { mutableStateOf(0) }
    var fabVisible by remember { mutableStateOf(true) }
    var hasTriggeredAutoRead by remember { mutableStateOf(false) }

    LaunchedEffect(scrollState.firstVisibleItemScrollOffset, scrollState.firstVisibleItemIndex) {
        val currentOffset = scrollState.firstVisibleItemIndex * 1000 + scrollState.firstVisibleItemScrollOffset
        val scrollingDown = currentOffset > previousScrollOffset

        // Hide FAB when scrolling down, show when scrolling up
        if (currentOffset > 100) { // Only hide after scrolling past 100px
            fabVisible = !scrollingDown || fabExpanded // Keep visible if expanded
        } else {
            fabVisible = true // Always show at top
        }

        previousScrollOffset = currentOffset

        // Check for auto-mark read
        if (!hasTriggeredAutoRead && autoMarkReadOnScroll && !isBookmarkRead) {
            val layoutInfo = scrollState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItemsInfo = layoutInfo.visibleItemsInfo

            if (visibleItemsInfo.isNotEmpty()) {
                val lastVisibleItem = visibleItemsInfo.last()
                // Check if we are near the end (last item is visible AND its bottom edge is near the viewport bottom)
                val isLastItem = lastVisibleItem.index == totalItems - 1

                if (isLastItem) {
                    // layoutInfo.viewportEndOffset gives the height of the viewport
                    // lastVisibleItem.offset is the top position relative to viewport start
                    // lastVisibleItem.size is the height of the item
                    // So (offset + size) is the position of the bottom edge relative to viewport start
                    val itemBottom = lastVisibleItem.offset + lastVisibleItem.size
                    val viewportBottom = layoutInfo.viewportEndOffset

                    // Trigger if the bottom of the content is within the viewport (with a small buffer of 50px)
                    if (itemBottom <= viewportBottom + 50) {
                        onMarkAsRead()
                        onSnackbarMessage("Marked as read")
                        hasTriggeredAutoRead = true
                    }
                }
            }
        }
    }

    return fabVisible
}

/**
 * Remembers sticky title visibility state based on scroll position and banner height
 */
@Composable
internal fun rememberStickyTitleVisibility(
    scrollState: LazyListState,
    bannerHeight: Dp,
    toolbarHeight: Dp
): Boolean {
    val density = LocalDensity.current
    val bannerHeightPx = with(density) { bannerHeight.toPx() }
    val toolbarHeightPx = with(density) { toolbarHeight.toPx() }
    val statusBarInsets = WindowInsets.statusBars.getTop(density)
    val showStickyTitleThreshold = (bannerHeightPx - toolbarHeightPx - statusBarInsets).toInt()

    val showStickyTitle by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > showStickyTitleThreshold
        }
    }

    return showStickyTitle
}
