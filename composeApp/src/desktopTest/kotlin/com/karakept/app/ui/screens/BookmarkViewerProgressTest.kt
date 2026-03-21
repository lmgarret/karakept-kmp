package com.karakept.app.ui.screens

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.pullReadingProgressFromServer
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for reading progress synchronization in [BookmarkViewerScreenModel].
 *
 * Verifies:
 * - serverProgressChecked starts as false
 * - serverProgressChecked becomes true only after pullReadingProgressFromServer completes
 * - Rapid onReadingStateChanged calls are debounced (only latest state persists)
 * - Local progress updates do not set serverProgressChecked to true
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkViewerProgressTest {

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
        description = "A test bookmark",
        createdAt = System.currentTimeMillis(),
        isArchived = false,
        isStarred = false,
        isRead = false,
        tags = "",
        listIds = "",
        readingProgress = 0f
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositorySyncKt")

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

        // Default stubs for SettingsRepository flows consumed via stateIn in the constructor
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
        every { settingsRepository.contentSyncStrategy } returns flowOf(com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK)

        // Default stub for pullReadingProgressFromServer extension function
        coEvery {
            bookmarkActionsRepository.pullReadingProgressFromServer(any(), any())
        } returns false

        // Default stubs for ServerRepository
        every { serverRepository.servers } returns flowOf(emptyList())

        // Default stubs for BookmarkDao
        every { bookmarkDao.observeBookmarkById(any()) } returns flowOf(testBookmark)
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositorySyncKt")
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

    // -----------------------------------------------------------------------
    // Test 1: serverProgressChecked starts as false
    // -----------------------------------------------------------------------

    @Test
    fun `serverProgressChecked starts as false`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()
        assertFalse(screenModel.serverProgressChecked.value,
            "serverProgressChecked should start as false before any load")
    }

    // -----------------------------------------------------------------------
    // Test 2: serverProgressChecked becomes true after pullReadingProgressFromServer
    // -----------------------------------------------------------------------

    @Test
    fun `serverProgressChecked becomes true after loadBookmark completes server pull`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()
        assertFalse(screenModel.serverProgressChecked.value)

        // Trigger loadBookmark which internally calls pullReadingProgressFromServer
        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertTrue(screenModel.serverProgressChecked.value,
            "serverProgressChecked should be true after pullReadingProgressFromServer completes")
    }

    // -----------------------------------------------------------------------
    // Test 3: Rapid onReadingStateChanged calls are debounced
    // -----------------------------------------------------------------------

    @Test
    fun `rapid onReadingStateChanged calls debounce to single DB write`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        // Send 5 rapid reading state changes
        screenModel.onReadingStateChanged(1L, 100L, 0.1f, 1, 0)
        screenModel.onReadingStateChanged(1L, 100L, 0.2f, 2, 0)
        screenModel.onReadingStateChanged(1L, 100L, 0.3f, 3, 0)
        screenModel.onReadingStateChanged(1L, 100L, 0.4f, 4, 0)
        screenModel.onReadingStateChanged(1L, 100L, 0.5f, 5, 0)

        // Advance past the 500ms debounce window
        advanceUntilIdle()

        // Should only have written to DB once (the last value, due to DROP_OLDEST + debounce)
        coVerify(atMost = 1) {
            bookmarkDao.updateReadingProgress(1L, any(), any(), any())
        }
    }

    @Test
    fun `debounced write persists the latest progress value`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        // Send rapid updates, last one is 0.8f
        screenModel.onReadingStateChanged(1L, 100L, 0.1f, 1, 0)
        screenModel.onReadingStateChanged(1L, 100L, 0.5f, 5, 0)
        screenModel.onReadingStateChanged(1L, 100L, 0.8f, 8, 0)

        advanceUntilIdle()

        // The debounced write should use the latest value (0.8f at index 8)
        coVerify {
            bookmarkDao.updateReadingProgress(1L, 0.8f, 8, 0)
        }
    }

    // -----------------------------------------------------------------------
    // Test 4: Local progress does not set serverProgressChecked
    // -----------------------------------------------------------------------

    @Test
    fun `onReadingStateChanged does not set serverProgressChecked to true`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()
        assertFalse(screenModel.serverProgressChecked.value)

        // Update reading progress locally (without calling loadBookmark)
        screenModel.onReadingStateChanged(1L, 100L, 0.5f, 5, 0)
        advanceUntilIdle()

        // serverProgressChecked should still be false — it's only for server-to-local sync
        assertFalse(screenModel.serverProgressChecked.value,
            "serverProgressChecked should not be set by local progress updates")
    }

    // -----------------------------------------------------------------------
    // Test 5: offline mode sets serverProgressChecked immediately
    // -----------------------------------------------------------------------

    @Test
    fun `offline mode sets serverProgressChecked true without server call`() = runTest(testDispatcher) {
        every { settingsRepository.offlineMode } returns flowOf(true)

        val screenModel = createScreenModel()
        assertFalse(screenModel.serverProgressChecked.value)

        // Load bookmark in offline mode — should skip pull and set checked=true directly
        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertTrue(screenModel.serverProgressChecked.value,
            "serverProgressChecked should be true in offline mode without contacting server")

        // pullReadingProgressFromServer should NOT have been called
        coVerify(exactly = 0) {
            bookmarkActionsRepository.pullReadingProgressFromServer(any(), any())
        }
    }
}
