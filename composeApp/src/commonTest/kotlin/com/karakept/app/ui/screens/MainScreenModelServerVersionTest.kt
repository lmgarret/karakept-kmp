package com.karakept.app.ui.screens

import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.ServerVersionRepository
import com.karakept.app.domain.ServerCompatibility
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
import io.mockk.coVerify
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
import com.karakept.app.utils.TestAppDispatchers

/** The non-blocking startup warning for a server older than the app's minimum. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelServerVersionTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var serverRepository: ServerRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var serversFlow: MutableStateFlow<List<Server>>
    private val remoteDataSource: RemoteDataSource = mockk()
    private val snackbarManager: ActionSnackbarManager = mockk(relaxed = true)
    private lateinit var serverVersionRepository: ServerVersionRepository

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
        serverVersionRepository = ServerVersionRepository(remoteDataSource, TestAppDispatchers(testDispatcher))

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
            snackbarManager = snackbarManager,
            highlightRepository = highlightRepository,
            appDispatchers = TestAppDispatchers(testDispatcher),
            serverVersionRepository = serverVersionRepository
        )
    }

    @Test
    fun outdatedServer_warnsOnce() = runTest(testDispatcher) {
        coEvery { remoteDataSource.fetchServerVersion(initialServer.url) } returns "0.1.0"

        createMainScreenModel()
        advanceUntilIdle()
        // The list screen is recreated, e.g. after returning from settings.
        createMainScreenModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { snackbarManager.showSnackbar(match { "0.1.0" in it }, any()) }
        assertEquals(
            ServerCompatibility.OUTDATED,
            serverVersionRepository.versions.value[initialServer.id]?.compatibility
        )
    }

    @Test
    fun supportedServer_doesNotWarn() = runTest(testDispatcher) {
        coEvery { remoteDataSource.fetchServerVersion(initialServer.url) } returns "99.0.0"

        createMainScreenModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { snackbarManager.showSnackbar(any(), any()) }
    }

    @Test
    fun unreachableServer_doesNotWarn() = runTest(testDispatcher) {
        coEvery { remoteDataSource.fetchServerVersion(initialServer.url) } throws ApiException("offline")

        createMainScreenModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { snackbarManager.showSnackbar(any(), any()) }
    }
}
