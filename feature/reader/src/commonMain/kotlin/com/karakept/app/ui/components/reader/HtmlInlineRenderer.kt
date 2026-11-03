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
import com.karakept.app.ui.theme.HighlightPalette

/**
 * Annotation tag used for highlight click targets.
 */
const val HIGHLIGHT_ANNOTATION_TAG = "karakept_highlight"

/**
 * Annotation tag used for link click targets.
 */
const val LINK_ANNOTATION_TAG = "karakept_link"

/**
 * Applies one highlight's fill and its click annotation over [start]..[end].
 *
 * On e-ink every highlight gets the same fill ([ReaderThemeData.monochromeHighlight]) because four
 * saturated colours collapse into the same grey; [AnnotatedClickableText] reads the annotations back
 * and draws the per-colour pattern rule that actually distinguishes them.
 */
internal fun AnnotatedString.Builder.addHighlightSpan(
    highlight: Highlight,
    start: Int,
    end: Int,
    theme: ReaderThemeData
) {
    val mono = theme.monochromeHighlight
    addStyle(
        SpanStyle(
            background = mono?.fill ?: HighlightPalette.styleFor(highlight.color).color,
            color = mono?.content ?: ReaderThemeData.highlightTextColor
        ),
        start,
        end
    )
    addStringAnnotation(
        tag = HIGHLIGHT_ANNOTATION_TAG,
        annotation = highlight.id,
        start = start,
        end = end
    )
}

/**
 * Styling for a `<mark>` that came from the article's own HTML rather than from a user highlight.
 *
 * The two call sites (block and inline paths) used to hardcode the same yellow at different
 * opacities.
 */
internal fun sourceMarkSpanStyle(theme: ReaderThemeData): SpanStyle {
    val mono = theme.monochromeHighlight
    return if (mono != null) {
        SpanStyle(background = mono.fill, color = mono.content)
    } else {
        SpanStyle(background = HighlightPalette.default.color)
    }
}

/**
 * Builds an [AnnotatedString] from the inline children of a block-level HTML element.
 *
 * Each text node records where it landed in the rendered string against where it
 * sits in the document's text stream ([ReaderTextOffsets]), and highlight and
 * search spans are placed by translating stream offsets through those records.
 * The string is not the stream — a `<br>` puts a newline in one and nothing in
 * the other — so the two are related by what was recorded, never by arithmetic
 * on a running counter.
 *
 * @param element     The block-level element whose children to render.
 * @param theme       Reader theme providing colors, font, etc.
 * @param highlights  All highlights for this bookmark — only those overlapping
 *                    the current block's offset range will be applied.
 * @param offsets     The document's text stream.
 * @param onLinkClick Callback when a link is tapped.
 * @param onHighlightClick Callback when a highlight is tapped.
 * @return The styled [AnnotatedString].
 */
fun buildInlineAnnotatedString(
    element: Element,
    theme: ReaderThemeData,
    highlights: List<Highlight>,
    offsets: ReaderTextOffsets,
    onLinkClick: (String) -> Unit,
    onHighlightClick: (String) -> Unit,
    selectedHighlightId: String? = null,
    searchState: Pair<List<SearchMatch>, Int>? = null
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val runs = TextRuns()
    appendNodeChildren(builder, element, theme, offsets, runs, onLinkClick)

    val result = builder.toAnnotatedString()
    return applyHighlightSpans(result, runs, highlights, theme, searchState)
}

/**
 * Draws [highlights] and the active search state over an already-rendered string,
 * placing each one through [runs].
 */
internal fun applyHighlightSpans(
    result: AnnotatedString,
    runs: TextRuns,
    highlights: List<Highlight>,
    theme: ReaderThemeData,
    searchState: Pair<List<SearchMatch>, Int>?
): AnnotatedString {
    val overlapping = highlights.filter { runs.overlaps(it.startOffset, it.endOffset) }
    if (overlapping.isEmpty() && searchState == null) return result

    return buildAnnotatedString {
        // append(AnnotatedString) copies text + all existing spans/annotations
        append(result)

        for (highlight in overlapping) {
            val local = runs.localRange(highlight.startOffset, highlight.endOffset) ?: continue
            addHighlightSpan(highlight, local.first, local.last + 1, theme)
        }

        // Applied on top of highlights so the active match stays visible over one
        if (searchState != null) {
            val (searchMatches, activeIndex) = searchState
            for ((matchIndex, match) in searchMatches.withIndex()) {
                val local = runs.localRange(match.startOffset, match.endOffset) ?: continue
                val bg = if (matchIndex == activeIndex) Color(0xCCFF9800) else Color(0x66FFC107)
                addStyle(SpanStyle(background = bg, color = Color.Black), local.first, local.last + 1)
            }
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
    offsets: ReaderTextOffsets,
    runs: TextRuns,
    onLinkClick: (String) -> Unit
) {
    for (child in node.childNodes()) {
        when (child) {
            is TextNode -> {
                val text = child.getWholeText()
                runs.record(builder.length, offsets.startOf(child), text.length)
                builder.append(text)
            }

            is Element -> {
                val tag = child.tagName().lowercase()
                when (tag) {
                    "b", "strong" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
                        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, builder.length)
                    }

                    "i", "em", "cite", "dfn" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
                        builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, builder.length)
                    }

                    "u" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
                        builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, builder.length)
                    }

                    "del", "s", "strike" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
                        builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, builder.length)
                    }

                    "code" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
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
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
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
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
                        // Use the data-id if present (our highlight marks), otherwise generic yellow
                        val highlightId = child.attr("data-id")
                        if (highlightId.isNotBlank()) {
                            // Our highlight marks — handled in the overlay pass
                        } else {
                            // Generic <mark> from HTML content
                            builder.addStyle(sourceMarkSpanStyle(theme), start, builder.length)
                        }
                    }

                    "sup" -> {
                        val start = builder.length
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
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
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
                        builder.addStyle(
                            SpanStyle(
                                baselineShift = BaselineShift.Subscript,
                                fontSize = (theme.fontSize.value * 0.75f).sp
                            ),
                            start,
                            builder.length
                        )
                    }

                    "span" -> {
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
                    }

                    "div", "p", "section", "article", "header", "footer",
                    "nav", "aside", "main", "address" -> {
                        // Block-level element inside inline context (e.g. <div>/<p> lines inside <pre>)
                        // — render children then add a newline to preserve line structure.
                        // Skip the newline if the last char is already a newline (avoids
                        // double-newlines from nested block elements like <div><p>...</p></div>).
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
                        if (builder.length == 0 || builder.toAnnotatedString().text.last() != '\n') {
                            builder.append("\n")
                        }
                    }

                    "br" -> {
                        // A line the reader draws, not text the document holds: it takes a
                        // character here and none in the stream.
                        builder.append("\n")
                    }

                    "img" -> {
                        // Images inside inline context — add placeholder text
                        // Actual image rendering is handled in block renderer
                        // but we still need to handle <img> inside <a> or <p> etc.
                        // Add an object replacement character as placeholder
                        builder.append(" ")
                    }

                    else -> {
                        // Unknown inline tag — render children
                        appendNodeChildren(builder, child, theme, offsets, runs, onLinkClick)
                    }
                }
            }
        }
    }
}
