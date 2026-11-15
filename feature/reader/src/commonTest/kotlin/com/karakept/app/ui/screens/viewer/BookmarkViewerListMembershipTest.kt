package com.karakept.app.ui.screens.viewer

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.AssetRepository
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.domain.ListMembershipChange
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.testing.testBookmark
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkViewerListMembershipTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var assetRepository: AssetRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var bookmarkActionsRepository: BookmarkActionsRepository
    private lateinit var serverRepository: ServerRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var listRepository: ListRepository
    private lateinit var bookmarkActionController: BookmarkActionController
    private lateinit var highlightRepository: HighlightRepository
    private lateinit var snackbarManager: ActionSnackbarManager

    private val testServer = Server(
        id = "server-1",
        url = "https://karakeep.example.com",
        apiKey = "key",
        label = "Test"
    )

    private val bookmark = testBookmark(1L, listIds = "a,smart")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        assetRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        bookmarkActionsRepository = mockk(relaxed = true)
        serverRepository = mockk(relaxed = true)
        bookmarkRepository = mockk(relaxed = true)
        listRepository = mockk(relaxed = true)
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
        every { bookmarkRepository.observeBookmark(any()) } returns flowOf(bookmark)
        coEvery { assetRepository.assetsFor(any()) } returns emptyList()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createScreenModel() = BookmarkViewerScreenModel(
        assetRepository = assetRepository,
        settingsRepository = settingsRepository,
        bookmarkActionsRepository = bookmarkActionsRepository,
        serverRepository = serverRepository,
        bookmarkRepository = bookmarkRepository,
        listRepository = listRepository,
        bookmarkActionController = bookmarkActionController,
        highlightRepository = highlightRepository,
        snackbarManager = snackbarManager
    )

    @Test
    fun `an edit adds and removes only the lists it names`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        screenModel.updateBookmarkLists(bookmark, ListMembershipChange(added = setOf("b"), removed = setOf("a")))
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkActionsRepository.addToList(bookmark.remoteId, bookmark.serverId, "b") }
        coVerify(exactly = 1) { bookmarkActionsRepository.removeFromList(bookmark.remoteId, bookmark.serverId, "a") }
        coVerify(exactly = 0) { bookmarkActionsRepository.removeFromList(any(), any(), "smart") }
    }

    @Test
    fun `lists read as not loaded until the fetch lands`() = runTest(testDispatcher) {
        val fetched = listOf(KarakeepList(id = "a", name = "Read Later", type = KarakeepList.Type.MANUAL))
        coEvery { listRepository.fetchLists(testServer) } returns fetched
        val screenModel = createScreenModel()
        assertNull(screenModel.lists.value)

        screenModel.loadLists(testServer)
        advanceUntilIdle()

        assertEquals(fetched, screenModel.lists.value)
    }
}
