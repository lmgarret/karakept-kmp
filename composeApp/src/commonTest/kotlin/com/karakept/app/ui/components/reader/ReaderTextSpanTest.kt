package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression coverage for #295: highlights were created at the offsets
 * [findTextOffsets] resolves, but drawn at offsets the reader accumulated as it
 * walked the document's top-level children. The two walks disagreed — the
 * renderer's left out the newline that joins block elements and the whitespace
 * between them — so every highlight landed a little earlier than the selection,
 * drifting further with each block.
 *
 * [computeReaderTextSpans] is now the single definition of that stream, and
 * these tests hold it to the offsets [findTextOffsets] hands out.
 */
class ReaderTextSpanTest {

    private fun spansOf(html: String) = computeReaderTextSpans(Ksoup.parse(html).body())

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
    fun blockSeparatorsAreCountedBetweenTopLevelBlocks() {
        val html = "<p>abc</p><p>de</p><p>f</p>"

        val spans = spansOf(html).filter { it.isRenderable }

        // "abc" + "\n" + "de" + "\n" + "f"
        assertEquals(listOf(0, 4, 7), spans.map { it.startOffset })
    }

    @Test
    fun whitespaceBetweenTopLevelBlocksOccupiesTheStream() {
        val html = "<p>abc</p>\n  <p>de</p>"

        val spans = spansOf(html)

        val paragraphs = spans.filter { it.isRenderable }
        assertEquals(createdOffsetOf(html, "de"), paragraphs[1].startOffset)
        assertTrue(
            paragraphs[1].startOffset > 4,
            "Whitespace between blocks has to be counted, not skipped"
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
    fun theFirstBlockGetsNoLeadingSeparator() {
        val spans = spansOf("<p>abc</p><p>de</p>").filter { it.isRenderable }

        assertEquals(0, spans.first().startOffset)
    }

    @Test
    fun inlineContentAtTopLevelIsMeasuredWithoutASeparator() {
        val html = "<span>lead in</span><p>body</p>"

        val spans = spansOf(html)

        // The span is measured but not drawn; the paragraph still follows a
        // separator because content precedes it.
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
