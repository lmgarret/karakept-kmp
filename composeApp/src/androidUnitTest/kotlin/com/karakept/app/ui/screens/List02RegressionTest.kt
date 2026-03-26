package com.karakept.app.ui.screens

import com.karakept.api.model.KarakeepList
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
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

/**
 * Regression tests for LIST-02: Smart lists do not update after quick actions that
 * change a bookmark's list membership.
 *
 * Verifies that after moveBookmarkToList and removeBookmarkFromList,
 * syncBookmarksForList is called for every SMART list but NOT for MANUAL lists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class List02RegressionTest {

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

    private val smartList1 = KarakeepList(id = "smart-1", name = "Unread", type = KarakeepList.Type.SMART)
    private val smartList2 = KarakeepList(id = "smart-2", name = "Recent", type = KarakeepList.Type.SMART)
    private val manualList = KarakeepList(id = "manual-1", name = "Read Later", type = KarakeepList.Type.MANUAL)

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
        every { listRepository.lists } returns MutableStateFlow(listOf(smartList1, smartList2, manualList))
        every { highlightRepository.getHighlightsCount(any()) } returns flowOf(0)
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<Long>()
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow<com.karakept.app.domain.action.UndoCompletedEvent>()
    }

    @After
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
        listIds: String = ""
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
        createdAt = System.currentTimeMillis(),
        isArchived = false,
        isStarred = false,
        listIds = listIds
    )

    @Test
    fun `moveBookmarkToList triggers syncBookmarksForList for SMART lists only`() = runTest(testDispatcher) {
        val bookmark = createBookmarkEntity(remoteId = 1L)
        val model = createMainScreenModel()
        advanceUntilIdle()

        model.moveBookmarkToList(bookmark, "manual-1")
        advanceUntilIdle()

        // Smart lists MUST be synced
        coVerify { bookmarkRepository.syncBookmarksForList(any(), "smart-1") }
        coVerify { bookmarkRepository.syncBookmarksForList(any(), "smart-2") }
        // Manual list MUST NOT be synced via syncSmartLists
        coVerify(exactly = 0) { bookmarkRepository.syncBookmarksForList(any(), "manual-1") }
    }

    @Test
    fun `removeBookmarkFromList triggers syncBookmarksForList for SMART lists only`() = runTest(testDispatcher) {
        val bookmark = createBookmarkEntity(remoteId = 2L, listIds = "manual-1")
        val model = createMainScreenModel()
        advanceUntilIdle()

        model.removeBookmarkFromList(bookmark, "manual-1")
        advanceUntilIdle()

        // Smart lists MUST be synced
        coVerify { bookmarkRepository.syncBookmarksForList(any(), "smart-1") }
        coVerify { bookmarkRepository.syncBookmarksForList(any(), "smart-2") }
        // Manual list MUST NOT be synced via syncSmartLists
        coVerify(exactly = 0) { bookmarkRepository.syncBookmarksForList(any(), "manual-1") }
    }

    @Test
    fun `moveBookmarkToList does not sync when no SMART lists exist`() = runTest(testDispatcher) {
        every { listRepository.lists } returns MutableStateFlow(listOf(manualList))

        val bookmark = createBookmarkEntity(remoteId = 3L)
        val model = createMainScreenModel()
        advanceUntilIdle()

        model.moveBookmarkToList(bookmark, "manual-1")
        advanceUntilIdle()

        // No syncBookmarksForList calls should be made at all
        coVerify(exactly = 0) { bookmarkRepository.syncBookmarksForList(any(), any()) }
    }

    @Test
    fun `executeScrollAction with ADD_TO_LIST triggers smart list sync transitively`() = runTest(testDispatcher) {
        val bookmark = createBookmarkEntity(remoteId = 4L)
        val config = com.karakept.app.data.model.CustomSwipeActionConfig(
            id = "cfg-1",
            type = com.karakept.app.data.model.CustomSwipeActionType.ADD_TO_LIST,
            listId = "manual-1"
        )
        val model = createMainScreenModel()
        advanceUntilIdle()

        model.executeScrollAction(bookmark, SwipeAction.ADD_TO_LIST, config)
        advanceUntilIdle()

        // Transitive: executeScrollAction → moveBookmarkToList → syncSmartLists
        coVerify { bookmarkRepository.syncBookmarksForList(any(), "smart-1") }
        coVerify { bookmarkRepository.syncBookmarksForList(any(), "smart-2") }
    }
}
