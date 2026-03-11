package com.karakept.app.ui.components.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

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
    overflow: TextOverflow = TextOverflow.Clip
) {
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        lineHeight = lineHeight,
        overflow = overflow,
        onTextLayout = { layoutResult.value = it },
        modifier = modifier.pointerInput(text) {
            detectTapGestures { offset ->
                val layout = layoutResult.value ?: return@detectTapGestures
                val charOffset = layout.getOffsetForPosition(offset)

                // Check highlight annotations first (more specific)
                val highlightAnnotations = text.getStringAnnotations(
                    tag = HIGHLIGHT_ANNOTATION_TAG,
                    start = charOffset,
                    end = charOffset + 1
                )
                if (highlightAnnotations.isNotEmpty()) {
                    onHighlightClick(highlightAnnotations.first().item)
                    return@detectTapGestures
                }

                // Then check link annotations
                val linkAnnotations = text.getStringAnnotations(
                    tag = LINK_ANNOTATION_TAG,
                    start = charOffset,
                    end = charOffset + 1
                )
                if (linkAnnotations.isNotEmpty()) {
                    onLinkClick(linkAnnotations.first().item)
                    return@detectTapGestures
                }
            }
        }
    )
}
