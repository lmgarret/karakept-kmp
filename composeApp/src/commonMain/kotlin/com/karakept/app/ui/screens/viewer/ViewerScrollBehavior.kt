package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.utils.HapticUtils

/**
 * Tracks state for the pull-past-end action indicator.
 *
 * @property overscrollPx How far the user has pulled past the end (0..triggerThresholdPx).
 * @property triggered Whether the threshold has been reached and the action was fired.
 * @property action The configured action.
 */
data class ScrollEndActionState(
    val overscrollPx: Float,
    val triggered: Boolean,
    val action: SwipeAction
)

/**
 * Remembers FAB visibility state based on scroll direction and scroll-end action functionality.
 *
 * @param scrollEndAction The action to perform when the user pulls past the end of the article.
 *   Use [SwipeAction.NONE] to disable this feature.
 * @param onScrollEndAction Called when the scroll-end threshold is reached with the configured action.
 * @param onScrollEndActionStateChanged Called with updated [ScrollEndActionState] so the UI can
 *   render the pull indicator.
 */
@Composable
internal fun rememberFabVisibilityState(
    scrollState: LazyListState,
    fabExpanded: Boolean,
    autoMarkReadOnScroll: Boolean,
    isBookmarkRead: Boolean,
    onMarkAsRead: () -> Unit,
    onUnmarkAsRead: () -> Unit,
    onShowSnackbarWithUndo: () -> Unit,
    scrollEndAction: SwipeAction = SwipeAction.NONE,
    onScrollEndAction: ((SwipeAction) -> Unit)? = null,
    onScrollEndActionStateChanged: ((ScrollEndActionState) -> Unit)? = null
): Boolean {
    var previousScrollOffset by remember { mutableStateOf(0) }
    var fabVisible by remember { mutableStateOf(true) }
    var hasTriggeredAutoRead by remember { mutableStateOf(false) }
    var maxScrollReached by remember { mutableStateOf(0) }
    var hasTriggeredScrollEndAction by remember { mutableStateOf(false) }
    var overscrollAccumulator by remember { mutableFloatStateOf(0f) }

    // Threshold in virtual scroll units to trigger the action
    val triggerThreshold = 300

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

        // Check for auto-mark read (legacy boolean path)
        // Only trigger when user has scrolled significantly AND is at the bottom
        if (!hasTriggeredAutoRead && autoMarkReadOnScroll && !isBookmarkRead && scrollEndAction == SwipeAction.NONE) {
            val layoutInfo = scrollState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItemsInfo = layoutInfo.visibleItemsInfo

            if (maxScrollReached < 1000) {
                return@LaunchedEffect
            }

            if (visibleItemsInfo.isNotEmpty() && totalItems > 0) {
                val lastVisibleItem = visibleItemsInfo.last()
                val isLastItem = lastVisibleItem.index == totalItems - 1

                if (isLastItem) {
                    val itemBottom = lastVisibleItem.offset + lastVisibleItem.size
                    val viewportBottom = layoutInfo.viewportEndOffset

                    if (itemBottom <= viewportBottom + 200 && itemBottom > 0) {
                        onMarkAsRead()
                        onShowSnackbarWithUndo()
                        hasTriggeredAutoRead = true
                    }
                }
            }
        }

        // Pull-past-end action: detect when user is at the bottom and accumulate overscroll
        if (scrollEndAction != SwipeAction.NONE && !hasTriggeredScrollEndAction && maxScrollReached >= 500) {
            val layoutInfo = scrollState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItemsInfo = layoutInfo.visibleItemsInfo

            if (visibleItemsInfo.isNotEmpty() && totalItems > 0) {
                val lastVisibleItem = visibleItemsInfo.last()
                val isLastItem = lastVisibleItem.index == totalItems - 1

                if (isLastItem) {
                    val itemBottom = lastVisibleItem.offset + lastVisibleItem.size
                    val viewportBottom = layoutInfo.viewportEndOffset
                    val distancePastEnd = viewportBottom - itemBottom

                    if (distancePastEnd > 0 && scrollingDown) {
                        // User is at the bottom and trying to scroll further down
                        overscrollAccumulator = (overscrollAccumulator + distancePastEnd.toFloat())
                            .coerceIn(0f, triggerThreshold.toFloat())

                        val progress = overscrollAccumulator / triggerThreshold
                        onScrollEndActionStateChanged?.invoke(
                            ScrollEndActionState(
                                overscrollPx = overscrollAccumulator,
                                triggered = false,
                                action = scrollEndAction
                            )
                        )

                        // Haptic feedback at 50% threshold
                        if (progress >= 0.5f && overscrollAccumulator - distancePastEnd < triggerThreshold * 0.5f) {
                            HapticUtils.performMedium()
                        }

                        if (overscrollAccumulator >= triggerThreshold) {
                            // Trigger the action
                            HapticUtils.performMedium()
                            onScrollEndAction?.invoke(scrollEndAction)
                            hasTriggeredScrollEndAction = true
                            onScrollEndActionStateChanged?.invoke(
                                ScrollEndActionState(
                                    overscrollPx = triggerThreshold.toFloat(),
                                    triggered = true,
                                    action = scrollEndAction
                                )
                            )
                        }
                    } else if (!scrollingDown) {
                        // Reset accumulator when scrolling back up
                        overscrollAccumulator = 0f
                        onScrollEndActionStateChanged?.invoke(
                            ScrollEndActionState(
                                overscrollPx = 0f,
                                triggered = false,
                                action = scrollEndAction
                            )
                        )
                    }
                } else {
                    // Not at last item, reset
                    overscrollAccumulator = 0f
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
