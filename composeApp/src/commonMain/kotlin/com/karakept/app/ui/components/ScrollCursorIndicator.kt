package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.SortOption
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.math.roundToInt

/**
 * Fast-scroll thumb on the right edge of the bookmark list.
 *
 * The thumb tracks the list scroll position and can be grabbed + dragged to jump
 * through the list. While dragging, a circular label appears to the left of the
 * thumb showing the current position in terms of the active sort order:
 *   - Date sorts  → compact elapsed time ("3d", "5h", "2mo")
 *   - Title sorts → first letter of the title ("A", "B", …)
 *   - Reading-time sorts → minutes ("5mn", "12mn")
 */
@Composable
fun ScrollCursorIndicator(
    listState: LazyListState,
    bookmarks: List<BookmarkEntity>,
    sortOption: SortOption,
    modifier: Modifier = Modifier
) {
    if (bookmarks.size < 2) return

    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var trackHeightPx by remember { mutableStateOf(0f) }

    val listScrollFraction by remember {
        derivedStateOf {
            val total = bookmarks.size
            if (total <= 1) 0f
            else listState.firstVisibleItemIndex.toFloat() / (total - 1).toFloat()
        }
    }

    val displayFraction = if (isDragging) dragFraction else listScrollFraction

    val pointedIndex = (displayFraction * (bookmarks.size - 1))
        .roundToInt().coerceIn(0, bookmarks.size - 1)
    val label = scrollCursorLabel(bookmarks.getOrNull(pointedIndex), sortOption)

    val density = LocalDensity.current
    val thumbHeightDp = 48.dp
    val thumbWidthDp = 8.dp
    val trackWidthDp = 3.dp
    val touchTargetWidthDp = 24.dp
    val labelSizeDp = 56.dp

    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    Box(modifier = modifier) {
        // Touch target that contains the track and thumb
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(touchTargetWidthDp)
                .onSizeChanged { trackHeightPx = it.height.toFloat() }
                .pointerInput(bookmarks.size) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            isDragging = true
                            val trackH = size.height.toFloat()
                            dragFraction = (down.position.y / trackH).coerceIn(0f, 1f)
                            coroutineScope.launch {
                                listState.scrollToItem(
                                    (dragFraction * (bookmarks.size - 1))
                                        .roundToInt().coerceIn(0, bookmarks.size - 1)
                                )
                            }
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (!event.changes.any { it.pressed }) break
                                dragFraction = (event.changes.first().position.y / trackH)
                                    .coerceIn(0f, 1f)
                                coroutineScope.launch {
                                    listState.scrollToItem(
                                        (dragFraction * (bookmarks.size - 1))
                                            .roundToInt().coerceIn(0, bookmarks.size - 1)
                                    )
                                }
                            }
                            isDragging = false
                        }
                    }
                }
        ) {
            val effectiveTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
            val thumbOffsetPx = displayFraction * effectiveTrackPx

            // Track bar
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(trackWidthDp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(2.dp)
                    )
            )

            // Draggable thumb
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                    .size(thumbWidthDp, thumbHeightDp)
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(4.dp)
                    )
            )
        }

        // Circular label bubble that appears while dragging
        if (isDragging && label.isNotEmpty()) {
            val effectiveTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
            val thumbCenterPx = displayFraction * effectiveTrackPx + thumbHeightPx / 2f
            val labelSizePx = with(density) { labelSizeDp.toPx() }
            val labelTopPx = (thumbCenterPx - labelSizePx / 2f)
                .coerceIn(0f, (trackHeightPx - labelSizePx).coerceAtLeast(0f))
            val labelTopDp = with(density) { labelTopPx.toDp() }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = touchTargetWidthDp + 8.dp)
                    .offset(y = labelTopDp)
                    .size(labelSizeDp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

private fun scrollCursorLabel(bookmark: BookmarkEntity?, sortOption: SortOption): String {
    bookmark ?: return ""
    return when (sortOption) {
        SortOption.NEWEST, SortOption.OLDEST ->
            formatScrollCursorDate(bookmark.createdAt)
        SortOption.TITLE_AZ, SortOption.TITLE_ZA ->
            bookmark.title.firstOrNull()?.uppercaseChar()?.toString() ?: ""
        SortOption.READING_TIME_SHORT, SortOption.READING_TIME_LONG -> {
            val mins = bookmark.readingTimeMinutes
            if (mins > 0) "${mins}mn" else ""
        }
    }
}

private fun formatScrollCursorDate(epochMillis: Long): String {
    return try {
        val now = Clock.System.now().toEpochMilliseconds()
        val diffMs = now - epochMillis
        when {
            diffMs < 60_000L -> "now"
            diffMs < 3_600_000L -> "${diffMs / 60_000}m"
            diffMs < 86_400_000L -> "${diffMs / 3_600_000}h"
            diffMs < 7 * 86_400_000L -> "${diffMs / 86_400_000}d"
            diffMs < 30 * 86_400_000L -> "${diffMs / (7 * 86_400_000)}w"
            diffMs < 365 * 86_400_000L -> "${diffMs / (30 * 86_400_000)}mo"
            else -> "${diffMs / (365 * 86_400_000L)}y"
        }
    } catch (e: Exception) {
        ""
    }
}
