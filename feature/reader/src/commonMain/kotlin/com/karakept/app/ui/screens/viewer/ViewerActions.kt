package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key as keyboardKey
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.UriHandler
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.ui.components.reader.GalleryViewerState
import com.karakept.app.ui.components.reader.ReaderSnapRegistry
import com.karakept.app.ui.input.PageTurnDispatcher
import com.karakept.app.ui.input.handleDesktopPageKey
import com.karakept.app.ui.utils.READER_MAX_SNAP_FRACTION
import com.karakept.app.ui.utils.computeTurnSnapAdjustment
import com.karakept.app.ui.utils.pageWorthTurning
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.ShareUtils
import getPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun contentBodyIndexFor(bookmark: Bookmark): Int =
    if (!bookmark.description.isNullOrBlank()) 2 else 1

internal fun highlightCenteringOffset(
    layoutInfo: LazyListLayoutInfo,
    contentBodyIndex: Int,
    highlightY: Float,
    panelHeightPx: Int
): Int {
    val contentBodyItem = layoutInfo.visibleItemsInfo.find { it.index == contentBodyIndex }
    val contentBodyTop = contentBodyItem?.offset ?: 0
    val highlightOffsetInItem = (highlightY - contentBodyTop).toInt()
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val availableHeight = viewportHeight - panelHeightPx
    return maxOf(0, highlightOffsetInItem - availableHeight / 2)
}

internal fun searchMatchScrollOffset(
    layoutInfo: LazyListLayoutInfo,
    contentBodyIndex: Int,
    matchY: Float
): Int {
    val itemTop = layoutInfo.visibleItemsInfo.find { it.index == contentBodyIndex }?.offset ?: 0
    val matchInItem = (matchY.toInt() - itemTop).coerceAtLeast(0)
    val vpHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    return maxOf(0, matchInItem - vpHeight / 4)
}

internal fun readerPredictiveSnap(
    snapRegistry: ReaderSnapRegistry,
    foldInRoot: Float,
    direction: PageTurnDirection,
    pageDelta: Float
): Float {
    // Scrolling forward by `pageDelta` moves content up by the same amount, so the line
    // that ends up at the fold is the one sitting `pageDelta` below it right now.
    val landingPoint = when (direction) {
        PageTurnDirection.NEXT -> foldInRoot + pageDelta
        PageTurnDirection.PREVIOUS -> foldInRoot - pageDelta
    }
    return snapRegistry.lineAt(landingPoint)?.let { line ->
        computeTurnSnapAdjustment(
            direction = direction,
            landingPx = landingPoint,
            lineTopPx = line.top,
            lineBottomPx = line.bottom,
            pageDeltaPx = pageDelta,
            maxSnapFraction = READER_MAX_SNAP_FRACTION
        )
    } ?: 0f
}

internal fun readerMoreContentBelow(
    snapRegistry: ReaderSnapRegistry,
    fold: Float,
    minAdvancePx: Float
): Boolean =
    snapRegistry.contentEndInRoot?.let { contentEnd ->
        pageWorthTurning(
            hasLineBelow = snapRegistry.hasLineBelow(fold),
            contentEndGapPx = contentEnd - fold,
            minAdvancePx = minAdvancePx
        )
    } ?: true

internal fun openReaderLink(
    url: String,
    linkOpenMode: LinkOpenMode,
    openInCustomTab: (String) -> Unit,
    uriHandler: UriHandler,
    failureMessage: String
) {
    try { when (linkOpenMode) { LinkOpenMode.CUSTOM_TAB -> openInCustomTab(url); LinkOpenMode.EXTERNAL_BROWSER -> uriHandler.openUri(url) } }
    catch (e: Exception) { AppLogger.e("ViewerScreen", "$failureMessage: ${e.message}", e) }
}

internal fun handleViewerKeyEvent(
    keyEvent: KeyEvent,
    isEmbedded: Boolean,
    ui: ViewerUiState,
    galleryViewerState: GalleryViewerState,
    pageTurnDispatcher: PageTurnDispatcher,
    scope: CoroutineScope
): Boolean =
    if (keyEvent.type == KeyEventType.KeyDown) {
        when {
            !isEmbedded && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.keyboardKey == Key.F -> {
                ui.showSearch = true
                true
            }
            keyEvent.keyboardKey == Key.Escape && ui.showSearch -> {
                ui.showSearch = false
                ui.searchQuery = ""
                ui.searchMatches = emptyList()
                // Delay so the TextField releases focus before we claim it,
                // ensuring the outer onPreviewKeyEvent sees the next Ctrl+F.
                scope.launch { kotlinx.coroutines.delay(100); ui.contentFocusRequester.requestFocus() }
                true
            }
            // Desktop has no hardware back button, so Escape is the gallery's own
            // dismiss key here (BackHandler is a no-op on desktop).
            keyEvent.keyboardKey == Key.Escape && galleryViewerState.request != null -> {
                galleryViewerState.close()
                true
            }
            // Keyboard equivalents of the e-ink facade buttons, so page turning can be
            // exercised on desktop without the device.
            !ui.showSearch && pageTurnDispatcher.handleDesktopPageKey(keyEvent) -> true
            else -> false
        }
    } else false

internal fun shareBookmark(
    bookmark: Bookmark,
    scope: CoroutineScope,
    snackbarManager: ActionSnackbarManager
) {
    ShareUtils.shareText(bookmark.url, bookmark.title)
    scope.launch { snackbarManager.showSnackbar(if (getPlatform().isDesktop) "Copied to clipboard" else "Shared") }
}

internal fun openBookmarkInBrowser(
    bookmark: Bookmark,
    uriHandler: UriHandler,
    scope: CoroutineScope,
    snackbarManager: ActionSnackbarManager
) {
    uriHandler.openUri(bookmark.url)
    scope.launch { snackbarManager.showSnackbar("Opening in browser") }
}
