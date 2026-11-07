package com.karakept.app.ui.components.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.components.AnimatedVisibilityOrPlain
import com.karakept.app.ui.components.borderStroke
import com.karakept.app.ui.components.floatingSurfaceStyle
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.ReaderScrollTarget
import com.karakept.app.ui.utils.pickTocLabels
import com.karakept.app.ui.utils.readerAbsoluteScrollOffset
import com.karakept.app.ui.utils.readerScrollRange
import com.karakept.app.ui.utils.readerScrollTarget
import com.karakept.app.ui.utils.sectionIndexAt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Fast-scroll thumb on the right edge of the reader, previewing the article's table of contents.
 *
 * Grabbing the thumb lays the article's headings out along the track at the scroll position each
 * one sits at, with the section the thumb is currently pointing at picked out — so a drag is aimed
 * at a section rather than at a percentage. Articles with no headings still get the thumb, and the
 * percentage in place of a section name.
 *
 * @param listTopInRoot where the reader's list sits in root coordinates. Headings register
 * themselves in root coordinates, and on the wide desktop layout the reader pane's top is not the
 * window's. A lambda for the same reason the registry's positions are: read when a drag asks.
 * @param onScrollTo how the cursor moves the reader. The reader runs a scroll guard that snaps
 * back movement it did not sanction, so this has to be its `safeScrollToItem` rather than
 * `LazyListState.scrollToItem`.
 * @param obscuredTopPx how much of the viewport's top the reader paints its own chrome over. The
 * top bar is not a Scaffold `topBar` but a sibling drawn above the list, so the first band of the
 * viewport is not readable — and a section is reached when its heading arrives at the top of what
 * can be *seen*, not at the top of the viewport. See [projectToc].
 */
@Composable
fun ReaderScrollCursor(
    listState: LazyListState,
    toc: ReaderTocState,
    listTopInRoot: () -> Float,
    onScrollTo: suspend (Int, Int) -> Unit,
    obscuredTopPx: Int,
    modifier: Modifier = Modifier
) {
    val einkMode = LocalEinkMode.current
    val scope = rememberCoroutineScope()

    // Item heights are cached as they become visible so the absolute scroll position can be
    // reconstructed for items that have already scrolled past. Written from inside derivedStateOf,
    // which is safe here for the same reason as in rememberReadingProgress: the cache is written
    // and read within one execution, and that execution re-runs on every layoutInfo change. Keyed
    // on [toc], which the reader replaces per bookmark — the same screen shows one article after
    // another, and the last one's hero height is not this one's.
    val itemHeights = remember(toc) { mutableMapOf<Int, Int>() }

    val geometry by remember(listState, toc, obscuredTopPx) {
        derivedStateOf {
            val info = listState.layoutInfo
            for (item in info.visibleItemsInfo) itemHeights[item.index] = item.size
            val heights = List(info.totalItemsCount) { itemHeights[it] ?: 0 }
            ReaderCursorGeometry(
                itemHeights = heights,
                rangePx = readerScrollRange(
                    itemHeights = heights,
                    viewportHeightPx = info.viewportSize.height,
                    contentPaddingPx = info.beforeContentPadding + info.afterContentPadding
                ),
                absolutePx = readerAbsoluteScrollOffset(
                    itemHeights = heights,
                    firstVisibleIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
                ),
                viewportStartOffsetPx = info.viewportStartOffset,
                obscuredTopPx = obscuredTopPx
            )
        }
    }

    if (geometry.rangePx <= 0) return

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var trackHeightPx by remember { mutableStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    val geometryState = rememberUpdatedState(geometry)
    val onScrollToState = rememberUpdatedState(onScrollTo)
    // A drag is a finger tracked across many frames, and following it live means a full-panel
    // refresh per frame. On e-ink the article is moved once, when the thumb is let go; the table
    // of contents is what says where it will land in the meantime.
    val followDrag = !einkMode.enabled

    val requestScroll: (Float) -> Unit = { fraction ->
        val g = geometryState.value
        val target: ReaderScrollTarget =
            readerScrollTarget(g.itemHeights, (fraction * g.rangePx).roundToInt())
        scrollJob?.cancel()
        scrollJob = scope.launch { onScrollToState.value(target.index, target.offsetPx) }
    }

    val displayFraction = if (isDragging) {
        dragFraction
    } else {
        (geometry.absolutePx.toFloat() / geometry.rangePx).coerceIn(0f, 1f)
    }

    val density = LocalDensity.current
    val thumbHeightPx = with(density) { ThumbHeight.toPx() }
    val touchTargetWidthPx = with(density) { TouchTargetWidth.toPx() }
    // Spacing stays keyed to the *active* label, the tallest of them, so shrinking the inactive
    // ones reduces their footprint without packing more of them onto the track.
    val minLabelSpacingPx = with(density) { (tocLabelHeight(isCurrent = true) + 2.dp).toPx() }

    // The span the thumb's *top* travels over, so a fraction means the same thing to the thumb,
    // the ticks and the labels.
    val effectiveTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    fun centerOf(fraction: Float) = fraction * effectiveTrackPx + thumbHeightPx / 2f

    Box(
        modifier = modifier
            .widthIn(min = TouchTargetWidth)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            // The gesture lives on the outer Box so it cannot be stolen once a drag starts — by
            // the article's own tap handler, the selection container, or the FAB. A press outside
            // the right-edge touch target is left unconsumed so it reaches the article underneath,
            // which is what keeps tap-to-toggle-chrome and text selection working up to the track.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x < size.width - touchTargetWidthPx) return@awaitEachGesture
                    down.consume()
                    isDragging = true
                    dragFraction = (down.position.y / size.height).coerceIn(0f, 1f)
                    if (followDrag) requestScroll(dragFraction)
                    drag(down.id) { change ->
                        change.consume()
                        dragFraction = (change.position.y / size.height).coerceIn(0f, 1f)
                        if (followDrag) requestScroll(dragFraction)
                    }
                    requestScroll(dragFraction)
                    isDragging = false
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(TrackWidth)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                    RoundedCornerShape(1.dp)
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (displayFraction * effectiveTrackPx).roundToInt()) }
                .size(ThumbWidth, ThumbHeight)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )

        AnimatedVisibilityOrPlain(
            visible = isDragging,
            animated = !einkMode.animationsDisabled,
            durationMillis = 150,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight()
        ) {
            Box(modifier = Modifier.fillMaxHeight()) {
                // Projected here, inside the preview, so a reader that is merely being scrolled
                // pays nothing: asking every heading for its position is cheap next to the
                // per-frame recomposition a pushed position would cost, but it is not free, and
                // outside a drag there is nobody to show it to. Composed for the whole exit
                // animation too, so the preview never spends its fade showing the fallback.
                val sections = projectToc(toc, geometry, listTopInRoot())
                val fractions = sections.map { it.fraction }
                val currentSection = sectionIndexAt(fractions, displayFraction)

                // Every section gets a tick, even the ones with no room for a label — the ticks
                // are the map, the labels only name the parts of it that fit.
                if (sections.isNotEmpty()) {
                    val tickColor = MaterialTheme.colorScheme.primary
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight()
                            .width(TickWidth)
                    ) {
                        for (section in sections) {
                            val y = centerOf(section.fraction)
                            val length =
                                if (section.entry.level <= 2) size.width else size.width * 0.55f
                            drawRect(
                                color = tickColor,
                                topLeft = Offset(size.width - length, y - 0.75f),
                                size = Size(length, 1.5f)
                            )
                        }
                    }
                }

                if (sections.isEmpty()) {
                    TocLabel(
                        text = "${(displayFraction * 100).roundToInt()}%",
                        level = 1,
                        isCurrent = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = TickWidth + 4.dp)
                            .offset {
                                IntOffset(
                                    0,
                                    labelTopPx(
                                        centerPx = centerOf(displayFraction),
                                        labelHeightPx = with(density) {
                                            tocLabelHeight(isCurrent = true).toPx()
                                        },
                                        trackHeightPx = trackHeightPx
                                    )
                                )
                            }
                    )
                } else {
                    val visibleLabels = pickTocLabels(
                        fractions = fractions,
                        trackHeightPx = effectiveTrackPx,
                        minSpacingPx = minLabelSpacingPx,
                        current = currentSection
                    )
                    for (index in visibleLabels) {
                        val section = sections[index]
                        val isCurrent = index == currentSection
                        val ownHeightPx = with(density) { tocLabelHeight(isCurrent).toPx() }
                        TocLabel(
                            text = section.entry.title,
                            level = section.entry.level,
                            isCurrent = isCurrent,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = TickWidth + 4.dp)
                                .offset {
                                    IntOffset(
                                        0,
                                        labelTopPx(
                                            centerPx = centerOf(section.fraction),
                                            labelHeightPx = ownHeightPx,
                                            trackHeightPx = trackHeightPx
                                        )
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

/** A section of the article, and where along the track it sits. */
internal data class ReaderTocSection(val entry: ReaderTocEntry, val fraction: Float)

/** The reader's scroll extent, reconstructed from the item heights seen so far. */
internal data class ReaderCursorGeometry(
    val itemHeights: List<Int>,
    val rangePx: Int,
    val absolutePx: Int,
    val viewportStartOffsetPx: Int,
    /** The band at the top of the viewport the reader's own top bar is painted over. */
    val obscuredTopPx: Int = 0
)

/**
 * Where each laid-out heading goes on the track: the scroll offset that brings it to the top of
 * what the reader can actually *see*, as a fraction of the scroll range.
 *
 * Not the heading's own position in the document, which is that offset plus
 * [ReaderCursorGeometry.obscuredTopPx] — the reader's top bar is painted over the list rather than
 * reserved by the Scaffold, so the first band of the viewport is covered. Projecting the document
 * position instead put every section a bar's height too far down the track, with two visible
 * consequences: a section only became the current one once its heading had scrolled that far past
 * the top of the readable area (#277), and dropping the thumb on a label landed its heading behind
 * the bar. Taking the band off here puts the thumb, the active label and the drop target in one
 * coordinate — the same one `onScrollTo` moves the list in — so all three agree.
 *
 * Headings that have not been laid out are dropped rather than guessed at: the article is eagerly
 * composed in full, so that only happens before the body item is first measured.
 */
internal fun projectToc(
    toc: ReaderTocState,
    geometry: ReaderCursorGeometry,
    listTopInRoot: Float
): List<ReaderTocSection> {
    if (geometry.rangePx <= 0) return emptyList()
    val viewportTopInRoot = listTopInRoot + geometry.viewportStartOffsetPx
    return toc.entries.mapNotNull { entry ->
        val topInRoot = toc.positionOf(entry.id) ?: return@mapNotNull null
        val headingPx = geometry.absolutePx + (topInRoot - viewportTopInRoot)
        val aimPx = headingPx - geometry.obscuredTopPx
        ReaderTocSection(entry, (aimPx / geometry.rangePx).coerceIn(0f, 1f))
    }
}

private fun labelTopPx(centerPx: Float, labelHeightPx: Float, trackHeightPx: Float): Int =
    (centerPx - labelHeightPx / 2f)
        .coerceIn(0f, (trackHeightPx - labelHeightPx).coerceAtLeast(0f))
        .roundToInt()

/**
 * One section's name on the track.
 *
 * Only the section being pointed at is a full pill. The rest are context — they say what is above
 * and below without being read — so they get the smaller type, a narrower truncation and a quieter
 * container: a column of equally loud pills covered most of the page it was previewing.
 */
@Composable
private fun TocLabel(
    text: String,
    level: Int,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    val style = floatingSurfaceStyle(if (isCurrent) 4.dp else 1.dp)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (isCurrent) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shadowElevation = style.shadowElevation,
        tonalElevation = 0.dp,
        border = style.borderStroke(),
        modifier = modifier
            .height(tocLabelHeight(isCurrent))
            .widthIn(max = if (isCurrent) LabelMaxWidth else InactiveLabelMaxWidth)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = if (isCurrent) 8.dp else 6.dp,
                vertical = if (isCurrent) 3.dp else 2.dp
            ),
            // The active label is the one being read, so its heading level is worth showing; an
            // inactive one carries its level in the length of its tick instead.
            style = when {
                !isCurrent -> MaterialTheme.typography.labelSmall
                level <= 2 -> MaterialTheme.typography.labelLarge
                else -> MaterialTheme.typography.labelMedium
            },
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Kept next to [TocLabel] because the track's label spacing and centring both need it. */
internal fun tocLabelHeight(isCurrent: Boolean): Dp =
    if (isCurrent) LabelHeight else InactiveLabelHeight

private val ThumbHeight = 28.dp
private val ThumbWidth = 4.dp
private val TrackWidth = 2.dp
private val TickWidth = 10.dp
private val TouchTargetWidth = 24.dp
private val LabelHeight = 24.dp
private val InactiveLabelHeight = 18.dp
private val LabelMaxWidth = 220.dp
private val InactiveLabelMaxWidth = 132.dp
