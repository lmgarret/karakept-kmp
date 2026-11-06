package com.karakept.app.ui.components.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.karakept.app.data.model.Highlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * End-to-end cover for what a highlight actually lands on.
 *
 * A selection is resolved to offsets the way the reader resolves one, the real
 * renderers draw the document, and the assertion is the text the reader painted.
 * Creation and drawing used to walk the document separately and disagree about
 * what sits between blocks, which shifted every highlight a little further along
 * the further down the article it was made.
 */
@OptIn(ExperimentalTestApi::class)
class HighlightRenderAlignmentTest {

    private val theme = ReaderThemeData(
        textColor = Color.Black,
        backgroundColor = Color.White,
        fontSize = 16.sp,
        fontFamily = FontFamily.Default,
        linkColor = Color.Blue,
        codeBackgroundColor = Color.LightGray
    )

    /** What the reader actually paints a highlight over, for the whole document. */
    private fun renderedHighlightText(html: String, highlight: Highlight): String {
        var painted = ""
        runComposeUiTest {
            setContent {
                val body = Ksoup.parse(html).body()
                val offsets = buildReaderTextOffsets(body)
                CompositionLocalProvider(LocalReaderTheme provides theme) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (span in computeReaderTextSpans(body, offsets).filter { it.isRenderable }) {
                            val node = span.node
                            if (node is Element && isBlockElement(node)) {
                                RenderBlock(
                                    element = node,
                                    highlights = listOf(highlight),
                                    offsets = offsets,
                                    onLinkClick = {},
                                    onHighlightClick = {},
                                    onHighlightPosition = { _, _ -> }
                                )
                            }
                        }
                    }
                }
            }
            val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
                .fetchSemanticsNodes()
            painted = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text) ?: emptyList() }
                .flatMap { annotated: AnnotatedString ->
                    annotated.getStringAnnotations(HIGHLIGHT_ANNOTATION_TAG, 0, annotated.length)
                        .map { annotated.text.substring(it.start, it.end) }
                }
                .joinToString(" ")
        }
        return painted
    }

    private fun highlightFor(html: String, phrase: String): Highlight {
        val offsets = findTextOffsets(html, phrase)
        assertNotNull(offsets, "\"$phrase\" should resolve to offsets")
        return Highlight(
            id = "h1",
            bookmarkId = "b1",
            text = offsets.matchedText,
            startOffset = offsets.startOffset,
            endOffset = offsets.endOffset,
            color = "yellow",
            createdAt = 0L
        )
    }

    @Test
    fun highlightLandsOnSelectionInFlatDocument() {
        val html = "<p>First paragraph here.</p><p>Second paragraph here.</p><p>Third one.</p>"
        val phrase = "Third one."
        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }

    @Test
    fun highlightLandsOnSelectionAfterNestedContainer() {
        val html = "<p>Intro line.</p><div><p>Nested alpha.</p><p>Nested beta.</p></div><p>Target phrase here.</p>"
        val phrase = "Target phrase here."
        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }

    @Test
    fun highlightLandsOnSelectionInsideReadabilityWrapper() {
        val body = (1..6).joinToString("") { "<p>Paragraph number $it with some words.</p>" }
        val html = "<div id=\"readability-page-1\" class=\"page\">$body</div>"
        val phrase = "Paragraph number 6 with some words."
        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }

    @Test
    fun highlightLandsOnSelectionAfterLineBreak() {
        val html = "<div><p>Line one.<br>Line two.</p><p>Tail phrase here.</p></div>"
        val phrase = "Tail phrase here."
        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }

    @Test
    fun highlightLandsOnSelectionAfterList() {
        val html = "<div><p>Intro line.</p><ul><li>First item</li><li>Second item</li></ul><p>Tail phrase here.</p></div>"
        val phrase = "Tail phrase here."
        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }

    @Test
    fun highlightLandsOnSelectionAfterTable() {
        val html = "<div><table><tr><td>First cell</td><td>Second cell</td></tr></table>" +
            "<p>Tail phrase here.</p></div>"
        val phrase = "Tail phrase here."
        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }

    @Test
    fun highlightLandsOnSelectionAfterBlockquoteAndFigure() {
        val html = "<div><p>Intro line.</p>" +
            "<blockquote><p>Quoted line.</p></blockquote>" +
            "<figure><img src=\"x.png\"><figcaption>A caption.</figcaption></figure>" +
            "<p>Tail phrase here.</p></div>"
        val phrase = "Tail phrase here."
        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }

    @Test
    fun highlightLandsOnSelectionInsideAParagraphWithLineBreaks() {
        val html = "<div><p>Line one.<br>Line two.</p><p>Third line.<br>Target phrase here.</p></div>"
        val phrase = "Target phrase here."
        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }

    @Test
    fun highlightSpanningTwoParagraphsCoversBoth() {
        val html = "<div><p>First half</p><p>second half</p></div>"

        // Compose hands back the two paragraphs joined with a newline.
        val highlight = highlightFor(html, "First half\nsecond half")

        assertEquals("First half second half", renderedHighlightText(html, highlight))
    }

    @Test
    fun highlightLandsOnSelectionDeepInAMixedArticle() {
        val body = buildString {
            append("<h1>An article</h1>")
            repeat(4) { index ->
                append("<p>Paragraph number $index with some words in it.</p>")
                append("<ul><li>Item $index a</li><li>Item $index b</li></ul>")
                append("<blockquote><p>Quote $index.</p></blockquote>")
            }
            append("<p>The very last phrase.</p>")
        }
        val html = "<div id=\"readability-page-1\" class=\"page\">$body</div>"
        val phrase = "The very last phrase."

        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }

    @Test
    fun highlightLandsOnSelectionAfterALineBreakInsideOneText() {
        // The <br> puts a newline in the rendered string that the stream has no
        // character for, so the two only line up through what each run recorded.
        val html = "<div><p><span>Line one.<br>Target phrase here.</span></p></div>"
        val phrase = "Target phrase here."
        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }

    @Test
    fun highlightLandsOnSelectionAfterAnInlineImage() {
        val html = "<div><p>Lead in <span><img src=\"x.png\"></span>Target phrase here.</p></div>"
        val phrase = "Target phrase here."
        assertEquals(phrase, renderedHighlightText(html, highlightFor(html, phrase)))
    }
}
