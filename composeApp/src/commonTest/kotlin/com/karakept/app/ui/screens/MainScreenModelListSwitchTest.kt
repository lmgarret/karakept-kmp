package com.karakept.app.ui.screens

import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
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
 * Two lists must never be rendered as one.
 *
 * The user taps a list and the requested view flips at once, while the read it triggers runs on a
 * coroutine. Under the old walk that was a race worth guarding by hand: a page fetched for the
 * list being left could land on top of the list being entered, and a page appended with the
 * previous list's offset would interleave two lists in one LazyColumn. Every write into the window
 * had to check the view it belonged to first.
 *
 * The window is now a flow of the server and the filter, so a view switch cancels the reads
 * belonging to the previous one rather than racing them — there is no window to write into out of
 * turn. These tests assert what that buys: after a switch, every page read is for the new view,
 * and the rows on screen are the new view's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelListSwitchTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var harness: MainScreenModelHarness

    private val feeds = FilterConfig(
        status = FilterStatus.ALL_INCLUDING_ARCHIVED,
        lists = listOf("feeds")
    )
    private val readLater = FilterConfig(
        status = FilterStatus.ALL_INCLUDING_ARCHIVED,
        lists = listOf("read-later")
    )

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
    fun `after a switch every page is read for the new view`() = runTest(testDispatcher) {
        harness.publish((1L..100L).map { bookmark(it, listIds = "feeds") })
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        model.applyFilter(feeds)
        advanceUntilIdle()
        harness.publish((200L..300L).map { bookmark(it, listIds = "read-later") })
        advanceUntilIdle()
        harness.clearReads()

        model.applyFilter(readLater)
        advanceUntilIdle()

        assertTrue(harness.readFilters.isNotEmpty(), "the new view was read")
        assertTrue(
            harness.readFilters.all { it == readLater },
            "a page read with the previous list's filter would put its rows in this list's slots"
        )
        job.cancel()
    }

    @Test
    fun `the list holds the new view's rows, never a mix of both`() = runTest(testDispatcher) {
        harness.publish((1L..40L).map { bookmark(it, listIds = "feeds") })
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        model.applyFilter(feeds)
        advanceUntilIdle()

        harness.publish((200L..230L).map { bookmark(it, listIds = "read-later") })
        model.applyFilter(readLater)
        advanceUntilIdle()

        val rows = model.bookmarkWindow.value.loadedRows()
        assertTrue(rows.isNotEmpty())
        assertTrue(
            rows.all { it.listIds == "read-later" },
            "the outgoing list's rows must not survive into the incoming one"
        )
        assertEquals(31, model.bookmarkWindow.value.total)
        job.cancel()
    }

    @Test
    fun `switching away mid-read discards what that read was for`() = runTest(testDispatcher) {
        // The race the old guards existed for: the view flips while a read is in flight.
        harness.publish((1L..100L).map { bookmark(it, listIds = "feeds") })
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        model.applyFilter(feeds)
        // No advanceUntilIdle: the read for `feeds` has not finished.
        harness.publish((200L..210L).map { bookmark(it, listIds = "read-later") })
        model.applyFilter(readLater)
        advanceUntilIdle()

        val window = model.bookmarkWindow.value
        assertEquals(11, window.total, "the list settled on the view the user actually asked for")
        assertTrue(window.loadedRows().all { it.listIds == "read-later" })
        job.cancel()
    }

    @Test
    fun `switching back to a list reads it again rather than showing what it held before`() =
        runTest(testDispatcher) {
            harness.publish((1L..20L).map { bookmark(it, listIds = "feeds") })
            val model = harness.createModel()
            val job = launch { model.bookmarkWindow.collect {} }
            advanceUntilIdle()
            model.applyFilter(feeds)
            advanceUntilIdle()

            harness.publish((200L..205L).map { bookmark(it, listIds = "read-later") })
            model.applyFilter(readLater)
            advanceUntilIdle()

            // Feeds has changed while the user was away.
            harness.publish((1L..50L).map { bookmark(it, listIds = "feeds") })
            model.applyFilter(feeds)
            advanceUntilIdle()

            assertEquals(50, model.bookmarkWindow.value.total, "read again, not remembered")
            job.cancel()
        }
}
