package com.karakept.app.ui.screens

import com.karakept.app.data.model.BookmarkWindow
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
 * A page already in hand is not read again for a scroll, and always read again after a write.
 *
 * The window is re-read on two signals that reach it down one flow: the viewport crossing a page
 * boundary, and Room invalidating the view's count because something wrote to the table. Reading
 * the whole span on both treats them as the same event, and they are opposites — after a scroll
 * every page held is still exactly right, after a write none of them are. Measured on a real
 * library that cost a re-read of the page the user was looking at on every scroll, and a full
 * re-read on every one of the writes a sync makes continuously.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelPageReuseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var harness: MainScreenModelHarness

    private val pageSize = BookmarkWindow.DEFAULT_PAGE_SIZE

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        harness = MainScreenModelHarness(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scrolling into a new page reads only that page`() = runTest(testDispatcher) {
        harness.publish((1L..500L).map { bookmark(it) })
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        // Viewport on page 1, so pages 0..2 are in hand.
        model.reportVisibleSlots(pageSize until 2 * pageSize)
        advanceUntilIdle()
        harness.clearReads()

        // One page further down: pages 1..3 are wanted, and only page 3 is new.
        model.reportVisibleSlots(2 * pageSize until 3 * pageSize)
        advanceUntilIdle()

        assertEquals(
            listOf(3 * pageSize),
            harness.offsetsRead(),
            "pages 1 and 2 were already held; re-reading them is a query per page for rows " +
                "that are already on screen"
        )
        job.cancel()
    }

    @Test
    fun `scrolling back reads only what was dropped`() = runTest(testDispatcher) {
        harness.publish((1L..500L).map { bookmark(it) })
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        model.reportVisibleSlots(pageSize until 2 * pageSize)
        advanceUntilIdle()
        model.reportVisibleSlots(3 * pageSize until 4 * pageSize)
        advanceUntilIdle()
        harness.clearReads()

        // Back to where it started. The span at page 3 was 2..4, so pages 0 and 1 were dropped
        // and have to be read again — page 2 is the one still held.
        model.reportVisibleSlots(pageSize until 2 * pageSize)
        advanceUntilIdle()

        assertEquals(
            listOf(0, pageSize),
            harness.offsetsRead(),
            "what the window holds is bounded by the span, so scrolling back is a read — but " +
                "only of the pages that actually left it"
        )
        job.cancel()
    }

    @Test
    fun `a write re-reads every page in the span`() = runTest(testDispatcher) {
        harness.publish((1L..500L).map { bookmark(it) })
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        model.reportVisibleSlots(pageSize until 2 * pageSize)
        advanceUntilIdle()
        harness.clearReads()

        // A row edited without the count moving — the case a stale page would survive.
        harness.publish((1L..500L).map { bookmark(it, isRead = it == 1L) })
        advanceUntilIdle()

        assertEquals(
            listOf(0, pageSize, 2 * pageSize),
            harness.offsetsRead(),
            "a write can change any row of any page, so none of them may be reused"
        )
        assertTrue(
            model.bookmarkWindow.value.bookmarkAt(0)!!.isRead,
            "the edited row reached the screen"
        )
        job.cancel()
    }

    @Test
    fun `the viewport's page is published before the margins are read`() = runTest(testDispatcher) {
        harness.publish((1L..500L).map { bookmark(it) })
        val model = harness.createModel()

        val loadedCounts = mutableListOf<Int>()
        val job = launch {
            model.bookmarkWindow.collect { if (it.resolved) loadedCounts += it.loadedCount }
        }
        advanceUntilIdle()

        assertTrue(
            loadedCounts.first() <= pageSize,
            "the first publish holds the viewport's page alone, so the rows the user is " +
                "waiting for do not wait on the pages they are not looking at"
        )
        assertTrue(
            loadedCounts.last() > pageSize,
            "the margin pages follow, so an ordinary scroll still reaches loaded rows"
        )
        job.cancel()
    }

    @Test
    fun `a re-read that changes nothing is not published again`() = runTest(testDispatcher) {
        val rows = (1L..500L).map { bookmark(it) }
        harness.publish(rows)
        val model = harness.createModel()

        var publishes = 0
        val job = launch { model.bookmarkWindow.collect { publishes++ } }
        advanceUntilIdle()
        val settled = publishes

        // The same rows written again: Room invalidates, the pages are re-read, and every row
        // comes back identical. Sync does this continuously.
        harness.publish(rows.toList())
        advanceUntilIdle()

        assertEquals(
            settled,
            publishes,
            "an unchanged window still costs a publish and a recomposition to say so"
        )
        job.cancel()
    }
}
