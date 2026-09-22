package com.karakept.app.ui.screens

import com.karakept.app.data.model.BookmarkSlot
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
 * #333, in the terms that now hold it.
 *
 * The bug needed two things together: a position the list *accumulated*, and a walk that only
 * moved forward. Rows a sync committed above the read position shifted everything below them
 * down, so the next read started that many rows back — re-returning rows the window already held
 * and pushing an equal number past the read position, where a forward-only walk never returned
 * for them. The window sat short of the table with `_hasMoreItems` false: silently complete, and
 * unrecoverable without toggling the filter.
 *
 * Neither half exists now. The list is sized by a `COUNT(*)` and reads the pages under the
 * viewport by their offsets, so there is no accumulated position to drift and no "complete" flag
 * to be wrongly false. Every write re-reads those pages, which is what these tests assert: after
 * rows are committed above the viewport, the list is the size of the table and every row in it is
 * still reachable.
 *
 * The same scenario is also replayed against real SQLite in `BookmarkPagingOrderTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelOffsetDriftTest {

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

    @Test
    fun rowsCommittedAboveTheViewportDoNotStrandTheRowsBelowThem() = runTest(testDispatcher) {
        harness.publish((1L..200L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        // The user is partway down the list.
        model.reportVisibleSlots(100..110)
        advanceUntilIdle()

        // A sync commits fifteen rows that sort above everything on screen.
        val synced = (1000L..1014L).map(::bookmark)
        harness.publish(synced + harness.rows)
        advanceUntilIdle()

        val window = model.bookmarkWindow.value
        assertEquals(215, window.total, "the list is the size of the table, not of a walk")
        // Under the old walk these fifteen were the rows pushed past the read position and never
        // returned for. Every one of them is addressable now.
        for (index in 0 until 15) {
            assertEquals(
                BookmarkSlot.Placeholder,
                window[index],
                "index $index is a slot the list holds — its page simply is not on screen"
            )
            assertTrue(window.pageOf(index) != null, "and the list knows which page to read for it")
        }
        job.cancel()
    }

    @Test
    fun aWriteReReadsWhatIsOnScreenRatherThanLeavingItShort() = runTest(testDispatcher) {
        harness.publish((1L..200L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        harness.clearReads()

        harness.publish((1000L..1014L).map(::bookmark) + harness.rows)
        advanceUntilIdle()

        assertTrue(harness.offsetsRead().isNotEmpty(), "the pages on screen were read again")
        assertEquals(
            bookmark(1000L),
            model.bookmarkWindow.value.bookmarkAt(0),
            "and the newly committed rows are where the query puts them"
        )
        job.cancel()
    }

    @Test
    fun theListIsNeverCompleteShortOfTheTable() = runTest(testDispatcher) {
        // The shape of the #333 failure: a window claiming to hold everything while the table
        // held more. The size comes from the table, so the two cannot disagree.
        harness.publish((1L..200L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        assertEquals(200, model.bookmarkWindow.value.total)

        harness.publish((1L..500L).map(::bookmark))
        advanceUntilIdle()

        assertEquals(500, model.bookmarkWindow.value.total)
        job.cancel()
    }

    @Test
    fun aShrunkenTableShrinksTheList() = runTest(testDispatcher) {
        harness.publish((1L..200L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        harness.publish((1L..50L).map(::bookmark))
        advanceUntilIdle()

        val window = model.bookmarkWindow.value
        assertEquals(50, window.total)
        assertEquals(BookmarkSlot.Placeholder, window[50], "there is no row 50 any more")
        job.cancel()
    }
}
