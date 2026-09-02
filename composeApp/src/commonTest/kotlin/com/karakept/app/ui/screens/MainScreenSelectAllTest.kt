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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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

/**
 * Tests for FILT-01: selectAll() behavior on MainScreenModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenSelectAllTest {

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
        highlightRepository = highlightRepository
    )

    private fun createBookmarkEntity(
        remoteId: Long,
        isArchived: Boolean = false,
        isStarred: Boolean = false
    ) = BookmarkEntity(
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
        isArchived = isArchived,
        isStarred = isStarred
    )

    @Test
    fun `selectAll selects accumulated bookmarks when all pages loaded`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        val bookmarks = (1L..20L).map { createBookmarkEntity(it) }
        model._hasMoreItems.value = false
        model._accumulatedBookmarks.value = bookmarks

        model.selectAll()
        advanceUntilIdle()

        assertEquals(20, model._selectedBookmarkIds.value.size)
    }

    @Test
    fun `selectAll fetches all items when hasMoreItems is true`() = runTest(testDispatcher) {
        val allBookmarks = (1L..50L).map { createBookmarkEntity(it) }
        coEvery { bookmarkRepository.getAllBookmarks(any(), any(), any()) } returns allBookmarks

        val model = createMainScreenModel()
        advanceUntilIdle()

        model._selectedServer.value = fakeServer
        advanceUntilIdle()

        model._hasMoreItems.value = true
        model._accumulatedBookmarks.value = allBookmarks.take(20)

        model.selectAll()
        advanceUntilIdle()

        assertEquals(50, model._selectedBookmarkIds.value.size)
    }

    @Test
    fun `selectAll sets hasMoreItems to false after fetching all`() = runTest(testDispatcher) {
        val allBookmarks = (1L..50L).map { createBookmarkEntity(it) }
        coEvery { bookmarkRepository.getAllBookmarks(any(), any(), any()) } returns allBookmarks

        val model = createMainScreenModel()
        advanceUntilIdle()

        model._selectedServer.value = fakeServer
        advanceUntilIdle()

        model._hasMoreItems.value = true
        model._accumulatedBookmarks.value = allBookmarks.take(20)

        model.selectAll()
        advanceUntilIdle()

        assertFalse(model._hasMoreItems.value)
    }

    @Test
    fun `selectAll updates accumulated bookmarks with all fetched items`() = runTest(testDispatcher) {
        val allBookmarks = (1L..50L).map { createBookmarkEntity(it) }
        coEvery { bookmarkRepository.getAllBookmarks(any(), any(), any()) } returns allBookmarks

        val model = createMainScreenModel()
        advanceUntilIdle()

        model._selectedServer.value = fakeServer
        advanceUntilIdle()

        model._hasMoreItems.value = true
        model._accumulatedBookmarks.value = allBookmarks.take(20)

        model.selectAll()
        advanceUntilIdle()

        assertEquals(50, model._accumulatedBookmarks.value.size)
    }
}
