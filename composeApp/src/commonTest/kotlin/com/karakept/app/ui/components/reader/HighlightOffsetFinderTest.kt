package com.karakept.app.ui.components.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [findTextOffsets].
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
}
