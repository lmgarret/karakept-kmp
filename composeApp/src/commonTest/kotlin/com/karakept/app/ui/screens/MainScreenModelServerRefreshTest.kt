package com.karakept.app.ui.screens

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
import kotlin.test.assertNull
import com.karakept.app.utils.TestAppDispatchers

/**
 * Regression tests for #173: re-authenticating replaces the server row (same id,
 * new apiKey), but MainScreenModel kept the stale Server snapshot captured at
 * startup, so every subsequent sync sent the old credentials and 401ed until
 * the app was restarted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelServerRefreshTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var serverRepository: ServerRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var serversFlow: MutableStateFlow<List<Server>>

    private val initialServer = Server(
        id = "server-1",
        url = "https://example.com",
        apiKey = "old-key",
        label = "Test"
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        serversFlow = MutableStateFlow(listOf(initialServer))

        serverRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)

        every { serverRepository.servers } returns serversFlow
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
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMainScreenModel(): MainScreenModel {
        val listRepository = mockk<ListRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkRepository>(relaxed = true)
        val bookmarkActionsRepository = mockk<BookmarkActionsRepository>(relaxed = true)
        val bookmarkActionController = mockk<BookmarkActionController>(relaxed = true)
        val highlightRepository = mockk<HighlightRepository>(relaxed = true)
        every { listRepository.lists } returns MutableStateFlow(emptyList())
        every { highlightRepository.getHighlightsCount(any()) } returns flowOf(0)
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<String>()
        every { bookmarkActionsRepository.aiCapabilities } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow<com.karakept.app.domain.action.UndoCompletedEvent>()
        every { bookmarkRepository.syncReports } returns MutableSharedFlow()
        every { bookmarkRepository.backgroundSyncCompleted } returns MutableSharedFlow()
        return MainScreenModel(
            serverRepository = serverRepository,
            bookmarkRepository = bookmarkRepository,
            bookmarkActionsRepository = bookmarkActionsRepository,
            settingsRepository = settingsRepository,
            listRepository = listRepository,
            bookmarkActionController = bookmarkActionController,
            snackbarManager = mockk<ActionSnackbarManager>(relaxed = true),
            highlightRepository = highlightRepository,
            appDispatchers = TestAppDispatchers(testDispatcher)
        )
    }

    @Test
    fun selectedServer_refreshesWhenApiKeyChanges() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()
        assertEquals("old-key", model.selectedServer.value?.apiKey)

        // Re-authentication replaces the server row: same id, new key
        serversFlow.value = listOf(initialServer.copy(apiKey = "new-key"))
        advanceUntilIdle()

        assertEquals("new-key", model.selectedServer.value?.apiKey, "Refreshed credentials must propagate without a restart")
    }

    @Test
    fun selectedServer_keepsSelectionAcrossUnrelatedServerListChanges() = runTest(testDispatcher) {
        val otherServer = Server(id = "server-2", url = "https://other.com", apiKey = "k2", label = "Other")
        val model = createMainScreenModel()
        advanceUntilIdle()

        serversFlow.value = listOf(otherServer, initialServer)
        advanceUntilIdle()

        assertEquals("server-1", model.selectedServer.value?.id, "Selection must stick to the same server id")
    }

    @Test
    fun selectedServer_clearedWhenAllServersRemoved() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        serversFlow.value = emptyList()
        advanceUntilIdle()

        assertNull(model.selectedServer.value)
    }

    @Test
    fun selectedServer_fallsBackWhenSelectedServerDeleted() = runTest(testDispatcher) {
        val otherServer = Server(id = "server-2", url = "https://other.com", apiKey = "k2", label = "Other")
        val model = createMainScreenModel()
        advanceUntilIdle()

        serversFlow.value = listOf(otherServer)
        advanceUntilIdle()

        assertEquals("server-2", model.selectedServer.value?.id)
    }
}
