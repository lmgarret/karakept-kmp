package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.UnsupportedServerActionException
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.requestAiRetag
import com.karakept.app.data.repository.summarizeBookmark
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.AiAction
import com.karakept.app.domain.action.BookmarkActionController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private lateinit var serverRepository: ServerRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var bookmarkActionsRepository: BookmarkActionsRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var listRepository: ListRepository
    private lateinit var bookmarkActionController: BookmarkActionController
    private lateinit var snackbarManager: ActionSnackbarManager
    private lateinit var highlightRepository: HighlightRepository

    private val fakeServer = Server(id = "server-1", url = "https://example.com", apiKey = "k", label = "Test")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The AI actions are extension functions, so they are stubbed on their file class.
        mockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositoryAiKt")

        serverRepository = mockk(relaxed = true)
        bookmarkRepository = mockk(relaxed = true)
        bookmarkActionsRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        listRepository = mockk(relaxed = true)
        bookmarkActionController = mockk(relaxed = true)
        snackbarManager = mockk(relaxed = true)
        highlightRepository = mockk(relaxed = true)

        every { serverRepository.servers } returns flowOf(emptyList())
        every { settingsRepository.allListSettings } returns flowOf(emptyMap())
        every { settingsRepository.swipeLeftAction } returns flowOf(SwipeAction.MARK_READ)
        every { settingsRepository.swipeRightAction } returns flowOf(SwipeAction.ARCHIVE)
        every { settingsRepository.customSwipeActionConfigs } returns flowOf(emptyList())
        every { settingsRepository.swipeLeftConfigId } returns flowOf(null)
        every { settingsRepository.swipeRightConfigId } returns flowOf(null)
        every { settingsRepository.dimReadBookmarks } returns flowOf(true)
        every { settingsRepository.defaultLayoutId } returns flowOf(null)
        every { settingsRepository.customLayouts } returns flowOf(emptyList())
        every { settingsRepository.offlineMode } returns flowOf(true)
        every { settingsRepository.activeServerId } returns flowOf("server-1")
        every { settingsRepository.defaultListType } returns flowOf(DefaultListType.ALL_BOOKMARKS)
        every { settingsRepository.defaultListId } returns flowOf(null)
        every { settingsRepository.lastActiveFilterStatus } returns flowOf(null)
        every { settingsRepository.lastActiveFilterListId } returns flowOf(null)
        every { listRepository.lists } returns MutableStateFlow(emptyList())
        every { highlightRepository.getHighlightsCount(any()) } returns flowOf(0)
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns kotlinx.coroutines.flow.MutableSharedFlow<Long>()
        every { bookmarkActionsRepository.aiCapabilities } returns MutableStateFlow(emptyMap())
        every { bookmarkActionController.undoCompletedEvents } returns kotlinx.coroutines.flow.MutableSharedFlow<com.karakept.app.domain.action.UndoCompletedEvent>()
        every { bookmarkRepository.syncReports } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { bookmarkRepository.backgroundSyncCompleted } returns kotlinx.coroutines.flow.MutableSharedFlow()

        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } returns "A summary."
        coEvery { bookmarkActionsRepository.requestAiRetag(any(), any()) } returns true
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositoryAiKt")
        Dispatchers.resetMain()
    }

    private fun createMainScreenModel() = MainScreenModel(
        serverRepository = serverRepository,
        bookmarkRepository = bookmarkRepository,
        bookmarkActionsRepository = bookmarkActionsRepository,
        settingsRepository = settingsRepository,
        listRepository = listRepository,
        bookmarkActionController = bookmarkActionController,
        snackbarManager = snackbarManager,
        highlightRepository = highlightRepository
    )

    private fun bookmark(remoteId: Long) = BookmarkEntity(
        localId = remoteId,
        remoteId = remoteId,
        originalRemoteId = "remote-$remoteId",
        serverId = "server-1",
        url = "https://example.com/$remoteId",
        title = "Bookmark $remoteId",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 0L,
        isArchived = false,
        isStarred = false
    )

    /** A model with [count] bookmarks loaded and hand-selected. */
    private fun MainScreenModel.withHandPickedSelection(count: Int): List<BookmarkEntity> {
        val bookmarks = (1L..count).map { bookmark(it) }
        _hasMoreItems.value = false
        _accumulatedBookmarks.value = bookmarks
        bookmarks.forEach { toggleBookmarkSelection(it) }
        return bookmarks
    }

    // ── select-all tracking ────────────────────────────────────

    @Test
    fun `selectAll marks the selection so AI actions can be withheld`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()
        model._hasMoreItems.value = false
        model._accumulatedBookmarks.value = (1L..3L).map { bookmark(it) }

        model.selectAll()
        advanceUntilIdle()

        assertTrue(model.selectedViaSelectAll.value)
    }

    @Test
    fun `hand-picking after Select All clears the mark`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()
        val bookmarks = (1L..3L).map { bookmark(it) }
        model._hasMoreItems.value = false
        model._accumulatedBookmarks.value = bookmarks
        model.selectAll()
        advanceUntilIdle()

        model.toggleBookmarkSelection(bookmarks.first())

        // The selection is no longer "everything" — it is whatever the user left checked.
        assertFalse(model.selectedViaSelectAll.value)
    }

    @Test
    fun `clearing the selection clears the mark`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()
        model._hasMoreItems.value = false
        model._accumulatedBookmarks.value = (1L..3L).map { bookmark(it) }
        model.selectAll()
        advanceUntilIdle()

        model.clearSelection()

        assertFalse(model.selectedViaSelectAll.value)
    }

    @Test
    fun `entering selection mode from a long press does not mark it`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        model.enterSelectionMode(bookmark(1L))

        assertFalse(model.selectedViaSelectAll.value)
    }

    // ── batch run ──────────────────────────────────────────────

    @Test
    fun `summarize runs once per selected bookmark`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()
        val bookmarks = model.withHandPickedSelection(3)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        bookmarks.forEach { coVerify(exactly = 1) { bookmarkActionsRepository.summarizeBookmark(it) } }
    }

    @Test
    fun `retag runs once per selected bookmark`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()
        val bookmarks = model.withHandPickedSelection(2)

        model.batchAiAction(AiAction.RETAG)
        advanceUntilIdle()

        // awaitResult = false: a batch queues the jobs rather than polling each one for its tags.
        bookmarks.forEach {
            coVerify(exactly = 1) { bookmarkActionsRepository.requestAiRetag(it, awaitResult = false) }
        }
        coVerify(exactly = 0) { bookmarkActionsRepository.summarizeBookmark(any()) }
    }

    @Test
    fun `items run one at a time rather than all at once`() = runTest(testDispatcher) {
        var inFlight = 0
        var maxInFlight = 0
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            kotlinx.coroutines.yield()
            inFlight--
            "summary"
        }
        val model = createMainScreenModel()
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
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers {
            started++
            if (started == 1) gate.await() else "summary"
        }
        val model = createMainScreenModel()
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
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers { gate.await() }
        val model = createMainScreenModel()
        advanceUntilIdle()
        model.withHandPickedSelection(5)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        model.cancelAiBatchAction()
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkActionsRepository.summarizeBookmark(any()) }
        assertNull(model.aiBatchProgress.value)
        assertTrue(model.selectedBookmarkIds.value.isEmpty())
    }

    @Test
    fun `one failure does not abandon the rest of the selection`() = runTest(testDispatcher) {
        var call = 0
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers {
            call++
            if (call == 2) throw ApiException("HTTP 500") else "summary"
        }
        val model = createMainScreenModel()
        advanceUntilIdle()
        model.withHandPickedSelection(3)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        coVerify(exactly = 3) { bookmarkActionsRepository.summarizeBookmark(any()) }
        coVerify { snackbarManager.showSnackbar("Summarized 2 of 3 — 1 failed", any()) }
    }

    @Test
    fun `an unsupported server stops the run instead of failing every item in turn`() = runTest(testDispatcher) {
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } throws
            UnsupportedServerActionException("This Karakeep server has no AI model configured")
        val model = createMainScreenModel()
        advanceUntilIdle()
        model.withHandPickedSelection(4)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkActionsRepository.summarizeBookmark(any()) }
        coVerify { snackbarManager.showSnackbar("This Karakeep server has no AI model configured", any()) }
    }

    @Test
    fun `a second run while one is in flight is ignored`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<String?>()
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers { gate.await() }
        val model = createMainScreenModel()
        advanceUntilIdle()
        model.withHandPickedSelection(2)

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()
        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        gate.complete("summary")
        advanceUntilIdle()

        coVerify(exactly = 2) { bookmarkActionsRepository.summarizeBookmark(any()) }
    }

    @Test
    fun `an empty selection just exits selection mode`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        model.batchAiAction(AiAction.SUMMARIZE)
        advanceUntilIdle()

        coVerify(exactly = 0) { bookmarkActionsRepository.summarizeBookmark(any()) }
        assertTrue(model.selectedBookmarkIds.value.isEmpty())
    }
}
