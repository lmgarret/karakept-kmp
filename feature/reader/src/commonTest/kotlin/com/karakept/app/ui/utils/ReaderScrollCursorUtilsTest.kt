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
    fun aListParkedOnAHeadingWithinAPixelHasReachedIt() {
        // Scrolling to a section rounds its fraction to whole pixels; read back, the list can sit a
        // hair short of the heading. The cursor passes that much slack.
        assertEquals(0, sectionIndexAt(listOf(0.40002f), 0.4f, epsilon = 0.0001f))
        assertEquals(-1, sectionIndexAt(listOf(0.40002f), 0.4f, epsilon = 0f))
    }

    // ---- the dwell track ----

    @Test
    fun withoutSectionsTheThumbIsTheScrollPosition() {
        val track = readerCursorTrack(emptyList(), dwellHalfWidth = 0.05f)

        for (t in listOf(0f, 0.25f, 0.5f, 1f)) {
            assertEquals(t, track.scrollAt(t))
            assertEquals(t, track.thumbAt(t))
        }
    }

    @Test
    fun theListHoldsStillOnAHeadingAcrossItsDwell() {
        // The fix for headings never being seen: over its dwell, the scroll is exactly the offset
        // that brings the heading into view, however far the finger moves inside it.
        val track = readerCursorTrack(listOf(0.4f), dwellHalfWidth = 0.05f)

        for (t in listOf(0.35f, 0.38f, 0.4f, 0.42f, 0.45f)) {
            assertEquals(0.4f, track.scrollAt(t), "thumb at $t")
        }
    }

    @Test
    fun outsideTheDwellsTheTrackRunsStraightFromOneToTheNext() {
        val track = readerCursorTrack(listOf(0.4f), dwellHalfWidth = 0.05f)

        // From the top of the track to the dwell's start, 0 → 0.4 over 0 → 0.35.
        assertEquals(0.2f, track.scrollAt(0.175f), 1e-5f)
        // From the dwell's end to the bottom, 0.4 → 1 over 0.45 → 1.
        assertEquals(0.7f, track.scrollAt(0.725f), 1e-5f)
    }

    @Test
    fun theArticleNeverJumpsOrRunsBackwardsUnderTheFinger() {
        // Two sections 0.02 apart, one near each end: the cases where dwells crowd each other.
        val track = readerCursorTrack(listOf(0.05f, 0.3f, 0.32f, 0.6f, 0.98f), dwellHalfWidth = 0.04f)

        val step = 1f / 2000
        var previous = track.scrollAt(0f)
        for (i in 1..2000) {
            val scroll = track.scrollAt(i * step)
            assertTrue(scroll >= previous, "scroll ran backwards at ${i * step}")
            // Never steeper than three times the average, so never a jump.
            assertTrue(scroll - previous <= 3f * step + 1e-5f, "scroll jumped at ${i * step}")
            previous = scroll
        }
        assertEquals(0f, track.scrollAt(0f))
        assertEquals(1f, track.scrollAt(1f))
    }

    @Test
    fun crowdedDwellsAreNarrowedWithASlopeLeftBetweenThem() {
        // Two short sections each still get a dwell, a third of the way to the other at most,
        // so the article moves from one heading to the next instead of jumping.
        val track = readerCursorTrack(listOf(0.5f, 0.53f), dwellHalfWidth = 0.05f)

        assertEquals(0.51f, track.dwells[0].end, 1e-5f)
        assertEquals(0.52f, track.dwells[1].start, 1e-5f)
        assertEquals(0.5f, track.scrollAt(0.505f))
        assertEquals(0.515f, track.scrollAt(0.515f), 1e-5f)
        assertEquals(0.53f, track.scrollAt(0.525f))
    }

    @Test
    fun aDwellNeverRunsOffEitherEndOfTheTrack() {
        val track = readerCursorTrack(listOf(0f, 1f), dwellHalfWidth = 0.05f)

        assertEquals(0f, track.dwells[0].start)
        assertEquals(1f, track.dwells[1].end)
    }

    @Test
    fun aSectionRunningBackwardsIsHeldAtTheOneBeforeIt() {
        val track = readerCursorTrack(listOf(0.5f, 0.3f), dwellHalfWidth = 0.02f)

        assertEquals(listOf(0.5f, 0.5f), track.dwells.map { it.scroll })
    }

    @Test
    fun aThumbLetGoOnADwellStaysOnItsMark() {
        // The inverse is what the idle thumb uses. Mapped linearly instead, a thumb let go on a
        // dwell would hop off it the moment the drag ended.
        val track = readerCursorTrack(listOf(0.4f), dwellHalfWidth = 0.05f)

        assertEquals(track.dwells[0].center, track.thumbAt(0.4f))
        assertEquals(track.dwells[0].center, track.thumbAt(0.40001f, epsilon = 0.0001f))
    }

    @Test
    fun theIdleThumbRetracesTheDragEverywhereElse() {
        val track = readerCursorTrack(listOf(0.2f, 0.55f, 0.8f), dwellHalfWidth = 0.03f)

        for (t in listOf(0.05f, 0.12f, 0.3f, 0.45f, 0.65f, 0.9f, 0.99f)) {
            assertEquals(t, track.thumbAt(track.scrollAt(t)), 1e-4f, "thumb at $t")
        }
    }
}
