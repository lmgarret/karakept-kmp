package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.UndoCompletedEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for SAVE-02: MainScreenModel must correctly initialise and
 * load bookmarks when created in a fresh Navigator context (e.g. inside
 * BookmarkSavingActivity after a successful bookmark save).
 *
 * Verifies:
 * - Test 1: A freshly constructed MainScreenModel loads bookmarks into
 *   _accumulatedBookmarks when getBookmarksPaged returns data.
 * - Test 2: bookmarks.value is non-empty after init completes.
 * - Test 3: quickFilterCounts reflects the loaded bookmarks (drawer counters work).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class Save02RegressionTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var serverRepository: ServerRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var bookmarkActionsRepository: BookmarkActionsRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var listRepository: ListRepository
    private lateinit var bookmarkActionController: BookmarkActionController
    private lateinit var snackbarManager: ActionSnackbarManager
    private lateinit var highlightRepository: HighlightRepository

    private val fakeServer = Server(
        id = "server-1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        serverRepository = mockk(relaxed = true)
        bookmarkRepository = mockk(relaxed = true)
        bookmarkActionsRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        listRepository = mockk(relaxed = true)
        bookmarkActionController = mockk(relaxed = true)
        snackbarManager = mockk(relaxed = true)
        highlightRepository = mockk(relaxed = true)

        // Server is available immediately (simulates the BookmarkSavingActivity context
        // where the server is already configured).
        every { serverRepository.servers } returns flowOf(listOf(fakeServer))
        every { settingsRepository.allListSettings } returns flowOf(emptyMap())
        every { settingsRepository.swipeLeftAction } returns flowOf(SwipeAction.MARK_READ)
        every { settingsRepository.swipeRightAction } returns flowOf(SwipeAction.ARCHIVE)
        every { settingsRepository.customSwipeActionConfigs } returns flowOf(emptyList())
        every { settingsRepository.swipeLeftConfigId } returns flowOf(null)
        every { settingsRepository.swipeRightConfigId } returns flowOf(null)
        every { settingsRepository.dimReadBookmarks } returns flowOf(true)
        every { settingsRepository.defaultLayoutId } returns flowOf(null)
        every { settingsRepository.customLayouts } returns flowOf(emptyList())
        // offlineMode = false so syncBookmarks() is attempted (and safely no-ops via mockk relaxed)
        every { settingsRepository.offlineMode } returns flowOf(false)
        every { settingsRepository.activeServerId } returns flowOf("server-1")
        every { settingsRepository.defaultListType } returns flowOf(DefaultListType.ALL_BOOKMARKS)
        every { settingsRepository.defaultListId } returns flowOf(null)
        every { listRepository.lists } returns MutableStateFlow(emptyList())
        every { highlightRepository.getHighlightsCount(any()) } returns flowOf(0)
        // allBookmarks is used for quickFilterCounts / listCounts
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())
        // Replace SharedFlow relaxed mocks with real instances to avoid KotlinNothingValueException
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<Long>()
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow<UndoCompletedEvent>()
        // bookmarkRepository.syncProgress used by syncProgress StateFlow
        every { bookmarkRepository.syncProgress } returns MutableStateFlow(
            com.karakept.app.data.model.SyncProgress.Idle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createBookmarkEntity(remoteId: Long) = BookmarkEntity(
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
        createdAt = System.currentTimeMillis(),
        isArchived = false,
        isStarred = false
    )

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

    /**
     * Test 1: _accumulatedBookmarks is non-empty after init completes in a fresh
     * MainScreenModel when the server is available and getBookmarksPaged returns data.
     * This simulates the secondary Activity scenario (SAVE-02).
     */
    @Test
    fun `_accumulatedBookmarks is non-empty after init when server available and bookmarks exist`() =
        runTest(testDispatcher) {
            val fakeBookmarks = (1L..5L).map { createBookmarkEntity(it) }
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(),
                    status = any(),
                    offset = any(),
                    limit = any(),
                    listId = any()
                )
            } returns fakeBookmarks

            val model = createMainScreenModel()
            advanceUntilIdle()

            assertTrue(
                model._accumulatedBookmarks.value.isNotEmpty(),
                "Expected _accumulatedBookmarks to be non-empty after init, " +
                    "but was ${model._accumulatedBookmarks.value.size}"
            )
        }

    /**
     * Test 2: bookmarks.value is non-empty after init completes (the UI-facing
     * StateFlow that the MainScreen list observes).
     */
    @Test
    fun `bookmarks StateFlow is non-empty after init when server available and bookmarks exist`() =
        runTest(testDispatcher) {
            val fakeBookmarks = (1L..3L).map { createBookmarkEntity(it) }
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(),
                    status = any(),
                    offset = any(),
                    limit = any(),
                    listId = any()
                )
            } returns fakeBookmarks

            val model = createMainScreenModel()
            // Subscribe to bookmarks to activate SharingStarted.Lazily upstream
            val job = launch { model.bookmarks.collect {} }
            advanceUntilIdle()

            assertTrue(
                model.bookmarks.value.isNotEmpty(),
                "Expected bookmarks.value to be non-empty after init, " +
                    "but was ${model.bookmarks.value.size}"
            )
            job.cancel()
        }

    /**
     * Test 3: quickFilterCounts reflects loaded bookmarks, proving drawer counters
     * work after init in the secondary Activity context (SAVE-02).
     */
    @Test
    fun `quickFilterCounts reflects loaded bookmarks after init`() = runTest(testDispatcher) {
        val fakeBookmarks = (1L..4L).map { createBookmarkEntity(it) }
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(),
                status = any(),
                offset = any(),
                limit = any(),
                listId = any()
            )
        } returns fakeBookmarks

        // allBookmarks is used for quickFilterCounts — return the same bookmarks
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(fakeBookmarks)

        val model = createMainScreenModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        val counts = model.quickFilterCounts.value
        assertEquals(
            4,
            counts.all,
            "Expected quickFilterCounts.all = 4 (all non-archived), but was ${counts.all}"
        )
        job.cancel()
    }
}
