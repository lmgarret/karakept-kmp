package com.karakept.app.ui.components.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.components.AnimatedVisibilityOrPlain
import com.karakept.app.ui.components.borderStroke
import com.karakept.app.ui.components.floatingSurfaceStyle
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.readerAbsoluteScrollOffset
import com.karakept.app.ui.utils.readerScrollRange
import com.karakept.app.ui.utils.readerScrollTarget
import com.karakept.app.ui.utils.sectionIndexAt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fast-scroll thumb on the right edge of the reader, previewing the article's table of contents.
 *
 * Grabbing the thumb lays every heading out along the track as a small muted pill at the place
 * that scrolls to it, the one the thumb is in turning to the accent colour. The thumb carries the
 * bookmark list cursor's speech bubble, naming that section — so a drag is aimed at a section
 * rather than at a percentage. Articles with no headings get the percentage in the bubble instead.
 *
 * The thumb maps linearly onto the article, as the bookmark list's does.
 *
 * @param listTopInRoot where the reader's list sits in root coordinates. Headings register
 * themselves in root coordinates, and on the wide desktop layout the reader pane's top is not the
 * window's.
 * @param onScrollTo how the cursor moves the reader. The reader runs a scroll guard that snaps
 * back movement it did not sanction, so this has to be its `safeScrollToItem` rather than
 * `LazyListState.scrollToItem`.
 * @param obscuredTopPx how much of the viewport's top the reader paints its own chrome over. The
 * top bar is not a Scaffold `topBar` but a sibling drawn above the list, so the first band of the
 * viewport is not readable. See [projectToc].
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
                obscuredTopPx = obscuredTopPx,
                lookaheadPx = sectionLookaheadPx(info.viewportSize.height, obscuredTopPx)
            )
        }
    }

    if (geometry.rangePx <= 0) return

    var isDragging by remember { mutableStateOf(false) }
    var dragThumb by remember { mutableStateOf(0f) }
    var trackHeightPx by remember { mutableStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    val density = LocalDensity.current
    val thumbHeightPx = with(density) { ThumbHeight.toPx() }
    val touchTargetWidthPx = with(density) { TouchTargetWidth.toPx() }
    // The span the thumb's *top* travels over: a fraction means the same place to the thumb, the
    // marks and the finger.
    val effectiveTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    fun centerOf(fraction: Float) = fraction * effectiveTrackPx + thumbHeightPx / 2f

    // A section's scroll offset is a position in the article, not on screen, so it changes only
    // when the article's layout does — and that changes the item heights this is keyed on. Projected
    // once per layout, then, not once per scroll frame.
    val listTop = listTopInRoot()
    val entries = toc.entries
    val sections = remember(entries, geometry.itemHeights, geometry.rangePx, obscuredTopPx, listTop) {
        projectToc(toc, geometry, listTop)
    }
    val sectionScrolls = remember(sections) { sections.map { it.fraction } }
    // About a pixel and a half of scroll: what rounding a fraction to whole pixels and back costs.
    val scrollEpsilon = 1.5f / geometry.rangePx

    val thumbFraction = if (isDragging) {
        dragThumb
    } else {
        (geometry.absolutePx.toFloat() / geometry.rangePx).coerceIn(0f, 1f)
    }
    val currentSection = sectionIndexAt(sectionScrolls, thumbFraction, scrollEpsilon)
    val bubbleText = when {
        sections.isEmpty() -> "${(thumbFraction * 100).roundToInt()}%"
        currentSection >= 0 -> sections[currentSection].entry.title
        // Above the first heading: the bookmark list's bubble shows nothing it cannot label either.
        else -> ""
    }

    val geometryState = rememberUpdatedState(geometry)
    val onScrollToState = rememberUpdatedState(onScrollTo)
    // A drag is a finger tracked across many frames, and following it live means a full-panel
    // refresh per frame. On e-ink the article is moved once, when the thumb is let go; the table
    // of contents is what says where it will land in the meantime.
    val followDrag = !einkMode.enabled

    val requestScroll: (Float) -> Unit = { thumb ->
        val g = geometryState.value
        val target = readerScrollTarget(g.itemHeights, (thumb * g.rangePx).roundToInt())
        scrollJob?.cancel()
        scrollJob = scope.launch { onScrollToState.value(target.index, target.offsetPx) }
    }

    Box(
        modifier = modifier
            .widthIn(min = TouchTargetWidth)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            // The gesture lives on the outer Box so it cannot be stolen once a drag starts — by
            // the article's own tap handler, the selection container, or the FAB. A press outside
            // the right-edge touch target is left unconsumed so it reaches the article underneath,
            // which is what keeps tap-to-toggle-chrome and text selection working up to the track.
            .pointerInput(thumbHeightPx, touchTargetWidthPx) {
                // The finger holds the thumb by its middle. Mapped over the whole track instead, the
                // thumb drifted half its height from the marks towards each end.
                fun thumbUnder(y: Float): Float {
                    val travel = size.height - thumbHeightPx
                    return if (travel <= 0f) 0f else ((y - thumbHeightPx / 2f) / travel).coerceIn(0f, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x < size.width - touchTargetWidthPx) return@awaitEachGesture
                    down.consume()
                    isDragging = true
                    dragThumb = thumbUnder(down.position.y)
                    if (followDrag) requestScroll(dragThumb)
                    drag(down.id) { change ->
                        change.consume()
                        dragThumb = thumbUnder(change.position.y)
                        if (followDrag) requestScroll(dragThumb)
                    }
                    requestScroll(dragThumb)
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

        AnimatedVisibilityOrPlain(
            visible = isDragging && sections.isNotEmpty(),
            animated = !einkMode.animationsDisabled,
            durationMillis = 150,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight()
        ) {
            Box(modifier = Modifier.fillMaxHeight()) {
                sections.forEachIndexed { index, section ->
                    key(section.entry.id) {
                        SectionMark(
                            level = section.entry.level,
                            isActive = index == currentSection,
                            animated = !einkMode.animationsDisabled,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = TrackWidth + MarkGap)
                                .offset {
                                    val markHeightPx = MarkHeight.toPx()
                                    IntOffset(0, (centerOf(section.fraction) - markHeightPx / 2f).roundToInt())
                                }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (thumbFraction * effectiveTrackPx).roundToInt()) }
                .size(ThumbWidth, ThumbHeight)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )

        CursorBubble(
            text = bubbleText,
            visible = isDragging && bubbleText.isNotEmpty(),
            animated = !einkMode.animationsDisabled,
            anchorPx = centerOf(thumbFraction),
            trackHeightPx = trackHeightPx,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

/** A section of the article, and the scroll fraction that brings its heading into view. */
internal data class ReaderTocSection(val entry: ReaderTocEntry, val fraction: Float)

/** The reader's scroll extent, reconstructed from the item heights seen so far. */
internal data class ReaderCursorGeometry(
    val itemHeights: List<Int>,
    val rangePx: Int,
    val absolutePx: Int,
    val viewportStartOffsetPx: Int,
    /** The band at the top of the viewport the reader's own top bar is painted over. */
    val obscuredTopPx: Int = 0,
    /** How far below the readable top a heading names its section — see [sectionLookaheadPx]. */
    val lookaheadPx: Int = 0
)

/**
 * How far into the readable area a heading has to come before its section is the one named:
 * [SECTION_LOOKAHEAD_FRACTION] of the readable height, below the top bar.
 *
 * Not zero, because the thumb maps linearly onto the article and a long article moves tens of
 * pixels per pixel of thumb. A section named only once its heading reached the very top was named
 * on a frame that had already carried the heading behind the bar — the frame where it crossed was
 * skipped. Naming it while the heading is still in the top quarter means the heading is on screen,
 * under the bar, when its name appears.
 */
internal fun sectionLookaheadPx(viewportHeightPx: Int, obscuredTopPx: Int): Int =
    ((viewportHeightPx - obscuredTopPx).coerceAtLeast(0) * SECTION_LOOKAHEAD_FRACTION).roundToInt()

internal const val SECTION_LOOKAHEAD_FRACTION = 0.25f

/**
 * Where each laid-out heading goes on the track: the scroll offset at which it comes
 * [ReaderCursorGeometry.lookaheadPx] into what the reader can actually *see*, as a fraction of the
 * scroll range — and so the point at which its section becomes the one named.
 *
 * Measured from the readable top, not the viewport's: the reader's top bar is painted over the list
 * rather than reserved by the Scaffold, so [ReaderCursorGeometry.obscuredTopPx] of the viewport is
 * covered. Projected from the viewport top, a section was named only once its heading had scrolled
 * a bar's height past where it could be seen.
 *
 * The result is a position in the article, not on screen: [ReaderCursorGeometry.absolutePx] and
 * the headings' root positions move together as the list scrolls, so a projection taken at any
 * scroll offset gives the same answer.
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
        val aimPx = headingPx - geometry.obscuredTopPx - geometry.lookaheadPx
        ReaderTocSection(entry, (aimPx / geometry.rangePx).coerceIn(0f, 1f))
    }
}

/**
 * One heading on the track: a small muted pill, turning to the accent colour while it is the
 * section the thumb is in. Its level shows in its length.
 */
@Composable
private fun SectionMark(
    level: Int,
    isActive: Boolean,
    animated: Boolean,
    modifier: Modifier = Modifier
) {
    val muted = if (LocalEinkMode.current.highContrast) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val target = if (isActive) MaterialTheme.colorScheme.primary else muted
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = if (animated) tween(GROW_MILLIS) else snap(),
        label = "sectionMarkColor"
    )
    Box(
        modifier = modifier
            .size(if (level <= 2) MarkWidthMajor else MarkWidthMinor, MarkHeight)
            .background(color, RoundedCornerShape(50))
    )
}

/**
 * The bookmark list cursor's speech bubble, riding the thumb: an accent bubble standing off the
 * track, its arrow pointing at the thumb, growing out of it as a drag starts and shrinking back
 * into it when the thumb is let go.
 *
 * Centred on the thumb where it can be, and pinned inside the track near its ends, where the arrow
 * slides along the bubble's edge instead so it still points at the thumb.
 */
@Composable
private fun CursorBubble(
    text: String,
    visible: Boolean,
    animated: Boolean,
    anchorPx: Float,
    trackHeightPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val arrowWidthPx = with(density) { BubbleArrowWidth.toPx() }
    val cornerPx = with(density) { BubbleCornerRadius.toPx() }

    var heightPx by remember { mutableStateOf(0f) }
    val topPx = (anchorPx - heightPx / 2f).coerceIn(0f, (trackHeightPx - heightPx).coerceAtLeast(0f))
    val arrowFraction = if (heightPx > 1f) {
        // Kept to the straight part of the edge, clear of the rounded corners.
        val inset = ((cornerPx + arrowWidthPx * 0.7f) / heightPx).coerceAtMost(0.5f)
        ((anchorPx - topPx) / heightPx).coerceIn(inset, 1f - inset)
    } else {
        0.5f
    }
    val shape = remember(arrowWidthPx, cornerPx, arrowFraction) {
        cursorBubbleShape(arrowWidthPx, cornerPx, arrowFraction)
    }
    // The last name stays while the bubble shrinks away, rather than emptying first.
    var shownText by remember { mutableStateOf(text) }
    if (text.isNotEmpty()) shownText = text

    val style = floatingSurfaceStyle(BubbleElevation)
    AnimatedVisibilityOrPlain(
        visible = visible,
        animated = animated,
        enter = scaleIn(tween(180), transformOrigin = TransformOrigin(1f, 0.5f)) + fadeIn(tween(180)),
        exit = scaleOut(tween(130), transformOrigin = TransformOrigin(1f, 0.5f)) + fadeOut(tween(130)),
        modifier = modifier
            // Stands off the track the way the bookmark list's bubble does, so the finger on the
            // thumb does not cover the name.
            .padding(end = TrackWidth)
            .offset { IntOffset(-BubbleGap.roundToPx(), topPx.roundToInt()) }
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = style.shadowElevation,
            tonalElevation = 0.dp,
            border = style.borderStroke(),
            modifier = Modifier
                .testTag(ACTIVE_SECTION_TAG)
                .onSizeChanged { heightPx = it.height.toFloat() }
        ) {
            Text(
                text = shownText,
                modifier = Modifier
                    .padding(
                        start = BubbleHorizontalPadding,
                        top = BubbleVerticalPadding,
                        // Room for the arrow, which the shape cuts out of the right edge.
                        end = BubbleHorizontalPadding + BubbleArrowWidth,
                        bottom = BubbleVerticalPadding
                    )
                    .widthIn(max = BubbleMaxTextWidth),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The bookmark list cursor's speech bubble: a rounded body with an arrow out of its right edge. */
private fun cursorBubbleShape(arrowWidthPx: Float, cornerPx: Float, arrowFraction: Float) =
    GenericShape { size, _ ->
        val corner = min(cornerPx, size.height / 2f)
        val bodyWidth = (size.width - arrowWidthPx).coerceAtLeast(0f)
        addRoundRect(
            RoundRect(
                left = 0f, top = 0f, right = bodyWidth, bottom = size.height,
                cornerRadius = CornerRadius(corner)
            )
        )
        val arrowY = size.height * arrowFraction
        val arrowHalf = min(
            arrowWidthPx * 0.7f,
            min((arrowY - corner).coerceAtLeast(0f), (size.height - arrowY - corner).coerceAtLeast(0f))
        )
        moveTo(bodyWidth, arrowY - arrowHalf)
        lineTo(size.width, arrowY)
        lineTo(bodyWidth, arrowY + arrowHalf)
        close()
    }

/** The label naming the section the thumb is in. */
internal const val ACTIVE_SECTION_TAG = "ReaderTocActiveSection"

private const val GROW_MILLIS = 180

private val ThumbHeight = 28.dp
private val ThumbWidth = 4.dp
private val TrackWidth = 2.dp
private val TouchTargetWidth = 24.dp

private val MarkHeight = 4.dp
private val MarkWidthMajor = 12.dp
private val MarkWidthMinor = 8.dp
private val MarkGap = 3.dp

// The bookmark list cursor's bubble, so the two read as the same control.
private val BubbleGap = 15.dp
private val BubbleArrowWidth = 12.dp
private val BubbleCornerRadius = 10.dp
private val BubbleHorizontalPadding = 10.dp
private val BubbleVerticalPadding = 7.dp
private val BubbleElevation = 4.dp
private val BubbleMaxTextWidth = 220.dp
