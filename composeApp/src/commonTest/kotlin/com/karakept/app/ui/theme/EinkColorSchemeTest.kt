package com.karakept.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The e-ink scheme exists because MD3's tonal surface steps collapse into indistinguishable greys
 * on an electronic-paper panel. These tests assert the two properties that makes it usable:
 * maximum foreground/background separation, and no mid-tone anywhere.
 */
class EinkColorSchemeTest {

    private fun assertMaxContrast(foreground: Color, background: Color, label: String) {
        val delta = abs(foreground.luminance() - background.luminance())
        assertTrue(delta > 0.99f, "$label should be full black-on-white contrast, got delta $delta")
    }

    @Test
    fun `light scheme paints ink on a white page`() {
        val scheme = einkColorScheme(isDark = false)
        assertEquals(Color.White, scheme.surface)
        assertEquals(Color.White, scheme.background)
        assertEquals(Color.Black, scheme.onSurface)
        assertMaxContrast(scheme.onSurface, scheme.surface, "onSurface/surface")
    }

    @Test
    fun `dark scheme inverts the page without losing contrast`() {
        val scheme = einkColorScheme(isDark = true)
        assertEquals(Color.Black, scheme.surface)
        assertEquals(Color.White, scheme.onSurface)
        assertMaxContrast(scheme.onSurface, scheme.surface, "onSurface/surface")
    }

    @Test
    fun `every content role contrasts fully with its container`() {
        for (isDark in listOf(false, true)) {
            val scheme = einkColorScheme(isDark)
            val label = if (isDark) "dark" else "light"
            assertMaxContrast(scheme.onPrimary, scheme.primary, "$label onPrimary/primary")
            assertMaxContrast(scheme.onSecondary, scheme.secondary, "$label onSecondary/secondary")
            assertMaxContrast(scheme.onTertiary, scheme.tertiary, "$label onTertiary/tertiary")
            assertMaxContrast(scheme.onBackground, scheme.background, "$label onBackground/background")
            assertMaxContrast(scheme.onSurfaceVariant, scheme.surfaceVariant, "$label onSurfaceVariant/surfaceVariant")
            assertMaxContrast(scheme.onError, scheme.error, "$label onError/error")
            assertMaxContrast(
                scheme.onPrimaryContainer, scheme.primaryContainer,
                "$label onPrimaryContainer/primaryContainer"
            )
        }
    }

    @Test
    fun `no surface role carries a tonal step`() {
        // Any grey here would be the exact problem the scheme exists to avoid: two surfaces the
        // panel renders as the same shade, with nothing else separating them.
        for (isDark in listOf(false, true)) {
            val scheme = einkColorScheme(isDark)
            val page = scheme.surface
            val surfaces = mapOf(
                "background" to scheme.background,
                "surfaceVariant" to scheme.surfaceVariant,
                "surfaceContainer" to scheme.surfaceContainer,
                "surfaceContainerLow" to scheme.surfaceContainerLow,
                "surfaceContainerLowest" to scheme.surfaceContainerLowest,
                "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                "surfaceContainerHighest" to scheme.surfaceContainerHighest,
                "primaryContainer" to scheme.primaryContainer,
                "tertiaryContainer" to scheme.tertiaryContainer,
                "errorContainer" to scheme.errorContainer
            )
            for ((name, color) in surfaces) {
                assertEquals(page, color, "$name must be the page colour, not a tonal step")
            }
        }
    }

    /**
     * Panel greyscale levels are evenly spaced in sRGB, so "will the panel show these as different
     * shades" is an sRGB question. Relative luminance is the wrong metric for it — it compresses
     * dark tones so hard that two clearly distinct dark greys look identical by that measure.
     */
    private fun srgbGap(a: Color, b: Color) = abs(a.red - b.red)

    /** WCAG contrast ratio — the right metric for "is this text readable", unlike a raw gap. */
    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    @Test
    fun `the chip grey is the single deliberate mid-tone`() {
        // Tag chips need to read as a group without each drawing a full-strength outline. The grey
        // has to sit clear of both the page and the ink, or a 16-level panel collapses it into one
        // of them — the exact failure the rest of this scheme avoids. A 0.15 sRGB gap is roughly
        // two and a half of those levels.
        for (isDark in listOf(false, true)) {
            val scheme = einkColorScheme(isDark)
            val label = if (isDark) "dark" else "light"

            assertTrue(
                srgbGap(scheme.secondaryContainer, scheme.surface) > 0.15f,
                "$label chip grey is too close to the page"
            )
            assertTrue(
                srgbGap(scheme.secondaryContainer, scheme.onSurface) > 0.15f,
                "$label chip grey is too close to the ink"
            )
            assertTrue(
                contrastRatio(scheme.onSecondaryContainer, scheme.secondaryContainer) > 4.5f,
                "$label chip text is not readable on the chip grey"
            )
        }
    }

    @Test
    fun `outline is full ink so borders can replace elevation`() {
        for (isDark in listOf(false, true)) {
            val scheme = einkColorScheme(isDark)
            assertEquals(scheme.onSurface, scheme.outline)
            assertEquals(scheme.onSurface, scheme.outlineVariant)
            assertMaxContrast(scheme.outline, scheme.surface, "outline/surface")
        }
    }
}
