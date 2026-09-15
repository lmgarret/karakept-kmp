package com.karakept.app.ui.screens

import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
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
 * A list-membership action must not throw the user's place away.
 *
 * The original bug: adding a bookmark to a list triggered a reconcile, the reconcile reloaded the
 * list from page 0, and the viewport jumped to a fixed near-top position wherever the user had
 * been. The fix was a surgical in-memory transform instead of a reload.
 *
 * Neither exists now. The reconcile writes the bookmark's corrected memberships and the list
 * re-reads the pages on screen, so what the user is looking at stays where it is and only the
 * rows that changed change. That the row then leaves the view it no longer belongs to, and stays
 * in the ones it does, is a property of the query and is asserted against real SQLite in
 * `BookmarkViewPredicateTest`. What is left here is the part the query does not decide: that the
 * reconcile does not reload the list, and that a bookmark acted on is marked so the
 * scroll-triggered action does not fire on it while the reconcile is in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkScrollPositionAfterReconcileTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var harness: MainScreenModelHarness

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        harness = MainScreenModelHarness(testDispatcher)
        harness.publish((1L..40L).map { bookmark(it, listIds = "feeds") })
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun movingABookmarkToAListMarksItActedOn() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()
        val target = bookmark(1L, listIds = "feeds")

        model.moveBookmarkToList(target, "read-later")

        // Marked synchronously: the reconcile involves network calls, so the window in which the
        // scroll-triggered action could fire on a row about to leave is seconds long.
        assertTrue(
            target.remoteId in model.actedOnBookmarkIds.value,
            "the mark has to be set before the coroutine runs, not after it finishes"
        )
    }

    @Test
    fun removingABookmarkFromAListMarksItActedOn() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()
        val target = bookmark(1L, listIds = "feeds")

        model.removeBookmarkFromList(target, "feeds")

        assertTrue(target.remoteId in model.actedOnBookmarkIds.value)
    }

    @Test
    fun switchingViewClearsTheActedOnMarks() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()
        model.moveBookmarkToList(bookmark(1L, listIds = "feeds"), "read-later")
        assertTrue(model.actedOnBookmarkIds.value.isNotEmpty())

        model.applyFilter(FilterConfig(status = FilterStatus.ARCHIVED))
        advanceUntilIdle()

        assertEquals(
            emptySet(),
            model.actedOnBookmarkIds.value,
            "the marks belong to the view being left"
        )
    }

    @Test
    fun aReconcileDoesNotReloadTheListFromTheTop() = runTest(testDispatcher) {
        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        // The user is partway down.
        model.reportVisibleSlots(20..30)
        advanceUntilIdle()
        harness.clearReads()

        model.moveBookmarkToList(bookmark(1L, listIds = "feeds"), "read-later")
        advanceUntilIdle()

        assertTrue(
            harness.offsetsRead().none { it == 0 },
            "nothing re-read page 0 — a reload to the top is what the original bug was"
        )
        coVerify(exactly = 0) {
            harness.bookmarkRepository.getBookmarksPaged(any(), any(), any(), any(), any(), any())
        }
        job.cancel()
    }
}
