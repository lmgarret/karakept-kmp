package com.karakept.app.ui.input

import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.data.model.PageTurnKeyBindings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Volume codes come from [PlatformKeyCodes] rather than literals so these run on desktop too,
 * where the volume rocker is never delivered to the app and the codes are unreachable sentinels.
 */
class PageTurnVolumeKeysTest {

    private val volumeUp = PlatformKeyCodes.VOLUME_UP
    private val volumeDown = PlatformKeyCodes.VOLUME_DOWN

    @Test
    fun `volume keys are ignored until the preset is switched on`() {
        val bindings = PageTurnKeyBindings()

        assertNull(bindings.directionFor(volumeUp))
        assertNull(bindings.directionFor(volumeDown))
    }

    @Test
    fun `volume up turns back and volume down turns forward`() {
        val bindings = PageTurnKeyBindings(useVolumeKeys = true)

        assertEquals(PageTurnDirection.PREVIOUS, bindings.directionFor(volumeUp))
        assertEquals(PageTurnDirection.NEXT, bindings.directionFor(volumeDown))
    }

    @Test
    fun `inverting swaps both directions`() {
        val bindings = PageTurnKeyBindings(useVolumeKeys = true, invertVolumeKeys = true)

        assertEquals(PageTurnDirection.NEXT, bindings.directionFor(volumeUp))
        assertEquals(PageTurnDirection.PREVIOUS, bindings.directionFor(volumeDown))
    }

    @Test
    fun `inverting alone does nothing while the preset is off`() {
        val bindings = PageTurnKeyBindings(invertVolumeKeys = true)

        assertNull(bindings.directionFor(volumeUp))
        assertNull(bindings.directionFor(volumeDown))
    }

    @Test
    fun `the preset wins over a learned code on the same key`() {
        val bindings = PageTurnKeyBindings(
            useVolumeKeys = true,
            previousKeyCode = volumeDown
        )

        assertEquals(PageTurnDirection.NEXT, bindings.directionFor(volumeDown))
    }

    @Test
    fun `learned codes still work alongside the preset`() {
        val learned = 92
        val bindings = PageTurnKeyBindings(useVolumeKeys = true, nextKeyCode = learned)

        assertEquals(PageTurnDirection.NEXT, bindings.directionFor(learned))
        assertEquals(PageTurnDirection.PREVIOUS, bindings.directionFor(volumeUp))
    }

    @Test
    fun `disabling hardware buttons silences the preset too`() {
        val bindings = PageTurnKeyBindings(enabled = false, useVolumeKeys = true)

        assertNull(bindings.directionFor(volumeUp))
        assertNull(bindings.directionFor(volumeDown))
    }

    @Test
    fun `an unrelated key is never claimed`() {
        val bindings = PageTurnKeyBindings(useVolumeKeys = true)

        assertNull(bindings.directionFor(66))
    }

    @Test
    fun `page turns are instant by default`() {
        assertEquals(true, PageTurnKeyBindings().instantPageTurn)
    }
}
