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
    onUnmarkAsRead: () -> Unit,
    onShowSnackbarWithUndo: () -> Unit
): Boolean {
    var previousScrollOffset by remember { mutableStateOf(0) }
    var fabVisible by remember { mutableStateOf(true) }
    var hasTriggeredAutoRead by remember { mutableStateOf(false) }
    var maxScrollReached by remember { mutableStateOf(0) }

    LaunchedEffect(scrollState.firstVisibleItemScrollOffset, scrollState.firstVisibleItemIndex) {
        val currentOffset = scrollState.firstVisibleItemIndex * 1000 + scrollState.firstVisibleItemScrollOffset
        val scrollingDown = currentOffset > previousScrollOffset

        // Hide FAB when scrolling down, show when scrolling up
        if (currentOffset > 100) { // Only hide after scrolling past 100px
            fabVisible = !scrollingDown || fabExpanded // Keep visible if expanded
        } else {
            fabVisible = true // Always show at top
        }

        // Track the maximum scroll distance reached
        if (currentOffset > maxScrollReached) {
            maxScrollReached = currentOffset
        }

        previousScrollOffset = currentOffset

        // Check for auto-mark read
        // Only trigger when user has scrolled significantly AND is at the bottom
        if (!hasTriggeredAutoRead && autoMarkReadOnScroll && !isBookmarkRead) {
            val layoutInfo = scrollState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItemsInfo = layoutInfo.visibleItemsInfo

            // CRITICAL: Only consider marking as read if user has scrolled at least 1000px
            // This prevents marking as read immediately when opening short articles
            // or when content is still loading
            if (maxScrollReached < 1000) {
                return@LaunchedEffect
            }

            if (visibleItemsInfo.isNotEmpty() && totalItems > 0) {
                // The last item is the content body which contains the HTML
                // We want to detect when the user has scrolled to near the bottom of this item
                val lastVisibleItem = visibleItemsInfo.last()
                val isLastItem = lastVisibleItem.index == totalItems - 1

                if (isLastItem) {
                    // Calculate how much of the last item has been scrolled through
                    // lastVisibleItem.offset is negative when the item is scrolled up past the top of viewport
                    // lastVisibleItem.size is the total height of the item
                    val itemBottom = lastVisibleItem.offset + lastVisibleItem.size
                    val viewportBottom = layoutInfo.viewportEndOffset

                    // Only trigger if:
                    // 1. User has scrolled at least 1000px (checked above)
                    // 2. The bottom of the content is visible (itemBottom <= viewportBottom)
                    // 3. We're within 200px of the absolute bottom
                    // This ensures the user has actually scrolled through the HTML content
                    if (itemBottom <= viewportBottom + 200 && itemBottom > 0) {
                        onMarkAsRead()
                        onShowSnackbarWithUndo()
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
