package com.karakept.app.ui.screens.main

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.BookmarkWindow
import kotlinx.coroutines.delay

/**
 * Long-press-and-drag range selection over the whole list, with edge auto-scroll.
 *
 * Lives on the list's container rather than per item: edge auto-scrolling recycles items, and
 * recycling the item that owns the gesture detector would kill the drag mid-gesture.
 */
@Composable
internal fun rememberDragSelectionModifier(
    isSelectionMode: Boolean,
    listState: LazyListState,
    window: BookmarkWindow,
    selectedBookmarkIds: Set<String>,
    onBookmarkSelectionToggle: (Bookmark) -> Unit
): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    val updatedWindow = rememberUpdatedState(window)
    val updatedSelectedIds = rememberUpdatedState(selectedBookmarkIds)
    val updatedOnToggle = rememberUpdatedState(onBookmarkSelectionToggle)

    val isDragSelecting = remember { mutableStateOf(false) }
    // Scroll speed (px/frame) applied at the list edges during drag selection.
    // Negative = scroll up, positive = scroll down, 0 = no auto-scroll.
    val autoScrollSpeed = remember { mutableStateOf(0f) }

    LaunchedEffect(isDragSelecting.value) {
        if (!isDragSelecting.value) {
            autoScrollSpeed.value = 0f
            return@LaunchedEffect
        }
        while (isDragSelecting.value) {
            val speed = autoScrollSpeed.value
            if (speed != 0f) {
                listState.scrollBy(speed)
            }
            delay(16L) // ~60 fps
        }
        autoScrollSpeed.value = 0f
    }

    return Modifier.pointerInput(isSelectionMode) {
        if (!isSelectionMode) return@pointerInput
        // dragStartIndex is the fixed anchor; on each move the old range [anchor, last] and the
        // new range [anchor, current] are diffed, so pulling the finger back un-selects the
        // overshoot.
        var dragStartIndex = -1
        var lastDragIndex = -1
        fun endDrag() {
            dragStartIndex = -1
            lastDragIndex = -1
            autoScrollSpeed.value = 0f
            isDragSelecting.value = false
        }
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val initialIndex = listState.itemIndexAt(offset.y) ?: return@detectDragGesturesAfterLongPress
                dragStartIndex = initialIndex
                lastDragIndex = initialIndex
                isDragSelecting.value = true
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                val bm = updatedWindow.value.bookmarkAt(initialIndex)
                if (bm != null && bm.remoteId !in updatedSelectedIds.value) {
                    updatedOnToggle.value(bm)
                }
            },
            onDrag = { change, _ ->
                change.consume()
                if (dragStartIndex >= 0) {
                    val currentIndex = listState.itemIndexAt(change.position.y)
                    if (currentIndex != null && currentIndex != lastDragIndex) {
                        val dragWindow = updatedWindow.value
                        val selectedIds = updatedSelectedIds.value
                        for (i in dragSelectionToggles(dragStartIndex, lastDragIndex, currentIndex)) {
                            val bm = dragWindow.bookmarkAt(i) ?: continue
                            val entering = i in minOf(dragStartIndex, currentIndex)..maxOf(dragStartIndex, currentIndex)
                            if (entering != (bm.remoteId in selectedIds)) updatedOnToggle.value(bm)
                        }
                        lastDragIndex = currentIndex
                    }
                }
                val viewportHeight = (listState.layoutInfo.viewportEndOffset -
                    listState.layoutInfo.viewportStartOffset).toFloat()
                autoScrollSpeed.value = edgeScrollSpeed(change.position.y, viewportHeight)
            },
            onDragEnd = { endDrag() },
            onDragCancel = { endDrag() }
        )
    }
}

private fun LazyListState.itemIndexAt(y: Float): Int? {
    val absoluteY = (y + layoutInfo.viewportStartOffset).toInt()
    return layoutInfo.visibleItemsInfo.firstOrNull { info ->
        absoluteY in info.offset until (info.offset + info.size)
    }?.index
}

/**
 * The indices whose membership changes when a drag anchored at [anchor] moves from [last] to
 * [current]: those entering the new range and those leaving the old one.
 */
internal fun dragSelectionToggles(anchor: Int, last: Int, current: Int): List<Int> {
    val old = minOf(anchor, last)..maxOf(anchor, last)
    val new = minOf(anchor, current)..maxOf(anchor, current)
    return (minOf(old.first, new.first)..maxOf(old.last, new.last)).filter { (it in old) != (it in new) }
}

/**
 * Auto-scroll speed in px/frame for a finger at [y]: 0 outside the 15% edge zones, easing
 * quadratically up to [maxSpeed] at the very edge. Negative scrolls up.
 */
internal fun edgeScrollSpeed(y: Float, viewportHeight: Float, maxSpeed: Float = 25f): Float {
    val edgeZone = viewportHeight * 0.15f
    return when {
        y < edgeZone -> {
            val fraction = ((edgeZone - y) / edgeZone).coerceIn(0f, 1f)
            -maxSpeed * fraction * fraction
        }
        y > viewportHeight - edgeZone -> {
            val fraction = ((y - (viewportHeight - edgeZone)) / edgeZone).coerceIn(0f, 1f)
            maxSpeed * fraction * fraction
        }
        else -> 0f
    }
}
