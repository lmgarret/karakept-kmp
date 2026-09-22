package com.karakept.app.ui.screens

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

/**
 * #274, in the terms that now hold it.
 *
 * The list feeds a LazyColumn keyed on remoteId, and a duplicate key crashes the app. Duplicates
 * used to slip into the accumulated window from three directions: a page re-returning rows the
 * window already held after OFFSET drift, an undo re-inserting a bookmark that was still there,
 * and a just-created bookmark appearing in the pending list and the window at once. The window
 * was a mutable list that many callers appended to, so the fix was to de-duplicate on every write.
 *
 * There is nothing to de-duplicate now. The window's rows are disjoint slices of one query — a
 * page is the rows at an offset, and two pages cannot overlap — and the only rows not from that
 * query are the placeholders standing in for bookmarks still being created, which carry ids no
 * row can have. These tests assert the keys are unique, including in the states that used to
 * produce collisions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelAccumulatedListTest {

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

    /** Every key the list would hand the LazyColumn. */
    private fun keysOf(model: MainScreenModel): List<Any> {
        val window = model.bookmarkWindow.value
        return (0 until window.total).map(window::keyAt)
    }

    @Test
    fun everySlotHasItsOwnKey() = runTest(testDispatcher) {
        harness.publish((1L..200L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        val keys = keysOf(model)
        assertEquals(200, keys.size)
        assertEquals(keys.size, keys.distinct().size, "a duplicate key crashes the LazyColumn")
        job.cancel()
    }

    @Test
    fun keysStayUniqueAcrossAPageBoundary() = runTest(testDispatcher) {
        // Where the drift used to put them: two pages both claiming the same rows. A page is the
        // rows at an offset, so the pages cannot overlap.
        harness.publish((1L..200L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        model.reportVisibleSlots(45..55)
        advanceUntilIdle()

        val loaded = model.bookmarkWindow.value.loadedRows().map { it.remoteId }
        assertEquals(loaded.size, loaded.distinct().size, "no row is returned by two pages")
        job.cancel()
    }

    @Test
    fun aPendingBookmarkNeverCollidesWithTheRowItBecomes() = runTest(testDispatcher) {
        // The third source: for a moment the created row is in the table while its placeholder is
        // still on screen. They are different rows with different ids, so both can be shown.
        harness.publish((1L..20L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        model._pendingBookmarks.value = listOf(bookmark(999L, remoteId = "temp-abc"))
        advanceUntilIdle()

        val keys = keysOf(model)
        assertEquals(21, keys.size, "the placeholder takes a slot above the view")
        assertEquals(keys.size, keys.distinct().size)
        assertEquals("temp-abc", keys.first())
        job.cancel()
    }

    @Test
    fun aRowChangedElsewhereIsReplacedRatherThanAdded() = runTest(testDispatcher) {
        // An update used to be a transform over the window, which is where an undo could
        // re-insert a bookmark that had never left. The row is read back at its own offset now,
        // so an update replaces and cannot duplicate.
        harness.publish((1L..20L).map(::bookmark))
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        harness.publish(harness.rows.map { if (it.localId == 5L) it.copy(isRead = true) else it })
        advanceUntilIdle()

        val rows = model.bookmarkWindow.value.loadedRows()
        assertEquals(20, rows.size)
        assertEquals(1, rows.count { it.remoteId == "remote-5" })
        assertEquals(true, rows.first { it.remoteId == "remote-5" }.isRead)
        job.cancel()
    }
}
