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
import androidx.compose.ui.unit.dp

/**
 * Calculates reading progress (0.0 to 1.0) based on scroll position in the content list.
 *
 * Progress starts at 0 exactly when the sticky title bar becomes visible (i.e. when the hero
 * banner has scrolled far enough that the toolbar overlaps it), and reaches 1 at the end of the
 * content. A single absolute-scroll-position coordinate is used throughout, so there are no
 * resets or jumps when the first-visible item index changes.
 *
 * Item heights are cached as items become visible so that the absolute scroll position can be
 * reconstructed accurately even for items that have already scrolled off screen.
 */
@Composable
internal fun rememberReadingProgress(
    scrollState: LazyListState,
    bannerHeight: Dp = 320.dp,
    toolbarHeight: Dp = 56.dp
): Float {
    // Cache item heights as they become visible. Updated as a side-effect inside derivedStateOf;
    // this is safe because the cache is only written and then read within the same execution, and
    // the derivedStateOf re-executes on every scrollState.layoutInfo change (i.e. every scroll).
    val itemHeights = remember { mutableMapOf<Int, Int>() }

    val density = LocalDensity.current
    // Pre-compute pixel values outside derivedStateOf so they are stable captures.
    val toolbarHeightPx = with(density) { toolbarHeight.toPx() }
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()

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

            // Hero height — use measured value if available, fall back to the declared banner height.
            val heroHeight = itemHeights[0] ?: with(density) { bannerHeight.toPx().toInt() }

            // The progress bar should start moving exactly when the sticky title bar appears.
            // This threshold mirrors the one in rememberStickyTitleVisibility:
            //   showStickyTitleThreshold = bannerHeightPx - toolbarHeightPx - statusBarInsets
            val startThreshold = (heroHeight - toolbarHeightPx - statusBarHeightPx)
                .toInt().coerceAtLeast(0)

            // Compute absolute scroll position: total pixels scrolled from the very top.
            val firstVisible = visibleItemsInfo.first()
            val absoluteScrollPos: Int = if (firstVisible.index == 0) {
                scrollState.firstVisibleItemScrollOffset
            } else {
                var pos = scrollState.firstVisibleItemScrollOffset
                for (i in 0 until firstVisible.index) {
                    pos += itemHeights[i] ?: 0
                }
                pos
            }

            // Before the title bar appears, keep progress at 0 (avoids the initial-load flash
            // where the bar briefly shows as full before content heights are measured).
            if (absoluteScrollPos < startThreshold) return@derivedStateOf 0f

            // Estimate total content height from known item heights.
            val knownContentHeight = itemHeights.entries.filter { it.key > 0 }.sumOf { it.value }
            val knownContentCount = itemHeights.count { it.key > 0 }
            if (knownContentCount == 0) return@derivedStateOf 0f

            val avgContentHeight = knownContentHeight / knownContentCount
            val contentItemsCount = totalItemsCount - 1 // hero excluded
            val estimatedContentTotalHeight =
                knownContentHeight + (contentItemsCount - knownContentCount) * avgContentHeight

            val totalHeight = heroHeight + estimatedContentTotalHeight
            val totalScrollableRange = (totalHeight - viewportSize).toFloat()
            if (totalScrollableRange <= 0f) return@derivedStateOf 0f

            // Progress is measured from the start threshold to the end of scrollable content.
            val progressRange = totalScrollableRange - startThreshold
            if (progressRange <= 0f) return@derivedStateOf 0f

            val scrolledPastThreshold = (absoluteScrollPos - startThreshold).toFloat()
            (scrolledPastThreshold / progressRange).coerceIn(0f, 1f)
        }
    }
    return progress
}

/**
 * Remembers FAB visibility state based on scroll direction.
 * Hides the FAB when scrolling down and shows it when scrolling up.
 */
@Composable
internal fun rememberFabVisibilityState(
    scrollState: LazyListState,
    fabExpanded: Boolean
): Boolean {
    var previousScrollOffset by remember { mutableStateOf(0) }
    var fabVisible by remember { mutableStateOf(true) }

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
