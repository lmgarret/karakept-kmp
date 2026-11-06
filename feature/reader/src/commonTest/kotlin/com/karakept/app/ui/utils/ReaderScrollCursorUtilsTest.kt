package com.karakept.app.ui.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The arithmetic behind the reader's scroll cursor.
 *
 * The bookmark list's cursor counts rows; the reader's cannot, because its whole article is a
 * single lazy item. It counts content pixels instead, which means every position it shows and
 * every position it scrolls to is reconstructed from item heights rather than read off
 * `layoutInfo` — so the reconstruction is what these tests pin down.
 */
class ReaderScrollCursorUtilsTest {

    // hero, description, article
    private val heights = listOf(800, 200, 12_000)

    @Test
    fun absoluteOffsetAddsUpTheItemsAlreadyScrolledPast() {
        assertEquals(0, readerAbsoluteScrollOffset(heights, 0, 0))
        assertEquals(300, readerAbsoluteScrollOffset(heights, 0, 300))
        assertEquals(1000 + 500, readerAbsoluteScrollOffset(heights, 2, 500))
    }

    @Test
    fun scrollRangeIsEverythingBelowTheFirstScreenful() {
        assertEquals(13_000 - 1800, readerScrollRange(heights, viewportHeightPx = 1800))
    }

    @Test
    fun trailingContentPaddingIsScrollableToo() {
        // E-ink mode pads the end of the article so the last page turn lands on a handover.
        assertEquals(
            13_000 + 900 - 1800,
            readerScrollRange(heights, viewportHeightPx = 1800, contentPaddingPx = 900)
        )
    }

    @Test
    fun anArticleShorterThanTheViewportHasNowhereToScroll() {
        assertEquals(0, readerScrollRange(listOf(400), viewportHeightPx = 1800))
    }

    @Test
    fun scrollTargetResolvesAContentPixelToAnItemAndAnOffset() {
        assertEquals(ReaderScrollTarget(0, 300), readerScrollTarget(heights, 300))
        assertEquals(ReaderScrollTarget(1, 100), readerScrollTarget(heights, 900))
        assertEquals(ReaderScrollTarget(2, 500), readerScrollTarget(heights, 1500))
    }

    @Test
    fun scrollTargetSkipsItemsThatHaveNotBeenMeasured() {
        // A summary card that has never been on screen reports no height; it occupies no pixels,
        // so a target lands in the item after it rather than in a zero-height gap.
        assertEquals(ReaderScrollTarget(2, 0), readerScrollTarget(listOf(800, 0, 12_000), 800))
    }

    @Test
    fun scrollTargetPastTheEndRestsOnTheLastMeasuredItem() {
        assertEquals(ReaderScrollTarget(2, 12_000), readerScrollTarget(heights, 99_999))
    }

    @Test
    fun scrollTargetWithoutAnyMeasuredItemStaysAtTheTop() {
        assertEquals(ReaderScrollTarget(0, 0), readerScrollTarget(listOf(0, 0), 500))
        assertEquals(ReaderScrollTarget(0, 0), readerScrollTarget(emptyList(), 500))
    }

    @Test
    fun sectionAtIsTheLastHeadingAtOrAboveThePosition() {
        val fractions = listOf(0.1f, 0.4f, 0.75f)

        assertEquals(0, sectionIndexAt(fractions, 0.1f))
        assertEquals(0, sectionIndexAt(fractions, 0.39f))
        assertEquals(1, sectionIndexAt(fractions, 0.4f))
        assertEquals(2, sectionIndexAt(fractions, 1f))
    }

    @Test
    fun aPositionAboveTheFirstHeadingIsInNoSection() {
        assertEquals(-1, sectionIndexAt(listOf(0.1f, 0.4f), 0.05f))
        assertEquals(-1, sectionIndexAt(emptyList(), 0.5f))
    }

    @Test
    fun labelsAreThinnedToWhatFitsAlongTheTrack() {
        // Ten evenly spread sections over a 1000px track: one every 100px, but a label needs 250.
        val fractions = List(10) { it / 10f }

        val picked = pickTocLabels(fractions, trackHeightPx = 1000f, minSpacingPx = 250f, current = 0)

        assertEquals(listOf(0, 3, 6, 9), picked)
    }

    @Test
    fun theSectionBeingPointedAtIsAlwaysLabelled() {
        val fractions = List(10) { it / 10f }

        val picked = pickTocLabels(fractions, trackHeightPx = 1000f, minSpacingPx = 250f, current = 4)

        assertTrue(4 in picked)
        assertEquals(picked.sorted(), picked)
    }

    @Test
    fun labelsSpreadOutwardsFromTheThumbInBothDirections() {
        val fractions = List(10) { it / 10f }

        val picked = pickTocLabels(fractions, trackHeightPx = 1000f, minSpacingPx = 250f, current = 4)

        assertTrue(picked.any { it < 4 }, "sections above the thumb should still be named")
        assertTrue(picked.any { it > 4 }, "sections below the thumb should still be named")
    }

    @Test
    fun labelCountIsCapped() {
        val fractions = List(200) { it / 200f }

        val picked = pickTocLabels(
            fractions, trackHeightPx = 100_000f, minSpacingPx = 1f, current = 100, maxLabels = 6
        )

        assertEquals(6, picked.size)
    }

    @Test
    fun aSingleSectionIsLabelledEvenWhenTheTrackIsTiny() {
        assertEquals(
            listOf(0),
            pickTocLabels(listOf(0.5f), trackHeightPx = 40f, minSpacingPx = 250f, current = 0)
        )
    }

    @Test
    fun noSectionsMeansNoLabels() {
        assertTrue(pickTocLabels(emptyList(), 1000f, 250f, current = -1).isEmpty())
    }
}
