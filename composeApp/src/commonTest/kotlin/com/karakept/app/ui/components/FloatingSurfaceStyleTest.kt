package com.karakept.app.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the shadow/outline split every element that floats above the page shares — FABs, the
 * "N new bookmarks" pill, snackbars, the busy card.
 *
 * The property that matters is that such an element always has an edge: off e-ink its drop shadow
 * draws one, and under high contrast — where the shadow is a grey gradient the panel cannot render
 * and the container has already collapsed to the page colour — an outline has to take over.
 */
class FloatingSurfaceStyleTest {

    @Test
    fun `off e-ink a floating element keeps its shadow and draws no outline`() {
        val style = floatingSurfaceStyle(highContrast = false, shadowElevation = 6.dp)
        assertEquals(6.dp, style.shadowElevation)
        assertFalse(style.outlined)
    }

    @Test
    fun `high contrast drops the shadow`() {
        val style = floatingSurfaceStyle(highContrast = true, shadowElevation = 6.dp)
        assertEquals(0.dp, style.shadowElevation)
    }

    @Test
    fun `high contrast outlines the element instead`() {
        assertTrue(floatingSurfaceStyle(highContrast = true, shadowElevation = 6.dp).outlined)
    }

    @Test
    fun `exactly one of the shadow and the outline separates the element from the page`() {
        for (highContrast in listOf(false, true)) {
            for (elevation in listOf(3.dp, 4.dp, 6.dp)) {
                val style = floatingSurfaceStyle(highContrast, elevation)
                val shadowed = style.shadowElevation > 0.dp
                assertTrue(
                    shadowed != style.outlined,
                    "highContrast=$highContrast elevation=$elevation left the element with " +
                        "shadowed=$shadowed outlined=${style.outlined}"
                )
            }
        }
    }

    @Test
    fun `an element with no shadow to begin with gains an outline on e-ink and nothing off it`() {
        assertFalse(floatingSurfaceStyle(highContrast = false, shadowElevation = 0.dp).outlined)
        assertTrue(floatingSurfaceStyle(highContrast = true, shadowElevation = 0.dp).outlined)
    }
}

/**
 * The e-ink snackbar host replaces Material's, which is what normally dismisses a snackbar once
 * its duration is up. These are the timings it has to reproduce for that to keep working.
 */
class SnackbarDurationTest {

    @Test
    fun `a short snackbar dismisses itself after four seconds`() {
        assertEquals(4_000L, snackbarDurationMillis(SnackbarDuration.Short))
    }

    @Test
    fun `a long snackbar stays up for ten`() {
        assertEquals(10_000L, snackbarDurationMillis(SnackbarDuration.Long))
    }

    @Test
    fun `an indefinite snackbar is never dismissed on a timer`() {
        assertEquals(Long.MAX_VALUE, snackbarDurationMillis(SnackbarDuration.Indefinite))
    }

    @Test
    fun `every duration waits at least as long as the shorter one`() {
        assertTrue(
            snackbarDurationMillis(SnackbarDuration.Short) <
                snackbarDurationMillis(SnackbarDuration.Long)
        )
        assertTrue(
            snackbarDurationMillis(SnackbarDuration.Long) <
                snackbarDurationMillis(SnackbarDuration.Indefinite)
        )
    }
}
