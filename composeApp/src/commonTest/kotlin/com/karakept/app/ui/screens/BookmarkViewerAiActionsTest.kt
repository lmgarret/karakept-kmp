package com.karakept.app.ui.screens

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.OfflineModeException
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.remote.UnsupportedServerActionException
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
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
import kotlin.test.assertNull

/**
 * Tests for the reader's AI actions.
 *
 * These are server-side jobs with no undo, so the contract is the same one the crawl actions have:
 * the right call goes out, the in-flight marker is always cleared, a second tap while one is
 * running is ignored, and each kind of refusal reaches the user as its own message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkViewerAiActionsTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var assetDao: AssetDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var bookmarkActionsRepository: BookmarkActionsRepository
    private lateinit var remoteDataSource: RemoteDataSource
    private lateinit var serverRepository: ServerRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var bookmarkActionController: BookmarkActionController
    private lateinit var highlightRepository: HighlightRepository
    private lateinit var snackbarManager: ActionSnackbarManager

    private val testServer = Server(
        id = "server-1",
        url = "https://karakeep.example.com",
        apiKey = "key",
        label = "Test"
    )

    private val testBookmark = BookmarkEntity(
        localId = 1L,
        remoteId = 100L,
        originalRemoteId = "remote-100",
        serverId = "server-1",
        url = "https://example.com/article",
        title = "Test Article",
        content = "<p>Test content</p>",
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 0L,
        isArchived = false,
        isStarred = false,
        isRead = false
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The AI actions are extension functions on the repository, so they are stubbed as
        // statics on their file class rather than as members of the mock.
        mockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositoryAiKt")

        bookmarkDao = mockk(relaxed = true)
        assetDao = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        bookmarkActionsRepository = mockk(relaxed = true)
        remoteDataSource = mockk(relaxed = true)
        serverRepository = mockk(relaxed = true)
        bookmarkRepository = mockk(relaxed = true)
        bookmarkActionController = mockk(relaxed = true)
        highlightRepository = mockk(relaxed = true)
        snackbarManager = mockk(relaxed = true)

        every { settingsRepository.viewerMode } returns flowOf(com.karakept.app.data.model.ViewerMode.READER)
        every { settingsRepository.hideArticleThumbnails } returns flowOf(true)
        every { settingsRepository.htmlTextColor } returns flowOf(null)
        every { settingsRepository.htmlBackgroundColor } returns flowOf(null)
        every { settingsRepository.htmlFontSize } returns flowOf(16)
        every { settingsRepository.htmlFontFamily } returns flowOf(com.karakept.app.data.model.ReaderFontFamily.SYSTEM)
        every { settingsRepository.showTagsInViewer } returns flowOf(true)
        every { settingsRepository.dateDisplayMode } returns flowOf(com.karakept.app.data.model.DateDisplayMode.ELAPSED)
        every { settingsRepository.linkOpenMode } returns flowOf(com.karakept.app.data.model.LinkOpenMode.CUSTOM_TAB)
        every { settingsRepository.offlineMode } returns flowOf(false)
        every { settingsRepository.trackReadingProgress } returns flowOf(true)
        every { settingsRepository.preferFullPageHtml } returns flowOf(false)

        every { serverRepository.servers } returns flowOf(listOf(testServer))
        every { bookmarkDao.observeBookmarkById(any()) } returns flowOf(testBookmark)
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns emptyList()
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } returns "A summary."
        coEvery { bookmarkActionsRepository.requestAiRetag(any()) } returns true
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositoryAiKt")
        Dispatchers.resetMain()
    }

    private fun createScreenModel() = BookmarkViewerScreenModel(
        bookmarkDao = bookmarkDao,
        assetDao = assetDao,
        settingsRepository = settingsRepository,
        bookmarkActionsRepository = bookmarkActionsRepository,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        bookmarkRepository = bookmarkRepository,
        bookmarkActionController = bookmarkActionController,
        highlightRepository = highlightRepository,
        snackbarManager = snackbarManager
    )

    @Test
    fun `summarize asks the repository to generate and store a summary`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        screenModel.runAiAction(testBookmark, AiAction.SUMMARIZE)
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkActionsRepository.summarizeBookmark(testBookmark) }
        coVerify(exactly = 0) { bookmarkActionsRepository.requestAiRetag(any()) }
    }

    @Test
    fun `retag asks the repository to re-run tagging`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        screenModel.runAiAction(testBookmark, AiAction.RETAG)
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkActionsRepository.requestAiRetag(testBookmark) }
        coVerify(exactly = 0) { bookmarkActionsRepository.summarizeBookmark(any()) }
    }

    @Test
    fun `in-flight marker is cleared after success`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        screenModel.runAiAction(testBookmark, AiAction.SUMMARIZE)
        advanceUntilIdle()

        assertNull(screenModel.aiActionInFlight.value)
    }

    @Test
    fun `in-flight marker is cleared after failure`() = runTest(testDispatcher) {
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } throws ApiException("HTTP 500")
        val screenModel = createScreenModel()

        screenModel.runAiAction(testBookmark, AiAction.SUMMARIZE)
        advanceUntilIdle()

        // A stuck marker would disable every AI row for the rest of the session.
        assertNull(screenModel.aiActionInFlight.value)
    }

    @Test
    fun `in-flight marker is set while the job runs`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<String?>()
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers { gate.await() }
        val screenModel = createScreenModel()

        screenModel.runAiAction(testBookmark, AiAction.SUMMARIZE)
        advanceUntilIdle()
        assertEquals(AiAction.SUMMARIZE, screenModel.aiActionInFlight.value)

        gate.complete("done")
        advanceUntilIdle()
        assertNull(screenModel.aiActionInFlight.value)
    }

    @Test
    fun `a second tap while a job is running is ignored`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<String?>()
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } coAnswers { gate.await() }
        val screenModel = createScreenModel()

        screenModel.runAiAction(testBookmark, AiAction.SUMMARIZE)
        advanceUntilIdle()
        screenModel.runAiAction(testBookmark, AiAction.SUMMARIZE)
        // The other AI action shares the slot too — one job at a time per bookmark.
        screenModel.runAiAction(testBookmark, AiAction.RETAG)
        advanceUntilIdle()

        gate.complete("done")
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkActionsRepository.summarizeBookmark(any()) }
        coVerify(exactly = 0) { bookmarkActionsRepository.requestAiRetag(any()) }
    }

    @Test
    fun `an unsupported server is reported in its own words`() = runTest(testDispatcher) {
        coEvery { bookmarkActionsRepository.requestAiRetag(any()) } throws
            UnsupportedServerActionException("Re-running AI tagging needs an admin account on this server")
        val screenModel = createScreenModel()

        screenModel.runAiAction(testBookmark, AiAction.RETAG)
        advanceUntilIdle()

        coVerify { snackbarManager.showSnackbar("Re-running AI tagging needs an admin account on this server") }
        coVerify(exactly = 0) { snackbarManager.showErrorWithRetry(any(), any(), any()) }
    }

    @Test
    fun `offline mode is reported without offering a retry`() = runTest(testDispatcher) {
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } throws OfflineModeException()
        val screenModel = createScreenModel()

        screenModel.runAiAction(testBookmark, AiAction.SUMMARIZE)
        advanceUntilIdle()

        coVerify { snackbarManager.showSnackbar("Not available in offline mode") }
        coVerify(exactly = 0) { snackbarManager.showErrorWithRetry(any(), any(), any()) }
    }

    @Test
    fun `an unreachable server offers a retry`() = runTest(testDispatcher) {
        coEvery { bookmarkActionsRepository.summarizeBookmark(any()) } throws ApiException("timeout")
        val screenModel = createScreenModel()

        screenModel.runAiAction(testBookmark, AiAction.SUMMARIZE)
        advanceUntilIdle()

        coVerify { snackbarManager.showErrorWithRetry("Couldn't reach the server", any(), any()) }
    }

    @Test
    fun `a retag that has not landed yet says so rather than claiming success`() = runTest(testDispatcher) {
        coEvery { bookmarkActionsRepository.requestAiRetag(any()) } returns false
        val screenModel = createScreenModel()

        screenModel.runAiAction(testBookmark, AiAction.RETAG)
        advanceUntilIdle()

        coVerify { snackbarManager.showSnackbar("Still tagging on the server — pull to refresh later") }
    }
}
