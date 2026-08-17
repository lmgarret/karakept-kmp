package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.ui.utils.blankBelowContent
import com.karakept.app.utils.AppLogger
import com.karakept.app.ui.screens.BookmarkLoadingState
import kotlinx.coroutines.delay

/**
 * State holder for scroll guard and reading progress restoration.
 */
class ScrollRestorationState(
    val safeScrollToItem: suspend (Int, Int) -> Unit,
    /**
     * Accepts wherever the list currently sits as intentional.
     *
     * An instant scroll finishes inside a single `scroll {}` block, so by the time the guard's
     * effect runs `isScrollInProgress` is already false and the move looks like an unintended
     * jump — the guard would snap it straight back. Animated scrolls survive only because they
     * span frames. Anything that moves the reader instantly has to call this.
     */
    val approveCurrentPosition: () -> Unit,
    val hasRestoredScroll: Boolean,
    val contentRendered: Boolean,
    val onContentRendered: () -> Unit
)

/**
 * Whether the reader may still act on a saved reading position.
 *
 * [hasRestoredScroll] latches for two different reasons — a position was restored, or there
 * was nothing to restore — and only the first is final. A cross-device pull that lands after
 * the reader gave up waiting for it must still be honoured, which is safe exactly while the
 * article has not moved: the reader is looking at the top either way, so nothing is
 * interrupted. Once the reader has scrolled off the top, or a position has actually been
 * restored, the door is closed for good.
 */
internal fun mayRestoreReadingPosition(
    hasRestoredScroll: Boolean,
    hasScrolledToSavedPosition: Boolean,
    stillAtTopOfArticle: Boolean
): Boolean = !hasRestoredScroll || (!hasScrolledToSavedPosition && stillAtTopOfArticle)

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
    scrollToHighlightId: String?,
    /** Chrome painted over the foot of the list; excluded when deciding what "the bottom" is. */
    obscuredBottomPx: Int = 0
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

    val approveCurrentPosition: () -> Unit = {
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
    // Separate from [hasRestoredScroll], which also latches for "there was nothing to
    // restore". Only an actual scroll to a saved position closes the door for good.
    var hasScrolledToSavedPosition by remember { mutableStateOf(false) }
    val isNativeRenderer = viewerMode == ViewerMode.READER

    LaunchedEffect(loadingState, trackReadingProgress, contentRendered, serverProgressChecked, contentFetchAttempted) {
        if (scrollToHighlightId != null) {
            hasRestoredScroll = true
            return@LaunchedEffect
        }
        val mayRestore = mayRestoreReadingPosition(
            hasRestoredScroll = hasRestoredScroll,
            hasScrolledToSavedPosition = hasScrolledToSavedPosition,
            stillAtTopOfArticle = scrollState.firstVisibleItemIndex == 0 &&
                scrollState.firstVisibleItemScrollOffset == 0
        )

        if (mayRestore && trackReadingProgress && loadingState is BookmarkLoadingState.FullyLoaded) {
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
                    // Trailing page padding leaves blank space after the article so a page *turn*
                    // can land on the previous page's handover. It is not somewhere to come to
                    // rest: a finished bookmark restores to `contentHeight * 1.0`, which clamps
                    // into that blank space and reopens the article on a near-empty page. Pull
                    // back until the text reaches the bottom edge again.
                    val blank = blankBelowContent(scrollState.layoutInfo, obscuredBottomPx)
                    if (blank > 0) {
                        // scrollBy clamps, so an article shorter than the viewport — where the
                        // blank space is unavoidable — simply settles back at the top.
                        scrollState.scroll { scrollBy(-blank.toFloat()) }
                        approveCurrentPosition()
                    }
                    hasScrolledToSavedPosition = true
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
        approveCurrentPosition = approveCurrentPosition,
        hasRestoredScroll = hasRestoredScroll,
        contentRendered = contentRendered,
        onContentRendered = { contentRendered = true }
    )
}
