package com.karakept.app.ui.components.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.karakept.app.data.model.Highlight

/**
 * Annotation tag used for highlight click targets.
 */
const val HIGHLIGHT_ANNOTATION_TAG = "karakept_highlight"

/**
 * Annotation tag used for link click targets.
 */
const val LINK_ANNOTATION_TAG = "karakept_link"

/**
 * Builds an [AnnotatedString] from the inline children of a block-level HTML element.
 *
 * Text offsets are tracked via [textOffset] which is a mutable counter that increments
 * for every text character encountered. This counter produces the same offset values
 * as the JavaScript TreeWalker used in the WebView implementation, ensuring highlights
 * created in one renderer are compatible with the other.
 *
 * @param element     The block-level element whose children to render.
 * @param theme       Reader theme providing colors, font, etc.
 * @param highlights  All highlights for this bookmark — only those overlapping
 *                    the current block's offset range will be applied.
 * @param textOffset  Running text offset counter (mutated as text nodes are consumed).
 * @param onLinkClick Callback when a link is tapped.
 * @param onHighlightClick Callback when a highlight is tapped.
 * @return The styled [AnnotatedString].
 */
fun buildInlineAnnotatedString(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    selectedHighlightId: String? = null
): AnnotatedString {
    // First pass: build the string and collect span info
    val blockStartOffset = textOffset.offset

    val builder = AnnotatedString.Builder()
    appendNodeChildren(builder, element, theme, textOffset, onLinkClick)

    val blockEndOffset = textOffset.offset

    // Second pass: apply highlight annotations on top
    val result = builder.toAnnotatedString()

    if (highlights.isEmpty()) return result

    // Find highlights that overlap with this block's offset range
    val overlapping = highlights.filter { h ->
        h.startOffset < blockEndOffset && h.endOffset > blockStartOffset
    }

    if (overlapping.isEmpty()) return result

    // Rebuild with highlight spans added on top
    return buildAnnotatedString {
        // append(AnnotatedString) copies text + all existing spans/annotations
        append(result)

        for (highlight in overlapping) {
            // Convert document offsets to local string positions
            val localStart = (highlight.startOffset - blockStartOffset).coerceIn(0, result.length)
            val localEnd = (highlight.endOffset - blockStartOffset).coerceIn(0, result.length)
            if (localStart >= localEnd) continue

            val bgColor = theme.highlightColors[highlight.color ?: "yellow"] ?: theme.highlightColors["yellow"]!!

            addStyle(
                SpanStyle(
                    background = bgColor,
                    color = ReaderThemeData.highlightTextColor
                ),
                localStart,
                localEnd
            )
            addStringAnnotation(
                tag = HIGHLIGHT_ANNOTATION_TAG,
                annotation = highlight.id,
                start = localStart,
                end = localEnd
            )
        }
    }
}

/**
 * Recursively appends child nodes of an element to the [AnnotatedString.Builder],
 * applying inline styling spans.
 */
private fun appendNodeChildren(
    builder: AnnotatedString.Builder,
    node: Node,
    theme: ReaderThemeData,
    textOffset: TextOffsetTracker,
    onLinkClick: (String) -> Unit
) {
    for (child in node.childNodes()) {
        when (child) {
            is TextNode -> {
                val text = child.getWholeText()
                builder.append(text)
                textOffset.advance(text.length)
            }

            is Element -> {
                val tag = child.tagName().lowercase()
                when (tag) {
                    "b", "strong" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, builder.length)
                    }

                    "i", "em", "cite", "dfn" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                        builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, builder.length)
                    }

                    "u" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                        builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, builder.length)
                    }

                    "del", "s", "strike" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                        builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, builder.length)
                    }

                    "code" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                        builder.addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = theme.codeBackgroundColor,
                                fontSize = (theme.fontSize.value * 0.875f).sp
                            ),
                            start,
                            builder.length
                        )
                    }

                    "a" -> {
                        val href = child.attr("href")
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                        val end = builder.length
                        if (href.isNotBlank() && start < end) {
                            builder.addStyle(
                                SpanStyle(
                                    color = theme.linkColor,
                                    textDecoration = TextDecoration.Underline
                                ),
                                start,
                                end
                            )
                            builder.addStringAnnotation(
                                tag = LINK_ANNOTATION_TAG,
                                annotation = href,
                                start = start,
                                end = end
                            )
                        }
                    }

                    "mark" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                        // Use the data-id if present (our highlight marks), otherwise generic yellow
                        val highlightId = child.attr("data-id")
                        if (highlightId.isNotBlank()) {
                            // Our highlight marks — handled in the overlay pass
                        } else {
                            // Generic <mark> from HTML content
                            builder.addStyle(
                                SpanStyle(background = Color(0xFFFFEB3B)), // Full opacity generic mark
                                start,
                                builder.length
                            )
                        }
                    }

                    "sup" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                        builder.addStyle(
                            SpanStyle(
                                baselineShift = BaselineShift.Superscript,
                                fontSize = (theme.fontSize.value * 0.75f).sp
                            ),
                            start,
                            builder.length
                        )
                    }

                    "sub" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                        builder.addStyle(
                            SpanStyle(
                                baselineShift = BaselineShift.Subscript,
                                fontSize = (theme.fontSize.value * 0.75f).sp
                            ),
                            start,
                            builder.length
                        )
                    }

                    "span", "div" -> {
                        // Pass-through for inline span/div — just render children
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                    }

                    "br" -> {
                        builder.append("\n")
                        // <br> is not a text node, it doesn't increment the text offset counter
                        // as TreeWalker only counts text nodes
                    }

                    "img" -> {
                        // Images inside inline context — add placeholder text
                        // Actual image rendering is handled in block renderer
                        // but we still need to handle <img> inside <a> or <p> etc.
                        // Add an object replacement character as placeholder
                        builder.append(" ")
                        textOffset.advance(0) // img has no text content in TreeWalker
                    }

                    else -> {
                        // Unknown inline tag — render children
                        appendNodeChildren(builder, child, theme, textOffset, onLinkClick)
                    }
                }
            }
        }
    }
}

/**
 * Mutable offset tracker that mirrors the TreeWalker text offset counting
 * used in the WebView's JavaScript highlight code.
 */
class TextOffsetTracker(var offset: Int = 0) {
    fun advance(chars: Int) {
        offset += chars
    }
}
