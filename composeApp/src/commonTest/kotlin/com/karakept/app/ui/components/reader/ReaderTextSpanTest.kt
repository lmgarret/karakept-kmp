package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Holds the reader's top-level spans to the offsets a highlight is created at.
 *
 * Highlights were created at the offsets [findTextOffsets] resolved and drawn at
 * offsets the renderer accumulated as it walked, and the two walks disagreed about
 * what sits between blocks — so every highlight landed a little off, drifting
 * further down the article (#295, and again for every container the renderer
 * walked with `children()`). Both now read [ReaderTextOffsets], where nothing sits
 * between blocks at all.
 */
class ReaderTextSpanTest {

    private fun spansOf(html: String): List<ReaderTextSpan> {
        val body = Ksoup.parse(html).body()
        return computeReaderTextSpans(body, buildReaderTextOffsets(body))
    }

    /** Offset a selection of [text] would be recorded at. */
    private fun createdOffsetOf(html: String, text: String): Int {
        val result = findTextOffsets(html, text)
        assertNotNull(result, "\"$text\" should be found in the document")
        return result.startOffset
    }

    @Test
    fun spanOffsetsMatchTheOffsetsHighlightsAreCreatedAt() {
        val html = "<p>First paragraph.</p><p>Second paragraph.</p><p>Third paragraph.</p>"

        val spans = spansOf(html).filter { it.isRenderable }

        assertEquals(3, spans.size)
        assertEquals(createdOffsetOf(html, "First paragraph."), spans[0].startOffset)
        assertEquals(createdOffsetOf(html, "Second paragraph."), spans[1].startOffset)
        assertEquals(createdOffsetOf(html, "Third paragraph."), spans[2].startOffset)
    }

    @Test
    fun blocksAreSeparatedByNothingInTheStream() {
        val html = "<p>abc</p><p>de</p><p>f</p>"

        val spans = spansOf(html).filter { it.isRenderable }

        // "abc" + "de" + "f" — a boundary matches as whitespace but occupies no offset
        assertEquals(listOf(0, 3, 5), spans.map { it.startOffset })
    }

    @Test
    fun whitespaceBetweenTopLevelBlocksOccupiesTheStream() {
        val html = "<p>abc</p>\n  <p>de</p>"

        val spans = spansOf(html)

        val paragraphs = spans.filter { it.isRenderable }
        assertEquals(createdOffsetOf(html, "de"), paragraphs[1].startOffset)
        assertTrue(
            paragraphs[1].startOffset > 3,
            "Whitespace between blocks is real text, and has to be counted"
        )
    }

    @Test
    fun whitespaceOnlyNodesAreNotRendered() {
        val html = "<p>abc</p>\n  <p>de</p>"

        val spans = spansOf(html)

        assertTrue(
            spans.any { !it.isRenderable },
            "The whitespace between the paragraphs is measured but not drawn"
        )
    }

    @Test
    fun theFirstBlockStartsAtZero() {
        val spans = spansOf("<p>abc</p><p>de</p>").filter { it.isRenderable }

        assertEquals(0, spans.first().startOffset)
    }

    @Test
    fun inlineContentAtTopLevelIsMeasuredWithoutASeparator() {
        val html = "<span>lead in</span><p>body</p>"

        val spans = spansOf(html)

        assertEquals(createdOffsetOf(html, "body"), spans.last().startOffset)
    }

    @Test
    fun nestedBlocksResolveThroughTheirTopLevelParent() {
        val html = "<div><p>alpha</p><p>beta</p></div><p>gamma</p>"

        val spans = spansOf(html).filter { it.isRenderable }

        assertEquals(2, spans.size, "Only the div and the trailing paragraph are top level")
        assertEquals(0, spans[0].startOffset)
        assertEquals(createdOffsetOf(html, "gamma"), spans[1].startOffset)
    }

    @Test
    fun aContainerFollowingContentDoesNotShiftWhatComesAfterIt() {
        // The container's own subtree used to be measured on its own, which lost the
        // separator its first block child was given in the document-wide walk.
        val html = "<p>Intro.</p><div><p>alpha</p><p>beta</p></div><p>gamma</p>"

        val spans = spansOf(html).filter { it.isRenderable }

        assertEquals(createdOffsetOf(html, "gamma"), spans.last().startOffset)
    }

    @Test
    fun offsetsStayAlignedAcrossAManyBlockDocument() {
        val paragraphs = (1..30).map { "<p>Paragraph number $it.</p>" }
        val html = paragraphs.joinToString("")

        val spans = spansOf(html).filter { it.isRenderable }

        // Drift used to grow by one character per block, so the tail is what
        // exposes it.
        for (index in 1..30) {
            assertEquals(
                createdOffsetOf(html, "Paragraph number $index."),
                spans[index - 1].startOffset,
                "Block $index is misaligned"
            )
        }
    }
}
