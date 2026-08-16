package com.karakept.app.ui.screens.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.karakept.app.ui.theme.einkColorScheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reader's summary card is a tinted block on the reader's own background. Under `highContrast`
 * every tonal role it used to mix in is the page colour, so the tint has to be replaced by the
 * scheme's one deliberate grey — the same fill tag chips use — or the card disappears into the page.
 */
class DescriptionCardColorsTest {

    private fun einkColors(isDark: Boolean): DescriptionCardColors {
        val scheme = einkColorScheme(isDark)
        return descriptionCardColors(
            highContrast = true,
            readerBackground = scheme.background,
            readerText = scheme.onBackground,
            accentContainer = scheme.primaryContainer,
            onAccentContainer = scheme.onPrimaryContainer,
            einkContainer = scheme.secondaryContainer,
            einkContent = scheme.onSecondaryContainer
        )
    }

    @Test
    fun `high contrast fills the card with the same grey as a tag chip`() {
        for (isDark in listOf(false, true)) {
            val scheme = einkColorScheme(isDark)
            assertEquals(scheme.secondaryContainer, einkColors(isDark).container)
            assertEquals(scheme.onSecondaryContainer, einkColors(isDark).content)
        }
    }

    /** Panel greyscale levels are evenly spaced in sRGB, so separation is an sRGB question. */
    private fun srgbGap(a: Color, b: Color) = abs(a.red - b.red)

    private fun contrastRatio(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    @Test
    fun `high contrast separates the card from the page it sits on`() {
        for (isDark in listOf(false, true)) {
            val page = einkColorScheme(isDark).background
            assertTrue(
                srgbGap(einkColors(isDark).container, page) > 0.15f,
                "isDark=$isDark left the summary card too close to the page colour"
            )
        }
    }

    @Test
    fun `high contrast keeps the summary text readable on that grey`() {
        for (isDark in listOf(false, true)) {
            val colors = einkColors(isDark)
            assertTrue(
                contrastRatio(colors.content, colors.container) > 4.5f,
                "isDark=$isDark left the summary text unreadable on the card"
            )
        }
    }

    @Test
    fun `off e-ink the card keeps its accent tint over the reader background`() {
        val readerBg = Color(0xFFFBF0D9)
        val readerText = Color(0xFF3A3222)
        val accent = Color(0xFF2D4BDA)
        val onAccent = Color(0xFF001453)

        val colors = descriptionCardColors(
            highContrast = false,
            readerBackground = readerBg,
            readerText = readerText,
            accentContainer = accent,
            onAccentContainer = onAccent,
            einkContainer = Color(0xFFC9C9C9),
            einkContent = Color.Black
        )

        assertTrue(colors.container != readerBg, "the card should tint away from the reader page")
        assertTrue(
            abs(colors.container.luminance() - readerBg.luminance()) <
                abs(colors.container.luminance() - accent.luminance()),
            "the tint should stay closer to the reader page than to the accent"
        )
        assertTrue(colors.content != readerText, "the text should pick up the accent too")
    }

    @Test
    fun `the e-ink fill never leaks into a normal reader`() {
        val eink = Color(0xFFC9C9C9)
        val colors = descriptionCardColors(
            highContrast = false,
            readerBackground = Color.White,
            readerText = Color.Black,
            accentContainer = Color(0xFFD7E3FF),
            onAccentContainer = Color(0xFF001B3F),
            einkContainer = eink,
            einkContent = Color.Black
        )
        assertTrue(colors.container != eink)
    }
}
