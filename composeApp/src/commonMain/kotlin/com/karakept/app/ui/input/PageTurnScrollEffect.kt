package com.karakept.app.ui.input

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.computePageScrollDelta
import org.koin.compose.koinInject

/**
 * Scrolls [listState] by one page whenever a bound hardware button fires.
 *
 * [enabled] exists because the events are broadcast to every collector: on the wide desktop
 * layout the bookmark list and the reader are composed side by side, and only one of them should
 * respond. The reader wins when it has a bookmark open.
 */
@Composable
fun PageTurnScrollEffect(
    listState: LazyListState,
    enabled: Boolean = true
) {
    val dispatcher = koinInject<PageTurnDispatcher>()
    val instantScroll = LocalEinkMode.current.instantScroll

    LaunchedEffect(listState, enabled, instantScroll) {
        if (!enabled) return@LaunchedEffect
        dispatcher.events.collect { direction ->
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val delta = computePageScrollDelta(
                viewportHeightPx = viewportHeight,
                overlapPercent = dispatcher.bindings.value.overlapPercent,
                direction = direction
            )
            if (delta == 0f) return@collect
            // scrollBy clamps at the content edges on its own, so no bounds check is needed.
            if (instantScroll) listState.scrollBy(delta) else listState.animateScrollBy(delta)
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
