package com.karakept.app.ui.components.reader

import com.karakept.app.ui.utils.sectionIndexAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Turning a laid-out heading into a position on the scroll cursor's track.
 *
 * Headings register themselves in root coordinates, which move under the reader on every scroll
 * frame; the track does not. The projection is what relates the two, and getting it wrong would
 * put every section label somewhere other than the scroll position it names.
 */
class ReaderScrollCursorTest {

    private fun geometry(
        absolutePx: Int = 500,
        rangePx: Int = 10_000,
        viewportStartOffsetPx: Int = 0,
        obscuredTopPx: Int = 0
    ) = ReaderCursorGeometry(
        itemHeights = listOf(800, 12_000),
        rangePx = rangePx,
        absolutePx = absolutePx,
        viewportStartOffsetPx = viewportStartOffsetPx,
        obscuredTopPx = obscuredTopPx
    )

    private fun entry(id: Int, title: String = "Section $id", level: Int = 2) =
        ReaderTocEntry(id = id, level = level, title = title)

    /** [positions] are the root coordinates each entry reports, or null for one not laid out. */
    private fun tocOf(vararg positions: Pair<ReaderTocEntry, Float?>) = ReaderTocState().apply {
        entries = positions.map { it.first }
        positions.forEach { (entry, y) -> if (y != null) report(entry.id) { y } }
    }

    @Test
    fun aHeadingAtTheTopOfTheViewportSitsAtTheCurrentScrollPosition() {
        val head = entry(0)
        val toc = tocOf(head to 100f)

        val sections = projectToc(toc, geometry(absolutePx = 500), listTopInRoot = 100f)

        assertEquals(0.05f, sections.single().fraction)
    }

    @Test
    fun aHeadingBelowTheFoldSitsFurtherDownTheTrack() {
        val head = entry(0)
        val toc = tocOf(head to 2100f)

        val sections = projectToc(toc, geometry(absolutePx = 500), listTopInRoot = 100f)

        // 500px scrolled + 2000px below the viewport top, over a 10 000px range.
        assertEquals(0.25f, sections.single().fraction)
    }

    @Test
    fun aHeadingScrolledPastSitsAboveTheThumb() {
        val head = entry(0)
        val toc = tocOf(head to -300f)

        val sections = projectToc(toc, geometry(absolutePx = 500), listTopInRoot = 100f)

        assertEquals(0.01f, sections.single().fraction)
    }

    @Test
    fun chromePaintedOverTheListDoesNotShiftTheProjection() {
        val head = entry(0)
        val toc = tocOf(head to 300f)

        val sections = projectToc(
            toc,
            geometry(absolutePx = 500, viewportStartOffsetPx = 200),
            listTopInRoot = 100f
        )

        // The heading is at the viewport's own top (100 + 200), so it is where the reader is.
        assertEquals(0.05f, sections.single().fraction)
    }

    @Test
    fun fractionsStayInsideTheTrack() {
        val above = entry(0)
        val below = entry(1)
        val toc = tocOf(above to -50_000f, below to 90_000f)

        val sections = projectToc(toc, geometry(), listTopInRoot = 0f)

        assertEquals(listOf(0f, 1f), sections.map { it.fraction })
    }

    @Test
    fun sectionsKeepTheirOrderAndTitles() {
        val first = entry(0, "Intro", level = 1)
        val second = entry(12, "Details", level = 3)
        val toc = tocOf(first to 0f, second to 5000f)

        val sections = projectToc(toc, geometry(absolutePx = 0), listTopInRoot = 0f)

        assertEquals(listOf("Intro", "Details"), sections.map { it.entry.title })
        assertEquals(listOf(1, 3), sections.map { it.entry.level })
        assertTrue(sections[0].fraction < sections[1].fraction)
    }

    @Test
    fun headingsThatHaveNotBeenLaidOutAreDroppedRatherThanGuessedAt() {
        val known = entry(0)
        val unknown = entry(7)
        val toc = tocOf(known to 100f, unknown to null)

        val sections = projectToc(toc, geometry(), listTopInRoot = 100f)

        assertEquals(listOf(0), sections.map { it.entry.id })
    }

    @Test
    fun aDetachedHeadingIsDroppedTheSameWay() {
        // A registered heading answers NaN once its node is gone, exactly as ReaderSnapRegistry's
        // entries do; the projection must not turn that into a position.
        val head = entry(0)
        val toc = ReaderTocState().apply {
            entries = listOf(head)
            report(head.id) { Float.NaN }
        }

        assertTrue(projectToc(toc, geometry(), listTopInRoot = 0f).isEmpty())
    }

    @Test
    fun positionsAreReadWhenAskedForRatherThanWhenReported() {
        // The registration is a lambda over live coordinates: scrolling moves the heading without
        // anything being reported again. Pinned here because pushing a position instead would cost
        // a callback per heading per scroll frame (see ReaderScrollCostTest).
        val head = entry(0)
        var topInRoot = 2100f
        val toc = ReaderTocState().apply {
            entries = listOf(head)
            report(head.id) { topInRoot }
        }

        assertEquals(0.25f, projectToc(toc, geometry(absolutePx = 500), 100f).single().fraction)

        topInRoot = 4100f
        assertEquals(0.45f, projectToc(toc, geometry(absolutePx = 500), 100f).single().fraction)
    }

    @Test
    fun aSectionSitsWhereTheReaderMustScrollToSeeItsHeading() {
        // The top bar is painted over the list, so the heading has to come to rest below it. With
        // a 300px bar, a heading 2000px below the viewport top is reached 1700px from here.
        val head = entry(0)
        val toc = tocOf(head to 2100f)

        val sections = projectToc(
            toc,
            geometry(absolutePx = 500, obscuredTopPx = 300),
            listTopInRoot = 100f
        )

        assertEquals(0.22f, sections.single().fraction)
    }

    /**
     * Whether the section headed at [headingInRoot] is the one the reader is in, with a 300px bar
     * painted over the top of the list and the list scrolled 1000px in.
     */
    private fun currentSectionWithHeadingAt(headingInRoot: Float): Int {
        val head = entry(0)
        val toc = tocOf(head to headingInRoot)
        val sections = projectToc(
            toc,
            geometry(absolutePx = 1000, rangePx = 10_000, obscuredTopPx = 300),
            listTopInRoot = 0f
        )
        return sectionIndexAt(sections.map { it.fraction }, 1000f / 10_000f)
    }

    @Test
    fun aSectionIsNotCurrentWhileItsHeadingIsStillBelowTheReadableTop() {
        // Root 400 is under the fold of the readable area, which starts at 300: the heading is on
        // its way up the screen and the reader is still in whatever came before it.
        assertEquals(-1, currentSectionWithHeadingAt(400f))
    }

    @Test
    fun aSectionGoesCurrentAsItsHeadingReachesTheReadableTop() {
        // Root 300 is the first pixel the bar is not covering, so this is the moment the reader
        // arrives at the section — and the label lands on the thumb, both being scroll offsets.
        assertEquals(0, currentSectionWithHeadingAt(300f))

        val head = entry(0)
        val sections = projectToc(
            tocOf(head to 300f),
            geometry(absolutePx = 1000, rangePx = 10_000, obscuredTopPx = 300),
            listTopInRoot = 0f
        )
        assertEquals(1000f / 10_000f, sections.single().fraction)
    }

    @Test
    fun aSectionStaysCurrentOnceItsHeadingHasScrolledUnderTheTopBar() {
        // The regression (#277). Root 150 is above the readable top and behind the bar: the
        // heading has gone, and its body is what is being read, so it is plainly the current
        // section. Projected as a document position it was not — that only happened once the
        // heading passed root 0, a whole bar's height later, which is what made the pill light up
        // long after the heading had disappeared.
        assertEquals(0, currentSectionWithHeadingAt(150f))

        // What the uncorrected projection would have said at the same position.
        val headingPx = 1000f + 150f
        assertEquals(-1, sectionIndexAt(listOf(headingPx / 10_000f), 1000f / 10_000f))
    }

    @Test
    fun droppingTheThumbOnALabelPutsThatHeadingWhereItCanBeRead() {
        // What the two above add up to: a section's fraction is a scroll offset, so scrolling to
        // it leaves the heading at the top of the readable area rather than behind the bar.
        val head = entry(0)
        val obscured = 300
        val rangePx = 10_000
        val headingInRoot = 2100f
        val toc = tocOf(head to headingInRoot)
        val geometry = geometry(absolutePx = 500, rangePx = rangePx, obscuredTopPx = obscured)

        val fraction = projectToc(toc, geometry, listTopInRoot = 100f).single().fraction

        // Where the list would be after the drop, and where the heading sits in the document.
        val landedAt = fraction * rangePx
        val headingPx = geometry.absolutePx + (headingInRoot - 100f)
        assertEquals(obscured.toFloat(), headingPx - landedAt)
    }

    @Test
    fun aSectionIsNamedOnceItsHeadingIsALookaheadIntoTheReadableArea() {
        // With a 300px bar and a 200px lookahead, a heading names its section once it is 500px
        // below the viewport top — on screen, under the bar — not once it has reached the bar.
        val head = entry(0)
        val geometry = ReaderCursorGeometry(
            itemHeights = listOf(800, 12_000),
            rangePx = 10_000,
            absolutePx = 1000,
            viewportStartOffsetPx = 0,
            obscuredTopPx = 300,
            lookaheadPx = 200
        )

        fun named(headingInRoot: Float): Int {
            val sections = projectToc(tocOf(head to headingInRoot), geometry, listTopInRoot = 0f)
            return sectionIndexAt(sections.map { it.fraction }, 1000f / 10_000f)
        }

        assertEquals(-1, named(520f))
        assertEquals(0, named(500f))
        assertEquals(0, named(350f))
    }

    @Test
    fun theLookaheadIsAQuarterOfTheReadableHeight() {
        assertEquals(170, sectionLookaheadPx(viewportHeightPx = 800, obscuredTopPx = 120))
        assertEquals(0, sectionLookaheadPx(viewportHeightPx = 100, obscuredTopPx = 120))
    }

    @Test
    fun aForgottenHeadingStopsBeingProjected() {
        val head = entry(0)
        val toc = tocOf(head to 100f)

        toc.forget(head.id)

        assertTrue(projectToc(toc, geometry(), listTopInRoot = 100f).isEmpty())
    }

    @Test
    fun anArticleWithNothingToScrollHasNoTrackToProjectOnto() {
        val head = entry(0)
        val toc = tocOf(head to 100f)

        assertTrue(projectToc(toc, geometry(rangePx = 0), listTopInRoot = 100f).isEmpty())
    }
}
