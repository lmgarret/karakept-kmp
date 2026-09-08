package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import com.karakept.app.data.model.Highlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [findTextOffsets].
 *
 * The offsets it hands out are karakeep's: the count of text-node characters
 * before the match and nothing else, so a highlight made here means the same
 * thing to the WebView viewer and to the server.
 */
class HighlightOffsetFinderTest {

    @Test
    fun findTextOffsets_blankSearchText_returnsNull() {
        val result = findTextOffsets("<p>text</p>", "")
        assertNull(result, "Blank search text should return null")
    }

    @Test
    fun findTextOffsets_simpleMatch_returnsCorrectOffsets() {
        val result = findTextOffsets("<p>Hello world</p>", "world")
        assertNotNull(result, "Should find 'world' in HTML")
        assertTrue(result.matchedText.contains("world"),
            "matchedText should contain 'world', got: ${result.matchedText}")
        assertTrue(result.startOffset < result.endOffset,
            "startOffset (${result.startOffset}) should be less than endOffset (${result.endOffset})")
    }

    @Test
    fun findTextOffsets_notFound_returnsNull() {
        val result = findTextOffsets("<p>Hello</p>", "nonexistent")
        assertNull(result, "Non-matching text should return null")
    }

    @Test
    fun findTextOffsets_whitespaceNormalization_matchesAcrossSpaces() {
        val result = findTextOffsets("<p>Hello    world</p>", "Hello world")
        assertNotNull(result, "Should find text even with multiple spaces in HTML")
        assertTrue(result.matchedText.contains("Hello"),
            "matchedText should contain the matched text")
    }

    @Test
    fun findTextOffsets_multipleTextNodes_matchesAcrossNodes() {
        val result = findTextOffsets("<p>Hello <b>world</b> here</p>", "world here")
        assertNotNull(result, "Should find text spanning across HTML element boundaries")
    }

    @Test
    fun findTextOffsets_caseInsensitive_matchesDifferentCase() {
        val result = findTextOffsets("<p>Hello World</p>", "hello world")
        assertNotNull(result, "Should find text with different case (case-insensitive)")
    }

    @Test
    fun findTextOffsets_nbspNormalization_matchesNbsp() {
        // \u00A0 is non-breaking space
        val html = "<p>Hello\u00A0World</p>"
        val result = findTextOffsets(html, "Hello World")
        assertNotNull(result, "Should match NBSP as regular space")
    }

    @Test
    fun findTextOffsets_invalidHtml_returnsNullOrValid() {
        // Should not throw -- graceful handling of malformed HTML
        val result = findTextOffsets("<<<>>>", "text")
        // Ksoup is permissive, so this may return null (text not found) but not throw
        // Just verify no exception
        assertNull(result, "Text should not be found in malformed HTML")
    }

    // ── The canonical stream ─────────────────────────────────────────────────

    private fun offsetsOf(html: String) = buildReaderTextOffsets(Ksoup.parse(html).body())

    @Test
    fun theStreamIsExactlyTheDocumentsTextNodes() {
        // ksoup's own concatenation of every text node — the count karakeep's
        // TreeWalker makes, arrived at without going through this walk.
        val body = Ksoup.parse(
            "<div><p>Intro.</p><ul><li>One</li><li>Two</li></ul><p>Tail</p></div>"
        ).body()

        assertEquals(body.wholeText(), buildReaderTextOffsets(body).text)
    }

    @Test
    fun findTextOffsets_countsTextNodesOnly() {
        // What a TreeWalker over the two paragraphs counts: "Hello" then "world".
        val result = findTextOffsets("<p>Hello</p><p>world</p>", "world")
        assertNotNull(result)
        assertEquals(5, result.startOffset)
        assertEquals(10, result.endOffset)
    }

    @Test
    fun findTextOffsets_containersAndListsTakeNoOffsetOfTheirOwn() {
        val html = "<div><p>Intro.</p><ul><li>One</li><li>Two</li></ul><p>Tail</p></div>"

        val result = findTextOffsets(html, "Tail")

        assertNotNull(result)
        assertEquals("Intro.OneTwo".length, result.startOffset)
    }

    @Test
    fun findTextOffsets_selectionSpanningBlocks_resolvesAcrossTheBoundary() {
        // Compose hands back the two paragraphs joined with a newline.
        val result = findTextOffsets("<p>Hello</p><p>world</p>", "Hello\nworld")

        assertNotNull(result)
        assertEquals(0, result.startOffset)
        assertEquals(10, result.endOffset)
        assertEquals("Hello\nworld", result.matchedText)
    }

    @Test
    fun findTextOffsets_selectionSpanningABlockAndTheTextAfterIt_resolves() {
        // The reader draws the paragraph and the trailing text separately, so the
        // selection has a newline where the stream has nothing.
        val result = findTextOffsets("<div><p>Quoted.</p>attribution</div>", "Quoted.\nattribution")

        assertNotNull(result)
        assertEquals(0, result.startOffset)
        assertEquals("Quoted.attribution".length, result.endOffset)
    }

    @Test
    fun findTextOffsets_matchStopsAtTheLastMatchedCharacter() {
        val result = findTextOffsets("<p>Hello   world   tail</p>", "Hello world")

        assertNotNull(result)
        assertEquals(0, result.startOffset)
        assertEquals("Hello   world".length, result.endOffset)
    }

    @Test
    fun findTextMatches_returnsEveryOccurrenceInOrder() {
        val offsets = offsetsOf("<p>the cat sat on the mat</p>")

        val matches = findTextMatches(offsets, "the")

        assertEquals(listOf(0, 15), matches.map { it.startOffset })
    }

    @Test
    fun offsets_areKeyedPerNodeNotPerContent() {
        // Two paragraphs that read the same are two entries, not one.
        val body = Ksoup.parse("<p>same</p><p>same</p>").body()
        val offsets = buildReaderTextOffsets(body)

        val paragraphs = body.children()
        assertEquals(0, offsets.startOf(paragraphs[0]))
        assertEquals(4, offsets.startOf(paragraphs[1]))
    }

    // ── Repairing offsets that no longer line up ─────────────────────────────

    private fun highlight(text: String, start: Int, end: Int) = Highlight(
        id = "h1",
        bookmarkId = "b1",
        text = text,
        startOffset = start,
        endOffset = end,
        createdAt = 0L
    )

    @Test
    fun resolveHighlights_leavesAMatchingHighlightAlone() {
        val offsets = offsetsOf("<p>Hello</p><p>world</p>")

        val resolved = resolveHighlights(listOf(highlight("world", 5, 10)), offsets).single()

        assertEquals(5, resolved.startOffset)
        assertEquals(10, resolved.endOffset)
    }

    @Test
    fun resolveHighlights_repairsOffsetsFromAnotherConvention() {
        // What the reader used to record for this document: a virtual newline per block.
        val offsets = offsetsOf("<p>Hello</p><p>world</p>")

        val resolved = resolveHighlights(listOf(highlight("world", 6, 11)), offsets).single()

        assertEquals(5, resolved.startOffset)
        assertEquals(10, resolved.endOffset)
    }

    @Test
    fun resolveHighlights_repairsToTheNearestOccurrence() {
        val offsets = offsetsOf("<p>repeat</p><p>filler</p><p>repeat</p>")

        // Stored against the trailing occurrence (12), one convention's drift away.
        val resolved = resolveHighlights(listOf(highlight("repeat", 14, 20)), offsets).single()

        assertEquals(12, resolved.startOffset)
        assertEquals(18, resolved.endOffset)
    }

    @Test
    fun resolveHighlights_keepsAHighlightWhoseTextIsGone() {
        val offsets = offsetsOf("<p>Hello world</p>")

        val stale = highlight("a phrase the article no longer has", 3, 9)
        val resolved = resolveHighlights(listOf(stale), offsets).single()

        assertEquals(stale, resolved)
    }
}
