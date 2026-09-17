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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
import com.karakept.app.ui.utils.scrollCursorFraction
import com.karakept.app.ui.utils.scrollCursorIndex
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
 * [bookmarkAtIndex] names the row the thumb points at. The thumb can be over rows the list has
 * not read, and naming one out of what *is* read means naming the nearest row it happens to have
 * — a label that trails the thumb, on exactly the gesture whose purpose is to answer "where am
 * I". One row at a position is a query, so the label asks for it.
 *
 * A drag lands at once. The list holds a slot for every row the view has, so the row the thumb
 * names is one it can already scroll to and the page under it arrives afterwards. This used to be
 * a walk: the list was indexed by the rows read so far, so a drag past them had to ask for the
 * rows in between and hold the thumb where it was dropped until they came back.
 */
@Composable
fun ScrollCursorIndicator(
    listState: LazyListState,
    sortOption: SortOption,
    totalBookmarkCount: Int = 0,
    /**
     * The row at an absolute slot, if the list has already read it. Answers without a query for
     * a thumb over rows that are on screen, which is where it starts and where it ends up.
     */
    loadedAt: (Int) -> BookmarkEntity? = { null },
    /** The row at an absolute slot, read from the database for slots the list does not hold. */
    bookmarkAtIndex: suspend (Int) -> BookmarkEntity? = { null },
    modifier: Modifier = Modifier
) {
    if (totalBookmarkCount < 2) return
    val total = totalBookmarkCount

    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var trackHeightPx by remember { mutableStateOf(0f) }
    var tooltipHeightPx by remember { mutableStateOf(0f) }

    val totalState = rememberUpdatedState(total)

    val listScrollFraction by remember {
        derivedStateOf {
            scrollCursorFraction(listState.firstVisibleItemIndex, totalState.value)
        }
    }

    val displayFraction = if (isDragging) dragFraction else listScrollFraction

    // Snapshot state rather than a plain value, so the read below can watch it move.
    val pointedIndex by remember {
        derivedStateOf {
            scrollCursorIndex(
                if (isDragging) dragFraction else listScrollFraction,
                totalState.value
            )
        }
    }

    // The slot the thumb points at, answered by the list when it holds that row and by the
    // database when it does not.
    //
    // Both are asked by *slot*. The loaded rows used to be asked by slot too, but they are a
    // compacted list — the rows that happen to be read, not one entry per slot — so indexing
    // them by a position in the view named some other row, and the index was then clamped to the
    // last one read. Dragging anywhere past what was loaded labelled the thumb with the same
    // wrong bookmark until the page under it arrived.
    val alreadyLoaded = loadedAt(pointedIndex)
    val loadedAtState = rememberUpdatedState(loadedAt)
    val currentBookmarkAtIndex = rememberUpdatedState(bookmarkAtIndex)
    var readBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }

    // One read per drag, restarted as the thumb settles — not one per slot it passes.
    //
    // Keyed on the slot, every read was cancelled by the next frame's slot and none of them ever
    // returned: for the whole of a moving drag there was no row, so no label, and the bubble was
    // hidden. It came back the moment the thumb stopped, which is what made it look like it was
    // opening and closing rather than never having opened.
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        snapshotFlow { pointedIndex }
            .distinctUntilChanged()
            .collectLatest { slot ->
                if (loadedAtState.value(slot) != null) return@collectLatest
                delay(LABEL_READ_SETTLE_MS)
                currentBookmarkAtIndex.value(slot)?.let { readBookmark = it }
            }
    }
    // Whatever the list is already holding is both the freshest answer and a free one, so it is
    // worth keeping for the stretches where the thumb is over rows that have not been read.
    LaunchedEffect(alreadyLoaded) { alreadyLoaded?.let { readBookmark = it } }

    // The last answer stands while the next is on its way. The thumb outruns the database on a
    // list this long, and a label one row out of date is the point of the bubble; no label is not.
    val resolvedLabel = scrollCursorLabel(alreadyLoaded ?: readBookmark, sortOption)
    var lastLabel by remember { mutableStateOf("") }
    LaunchedEffect(resolvedLabel) { if (resolvedLabel.isNotEmpty()) lastLabel = resolvedLabel }
    val label = resolvedLabel.ifEmpty { lastLabel }

    val density = LocalDensity.current

    // The width of the widest label this sort can produce, held for the whole drag.
    //
    // The label is re-resolved for every row the thumb passes, and the labels are not the same
    // length: a relative date runs from "6d" to "12mo". Sized to the text, the bubble grew and
    // shrank on every one of them — several times a second down a four-thousand-row list, which
    // reads as flickering rather than as a value changing. Reserving the widest leaves the
    // bubble one size for the whole gesture. A minimum rather than a fixed width, so a label
    // wider than anything anticipated — a title starting with a full-width character — grows the
    // bubble instead of being clipped by it.
    val labelStyle = MaterialTheme.typography.bodyLarge
    val textMeasurer = rememberTextMeasurer()
    val labelWidthDp = remember(sortOption, labelStyle, density) {
        val widest = scrollCursorLabelWidths(sortOption).maxOf { candidate ->
            textMeasurer.measure(candidate, labelStyle, maxLines = 1, softWrap = false).size.width
        }
        with(density) { widest.toDp() }
    }

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
                    scope.launch {
                        listState.scrollToItem(scrollCursorIndex(dragFraction, totalState.value))
                    }

                    // drag() tracks the pointer globally until lifted, regardless of whether
                    // it moves outside this composable's bounds.
                    drag(down.id) { change ->
                        change.consume()
                        dragFraction = (change.position.y / size.height).coerceIn(0f, 1f)
                        // The slot exists whether or not its row has been read, so the list
                        // follows the finger and the page under it arrives afterwards.
                        scope.launch {
                            listState.scrollToItem(
                                scrollCursorIndex(dragFraction, totalState.value)
                            )
                        }
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
                    modifier = Modifier
                        .padding(
                            start = tooltipHPadDp,
                            top = tooltipVPadDp,
                            // Extra right padding reserves space for the arrow within the shape
                            end = tooltipHPadDp + arrowWidthDp,
                            bottom = tooltipVPadDp
                        )
                        .widthIn(min = labelWidthDp),
                    textAlign = TextAlign.Center,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

/**
 * The longest labels [scrollCursorLabel] can return for [sortOption].
 *
 * Measured rather than guessed at in dp, because how wide "12mo" is depends on the type. They are
 * stated here rather than derived because the formats are a closed set — a relative date never
 * gets longer than its largest unit, and a title label is always one letter.
 */
internal fun scrollCursorLabelWidths(sortOption: SortOption): List<String> = when (sortOption) {
    // "12mo" is the longest a relative date reaches: every shorter unit caps below 60.
    SortOption.NEWEST, SortOption.OLDEST -> listOf("12mo", "now", "59m")
    // One uppercase letter, and W is the widest of them in every type this app ships.
    SortOption.TITLE_AZ, SortOption.TITLE_ZA -> listOf("W")
    // Unbounded in principle; a bookmark that takes a thousand minutes to read is the cap that
    // matters, and reserving it costs a couple of characters on the ordinary case.
    SortOption.READING_TIME_SHORT, SortOption.READING_TIME_LONG -> listOf("999mn")
}

internal fun scrollCursorLabel(bookmark: BookmarkEntity?, sortOption: SortOption): String {
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

/**
 * How long the thumb has to hold a slot before its row is read.
 *
 * A drag crosses a slot per frame, and a read takes tens of milliseconds; asking for every one of
 * them means none of them ever arrives. Waiting for the thumb to slow means the label updates a
 * few times a second while it moves and lands the moment it stops.
 */
private const val LABEL_READ_SETTLE_MS = 60L
