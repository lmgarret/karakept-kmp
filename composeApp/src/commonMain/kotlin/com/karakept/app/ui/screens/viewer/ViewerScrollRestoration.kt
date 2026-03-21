package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.utils.AppLogger
import com.karakept.app.ui.screens.BookmarkLoadingState
import kotlinx.coroutines.delay

/**
 * State holder for scroll guard and reading progress restoration.
 */
class ScrollRestorationState(
    val safeScrollToItem: suspend (Int, Int) -> Unit,
    val hasRestoredScroll: Boolean,
    val contentRendered: Boolean,
    val onContentRendered: () -> Unit
)

/**
 * Composable that installs a scroll guard to prevent unintended jumps
 * (e.g. from SelectionContainer/Focus), and handles reading progress
 * restoration once content is rendered.
 *
 * Returns a [ScrollRestorationState] that callers use to coordinate
 * content rendering and scroll position.
 */
@Composable
fun rememberScrollRestoration(
    scrollState: LazyListState,
    loadingState: BookmarkLoadingState,
    viewerMode: ViewerMode,
    trackReadingProgress: Boolean,
    serverProgressChecked: Boolean,
    contentFetchAttempted: Boolean,
    scrollToHighlightId: String?
): ScrollRestorationState {
    // --- SCROLL GUARD ---
    var approvedIndex by remember { mutableStateOf(0) }
    var approvedOffset by remember { mutableStateOf(0) }

    val safeScrollToItem: suspend (Int, Int) -> Unit = { index, offset ->
        approvedIndex = index
        approvedOffset = offset
        scrollState.scrollToItem(index, offset)
        kotlinx.coroutines.yield()
        approvedIndex = scrollState.firstVisibleItemIndex
        approvedOffset = scrollState.firstVisibleItemScrollOffset
    }

    LaunchedEffect(scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset) {
        if (scrollState.isScrollInProgress) {
            approvedIndex = scrollState.firstVisibleItemIndex
            approvedOffset = scrollState.firstVisibleItemScrollOffset
        } else {
            val jumped = (kotlin.math.abs(scrollState.firstVisibleItemIndex - approvedIndex) > 0) ||
                         (kotlin.math.abs(scrollState.firstVisibleItemScrollOffset - approvedOffset) > 50)
            if (jumped) {
                AppLogger.w("ViewerScrollRestoration", "Unintended jump to ${scrollState.firstVisibleItemIndex}:${scrollState.firstVisibleItemScrollOffset}. " +
                    "Snapping back to $approvedIndex:$approvedOffset")
                scrollState.scrollToItem(approvedIndex, approvedOffset)
            } else {
                approvedIndex = scrollState.firstVisibleItemIndex
                approvedOffset = scrollState.firstVisibleItemScrollOffset
            }
        }
    }

    // --- CONTENT RENDERED TRACKING ---
    var contentRendered by remember { mutableStateOf(false) }

    LaunchedEffect(loadingState) {
        if (loadingState !is BookmarkLoadingState.FullyLoaded) {
            contentRendered = false
        }
    }

    // --- READING PROGRESS RESTORATION ---
    var hasRestoredScroll by remember { mutableStateOf(false) }
    val isNativeRenderer = viewerMode == ViewerMode.READER

    LaunchedEffect(loadingState, trackReadingProgress, contentRendered, serverProgressChecked, contentFetchAttempted) {
        if (scrollToHighlightId != null) {
            hasRestoredScroll = true
            return@LaunchedEffect
        }
        if (!hasRestoredScroll && trackReadingProgress && loadingState is BookmarkLoadingState.FullyLoaded) {
            val bookmark = (loadingState as BookmarkLoadingState.FullyLoaded).bookmark
            val hasMeaningfulProgress = bookmark.readingProgress > 0.02f
            if (hasMeaningfulProgress && !bookmark.content.isNullOrBlank()) {
                if (contentRendered) {
                    if (isNativeRenderer) {
                        kotlinx.coroutines.yield()
                    } else {
                        delay(300)
                    }
                    val hasExactPosition = bookmark.readingScrollIndex > 0 || bookmark.readingScrollOffset > 0
                    val (targetIndex, targetOffset) = if (hasExactPosition) {
                        bookmark.readingScrollIndex to bookmark.readingScrollOffset
                    } else {
                        val layoutInfo = scrollState.layoutInfo
                        val totalHeight = layoutInfo.visibleItemsInfo.sumOf { it.size }
                            .coerceAtLeast(layoutInfo.viewportEndOffset)
                        val contentItemIndex = layoutInfo.totalItemsCount - 1
                        val contentItem = layoutInfo.visibleItemsInfo.lastOrNull()
                        val contentHeight = contentItem?.size ?: totalHeight
                        val estimatedOffset = (contentHeight * bookmark.readingProgress).toInt()
                        contentItemIndex.coerceAtLeast(0) to estimatedOffset
                    }
                    safeScrollToItem(targetIndex, targetOffset)
                    if (!isNativeRenderer) {
                        for (attempt in 1..3) {
                            val offsetOk = targetOffset < 200 ||
                                scrollState.firstVisibleItemScrollOffset >= targetOffset / 3
                            if (scrollState.firstVisibleItemIndex == targetIndex && offsetOk) break
                            delay(250)
                            safeScrollToItem(targetIndex, targetOffset)
                        }
                    }
                    hasRestoredScroll = true
                }
            } else if (!hasMeaningfulProgress) {
                if (serverProgressChecked) {
                    hasRestoredScroll = true
                }
            } else if (hasMeaningfulProgress && bookmark.content.isNullOrBlank() && contentFetchAttempted) {
                hasRestoredScroll = true
            }
        }
    }

    return ScrollRestorationState(
        safeScrollToItem = safeScrollToItem,
        hasRestoredScroll = hasRestoredScroll,
        contentRendered = contentRendered,
        onContentRendered = { contentRendered = true }
    )
}
