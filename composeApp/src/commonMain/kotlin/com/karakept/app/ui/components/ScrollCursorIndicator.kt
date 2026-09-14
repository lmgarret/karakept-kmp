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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.karakept.app.ui.utils.ScrollCursorStep
import com.karakept.app.ui.utils.scrollCursorFraction
import com.karakept.app.ui.utils.scrollCursorIndex
import com.karakept.app.ui.utils.scrollCursorStep
import kotlin.time.Clock
import kotlin.math.min

/**
 * Fast-scroll thumb on the right edge of the bookmark list.
 *
 * Drag the thumb to jump through the list. While dragging, an animated speech-bubble
 * tooltip grows from the thumb showing the current position label (date, letter, or
 * reading time) depending on the active sort option.
 *
 * [totalBookmarkCount] is the count of rows the active filter matches in the database, and the
 * thumb maps linearly over it — over the whole list, not over the pages loaded so far, which
 * grow as the list is scrolled and would walk the thumb back up the track on every load (#273).
 *
 * [filteredBookmarks] is that same list in order, which is what the tooltip names the pointed row
 * from. Naming it out of [bookmarks] instead means naming the last row *loaded* whenever the thumb
 * is past the window — a label that trails the thumb and then ticks forward as reads land, on
 * exactly the gesture whose whole purpose is to answer "where am I".
 *
 * A drag therefore aims at a row the window may not hold yet. [onSeekToIndex] is what gets it
 * there: it asks for the rows out to that one, and the list holds still until they arrive — a
 * jump to the end of a large list must not crawl through everything on the way. The walk stops
 * when the target is loaded, when [hasMoreItems] says the table ended first, or when a read comes
 * back having added nothing (see [scrollCursorStep]).
 */
@Composable
fun ScrollCursorIndicator(
    listState: LazyListState,
    bookmarks: List<BookmarkEntity>,
    sortOption: SortOption,
    totalBookmarkCount: Int = 0,
    filteredBookmarks: List<BookmarkEntity> = emptyList(),
    hasMoreItems: Boolean = false,
    isLoadingMore: Boolean = false,
    onSeekToIndex: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (bookmarks.size < 2) return

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    // The row the last drag asked for, over the whole list. Outlives the drag: a target past the
    // loaded window is reached one page at a time, and the finger is long gone by then.
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var pulledAtCount by remember { mutableStateOf<Int?>(null) }
    var trackHeightPx by remember { mutableStateOf(0f) }
    var tooltipHeightPx by remember { mutableStateOf(0f) }

    val effectiveTotal = if (totalBookmarkCount > bookmarks.size) totalBookmarkCount else bookmarks.size
    val effectiveTotalState = rememberUpdatedState(effectiveTotal)
    val currentOnSeekToIndex by rememberUpdatedState(onSeekToIndex)

    val listScrollFraction by remember {
        derivedStateOf {
            scrollCursorFraction(listState.firstVisibleItemIndex, effectiveTotalState.value)
        }
    }

    // Reaching the target can outlast the gesture — a row past the window has to be read in
    // first — so it is driven from here rather than from the drag.
    LaunchedEffect(targetIndex, isDragging, bookmarks.size, hasMoreItems, isLoadingMore) {
        val target = targetIndex ?: return@LaunchedEffect
        val step = scrollCursorStep(
            targetIndex = target,
            loadedCount = bookmarks.size,
            canLoadMore = hasMoreItems,
            isLoadingMore = isLoadingMore,
            pulledAtCount = pulledAtCount
        )
        when (step) {
            is ScrollCursorStep.Land -> {
                listState.scrollToItem(step.index)
                targetIndex = null
                pulledAtCount = null
            }
            is ScrollCursorStep.Pull -> {
                // Asked for at once, never on a settling delay. A moving finger does name a new
                // target every few milliseconds, but the read is already one-at-a-time — the
                // seek refuses while one is in flight, and this effect re-runs against the
                // current target when it lands — so the reads a drag issues are self-limiting.
                // Waiting for the finger to hold still instead starves the seek precisely when
                // the user is wiggling the thumb because nothing appears to be happening: every
                // twitch restarts this effect and cancels the wait.
                pulledAtCount = bookmarks.size
                currentOnSeekToIndex(step.throughIndex)
            }
            ScrollCursorStep.Wait -> Unit
        }
    }

    // The thumb holds where it was dropped until the walk resolves — following the list instead
    // would snap it back to the end of the window while the pages it is waiting on load.
    val displayFraction = if (isDragging || targetIndex != null) dragFraction else listScrollFraction
    val pointedIndex = scrollCursorIndex(displayFraction, effectiveTotal)
    // The loaded window is the fallback, for the frame before the view resolves or a row the
    // view has not caught up with; it can only ever answer for a thumb inside the window.
    val pointedBookmark = filteredBookmarks.getOrNull(pointedIndex)
        ?: bookmarks.getOrNull(pointedIndex.coerceAtMost(bookmarks.size - 1))
    val label = scrollCursorLabel(pointedBookmark, sortOption)

    val density = LocalDensity.current

    // Thumb is half the original size
    val thumbHeightDp = 24.dp
    val thumbWidthDp = 4.dp
    val trackWidthDp = 2.dp
    val touchTargetWidthDp = 24.dp

    // Tooltip speech-bubble geometry
    val arrowWidthDp = 12.dp
    val tooltipHPadDp = 10.dp
    val tooltipVPadDp = 7.dp

    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
    val touchTargetWidthPx = with(density) { touchTargetWidthDp.toPx() }
    val arrowWidthPx = with(density) { arrowWidthDp.toPx() }
    val cornerPx = with(density) { 10.dp.toPx() }

    val effectiveTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetDp = with(density) { (displayFraction * effectiveTrackPx).toDp() }
    val thumbTopPx = displayFraction * effectiveTrackPx
    val targetArrowFraction = 0.2f

    val idealTooltipTopPx = if (tooltipHeightPx > 1f)
        thumbTopPx - targetArrowFraction * tooltipHeightPx
    else
        thumbTopPx - 30f

    val tooltipTopPx = idealTooltipTopPx
        .coerceIn(0f, (trackHeightPx - tooltipHeightPx).coerceAtLeast(0f))
    val tooltipTopDp = with(density) { tooltipTopPx.toDp() }
    val tooltipLeftDp = (-15).dp

    val arrowYFraction = if (tooltipHeightPx > 1f) {
        val minFraction = (cornerPx + arrowWidthPx * 0.7f) / tooltipHeightPx
        val maxFraction = 1f - minFraction
        ((thumbTopPx - tooltipTopPx) / tooltipHeightPx)
            .coerceIn(minFraction.coerceAtMost(0.5f), maxFraction.coerceAtLeast(0.5f))
    } else {
        targetArrowFraction
    }
    val tooltipShape = remember(arrowWidthPx, cornerPx, arrowYFraction) {
        GenericShape { size, _ ->
            val bodyW = size.width - arrowWidthPx
            val arrowY = size.height * arrowYFraction
            // Keep the arrow base inside the straight part of the right edge,
            // i.e. between the top and bottom rounded corners.
            val maxHalfFromTop = (arrowY - cornerPx).coerceAtLeast(0f)
            val maxHalfFromBottom = (size.height - arrowY - cornerPx).coerceAtLeast(0f)
            val arrowHalf = min(
                arrowWidthPx * 0.7f,
                min(maxHalfFromTop, maxHalfFromBottom)
            )
            addRoundRect(
                RoundRect(
                    left = 0f, top = 0f,
                    right = bodyW, bottom = size.height,
                    cornerRadius = CornerRadius(cornerPx)
                )
            )
            moveTo(bodyW, arrowY - arrowHalf)
            lineTo(size.width, arrowY)
            lineTo(bodyW, arrowY + arrowHalf)
            close()
        }
    }
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
                    targetIndex = scrollCursorIndex(dragFraction, effectiveTotalState.value)
                    pulledAtCount = null

                    // drag() tracks the pointer globally until lifted, regardless of whether
                    // it moves outside this composable's bounds.
                    drag(down.id) { change ->
                        change.consume()
                        dragFraction = (change.position.y / size.height).coerceIn(0f, 1f)
                        targetIndex = scrollCursorIndex(dragFraction, effectiveTotalState.value)
                        // A fresh target deserves a fresh attempt at the pages behind it.
                        pulledAtCount = null
                    }

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
        AnimatedVisibility(
            // Stays up while a seek resolves: the list deliberately holds still until the rows
            // arrive, and with the tooltip gone too there is nothing on screen saying so.
            visible = (isDragging || targetIndex != null) && label.isNotEmpty(),
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
                .offset(x = tooltipLeftDp, y = tooltipTopDp)
        ) {
            Surface(
                shape = tooltipShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
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
            diffMs < 365 * 86_400_000L -> "${diffMs / (30 * 86_400_000L)}mo"
            else -> "${diffMs / (365 * 86_400_000L)}y"
        }
    } catch (e: Exception) {
        ""
    }
}
