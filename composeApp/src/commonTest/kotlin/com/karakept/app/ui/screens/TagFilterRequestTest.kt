package com.karakept.app.ui.screens

import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.ListSettings
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
import com.karakept.app.domain.action.TagFilterRequests
import com.karakept.app.domain.action.UndoCompletedEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

/**
 * The reader hands a tapped tag back to the bookmark list through [TagFilterRequests].
 *
 * It used to reach for the list's `MainScreenModel` directly, which built a *second* model —
 * outside any `ViewModelStore`, so `onCleared()` never ran and every repository collector it
 * started in `init` kept refreshing the window for the rest of the process. One leaked per
 * bookmark opened, which is what the #333 logs caught: seventeen models answering one sync.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagFilterRequestTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var serverRepository: ServerRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var bookmarkActionsRepository: BookmarkActionsRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var listRepository: ListRepository
    private lateinit var bookmarkActionController: BookmarkActionController
    private lateinit var snackbarManager: ActionSnackbarManager
    private lateinit var highlightRepository: HighlightRepository
    private lateinit var tagFilterRequests: TagFilterRequests

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
        tagFilterRequests = TagFilterRequests()

        every { serverRepository.servers } returns flowOf(listOf(fakeServer))
        every { settingsRepository.allListSettings } returns flowOf(emptyMap())
        every { settingsRepository.getListSettings(any()) } returns flowOf(ListSettings())
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
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<String>()
        every { bookmarkActionsRepository.aiCapabilities } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow<UndoCompletedEvent>()
        every { bookmarkRepository.syncReports } returns MutableSharedFlow()
        every { bookmarkRepository.backgroundSyncCompleted } returns MutableSharedFlow()
        every { bookmarkRepository.syncProgress } returns MutableStateFlow(
            com.karakept.app.data.model.SyncProgress.Idle
        )
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), after = any(), limit = any(),
                sort = any(), listId = any()
            )
        } returns emptyList()
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
        tagFilterRequests = tagFilterRequests
    )

    @Test
    fun requestFromAnotherScreen_appliesTheTagFilterToTheListOnScreen() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        tagFilterRequests.request("kotlin", sourceBookmarkId = 42L)
        advanceUntilIdle()

        assertEquals(listOf("kotlin"), model.currentFilter.value.tags)
        // The source bookmark travels with the request so the list can scroll back to it.
        assertEquals(42L, model.consumeTagFilterSource())
    }

    @Test
    fun noRequest_leavesTheFilterAlone() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        assertEquals(emptyList(), model.currentFilter.value.tags)
    }
}
