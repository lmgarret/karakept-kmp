package com.karakept.app.ui.theme

import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HighlightPaletteTest {

    @Test
    fun allCoversExactlyTheApiColorSet() {
        // The Karakeep API rejects anything outside this set, and RemoteDataSource maps the string
        // straight onto the generated enum.
        assertEquals(
            listOf("yellow", "blue", "green", "red"),
            HighlightPalette.all.map { it.name }
        )
    }

    @Test
    fun everyColorHasItsOwnPattern() {
        // The whole e-ink treatment rests on this: two colours sharing a pattern are two colours a
        // monochrome panel cannot tell apart.
        val patterns = HighlightPalette.all.map { it.pattern }
        assertEquals(patterns.size, patterns.toSet().size)
    }

    @Test
    fun styleForResolvesEachName() {
        for (style in HighlightPalette.all) {
            assertEquals(style, HighlightPalette.styleFor(style.name))
        }
    }

    @Test
    fun styleForIsCaseInsensitive() {
        assertEquals(HighlightPalette.styleFor("red"), HighlightPalette.styleFor("RED"))
        assertEquals(HighlightPalette.styleFor("blue"), HighlightPalette.styleFor("Blue"))
    }

    @Test
    fun styleForFallsBackToYellowForNullAndUnknown() {
        // Highlights created in the reader carry no colour, and a future server-side colour would
        // arrive as an unknown string.
        assertEquals(HighlightPalette.default, HighlightPalette.styleFor(null))
        assertEquals(HighlightPalette.default, HighlightPalette.styleFor("chartreuse"))
        assertEquals("yellow", HighlightPalette.default.name)
    }

    @Test
    fun cssHexMatchesTheComposeColor() {
        // The WebView stylesheet is generated from cssHex while the native reader uses color; they
        // have to name the same colour.
        for (style in HighlightPalette.all) {
            val rgb = style.color.toArgb() and 0xFFFFFF
            val expected = "#" + rgb.toString(16).padStart(6, '0')
            assertEquals(expected, style.cssHex, "cssHex drifted from color for ${style.name}")
        }
    }

    @Test
    fun labelsAreHumanReadable() {
        assertTrue(HighlightPalette.all.all { it.label.isNotBlank() && it.label != it.name })
    }
}
