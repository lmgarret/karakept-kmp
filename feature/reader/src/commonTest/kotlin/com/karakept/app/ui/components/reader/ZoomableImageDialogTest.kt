package com.karakept.app.ui.components.reader

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [clampImageZoom] and [clampImagePan], the pure gesture-math helpers behind
 * [ImageGalleryOverlay]'s pinch/scroll zoom and drag-to-pan behavior.
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

    // --- dismissDragProgress ---

    @Test
    fun dismissDragProgress_atRest_isZero() {
        assertEquals(0f, dismissDragProgress(0f, 300f))
    }

    @Test
    fun dismissDragProgress_halfwayToThreshold_isHalf() {
        assertEquals(0.5f, dismissDragProgress(-150f, 300f))
    }

    @Test
    fun dismissDragProgress_atThreshold_isOne() {
        assertEquals(1f, dismissDragProgress(-300f, 300f))
    }

    @Test
    fun dismissDragProgress_pastThreshold_clampsToOne() {
        assertEquals(1f, dismissDragProgress(-500f, 300f))
    }

    @Test
    fun dismissDragProgress_downwardDrag_clampsToZero() {
        // Downward drags are clamped to 0 before reaching this function in practice, but the
        // math itself must not report negative progress if a positive offset ever arrives.
        assertEquals(0f, dismissDragProgress(150f, 300f))
    }

    @Test
    fun dismissDragProgress_zeroOrNegativeThreshold_isZero() {
        // Threshold isn't known yet (e.g. before the first layout pass) — never report progress.
        assertEquals(0f, dismissDragProgress(-150f, 0f))
        assertEquals(0f, dismissDragProgress(-150f, -10f))
    }

    // --- shouldDismissFromDrag ---

    @Test
    fun shouldDismissFromDrag_belowThreshold_isFalse() {
        assertEquals(false, shouldDismissFromDrag(-200f, 300f))
    }

    @Test
    fun shouldDismissFromDrag_pastThreshold_isTrue() {
        assertEquals(true, shouldDismissFromDrag(-350f, 300f))
    }

    @Test
    fun shouldDismissFromDrag_exactlyAtThreshold_isFalse() {
        // Must travel strictly past the threshold, not just reach it.
        assertEquals(false, shouldDismissFromDrag(-300f, 300f))
    }

    @Test
    fun shouldDismissFromDrag_downwardDrag_isFalse() {
        assertEquals(false, shouldDismissFromDrag(300f, 300f))
    }

    @Test
    fun shouldDismissFromDrag_zeroOrNegativeThreshold_isFalse() {
        assertEquals(false, shouldDismissFromDrag(-500f, 0f))
        assertEquals(false, shouldDismissFromDrag(-500f, -10f))
    }
}
