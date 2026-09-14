package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import com.karakept.app.utils.TestAppDispatchers

/**
 * Tests for FILT-02: QuickFilterCounts computation in MainScreenModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickFilterCountsTest {

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

    @BeforeTest
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
        every { settingsRepository.offlineMode } returns flowOf(false)
        every { settingsRepository.activeServerId } returns flowOf("server-1")
        every { settingsRepository.defaultListType } returns flowOf(DefaultListType.ALL_BOOKMARKS)
        every { settingsRepository.defaultListId } returns flowOf(null)
        every { settingsRepository.lastActiveFilterStatus } returns flowOf(null)
        every { settingsRepository.lastActiveFilterListId } returns flowOf(null)
        every { listRepository.lists } returns MutableStateFlow(emptyList())
        every { highlightRepository.getHighlightsCount(any()) } returns flowOf(0)
        every { bookmarkRepository.getOfflineBookmarkCount(any()) } returns flowOf(0)
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns kotlinx.coroutines.flow.MutableSharedFlow<String>()
        every { bookmarkActionsRepository.aiCapabilities } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
        every { bookmarkActionController.undoCompletedEvents } returns kotlinx.coroutines.flow.MutableSharedFlow<com.karakept.app.domain.action.UndoCompletedEvent>()
        every { bookmarkRepository.syncReports } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { bookmarkRepository.backgroundSyncCompleted } returns kotlinx.coroutines.flow.MutableSharedFlow()
    }

    @AfterTest
    fun tearDown() {
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
        highlightRepository = highlightRepository,
        appDispatchers = TestAppDispatchers(testDispatcher)
    )

    private fun createBookmarkEntity(
        remoteId: Long,
        isArchived: Boolean = false,
        isStarred: Boolean = false
    ) = BookmarkEntity(
        localId = remoteId,
        remoteId = "remote-$remoteId",
        serverId = "server-1",
        url = "https://example.com/$remoteId",
        title = "Bookmark $remoteId",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 0L,
        isArchived = isArchived,
        isStarred = isStarred
    )

    @Test
    fun `quickFilterCounts is zero when no bookmarks`() = runTest(testDispatcher) {
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())

        val model = createMainScreenModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        assertEquals(QuickFilterCounts(0, 0, 0), model.quickFilterCounts.value)
        job.cancel()
    }

    @Test
    fun `quickFilterCounts computes correct values for mixed dataset`() = runTest(testDispatcher) {
        val bookmarks = listOf(
            createBookmarkEntity(1, isArchived = true),
            createBookmarkEntity(2, isArchived = true),
            createBookmarkEntity(3, isArchived = true),
            createBookmarkEntity(4, isStarred = true),
            createBookmarkEntity(5, isStarred = true),
            createBookmarkEntity(6),
            createBookmarkEntity(7),
            createBookmarkEntity(8),
            createBookmarkEntity(9),
            createBookmarkEntity(10)
        )
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(bookmarks)

        val model = createMainScreenModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        assertEquals(
            QuickFilterCounts(all = 7, favorites = 2, archived = 3, offline = 0),
            model.quickFilterCounts.value
        )
        job.cancel()
    }

    @Test
    fun `quickFilterCounts counts all archived when all bookmarks archived`() = runTest(testDispatcher) {
        val bookmarks = (1L..5L).map { createBookmarkEntity(it, isArchived = true) }
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(bookmarks)

        val model = createMainScreenModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        assertEquals(
            QuickFilterCounts(all = 0, favorites = 0, archived = 5, offline = 0),
            model.quickFilterCounts.value
        )
        job.cancel()
    }

    @Test
    fun `quickFilterCounts offline reflects repository count`() = runTest(testDispatcher) {
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())
        every { bookmarkRepository.getOfflineBookmarkCount(any()) } returns flowOf(4)

        val model = createMainScreenModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        assertEquals(4, model.quickFilterCounts.value.offline)
        job.cancel()
    }

    @Test
    fun `quickFilterCounts offline is zero when no server`() = runTest(testDispatcher) {
        every { serverRepository.servers } returns flowOf(emptyList())
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())

        val model = createMainScreenModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        assertEquals(0, model.quickFilterCounts.value.offline)
        job.cancel()
    }
}
