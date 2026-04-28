package com.karakept.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.SortOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fast-scroll thumb on the right edge of the bookmark list.
 *
 * Drag the thumb to jump through the list. While dragging, an animated speech-bubble
 * tooltip grows from the thumb showing the current position label (date, letter, or
 * reading time) depending on the active sort option.
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
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var trackHeightPx by remember { mutableStateOf(0f) }
    var tooltipHeightPx by remember { mutableStateOf(0f) }

    val effectiveTotal = if (totalBookmarkCount > bookmarks.size) totalBookmarkCount else bookmarks.size
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

    // Thumb is half the original size
    val thumbHeightDp = 24.dp
    val thumbWidthDp = 4.dp
    val trackWidthDp = 2.dp
    val touchTargetWidthDp = 24.dp

    // Tooltip speech-bubble geometry
    val arrowWidthDp = 9.dp
    val tooltipHPadDp = 10.dp
    val tooltipVPadDp = 7.dp

    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
    val touchTargetWidthPx = with(density) { touchTargetWidthDp.toPx() }
    val arrowWidthPx = with(density) { arrowWidthDp.toPx() }
    val cornerPx = with(density) { 10.dp.toPx() }

    // Speech-bubble shape: rounded-rect body + right-pointing arrow
    val tooltipShape = remember(arrowWidthPx, cornerPx) {
        GenericShape { size, _ ->
            val bodyW = size.width - arrowWidthPx
            val midY = size.height / 2f
            val arrowHalf = min(arrowWidthPx * 0.7f, size.height * 0.35f)
            // Rounded rectangle for the body
            addRoundRect(
                RoundRect(
                    left = 0f, top = 0f,
                    right = bodyW, bottom = size.height,
                    cornerRadius = CornerRadius(cornerPx)
                )
            )
            // Arrow triangle pointing right (toward the scrollbar)
            moveTo(bodyW, midY - arrowHalf)
            lineTo(size.width, midY)
            lineTo(bodyW, midY + arrowHalf)
            close()
        }
    }

    val effectiveTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetDp = with(density) { (displayFraction * effectiveTrackPx).toDp() }

    // Center the tooltip on the thumb, clamped within the track.
    // tooltipHeightPx starts at 0; once the Surface is measured it becomes accurate.
    val thumbCenterPx = displayFraction * effectiveTrackPx + thumbHeightPx / 2f
    val tooltipTopPx = (thumbCenterPx - tooltipHeightPx / 2f)
        .coerceIn(0f, (trackHeightPx - tooltipHeightPx).coerceAtLeast(0f))
    val tooltipTopDp = with(density) { tooltipTopPx.toDp() }

    Box(
        modifier = modifier
            // Always at least as wide as the touch target so the gesture fires correctly
            .widthIn(min = touchTargetWidthDp)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            // Gesture lives on the outer Box so it can't be stolen by sibling components
            // (LazyColumn scroll, FAB, swipeable items) once a drag starts.
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

                    scrollJob?.cancel()
                    isDragging = false
                }
            }
    ) {
        // Thin track bar, flush against the right edge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(trackWidthDp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                    RoundedCornerShape(1.dp)
                )
        )

        // Thumb, flush against the right edge, always themed
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = thumbOffsetDp)
                .size(thumbWidthDp, thumbHeightDp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(2.dp)
                )
        )

        // Speech-bubble tooltip: grows from the scrollbar, shrinks away when released.
        // zIndex keeps it above the scroll-to-top FAB that lives in the same parent Box.
        AnimatedVisibility(
            visible = isDragging && label.isNotEmpty(),
            enter = scaleIn(
                animationSpec = tween(180),
                transformOrigin = TransformOrigin(1f, 0.5f)
            ) + fadeIn(tween(180)),
            exit = scaleOut(
                animationSpec = tween(130),
                transformOrigin = TransformOrigin(1f, 0.5f)
            ) + fadeOut(tween(130)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Arrow tip sits right at the track's left edge
                .padding(end = trackWidthDp)
                .offset(y = tooltipTopDp)
        ) {
            Surface(
                shape = tooltipShape,
                color = MaterialTheme.colorScheme.primary,
                // shadowElevation must exceed SmallFloatingActionButton's default 6dp so that
                // Android's hardware renderer draws this node on top of the FAB.
                shadowElevation = 10.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.onSizeChanged { tooltipHeightPx = it.height.toFloat() }
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(
                        start = tooltipHPadDp,
                        top = tooltipVPadDp,
                        // Extra right padding reserves space for the arrow within the shape
                        end = tooltipHPadDp + arrowWidthDp,
                        bottom = tooltipVPadDp
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
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
