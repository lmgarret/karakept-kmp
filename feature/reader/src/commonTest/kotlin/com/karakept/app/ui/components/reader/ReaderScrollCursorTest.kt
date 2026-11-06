package com.karakept.app.ui.components.reader

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
        viewportStartOffsetPx: Int = 0
    ) = ReaderCursorGeometry(
        itemHeights = listOf(800, 12_000),
        rangePx = rangePx,
        absolutePx = absolutePx,
        viewportStartOffsetPx = viewportStartOffsetPx
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
