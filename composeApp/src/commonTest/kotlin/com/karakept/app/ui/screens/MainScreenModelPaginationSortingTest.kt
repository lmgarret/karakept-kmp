package com.karakept.app.ui.screens

import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.SortOption
import com.karakept.app.ui.screens.MainScreenModelHarness.Companion.bookmark
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two things the list has to get right about the view it is showing: that the sort it was asked
 * for is the sort the rows are read with, and that the "N new" pill counts what actually arrived
 * above the user.
 *
 * The sort half used to be about a walk carrying its sort across pages. The rows are read by
 * offset now, and the sort is part of the view's own definition, so what is left to check is that
 * it reaches the query at all — and keeps reaching it, since a page read with a different sort
 * would return rows belonging at other indices.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelPaginationSortingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var harness: MainScreenModelHarness

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        harness = MainScreenModelHarness(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── the sort reaches the query ─────────────────────────────

    @Test
    fun `the default sort is the one the first page is read with`() = runTest(testDispatcher) {
        harness.publish((1L..20L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        assertTrue(harness.readFilters.isNotEmpty())
        assertTrue(harness.readFilters.all { it.sort == SortOption.NEWEST })
        job.cancel()
    }

    @Test
    fun `changing the sort re-reads the view with it`() = runTest(testDispatcher) {
        harness.publish((1L..20L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        for (sort in listOf(SortOption.OLDEST, SortOption.TITLE_AZ, SortOption.READING_TIME_LONG)) {
            harness.clearReads()
            model.applyFilter(FilterConfig(sort = sort))
            advanceUntilIdle()

            assertTrue(harness.readFilters.isNotEmpty(), "$sort: the view was re-read")
            assertTrue(
                harness.readFilters.all { it.sort == sort },
                "$sort: a page read with another sort holds rows belonging at other indices"
            )
        }
        job.cancel()
    }

    @Test
    fun `every page of a view is read with the same sort`() = runTest(testDispatcher) {
        harness.publish((1L..300L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        model.applyFilter(FilterConfig(sort = SortOption.TITLE_AZ))
        advanceUntilIdle()
        harness.clearReads()

        model.reportVisibleSlots(200..210)
        advanceUntilIdle()

        assertTrue(harness.readFilters.isNotEmpty())
        assertTrue(harness.readFilters.all { it.sort == SortOption.TITLE_AZ })
        job.cancel()
    }

    // ── the list version, which decides whether the viewport is re-pinned ──

    @Test
    fun `a write re-reads the list without bumping its version`() = runTest(testDispatcher) {
        // The version is what makes PreserveListScrollAnchor drop its anchor and scroll to the
        // top. A sync landing rows must not do that, or the list blinks away from where the user
        // was reading.
        harness.publish((1L..20L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        val versionBefore = model.bookmarkListVersion.value
        harness.clearReads()

        harness.publish((1L..30L).map(::bookmark))
        advanceUntilIdle()

        assertEquals(30, model.bookmarkWindow.value.total, "the rows arrived")
        assertEquals(versionBefore, model.bookmarkListVersion.value, "and the viewport held")
        job.cancel()
    }

    @Test
    fun `switching view bumps the version so the list scrolls to the top`() =
        runTest(testDispatcher) {
            harness.publish((1L..20L).map(::bookmark))
            val model = harness.createModel()
            val job = launch { model.bookmarkWindow.collect {} }
            advanceUntilIdle()
            val versionBefore = model.bookmarkListVersion.value

            model.applyFilter(FilterConfig(status = FilterStatus.ARCHIVED))
            advanceUntilIdle()

            assertTrue(
                model.bookmarkListVersion.value > versionBefore,
                "a different view is not the same list changed"
            )
            job.cancel()
        }

    // ── the "N new" pill ───────────────────────────────────────

    /** A model showing a 200-row view, with the user scrolled to slot 40. */
    private suspend fun modelScrolledTo(slot: Int): MainScreenModel {
        harness.publish((1L..200L).map(::bookmark))
        val model = harness.createModel()
        model.reportVisibleSlots(slot..(slot + 9))
        return model
    }

    @Test
    fun `scrolling without a sync never raises the pill`() = runTest(testDispatcher) {
        // The bug this replaces: the count was taken by looking for an anchor row among the
        // loaded rows, and the list drops the pages it has scrolled away from. The anchor was
        // then usually missing, the walk moved it down to whatever was on screen, and scrolling
        // back up reported those rows as new — with no sync anywhere in sight.
        val model = modelScrolledTo(0)
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        for (slot in listOf(0, 40, 120, 60, 10, 90, 5)) {
            model.reportVisibleSlots(slot..(slot + 9))
            advanceUntilIdle()
            assertEquals(0, model.newBookmarksAbove.value, "scrolled to $slot, no sync")
        }
        job.cancel()
    }

    @Test
    fun `a sync reports what landed above where the user was`() = runTest(testDispatcher) {
        val model = modelScrolledTo(40)
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        harness.arrivedAbove = 12
        harness.setSyncing(true)
        advanceUntilIdle()
        harness.setSyncing(false)
        advanceUntilIdle()

        assertEquals(12, model.newBookmarksAbove.value)
        assertTrue(harness.countedBefore.isNotEmpty(), "the count was asked of the database")
        job.cancel()
    }

    @Test
    fun `the position the count is taken from is where the user was, not where they end up`() =
        runTest(testDispatcher) {
            val model = modelScrolledTo(40)
            val job = launch { model.bookmarkWindow.collect {} }
            advanceUntilIdle()
            val standingOn = model.bookmarkWindow.value.bookmarkAt(40)

            harness.setSyncing(true)
            advanceUntilIdle()
            // The list moves under the user while the sync runs.
            model.reportVisibleSlots(80..89)
            harness.setSyncing(false)
            advanceUntilIdle()

            assertEquals(standingOn?.localId, harness.countedBefore.last().localId)
            job.cancel()
        }

    @Test
    fun `scrolling up through the arrivals retires them`() = runTest(testDispatcher) {
        val model = modelScrolledTo(40)
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        harness.arrivedAbove = 10
        harness.setSyncing(true)
        advanceUntilIdle()
        harness.setSyncing(false)
        advanceUntilIdle()
        assertEquals(10, model.newBookmarksAbove.value)

        model.reportVisibleSlots(6..15)
        advanceUntilIdle()
        assertEquals(6, model.newBookmarksAbove.value, "four of the ten have been scrolled past")

        model.reportVisibleSlots(0..9)
        advanceUntilIdle()
        assertEquals(0, model.newBookmarksAbove.value, "reaching the top retires all of them")
        job.cancel()
    }

    @Test
    fun `scrolling back down does not re-raise what was seen`() = runTest(testDispatcher) {
        val model = modelScrolledTo(40)
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        harness.arrivedAbove = 10
        harness.setSyncing(true)
        advanceUntilIdle()
        harness.setSyncing(false)
        advanceUntilIdle()

        model.reportVisibleSlots(3..12)
        advanceUntilIdle()
        assertEquals(3, model.newBookmarksAbove.value)

        model.reportVisibleSlots(90..99)
        advanceUntilIdle()
        assertEquals(3, model.newBookmarksAbove.value, "the mark only ever falls")
        job.cancel()
    }

    @Test
    fun `a sync landing while the user is at the top raises nothing`() = runTest(testDispatcher) {
        val model = modelScrolledTo(0)
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        harness.arrivedAbove = 8
        harness.setSyncing(true)
        advanceUntilIdle()
        harness.setSyncing(false)
        advanceUntilIdle()

        assertEquals(
            0,
            model.newBookmarksAbove.value,
            "they are on screen, so the user is not being offered a trip to them"
        )
        job.cancel()
    }

    @Test
    fun `the pill leaves out bookmarks already read while they are faded`() =
        runTest(testDispatcher) {
            // Whether a row already read counts is decided by the query; what matters here is
            // that the list's own fading setting is what it is asked with.
            val model = modelScrolledTo(40)
            val job = launch { model.bookmarkWindow.collect {} }
            advanceUntilIdle()

            harness.setSyncing(true)
            advanceUntilIdle()
            harness.setSyncing(false)
            advanceUntilIdle()

            coVerify {
                harness.bookmarkRepository.countBookmarksBefore(any(), any(), any(), excludeRead = true)
            }
            job.cancel()
        }

    @Test
    fun `tapping the pill empties it`() = runTest(testDispatcher) {
        val model = modelScrolledTo(40)
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        harness.arrivedAbove = 5
        harness.setSyncing(true)
        advanceUntilIdle()
        harness.setSyncing(false)
        advanceUntilIdle()
        assertEquals(5, model.newBookmarksAbove.value)

        model.clearNewBookmarksAbove()
        advanceUntilIdle()

        assertEquals(0, model.newBookmarksAbove.value)
        job.cancel()
    }

    @Test
    fun `switching view forgets what a previous sync reported`() = runTest(testDispatcher) {
        val model = modelScrolledTo(40)
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        harness.arrivedAbove = 7
        harness.setSyncing(true)
        advanceUntilIdle()
        harness.setSyncing(false)
        advanceUntilIdle()
        assertEquals(7, model.newBookmarksAbove.value)

        model.applyFilter(FilterConfig(status = FilterStatus.ARCHIVED))
        advanceUntilIdle()

        assertEquals(
            0,
            model.newBookmarksAbove.value,
            "a different view is a different question"
        )
        job.cancel()
    }
}
