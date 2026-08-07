package com.karakept.app.ui.components.reader

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [clampImageZoom] and [clampImagePan], the pure gesture-math helpers behind
 * [ZoomableImageDialog]'s pinch/scroll zoom and drag-to-pan behavior.
 */
class ZoomableImageDialogTest {

    // --- clampImageZoom ---

    @Test
    fun clampImageZoom_withinRange_returnsUnchanged() {
        assertEquals(2.5f, clampImageZoom(2.5f))
    }

    @Test
    fun clampImageZoom_belowMin_clampsToMin() {
        assertEquals(MIN_IMAGE_ZOOM, clampImageZoom(0.3f))
    }

    @Test
    fun clampImageZoom_aboveMax_clampsToMax() {
        assertEquals(MAX_IMAGE_ZOOM, clampImageZoom(12f))
    }

    @Test
    fun clampImageZoom_atBounds_returnsBounds() {
        assertEquals(MIN_IMAGE_ZOOM, clampImageZoom(MIN_IMAGE_ZOOM))
        assertEquals(MAX_IMAGE_ZOOM, clampImageZoom(MAX_IMAGE_ZOOM))
    }

    // --- clampImagePan ---

    @Test
    fun clampImagePan_atMinZoom_alwaysZero() {
        assertEquals(0f, clampImagePan(500f, MIN_IMAGE_ZOOM, 1000f))
        assertEquals(0f, clampImagePan(-500f, MIN_IMAGE_ZOOM, 1000f))
    }

    @Test
    fun clampImagePan_belowMinZoom_alwaysZero() {
        // Defensive: scale should never go below MIN_IMAGE_ZOOM in practice, but the
        // pan clamp must not allow panning if it somehow did.
        assertEquals(0f, clampImagePan(500f, 0.5f, 1000f))
    }

    @Test
    fun clampImagePan_zoomedIn_withinBounds_returnsUnchanged() {
        // At 2x zoom on a 1000px container, max offset is 1000 * (2-1) / 2 = 500.
        assertEquals(200f, clampImagePan(200f, 2f, 1000f))
    }

    @Test
    fun clampImagePan_zoomedIn_exceedsPositiveBound_clamps() {
        assertEquals(500f, clampImagePan(800f, 2f, 1000f))
    }

    @Test
    fun clampImagePan_zoomedIn_exceedsNegativeBound_clamps() {
        assertEquals(-500f, clampImagePan(-800f, 2f, 1000f))
    }

    @Test
    fun clampImagePan_higherZoom_allowsLargerOffset() {
        // At 5x (MAX_IMAGE_ZOOM) on a 1000px container, max offset is 1000 * 4 / 2 = 2000.
        assertEquals(1800f, clampImagePan(1800f, MAX_IMAGE_ZOOM, 1000f))
    }

    @Test
    fun clampImagePan_zeroContainerDimension_clampsToZero() {
        assertEquals(0f, clampImagePan(100f, 2f, 0f))
    }
}
