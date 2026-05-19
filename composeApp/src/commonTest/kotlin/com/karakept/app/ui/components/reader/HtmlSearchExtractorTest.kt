package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [findSearchMatchesInDocument].
 *
 * The core invariant under test: the [SearchMatch.startOffset] and
 * [SearchMatch.endOffset] values mirror the [TextOffsetTracker] offsets
 * produced during rendering, so that highlight spans land on the correct text.
 */
class HtmlSearchExtractorTest {

    private fun search(html: String, query: String): List<SearchMatch> =
        findSearchMatchesInDocument(Ksoup.parse(html), query)

    // ── Basic matching ────────────────────────────────────────────────────────

    @Test
    fun emptyQuery_returnsNoMatches() {
        assertEquals(0, search("<p>Hello world</p>", "").size)
    }

    @Test
    fun queryNotPresent_returnsEmptyList() {
        assertEquals(0, search("<p>Hello world</p>", "foobar").size)
    }

    @Test
    fun singleMatch_returnsOneResult() {
        assertEquals(1, search("<p>Hello world</p>", "world").size)
    }

    @Test
    fun multipleOccurrences_allReturned() {
        // "the cat sat on the mat" → "the" appears twice
        val matches = search("<p>the cat sat on the mat</p>", "the")
        assertEquals(2, matches.size)
    }

    // ── Offset correctness ────────────────────────────────────────────────────

    @Test
    fun matchOffset_midParagraph_isCorrect() {
        // "Hello world" → "world" starts at index 6
        val matches = search("<p>Hello world</p>", "world")
        assertEquals(1, matches.size)
        assertEquals(6, matches[0].startOffset)
        assertEquals(11, matches[0].endOffset)
    }

    @Test
    fun matchOffset_firstWord_startsAtZero() {
        val matches = search("<p>Hello world</p>", "Hello")
        assertEquals(1, matches.size)
        assertEquals(0, matches[0].startOffset)
        assertEquals(5, matches[0].endOffset)
    }

    @Test
    fun matchOffset_overlappingOccurrences_allOffsetsCorrect() {
        // "aababab" → "ab" at 1, 3, 5
        val matches = search("<p>aababab</p>", "ab")
        assertEquals(3, matches.size)
        assertEquals(1, matches[0].startOffset); assertEquals(3,  matches[0].endOffset)
        assertEquals(3, matches[1].startOffset); assertEquals(5,  matches[1].endOffset)
        assertEquals(5, matches[2].startOffset); assertEquals(7,  matches[2].endOffset)
    }

    // ── Case insensitivity ────────────────────────────────────────────────────

    @Test
    fun caseInsensitive_lowerQueryUpperHtml_matches() {
        val matches = search("<p>Hello World</p>", "hello world")
        assertEquals(1, matches.size)
    }

    @Test
    fun caseInsensitive_allCaps_offsetStillCorrect() {
        // Offsets are into the *original* text, not the lowercased one
        val matches = search("<p>SAY HELLO NOW</p>", "hello")
        assertEquals(1, matches.size)
        assertEquals(4, matches[0].startOffset)
        assertEquals(9, matches[0].endOffset)
    }

    // ── Inline elements don't break offsets ──────────────────────────────────

    @Test
    fun boldWrappedWord_offsetUnaffected() {
        // "Hello <b>world</b>" renders as "Hello world"; "world" at 6
        val matches = search("<p>Hello <b>world</b></p>", "world")
        assertEquals(1, matches.size)
        assertEquals(6, matches[0].startOffset)
        assertEquals(11, matches[0].endOffset)
    }

    @Test
    fun boldSplitsWord_spanningMatchStillFound() {
        // "Hel<b>lo</b> world" → text "Hello world"; match at 0
        val matches = search("<p>Hel<b>lo</b> world</p>", "Hello")
        assertEquals(1, matches.size)
        assertEquals(0, matches[0].startOffset)
        assertEquals(5, matches[0].endOffset)
    }

    @Test
    fun brElement_doesNotAdvanceOffset() {
        // <br> is skipped (mirrors renderer), so "World" immediately follows "Hello"
        val matches = search("<p>Hello<br/>World</p>", "World")
        assertEquals(1, matches.size)
        assertEquals(5, matches[0].startOffset)
        assertEquals(10, matches[0].endOffset)
    }

    @Test
    fun linkText_offsetIsCorrect() {
        // "click here for more" → "here for" at offset 6
        val matches = search("<p><a href='x'>click here</a> for more</p>", "here for")
        assertEquals(1, matches.size)
        assertEquals(6, matches[0].startOffset)
        assertEquals(14, matches[0].endOffset)
    }

    // ── Block elements ────────────────────────────────────────────────────────

    @Test
    fun headingMatch_offsetCorrect() {
        // "Breaking news" → "news" at 9 ("Breaking " = 9 chars)
        val matches = search("<h1>Breaking news</h1>", "news")
        assertEquals(1, matches.size)
        assertEquals(9, matches[0].startOffset)
        assertEquals(13, matches[0].endOffset)
    }

    @Test
    fun listItem_matchFound() {
        val matches = search("<ul><li>First item</li><li>Second item</li></ul>", "Second")
        assertEquals(1, matches.size)
        // ul/ol iterate children without virtual separator; "First item" = 10 chars
        assertEquals(10, matches[0].startOffset)
        assertEquals(16, matches[0].endOffset)
    }

    @Test
    fun codeBlock_matchFound() {
        // <pre><code>def hello()…</code></pre> → "def " = 4 chars
        val matches = search("<pre><code>def hello():\n    pass</code></pre>", "hello")
        assertEquals(1, matches.size)
        assertEquals(4, matches[0].startOffset)
        assertEquals(9, matches[0].endOffset)
    }

    @Test
    fun blockquote_matchFound() {
        val matches = search("<blockquote><p>Famous quote here</p></blockquote>", "quote")
        assertEquals(1, matches.size)
    }

    // ── Virtual block separator ───────────────────────────────────────────────

    @Test
    fun nestedBlocks_virtualSpaceSeparatesAdjacentBlocks() {
        // extractChildrenForSearch inserts a virtual ' ' before the second <p>
        // when offset > 0. "Hello" (5) + ' ' (1) = "World" starts at 6.
        val html = "<div><p>Hello</p><p>World</p></div>"
        val world = search(html, "World")
        assertEquals(1, world.size)
        assertEquals(6, world[0].startOffset)
        assertEquals(11, world[0].endOffset)
    }

    @Test
    fun nestedBlocks_noMatchAcrossSeparator() {
        // "HelloWorld" cannot match because the virtual space sits between them
        val html = "<div><p>Hello</p><p>World</p></div>"
        assertEquals(0, search(html, "HelloWorld").size)
    }

    @Test
    fun nestedBlocks_spaceQueryMatchesAcrossSeparator() {
        // "Hello World" (with a space) does match because the virtual separator IS a space
        val html = "<div><p>Hello</p><p>World</p></div>"
        assertEquals(1, search(html, "Hello World").size)
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    @Test
    fun tableCell_matchFound() {
        val html = "<table><tr><td>Cell content</td><td>Another cell</td></tr></table>"
        assertEquals(1, search(html, "content").size)
    }

    @Test
    fun tableCell_secondCell_offsetCorrect() {
        // "First" = 5 chars; no separator between cells → "Second" at 5
        val html = "<table><tr><td>First</td><td>Second</td></tr></table>"
        val matches = search(html, "Second")
        assertEquals(1, matches.size)
        assertEquals(5, matches[0].startOffset)
        assertEquals(11, matches[0].endOffset)
    }
}
