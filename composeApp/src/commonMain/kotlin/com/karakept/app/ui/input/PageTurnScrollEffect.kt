package com.karakept.app.ui.input

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.ui.utils.computePageScrollDelta
import com.karakept.app.ui.utils.tailFullyVisible
import kotlin.math.abs
import org.koin.compose.koinInject

/**
 * Scrolls [listState] by one page whenever a bound hardware button fires.
 *
 * [enabled] exists because the events are broadcast to every collector: on the wide desktop
 * layout the bookmark list and the reader are composed side by side, and only one of them should
 * respond. The reader wins when it has a bookmark open.
 *
 * [obscuredTopPx]/[obscuredBottomPx] are the slices of [listState]'s viewport hidden behind chrome
 * drawn over it, which still count toward the reported viewport height. A screen that overlays its
 * own bars has to pass them, or every turn scrolls further than the user can read.
 *
 * [predictiveSnap] lands the turn on a content boundary instead of an arbitrary pixel, returning
 * the extra (negative) pixels to walk back — see `computeSnapAdjustment`. It is consulted only
 * while the user has snapping on, and because it answers *before* the scroll it folds into a
 * single `scrollBy`: no intermediate position is ever observable to the reader's scroll guard.
 *
 * [onScrolled] runs after each turn. The reader installs a scroll guard that snaps back any
 * movement it did not sanction, and an instant turn looks exactly like one of those — so the
 * reader passes a callback that re-approves the new position.
 */
@Composable
fun PageTurnScrollEffect(
    listState: LazyListState,
    enabled: Boolean = true,
    obscuredTopPx: Int = 0,
    obscuredBottomPx: Int = 0,
    predictiveSnap: ((direction: PageTurnDirection, pageDeltaPx: Float) -> Float)? = null,
    hasTrailingPadding: Boolean = false,
    /** Whether a forward turn would reveal anything worth a page. */
    moreContentBelow: (() -> Boolean)? = null,
    onScrolled: (() -> Unit)? = null
) {
    val dispatcher = koinInject<PageTurnDispatcher>()
    val currentOnScrolled by rememberUpdatedState(onScrolled)
    // Kept out of the LaunchedEffect keys so a rotation resizes the page without tearing down and
    // rebuilding the collector.
    val currentObscuredTop by rememberUpdatedState(obscuredTopPx)
    val currentObscuredBottom by rememberUpdatedState(obscuredBottomPx)
    val currentPredictiveSnap by rememberUpdatedState(predictiveSnap)
    val currentHasTrailingPadding by rememberUpdatedState(hasTrailingPadding)
    val currentMoreContentBelow by rememberUpdatedState(moreContentBelow)

    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        dispatcher.events.collect { direction ->
            val bindings = dispatcher.bindings.value
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val delta = computePageScrollDelta(
                viewportHeightPx = viewportHeight,
                overlapPercent = bindings.effectiveOverlapPercent,
                direction = direction,
                obscuredTopPx = currentObscuredTop,
                obscuredBottomPx = currentObscuredBottom
            )
            if (delta == 0f) return@collect
            // Trailing padding lets a forward turn run on into the blank space after the content,
            // which would show a page whose every line the previous one had already displayed.
            // The last page is the one that still has something below its bottom edge — and
            // "something" means content, not the trailing margin of the box holding it, which is
            // what [moreContentBelow] is there to tell apart.
            if (direction == PageTurnDirection.NEXT && currentHasTrailingPadding &&
                (tailFullyVisible(layoutInfo, currentObscuredBottom) ||
                    currentMoreContentBelow?.invoke() == false)
            ) return@collect
            val pageMagnitude = abs(delta)
            val predicted = if (bindings.snapToContent) {
                currentPredictiveSnap?.invoke(direction, pageMagnitude) ?: 0f
            } else 0f
            val requested = delta + predicted
            // scrollBy clamps at the content edges on its own, so no bounds check is needed.
            // instantPageTurn is read off the bindings rather than LocalEinkMode because the
            // latter ANDs in the master e-ink switch, which page-turn buttons are not gated on.
            if (bindings.instantPageTurn) listState.scrollBy(requested)
            else listState.animateScrollBy(requested)
            currentOnScrolled?.invoke()
        }
    }
}

/**
 * Maps desktop keyboard page keys onto the same dispatcher, so the feature can be exercised
 * without the e-ink device. Returns true when the event was consumed.
 */
fun PageTurnDispatcher.handleDesktopPageKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val direction = when (event.key) {
        Key.PageDown -> PageTurnDirection.NEXT
        Key.PageUp -> PageTurnDirection.PREVIOUS
        else -> return false
    }
    return emitDirection(direction)
}
