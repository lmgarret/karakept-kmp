package com.karakept.app.ui.screens.main

import com.karakept.app.testing.testBookmark
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ReadFilter
import com.karakept.app.data.model.Server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

/**
 * Tests for [MainScreenModel.filteredBookmarkCount] — the denominator the fast-scroll cursor maps
 * its thumb over.
 *
 * It has to describe the whole filtered view, not the window paged in so far: a denominator that
 * grows with the window walks the thumb back up the track every time a page lands (#273).
 *
 * The counting itself is now a `COUNT(*)` over the view's own predicate, so what it *means* to
 * count a tag, a list or the offline view is proved against real SQLite in
 * `BookmarkViewPredicateTest` — including that the count and the rows agree for every filter
 * shape. What is left here is the wiring: that the model asks for the count of the filter it is
 * actually showing, and re-asks when that filter changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilteredBookmarkCountTest {
    private val testDispatcher = StandardTestDispatcher()
    private val harness = MainScreenModelHarness(testDispatcher)

    private val fakeServer = Server(
        id = "server-1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test"
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { harness.serverRepository.servers } returns flowOf(listOf(fakeServer))
        every { harness.settingsRepository.offlineMode } returns flowOf(false)

        countedFilters.clear()
        every { harness.bookmarkRepository.countBookmarksForViewFlow(any(), any()) } answers {
            val filter = secondArg<FilterConfig>()
            countedFilters += filter
            flowOf(countsByFilter[filter] ?: 0)
        }
    }

    /** Every filter the model has asked the database to count, in order. */
    private val countedFilters = mutableListOf<FilterConfig>()

    /** What the database answers for a given filter. */
    private var countsByFilter: Map<FilterConfig, Int> = emptyMap()

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `filteredBookmarkCount is zero when no bookmarks`() = runTest(testDispatcher) {
        val model = harness.createModel()
        val job = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()

        assertEquals(0, model.filteredBookmarkCount.value)
        job.cancel()
    }

    @Test
    fun `filteredBookmarkCount counts the whole filtered view, not the loaded window`() =
        runTest(testDispatcher) {
            // Far more than one page. The count is answered by the database for the whole view,
            // so nothing about how far paging has walked can reach it (#273).
            countsByFilter = mapOf(FilterConfig() to 250)
            // The list has read 20 rows of the 250 the view holds.
            val window = (1L..20L).map { testBookmark(it) }
            coEvery {
                harness.bookmarkRepository.getBookmarkPage(any(), any(), any(), any())
            } answers {
                val offset = thirdArg<Int>()
                if (offset >= window.size) emptyList() else window.drop(offset)
            }

            val model = harness.createModel()
            val job = launch { model.filteredBookmarkCount.collect {} }
            val windowJob = launch { model.bookmarks.collect {} }
            advanceUntilIdle()

            assertEquals(20, model.bookmarks.value.size, "the rows the list has read")
            assertEquals(
                250,
                model.filteredBookmarkCount.value,
                "and the count is the whole view regardless — a denominator that grew with the " +
                    "window walked the thumb back up the track on every page (#273)"
            )
            job.cancel()
            windowJob.cancel()
        }

    @Test
    fun `filteredBookmarkCount follows the active filter`() = runTest(testDispatcher) {
        val kotlinTag = FilterConfig(tags = listOf("kotlin"))
        val kotlinUnread = FilterConfig(tags = listOf("kotlin"), readFilter = ReadFilter.UNREAD)
        countsByFilter = mapOf(FilterConfig() to 4, kotlinTag to 2, kotlinUnread to 1)

        val model = harness.createModel()
        val job = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()
        assertEquals(4, model.filteredBookmarkCount.value)

        model.applyFilter(kotlinTag)
        advanceUntilIdle()
        assertEquals(2, model.filteredBookmarkCount.value)

        model.applyFilter(kotlinUnread)
        advanceUntilIdle()
        assertEquals(1, model.filteredBookmarkCount.value)

        // Every narrowing was asked of the database rather than derived from the previous answer.
        assertEquals(
            listOf(FilterConfig(), kotlinTag, kotlinUnread),
            countedFilters.distinct(),
            "the count must be re-read for the filter on screen"
        )
        job.cancel()
    }

    @Test
    fun `a list view is counted by the list filter, not by a status the drawer chose`() =
        runTest(testDispatcher) {
            // That a single-list view counts its archived members is a property of the predicate
            // and is proved against SQLite in BookmarkViewPredicateTest. What matters here is
            // that the list reaches the query at all.
            val listView =
                FilterConfig(status = FilterStatus.ALL_INCLUDING_ARCHIVED, lists = listOf("list-1"))
            countsByFilter = mapOf(listView to 2)

            val model = harness.createModel()
            val job = launch { model.filteredBookmarkCount.collect {} }
            advanceUntilIdle()

            model.applyFilter(listView)
            advanceUntilIdle()

            assertEquals(2, model.filteredBookmarkCount.value)
            assertEquals(listView, countedFilters.last())
            job.cancel()
        }

    @Test
    fun `the offline view is counted like every other view`() = runTest(testDispatcher) {
        // It used to need its own count, because the offline condition reads a `content` column
        // the in-memory rows are loaded without, and the in-memory view stood on a
        // reading-time proxy instead. Counting in SQL reads the column it means, so the offline
        // view goes through the same path as the rest and the special case is gone.
        val offline = FilterConfig(status = FilterStatus.OFFLINE)
        countsByFilter = mapOf(offline to 3)
        every { harness.bookmarkRepository.getBookmarks(any()) } returns
            flowOf((1L..5L).map { testBookmark(it) })

        val model = harness.createModel()
        val job = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()

        model.applyFilter(offline)
        advanceUntilIdle()

        assertEquals(3, model.filteredBookmarkCount.value)
        assertEquals(offline, countedFilters.last())
        job.cancel()
    }

    @Test
    fun `the row at an absolute position is named by a query, not by a resident view`() =
        runTest(testDispatcher) {
            // What the scroll cursor's tooltip asks for: the row a thumb points at, whether or
            // not the list has read it. It used to be answered by keeping the whole view ordered
            // in memory; it is one row at a position.
            coEvery { harness.bookmarkRepository.getBookmarkAt(any(), any(), 299) } returns
                testBookmark(1)

            val model = harness.createModel()
            advanceUntilIdle()

            assertEquals("remote-1", model.bookmarkAtIndex(299)?.remoteId)
            coVerify { harness.bookmarkRepository.getBookmarkAt(any(), any(), 299) }
        }

    @Test
    fun `the count is asked for the same filter the rows are read for`() = runTest(testDispatcher) {
        // A thumb at the end of the track must point at a row the list can name, so the count and
        // the rows have to be about one view. They agree because they share a predicate — that
        // agreement is checked directly against SQLite in BookmarkViewPredicateTest; here it is
        // that the model does not hand the two different filters.
        val kotlinTag = FilterConfig(tags = listOf("kotlin"))
        countsByFilter = mapOf(kotlinTag to 2)

        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        val countJob = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()

        model.applyFilter(kotlinTag)
        advanceUntilIdle()

        assertEquals(2, model.filteredBookmarkCount.value)
        assertEquals(
            model.effectiveFilterNow(),
            countedFilters.last(),
            "the count must describe the view on screen, not the one requested before it resolved"
        )
        job.cancel()
        countJob.cancel()
    }
}
