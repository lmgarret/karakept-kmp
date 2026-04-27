package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.SortOption
import kotlinx.coroutines.Job
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
 *
 * [totalBookmarkCount] should be the full DB count for the current filter so that
 * the thumb position is accurate even when only a partial page has been loaded.
 */
@Composable
fun ScrollCursorIndicator(
    listState: LazyListState,
    bookmarks: List<BookmarkEntity>,
    sortOption: SortOption,
    totalBookmarkCount: Int = 0,
    modifier: Modifier = Modifier
) {
    if (bookmarks.size < 2) return

    val coroutineScope = rememberCoroutineScope()
    // Single job — cancelled before each new scrollToItem so concurrent jumps don't stack up.
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var trackHeightPx by remember { mutableStateOf(0f) }

    // Use the DB total when available so the thumb covers the full list, not just one page.
    val effectiveTotal = if (totalBookmarkCount > bookmarks.size) totalBookmarkCount else bookmarks.size

    // rememberUpdatedState lets the pointerInput coroutine (keyed on Unit) always read the
    // latest values without restarting mid-drag when pagination loads a new page.
    val effectiveTotalState = rememberUpdatedState(effectiveTotal)
    val bookmarksState = rememberUpdatedState(bookmarks)

    val listScrollFraction by remember {
        derivedStateOf {
            val total = effectiveTotalState.value
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
    val touchTargetWidthPx = with(density) { touchTargetWidthDp.toPx() }

    Box(
        modifier = modifier
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            // Gesture lives on the outer Box (full height) so it can't be stolen by sibling
            // components (LazyColumn scroll, FAB, swipeable items) once a drag starts.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Only start a drag if the touch lands on the right-edge touch target.
                    if (down.position.x < size.width - touchTargetWidthPx) return@awaitEachGesture

                    down.consume()
                    isDragging = true
                    dragFraction = (down.position.y / size.height).coerceIn(0f, 1f)
                    scrollJob?.cancel()
                    scrollJob = coroutineScope.launch {
                        val t = effectiveTotalState.value
                        val bs = bookmarksState.value.size
                        listState.scrollToItem(
                            (dragFraction * (t - 1)).roundToInt().coerceIn(0, bs - 1)
                        )
                    }

                    // drag() tracks the pointer globally until lifted, regardless of whether
                    // it moves outside this composable's bounds.
                    drag(down.id) { change ->
                        change.consume()
                        dragFraction = (change.position.y / size.height).coerceIn(0f, 1f)
                        scrollJob?.cancel()
                        scrollJob = coroutineScope.launch {
                            val t = effectiveTotalState.value
                            val bs = bookmarksState.value.size
                            listState.scrollToItem(
                                (dragFraction * (t - 1)).roundToInt().coerceIn(0, bs - 1)
                            )
                        }
                    }

                    // Cancel any in-flight scroll so it doesn't fight the list's own inertia.
                    scrollJob?.cancel()
                    isDragging = false
                }
            }
    ) {
        // Visual track + thumb at the right edge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(touchTargetWidthDp)
        ) {
            val effectiveTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
            val thumbOffsetDp = with(density) { (displayFraction * effectiveTrackPx).toDp() }

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

            // Thumb
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbOffsetDp)
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
