package com.karakept.app.ui.components.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import com.karakept.app.ui.components.HighlightPosition
import com.karakept.app.ui.components.drawHighlightRule
import com.karakept.app.ui.theme.HighlightPalette
import com.karakept.app.ui.theme.HighlightPattern
import com.karakept.app.ui.theme.LocalEinkMode


/**
 * A Text composable that supports tap-based click handling for string annotations
 * (links and highlights) while remaining fully compatible with [SelectionContainer].
 *
 * Unlike [LinkAnnotation.Clickable], which intercepts long-press gestures and
 * breaks text selection, this composable uses [pointerInput] with [detectTapGestures]
 * to handle single taps only. Long-press is left unhandled so that
 * [SelectionContainer] can detect it for text selection.
 *
 * @param text The [AnnotatedString] to display, with string annotations for links
 *             (tag = [LINK_ANNOTATION_TAG]) and highlights (tag = [HIGHLIGHT_ANNOTATION_TAG]).
 * @param onLinkClick Called when a link annotation is tapped, with the URL.
 * @param onHighlightClick Called when a highlight annotation is tapped, with the highlight ID.
 * @param modifier Modifier for the Text composable.
 * @param color Default text color.
 * @param fontSize Font size.
 * @param fontFamily Font family.
 * @param fontWeight Font weight.
 * @param fontStyle Font style.
 * @param lineHeight Line height.
 * @param overflow Text overflow behavior.
 */
@Composable
fun AnnotatedClickableText(
    text: AnnotatedString,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    selectedHighlightId: String? = null,
    highlights: List<com.karakept.app.data.model.Highlight> = emptyList(),
    onHighlightPosition: (String, HighlightPosition) -> Unit = { _, _ -> }
) {
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val selectionRange = remember(text, selectedHighlightId) {
        if (selectedHighlightId == null) null
        else {
            text.getStringAnnotations(tag = HIGHLIGHT_ANNOTATION_TAG, start = 0, end = text.length)
                .find { it.item == selectedHighlightId }
        }
    }

    // On a monochrome panel every highlight is filled with the same grey, so the colour has to be
    // carried by a pattern drawn under the run. Empty off e-ink — nothing is drawn and nothing is
    // allocated.
    val patternRules = LocalEinkMode.current.highContrast
    val patternRuns = remember(text, highlights, patternRules) {
        if (!patternRules) {
            emptyList()
        } else {
            text.getStringAnnotations(HIGHLIGHT_ANNOTATION_TAG, 0, text.length)
                .mapNotNull { annotation ->
                    val highlight = highlights.firstOrNull { it.id == annotation.item }
                        ?: return@mapNotNull null
                    PatternRun(
                        pattern = HighlightPalette.styleFor(highlight.color).pattern,
                        start = annotation.start,
                        end = annotation.end
                    )
                }
        }
    }
    val ruleColor = MaterialTheme.colorScheme.onSecondaryContainer

    var rootOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val linkHover = rememberReaderLinkHoverState()

    // Identifies this block's contribution to a highlight's mask across the
    // re-reports that scrolling triggers.
    val positionSourceKey = remember { Any() }

    // Page snapping needs where every line of this block sits so a turn can stop on a line top
    // rather than through the middle of one. Position and text layout arrive in either order and
    // from two different callbacks, so they are staged in a plain holder — snapshot state would
    // subscribe the layout pass to itself, and this is written on every scroll frame.
    val snapRegistry = LocalReaderSnapRegistry.current
    val snapReport = remember { SnapLineReport() }
    if (snapRegistry != null) {
        DisposableEffect(snapRegistry, positionSourceKey) {
            onDispose { snapRegistry.forget(positionSourceKey) }
        }
    }

    // Report position when layout or root position changes
    LaunchedEffect(selectionRange, layoutResult.value, rootOffset) {
        val layout = layoutResult.value ?: return@LaunchedEffect
        val range = selectionRange ?: return@LaunchedEffect

        val padding = 2f
        val cornerRadius = 6f

        // Build path in root coordinates so multiple text blocks
        // (multi-paragraph highlights) can be merged into one path.
        val path = Path().apply {
            for (box in highlightLineRects(layout, range.start, range.end)) {
                val rect = Rect(
                    left = box.left - padding + rootOffset.x,
                    top = box.top - padding + rootOffset.y,
                    right = box.right + padding + rootOffset.x,
                    bottom = box.bottom + padding + rootOffset.y
                )
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect, androidx.compose.ui.geometry.CornerRadius(cornerRadius)
                    )
                )
            }
        }
        val bounds = path.getBounds()

        onHighlightPosition(
            range.item,
            HighlightPosition(
                x = bounds.left,
                y = bounds.top,
                width = bounds.width,
                height = bounds.height,
                scrollX = 0f,
                scrollY = 0f,
                path = path,
                rootOffset = androidx.compose.ui.geometry.Offset.Zero,
                sourceKey = positionSourceKey
            )
        )
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val position = coords.positionInRoot()
                rootOffset = position
                snapRegistry?.let { snapReport.onPositioned(it, positionSourceKey, position.y) }
            }
            .readerLinkHover(linkHover, text) { layoutResult.value }
            .pointerInput(text) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation()
                    }
                    if (up != null) {
                        val layout = layoutResult.value ?: return@awaitEachGesture
                        val charOffset = layout.getOffsetForPosition(up.position)

                        // Check highlight annotations first (more specific)
                        val highlightAnnotations = text.getStringAnnotations(
                            tag = HIGHLIGHT_ANNOTATION_TAG,
                            start = charOffset,
                            end = charOffset + 1
                        )
                        if (highlightAnnotations.isNotEmpty()) {
                            up.consume()
                            onHighlightClick(highlightAnnotations.first().item)
                            return@awaitEachGesture
                        }

                        // Then check link annotations
                        val linkUrl = text.linkAt(charOffset)
                        if (linkUrl != null) {
                            up.consume()
                            onLinkClick(linkUrl)
                            return@awaitEachGesture
                        }
                    }
                    // Long press: don't consume — SelectionContainer handles it
                }
            }
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            lineHeight = lineHeight,
            overflow = overflow,
            onTextLayout = {
                layoutResult.value = it
                snapRegistry?.let { registry -> snapReport.onTextLayout(registry, positionSourceKey, it) }
            },
            modifier = Modifier.drawHighlightRules(patternRuns, ruleColor) { layoutResult.value }
        )

        linkHover.visibleUrl?.let { url -> LinkUrlTooltip(url, linkHover.anchor) }
    }
}

/** One highlight's pattern and the character range it covers within a single text block. */
private data class PatternRun(val pattern: HighlightPattern, val start: Int, val end: Int)

/**
 * Draws the per-colour rule under each highlighted run.
 *
 * Over the text rather than behind it: a highlight's fill is a `SpanStyle.background`, which the
 * text painter draws as part of its own content, so anything put behind it is covered up.
 *
 * Off e-ink [runs] is always empty and this adds nothing to the modifier chain. The layout is read
 * through a lambda because `onTextLayout` only fires after the modifier has been built.
 */
private fun Modifier.drawHighlightRules(
    runs: List<PatternRun>,
    color: Color,
    layout: () -> TextLayoutResult?
): Modifier {
    if (runs.isEmpty()) return this
    return drawWithContent {
        drawContent()
        val result = layout() ?: return@drawWithContent
        val strokeWidth = 1.dp.toPx()
        for (run in runs) {
            for (box in highlightLineRects(result, run.start, run.end)) {
                drawHighlightRule(
                    pattern = run.pattern,
                    color = color,
                    left = box.left,
                    right = box.right,
                    bottom = box.bottom,
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}

/**
 * The tight per-line boxes covering [start] until [end].
 *
 * Deliberately not `TextLayoutResult.getPathForRange()`, which extends every wrapped line to the
 * full column width and swallows the whitespace either side of the range.
 */
internal fun highlightLineRects(
    layout: TextLayoutResult,
    start: Int,
    end: Int
): List<Rect> {
    if (start >= end) return emptyList()
    val rects = mutableListOf<Rect>()
    val startLine = layout.getLineForOffset(start)
    val endLine = layout.getLineForOffset(end - 1)
    for (line in startLine..endLine) {
        val lineStart = maxOf(start, layout.getLineStart(line))
        val lineEnd = minOf(end, layout.getLineEnd(line))
        if (lineStart >= lineEnd) continue

        val firstBox = layout.getBoundingBox(lineStart)
        val lastBox = layout.getBoundingBox(lineEnd - 1)
        rects += Rect(
            left = minOf(firstBox.left, lastBox.left),
            top = firstBox.top,
            right = maxOf(firstBox.right, lastBox.right),
            bottom = firstBox.bottom
        )
    }
    return rects
}

/**
 * Staging for [ReaderSnapRegistry] reports, which need a block's root position and its line layout
 * — two values that arrive from separate callbacks in no fixed order, and neither of which may
 * observe the other as snapshot state without dragging layout into recomposition.
 */
private class SnapLineReport {
    private var topInRoot: Float? = null
    private var lineTops: FloatArray? = null
    private var lineBottoms: FloatArray? = null

    fun onPositioned(registry: ReaderSnapRegistry, key: Any, y: Float) {
        topInRoot = y
        publish(registry, key)
    }

    fun onTextLayout(registry: ReaderSnapRegistry, key: Any, layout: TextLayoutResult) {
        lineTops = FloatArray(layout.lineCount) { line -> layout.getLineTop(line) }
        lineBottoms = FloatArray(layout.lineCount) { line -> layout.getLineBottom(line) }
        publish(registry, key)
    }

    private fun publish(registry: ReaderSnapRegistry, key: Any) {
        val top = topInRoot ?: return
        val tops = lineTops ?: return
        val bottoms = lineBottoms ?: return
        registry.report(key, top, tops, bottoms)
    }
}
