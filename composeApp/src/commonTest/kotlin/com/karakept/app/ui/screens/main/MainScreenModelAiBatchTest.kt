package com.karakept.app.ui.screens.main

import com.karakept.app.testing.testBookmark
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.UnsupportedServerActionException
import com.karakept.app.data.repository.requestAiRetag
import com.karakept.app.data.repository.summarizeBookmark
import com.karakept.app.domain.action.AiAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for AI actions over a multi-selection.
 *
 * Two things matter beyond "it calls the repository". The run must be sequential — each item is an
 * inference call on the server, and firing a selection's worth in parallel is how a self-hosted
 * instance starts refusing them. And a Select All selection must never reach these actions at all:
 * Select All can pull the entire library into the selection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelAiBatchTest {
    private val testDispatcher = StandardTestDispatcher()
    private val harness = MainScreenModelHarness(testDispatcher)

    private val fakeServer = Server(id = "server-1", url = "https://example.com", apiKey = "k", label = "Test")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The AI actions are extension functions, so they are stubbed on their file class.
        mockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositoryAiKt")

        // The rows behind a selection are read by id, which needs a server to read them from.
        every { harness.serverRepository.servers } returns flowOf(listOf(fakeServer))

        coEvery { harness.bookmarkActionsRepository.summarizeBookmark(any()) } returns "A summary."
        coEvery { harness.bookmarkActionsRepository.requestAiRetag(any(), any()) } returns true
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositoryAiKt")
        Dispatchers.resetMain()
    }

    /**
     * A model with [count] bookmarks hand-selected.
     *
     * The rows behind a selection are read by id, so the view is described to the repository
     * rather than pushed into the list — a selection can cover rows the list has never shown.
     */
    private fun MainScreenModel.withHandPickedSelection(count: Int): List<Bookmark> {
        val bookmarks = (1L..count).map { testBookmark(it) }
        stubView(bookmarks)
        bookmarks.forEach { toggleBookmarkSelection(it) }
        return bookmarks
    }

    /** Makes [bookmarks] the view the repository answers for. */
    private fun stubView(bookmarks: List<Bookmark>) {
        coEvery { harness.bookmarkRepository.getViewRemoteIds(any(), any()) } returns
            bookmarks.map { it.remoteId }
        coEvery { harness.bookmarkRepository.getBookmarksByRemoteIds(any(), any()) } answers {
            val ids = secondArg<List<String>>().toSet()
            bookmarks.filter { it.remoteId in ids }
        }
    }

    // ── select-all tracking ────────────────────────────────────

    @Test
    fun `selectAll marks the selection so AI actions can be withheld`() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()
        stubView((1L..3L).map { testBookmark(it) })

        model.selectAll()
        advanceUntilIdle()

        assertTrue(model.selectedViaSelectAll.value)
    }

    @Test
    fun `hand-picking after Select All clears the mark`() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()
        val bookmarks = (1L..3L).map { testBookmark(it) }
        stubView(bookmarks)
        model.selectAll()
        advanceUntilIdle()

        model.toggleBookmarkSelection(bookmarks.first())

        // The selection is no longer "everything" — it is whatever the user left checked.
        assertFalse(model.selectedViaSelectAll.value)
    }

    @Test
    fun `clearing the selection clears the mark`() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()
        stubView((1L..3L).map { testBookmark(it) })
        model.selectAll()
        advanceUntilIdle()

        model.clearSelection()

        assertFalse(model.selectedViaSelectAll.value)
    }

    @Test
    fun `entering selection mode from a long press does not mark it`() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()

        model.enterSelectionMode(testBookmark(1L))

        assertFalse(model.selectedViaSelectAll.value)
    }

    // ── batch run ──────────────────────────────────────────────

    @Test
    fun `summarize runs once per selected bookmark`() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()
        val bookmarks = model.withHandPickedSelection(3)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        bookmarks.forEach { coVerify(exactly = 1) { harness.bookmarkActionsRepository.summarizeBookmark(it) } }
    }

    @Test
    fun `retag runs once per selected bookmark`() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()
        val bookmarks = model.withHandPickedSelection(2)

        model.batchAiAction(AiAction.RETAG)
        advanceUntilIdle()

        // awaitResult = false: a batch queues the jobs rather than polling each one for its tags.
        bookmarks.forEach {
            coVerify(exactly = 1) { harness.bookmarkActionsRepository.requestAiRetag(it, awaitResult = false) }
        }
        coVerify(exactly = 0) { harness.bookmarkActionsRepository.summarizeBookmark(any()) }
    }

    @Test
    fun `items run one at a time rather than all at once`() = runTest(testDispatcher) {
        var inFlight = 0
        var maxInFlight = 0
        coEvery { harness.bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            kotlinx.coroutines.yield()
            inFlight--
            "summary"
        }
        val model = harness.createModel()
        advanceUntilIdle()
        model.withHandPickedSelection(4)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        assertEquals(1, maxInFlight)
    }

    @Test
    fun `progress counts through the selection and is cleared at the end`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<String?>()
        var started = 0
        coEvery { harness.bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers {
            started++
            if (started == 1) gate.await() else "summary"
        }
        val model = harness.createModel()
        advanceUntilIdle()
        model.withHandPickedSelection(3)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        assertEquals(AiAction.SUMMARIZE, model.aiBatchProgress.value?.action)
        assertEquals(0, model.aiBatchProgress.value?.done)
        assertEquals(3, model.aiBatchProgress.value?.total)

        gate.complete("summary")
        advanceUntilIdle()

        assertNull(model.aiBatchProgress.value)
    }

    @Test
    fun `cancelling stops the run and leaves the rest untouched`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<String?>()
        coEvery { harness.bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers { gate.await() }
        val model = harness.createModel()
        advanceUntilIdle()
        model.withHandPickedSelection(5)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        model.cancelAiBatchAction()
        advanceUntilIdle()

        coVerify(exactly = 1) { harness.bookmarkActionsRepository.summarizeBookmark(any()) }
        assertNull(model.aiBatchProgress.value)
        assertTrue(model.selectedBookmarkIds.value.isEmpty())
    }

    @Test
    fun `one failure does not abandon the rest of the selection`() = runTest(testDispatcher) {
        var call = 0
        coEvery { harness.bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers {
            call++
            if (call == 2) throw ApiException("HTTP 500") else "summary"
        }
        val model = harness.createModel()
        advanceUntilIdle()
        model.withHandPickedSelection(3)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        coVerify(exactly = 3) { harness.bookmarkActionsRepository.summarizeBookmark(any()) }
        coVerify { harness.snackbarManager.showSnackbar("Summarized 2 of 3 — 1 failed", any()) }
    }

    @Test
    fun `an unsupported server stops the run instead of failing every item in turn`() = runTest(testDispatcher) {
        coEvery { harness.bookmarkActionsRepository.summarizeBookmark(any()) } throws
            UnsupportedServerActionException("This Karakeep server has no AI model configured")
        val model = harness.createModel()
        advanceUntilIdle()
        model.withHandPickedSelection(4)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        coVerify(exactly = 1) { harness.bookmarkActionsRepository.summarizeBookmark(any()) }
        coVerify { harness.snackbarManager.showSnackbar("This Karakeep server has no AI model configured", any()) }
    }

    @Test
    fun `a second run while one is in flight is ignored`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<String?>()
        coEvery { harness.bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers { gate.await() }
        val model = harness.createModel()
        advanceUntilIdle()
        model.withHandPickedSelection(2)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()
        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        gate.complete("summary")
        advanceUntilIdle()

        coVerify(exactly = 2) { harness.bookmarkActionsRepository.summarizeBookmark(any()) }
    }

    @Test
    fun `an empty selection just exits selection mode`() = runTest(testDispatcher) {
        val model = harness.createModel()
        advanceUntilIdle()

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        coVerify(exactly = 0) { harness.bookmarkActionsRepository.summarizeBookmark(any()) }
        assertTrue(model.selectedBookmarkIds.value.isEmpty())
    }
}
