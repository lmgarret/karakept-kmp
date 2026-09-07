package com.karakept.app.ui.components.reader

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.karakept.app.ui.components.borderStroke
import com.karakept.app.ui.components.floatingSurfaceStyle
import kotlinx.coroutines.delay

/** How long the pointer has to rest on a link before its URL is revealed, as a browser does. */
internal const val LINK_TOOLTIP_DELAY_MS = 1000L

/** Kept clear of the pointer hotspot: a tooltip drawn under the cursor takes the hover itself. */
private val TOOLTIP_CURSOR_GAP = 16.dp
private val TOOLTIP_MAX_WIDTH = 420.dp
private val TOOLTIP_ELEVATION = 2.dp

/**
 * The link the pointer is resting on, and the URL that rest has earned the right to show.
 *
 * Hover is a mouse-only affordance: a finger has no position between taps, so nothing here is
 * driven by touch input.
 */
@Stable
internal class ReaderLinkHoverState {
    /** The link directly under the pointer. Drives the cursor icon, and arms the reveal timer. */
    var hoveredUrl by mutableStateOf<String?>(null)

    /** Set once the pointer has rested on [hoveredUrl] long enough for the tooltip to show. */
    var visibleUrl by mutableStateOf<String?>(null)

    /** A click or a wheel turn withdraws the tooltip until the pointer moves again. */
    var suppressed by mutableStateOf(false)

    /** Where the pointer was when [visibleUrl] appeared — the tooltip does not chase the cursor. */
    var anchor by mutableStateOf(IntOffset.Zero)

    /**
     * The live pointer position within the text block. A plain field on purpose: it changes on
     * every mouse move and is only ever read when the timer fires.
     */
    var cursor: IntOffset = IntOffset.Zero
}

@Composable
internal fun rememberReaderLinkHoverState(): ReaderLinkHoverState {
    val state = remember { ReaderLinkHoverState() }
    LaunchedEffect(state.hoveredUrl, state.suppressed) {
        state.visibleUrl = null
        val url = state.hoveredUrl ?: return@LaunchedEffect
        if (state.suppressed) return@LaunchedEffect
        delay(LINK_TOOLTIP_DELAY_MS)
        state.anchor = state.cursor
        state.visibleUrl = url
    }
    return state
}

/**
 * Tracks which link — if any — the pointer sits on, and swaps the cursor for a hand over it.
 *
 * A whole text block is one composable, so the hand cannot come from a modifier on the link: the
 * character under the pointer has to be resolved against the block's own layout on every move.
 * [overrideDescendants] is what puts the hand ahead of the I-beam the selectable text asks for.
 */
@Composable
internal fun Modifier.readerLinkHover(
    state: ReaderLinkHoverState,
    text: AnnotatedString,
    layout: () -> TextLayoutResult?
): Modifier {
    val hasLinks = remember(text) {
        text.getStringAnnotations(LINK_ANNOTATION_TAG, 0, text.length).isNotEmpty()
    }
    if (!hasLinks) return this
    return this
        .then(
            if (state.hoveredUrl != null) {
                Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
            } else {
                Modifier
            }
        )
        .pointerInput(text, state) { trackLinkHover(state, text, layout) }
}

private suspend fun PointerInputScope.trackLinkHover(
    state: ReaderLinkHoverState,
    text: AnnotatedString,
    layout: () -> TextLayoutResult?
) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.lastOrNull() ?: continue
            if (change.type != PointerType.Mouse) continue
            when (event.type) {
                PointerEventType.Exit -> {
                    state.hoveredUrl = null
                    state.suppressed = false
                }
                // A wheel turn moves the text out from under a stationary pointer, so whatever the
                // tooltip is naming is no longer what it points at.
                PointerEventType.Press, PointerEventType.Scroll -> state.suppressed = true
                PointerEventType.Enter, PointerEventType.Move -> {
                    if (change.pressed) {
                        state.suppressed = true
                    } else {
                        state.suppressed = false
                        state.cursor = change.position.round()
                        state.hoveredUrl = layout()
                            ?.characterAt(change.position)
                            ?.let { text.linkAt(it) }
                    }
                }
                else -> {}
            }
        }
    }
}

/** The URL of the link covering [charOffset], or null where the text carries no link there. */
internal fun AnnotatedString.linkAt(charOffset: Int): String? {
    if (charOffset < 0 || charOffset >= length) return null
    return getStringAnnotations(LINK_ANNOTATION_TAG, charOffset, charOffset + 1)
        .firstOrNull()
        ?.item
}

/**
 * The character actually under [position], or null when the pointer is in the margin.
 *
 * [TextLayoutResult.getOffsetForPosition] answers with the nearest character *boundary*, so it
 * reports the character after the one being pointed at whenever the pointer is past its midpoint,
 * and reports the end of a line for a pointer anywhere in the space beside it. Confirming the
 * candidate against its own box is what keeps the hand off the margin and off the character after
 * a link's last one.
 */
internal fun TextLayoutResult.characterAt(position: Offset): Int? {
    val last = layoutInput.text.length - 1
    val candidate = getOffsetForPosition(position)
    for (offset in intArrayOf(candidate, candidate - 1)) {
        if (offset in 0..last && getBoundingBox(offset).contains(position)) return offset
    }
    return null
}

/** The URL of the hovered link, in a plain tooltip beside the pointer. */
@Composable
internal fun LinkUrlTooltip(url: String, cursor: IntOffset) {
    val gap = with(LocalDensity.current) { TOOLTIP_CURSOR_GAP.roundToPx() }
    val provider = remember(cursor, gap) { CursorTooltipPositionProvider(cursor, IntOffset(gap, gap)) }
    val style = floatingSurfaceStyle(TOOLTIP_ELEVATION)
    Popup(popupPositionProvider = provider) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = if (style.outlined) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.inverseSurface
            },
            contentColor = if (style.outlined) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.inverseOnSurface
            },
            shadowElevation = style.shadowElevation,
            border = style.borderStroke()
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = TOOLTIP_MAX_WIDTH)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** Places the tooltip beside the pointer rather than beside the anchor it was declared in. */
internal class CursorTooltipPositionProvider(
    private val cursor: IntOffset,
    private val gap: IntOffset
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = tooltipPosition(anchorBounds.topLeft + cursor, gap, windowSize, popupContentSize)
}

/**
 * Below and to the right of [cursor], flipped above it when the window has no room below and
 * pulled back inside the right edge when a long URL would otherwise run off it.
 */
internal fun tooltipPosition(
    cursor: IntOffset,
    gap: IntOffset,
    windowSize: IntSize,
    tooltipSize: IntSize
): IntOffset {
    val below = cursor.y + gap.y
    val y = if (below + tooltipSize.height <= windowSize.height) {
        below
    } else {
        (cursor.y - gap.y - tooltipSize.height).coerceAtLeast(0)
    }
    val x = (cursor.x + gap.x).coerceIn(0, (windowSize.width - tooltipSize.width).coerceAtLeast(0))
    return IntOffset(x, y)
}
