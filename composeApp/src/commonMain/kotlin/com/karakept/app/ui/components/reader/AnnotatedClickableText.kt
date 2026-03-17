package com.karakept.app.ui.components.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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

    var rootOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // Report position when layout or root position changes
    LaunchedEffect(selectionRange, layoutResult.value, rootOffset) {
        val layout = layoutResult.value ?: return@LaunchedEffect
        val range = selectionRange ?: return@LaunchedEffect

        // Build a tight path using per-line bounding boxes instead of
        // getPathForRange(), which extends rectangles to full line width
        // on wrapped lines and includes non-highlighted whitespace.
        val startLine = layout.getLineForOffset(range.start)
        val endLine = layout.getLineForOffset(range.end - 1)
        val padding = 2f
        val cornerRadius = 6f

        // Build path in root coordinates so multiple text blocks
        // (multi-paragraph highlights) can be merged into one path.
        val path = Path().apply {
            for (line in startLine..endLine) {
                val lineStart = maxOf(range.start, layout.getLineStart(line))
                val lineEnd = minOf(range.end, layout.getLineEnd(line))
                if (lineStart >= lineEnd) continue

                val firstBox = layout.getBoundingBox(lineStart)
                val lastBox = layout.getBoundingBox(lineEnd - 1)
                val rect = Rect(
                    left = minOf(firstBox.left, lastBox.left) - padding + rootOffset.x,
                    top = firstBox.top - padding + rootOffset.y,
                    right = maxOf(firstBox.right, lastBox.right) + padding + rootOffset.x,
                    bottom = firstBox.bottom + padding + rootOffset.y
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
                rootOffset = androidx.compose.ui.geometry.Offset.Zero
            )
        )
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                rootOffset = coords.positionInRoot()
            }
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
                        val linkAnnotations = text.getStringAnnotations(
                            tag = LINK_ANNOTATION_TAG,
                            start = charOffset,
                            end = charOffset + 1
                        )
                        if (linkAnnotations.isNotEmpty()) {
                            up.consume()
                            onLinkClick(linkAnnotations.first().item)
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
            onTextLayout = { layoutResult.value = it }
        )
    }
}
