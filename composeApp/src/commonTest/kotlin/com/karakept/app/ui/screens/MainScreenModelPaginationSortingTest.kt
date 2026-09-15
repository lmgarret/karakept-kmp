package com.karakept.app.ui.screens

import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.SortOption
import com.karakept.app.ui.screens.MainScreenModelHarness.Companion.bookmark
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

    /** A model showing [rows], with everything currently on screen counted as seen. */
    private suspend fun modelShowing(rows: List<com.karakept.app.data.local.entity.BookmarkEntity>) =
        harness.createModel().also {
            harness.publish(rows)
        }

    @Test
    fun `bookmarks arriving above the seen row are counted`() = runTest(testDispatcher) {
        harness.publish(listOf(bookmark(1L)))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        model.clearNewBookmarksAbove()
        advanceUntilIdle()
        assertEquals(0, model.newBookmarksAbove.value)

        harness.publish(listOf(bookmark(10L), bookmark(11L), bookmark(1L)))
        advanceUntilIdle()

        assertEquals(2, model.newBookmarksAbove.value)
        job.cancel()
    }

    @Test
    fun `scrolling up through new bookmarks retires them row by row`() = runTest(testDispatcher) {
        harness.publish(listOf(bookmark(10L), bookmark(11L), bookmark(12L), bookmark(1L)))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        model._seenTopRemoteId.value = "remote-1"
        advanceUntilIdle()
        assertEquals(3, model.newBookmarksAbove.value)

        model.markTopVisibleSeen("remote-12")
        advanceUntilIdle()
        assertEquals(2, model.newBookmarksAbove.value)

        model.markTopVisibleSeen("remote-11")
        advanceUntilIdle()
        assertEquals(1, model.newBookmarksAbove.value)

        // Stopping one row short of the very top must still leave only that one uncounted.
        assertEquals("remote-11", model._seenTopRemoteId.value)
        job.cancel()
    }

    @Test
    fun `scrolling back down does not re-count what was already seen`() = runTest(testDispatcher) {
        harness.publish(listOf(bookmark(10L), bookmark(11L), bookmark(1L)))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        model._seenTopRemoteId.value = "remote-10"
        advanceUntilIdle()
        assertEquals(0, model.newBookmarksAbove.value)

        // Scrolling down puts lower rows at the top of the viewport; the anchor must not follow,
        // or everything above it would be reported as new all over again.
        model.markTopVisibleSeen("remote-11")
        model.markTopVisibleSeen("remote-1")
        advanceUntilIdle()

        assertEquals("remote-10", model._seenTopRemoteId.value, "the anchor only moves up the list")
        assertEquals(0, model.newBookmarksAbove.value)
        job.cancel()
    }

    @Test
    fun `an anchor the list no longer holds is replaced by the row on screen`() =
        runTest(testDispatcher) {
            // Holding a row the list no longer has pinned the count to zero until the user
            // reached the very top.
            harness.publish(listOf(bookmark(10L), bookmark(11L)))
            val model = harness.createModel()
            val job = launch { model.bookmarkWindow.collect {} }
            advanceUntilIdle()
            model._seenTopRemoteId.value = "remote-999"
            advanceUntilIdle()

            model.markTopVisibleSeen("remote-11")
            advanceUntilIdle()

            assertEquals("remote-11", model._seenTopRemoteId.value)
            assertEquals(1, model.newBookmarksAbove.value)
            job.cancel()
        }

    @Test
    fun `a row the list does not hold leaves the anchor alone`() = runTest(testDispatcher) {
        // A just-created bookmark renders above the view as a placeholder.
        harness.publish(listOf(bookmark(10L), bookmark(1L)))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        model._seenTopRemoteId.value = "remote-1"
        advanceUntilIdle()

        model.markTopVisibleSeen("remote-777")
        advanceUntilIdle()

        assertEquals("remote-1", model._seenTopRemoteId.value)
        assertEquals(1, model.newBookmarksAbove.value)
        job.cancel()
    }

    @Test
    fun `the pill leaves out bookmarks already read while they are faded`() =
        runTest(testDispatcher) {
            // One of the arrivals is already read — marked on another device, or carried in by
            // the reading progress the sync pulls. The pill offers a trip to what arrived, and a
            // row already read is not something the user is being sent back for.
            harness.publish(
                listOf(bookmark(10L), bookmark(11L, isRead = true), bookmark(1L))
            )
            val model = harness.createModel()
            val job = launch { model.bookmarkWindow.collect {} }
            advanceUntilIdle()
            model._seenTopRemoteId.value = "remote-1"
            advanceUntilIdle()

            assertEquals(1, model.newBookmarksAbove.value)
            job.cancel()
        }
}
