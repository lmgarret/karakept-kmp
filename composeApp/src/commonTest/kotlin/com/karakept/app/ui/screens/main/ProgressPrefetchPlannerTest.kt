package com.karakept.app.ui.screens.main

import com.karakept.app.testing.testBookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading progress is asked for ahead of the viewport, at a pace scrolling cannot multiply.
 */
class ProgressPrefetchPlannerTest {
    private val total = 1000

    /** Scrolls one row at a time at [rowIntervalMs] and returns every band asked for. */
    private fun ProgressPrefetchPlanner.scroll(
        from: Int,
        to: Int,
        rowIntervalMs: Long,
        startMs: Long = 0
    ): List<IntRange> {
        val step = if (to >= from) 1 else -1
        var now = startMs
        val asked = mutableListOf<IntRange>()
        var first = from
        while (first != to + step) {
            onScrolled(first..first + 9, total, now)?.let(asked::add)
            now += rowIntervalMs
            first += step
        }
        return asked
    }

    @Test
    fun settlingAsksForTheRowsAheadOfTheViewport() {
        val planner = ProgressPrefetchPlanner()

        val band = planner.onSettled(0..9, total)

        assertEquals(0..9 + ProgressPrefetchPlanner.AHEAD, band)
    }

    @Test
    fun aSteadyScrollAsksOncePerStretchNotPerRow() {
        val planner = ProgressPrefetchPlanner()
        planner.onSettled(0..9, total)

        val asked = planner.scroll(from = 0, to = 100, rowIntervalMs = 200)

        // One request per ~30 rows, each reaching well past the rows on screen at the time.
        assertEquals(listOf(21..80, 52..111, 83..142), asked)
    }

    @Test
    fun rowsAreAskedForBeforeTheyReachTheScreen() {
        val planner = ProgressPrefetchPlanner()
        var covered = planner.onSettled(0..9, total).last

        for (first in 0..300) {
            val visible = first..first + 9
            planner.onScrolled(visible, total, first * 200L)?.let { covered = maxOf(covered, it.last) }
            assertEquals(true, visible.last < covered, "row ${visible.last} reached the screen unasked")
        }
    }

    @Test
    fun aFlingAsksForNothing() {
        val planner = ProgressPrefetchPlanner()
        planner.onSettled(0..9, total)

        val asked = planner.scroll(from = 0, to = 400, rowIntervalMs = 10)

        assertEquals(emptyList(), asked)
    }

    @Test
    fun aFlingIsPickedUpWhereItSettles() {
        val planner = ProgressPrefetchPlanner()
        planner.onSettled(0..9, total)
        planner.scroll(from = 0, to = 400, rowIntervalMs = 10)

        val band = planner.onSettled(400..409, total)

        assertEquals(390..449, band)
    }

    @Test
    fun scrollingUpLooksUp() {
        val planner = ProgressPrefetchPlanner()
        planner.onSettled(500..509, total)

        val asked = planner.scroll(from = 500, to = 480, rowIntervalMs = 200)

        assertEquals(499 - ProgressPrefetchPlanner.AHEAD..508 + ProgressPrefetchPlanner.BEHIND, asked.first())
    }

    @Test
    fun nothingIsAskedPastTheEndOfTheList() {
        val planner = ProgressPrefetchPlanner()
        val band = planner.onSettled(980..989, total)
        assertEquals(970..999, band)

        // The band already reaches the last row, so there is nothing further to ask for.
        assertNull(planner.onScrolled(985..994, total, nowMs = 10_000))
    }

    @Test
    fun anEmptyListAsksForNothing() {
        assertEquals(true, ProgressPrefetchPlanner().onSettled(0..0, total = 0).isEmpty())
    }

    @Test
    fun slotsAreResolvedAgainstTheRowTheLayoutSaw() {
        val rows = (1L..200L).map { testBookmark(it) }
        // A sync put three rows above the view since the layout last measured.
        val shifted = viewWindow((201L..203L).map { testBookmark(it) } + rows)
        val viewport = VisibleRows(slots = 10..12, anchorKey = rows[10].remoteId, anchorIndex = 10)

        val ids = shifted.remoteIdsAround(viewport, 10..12)

        assertEquals(rows.subList(10, 13).map { it.remoteId }, ids)
    }

    @Test
    fun unreadSlotsAreSkipped() {
        val rows = (1L..200L).map { testBookmark(it) }
        val window = viewWindow(rows, loadedPages = setOf(0))
        val viewport = VisibleRows(slots = 40..49, anchorKey = rows[40].remoteId, anchorIndex = 40)

        val ids = window.remoteIdsAround(viewport, 40..89)

        assertEquals(rows.subList(40, 50).map { it.remoteId }, ids)
    }
}
