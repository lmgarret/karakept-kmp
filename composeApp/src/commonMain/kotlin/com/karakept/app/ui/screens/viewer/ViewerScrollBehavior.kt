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
 * Calculates reading progress (0.0 to 1.0) based on scroll position in the content list.
 *
 * Progress represents how far through the reading content (items after the hero banner) the
 * user has scrolled. Item heights are cached as items become visible so that scrolled distance
 * is computed from actual measured heights rather than a per-frame average, which avoids the
 * large non-linear jump that occurs when the tall hero banner scrolls off screen and the average
 * height estimate suddenly skews to match only the content body height.
 *
 * Item 0 is always the hero banner; reading progress is 0% until the user scrolls past it.
 */
@Composable
internal fun rememberReadingProgress(scrollState: LazyListState): Float {
    // Cache item heights as they become visible. Updated as a side-effect inside derivedStateOf;
    // this is safe because the cache is only written and then read within the same execution, and
    // the derivedStateOf re-executes on every scrollState.layoutInfo change (i.e. every scroll).
    val itemHeights = remember { mutableMapOf<Int, Int>() }

    val progress by remember(scrollState) {
        derivedStateOf {
            val layoutInfo = scrollState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val visibleItemsInfo = layoutInfo.visibleItemsInfo

            if (totalItemsCount == 0 || visibleItemsInfo.isEmpty()) {
                return@derivedStateOf 0f
            }

            // Update the height cache for every currently visible item.
            for (item in visibleItemsInfo) {
                itemHeights[item.index] = item.size
            }

            val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

            // If the last item's bottom is fully within the viewport, progress is 100%.
            val lastVisibleItem = visibleItemsInfo.last()
            if (lastVisibleItem.index == totalItemsCount - 1) {
                val lastItemBottom = lastVisibleItem.offset + lastVisibleItem.size
                if (lastItemBottom <= layoutInfo.viewportEndOffset) {
                    return@derivedStateOf 1f
                }
            }

            val firstVisible = visibleItemsInfo.first()

            // Item 0 is the hero banner. Reading progress is defined over items 1..n only.
            // While the hero is still (partially) on screen, report 0% progress.
            val knownContentHeight = itemHeights.entries.filter { it.key > 0 }.sumOf { it.value }
            val knownContentCount = itemHeights.count { it.key > 0 }
            val avgContentHeight = if (knownContentCount > 0) knownContentHeight / knownContentCount else 0

            if (avgContentHeight <= 0) return@derivedStateOf 0f

            val contentItemsCount = totalItemsCount - 1 // hero excluded
            val estimatedContentTotalHeight =
                knownContentHeight + (contentItemsCount - knownContentCount) * avgContentHeight
            val scrollableRange = (estimatedContentTotalHeight - viewportSize).toFloat()

            if (scrollableRange <= 0f) return@derivedStateOf 1f

            // Calculate how far into the reading content (past the hero) the user has scrolled.
            val scrolledDistance: Float = when {
                firstVisible.index == 0 -> {
                    // Still scrolling through the hero banner — no reading progress yet.
                    0f
                }
                else -> {
                    // Sum heights of content items (index 1..firstVisible.index-1) already scrolled past,
                    // then add the current partial scroll offset into the first visible content item.
                    var dist = scrollState.firstVisibleItemScrollOffset.toFloat()
                    for (i in 1 until firstVisible.index) {
                        dist += (itemHeights[i] ?: avgContentHeight).toFloat()
                    }
                    dist
                }
            }

            (scrolledDistance / scrollableRange).coerceIn(0f, 1f)
        }
    }
    return progress
}

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
