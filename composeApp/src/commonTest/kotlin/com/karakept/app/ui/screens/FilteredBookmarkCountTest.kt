package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ReadFilter
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

/**
 * Tests for [MainScreenModel.filteredBookmarkCount] — the denominator the fast-scroll cursor maps
 * its thumb over.
 *
 * It has to describe the whole filtered view, not the window paged in so far: a denominator that
 * grows with the window walks the thumb back up the track every time a page lands (#273).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilteredBookmarkCountTest {

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
        highlightRepository = highlightRepository
    )

    private fun createBookmarkEntity(
        remoteId: Long,
        isArchived: Boolean = false,
        isStarred: Boolean = false,
        isRead: Boolean = false,
        tags: String = "",
        listIds: String = ""
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
        isStarred = isStarred,
        isRead = isRead,
        tags = tags,
        listIds = listIds
    )

    @Test
    fun `filteredBookmarkCount is zero when no bookmarks`() = runTest(testDispatcher) {
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())

        val model = createMainScreenModel()
        val job = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()

        assertEquals(0, model.filteredBookmarkCount.value)
        job.cancel()
    }

    @Test
    fun `filteredBookmarkCount counts the whole filtered view`() = runTest(testDispatcher) {
        // Far more than one page: the count must not describe the loaded window.
        val bookmarks = (1L..250L).map { createBookmarkEntity(it) } +
            (251L..260L).map { createBookmarkEntity(it, isArchived = true) }
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(bookmarks)

        val model = createMainScreenModel()
        val job = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()

        assertEquals(250, model.filteredBookmarkCount.value)
        job.cancel()
    }

    @Test
    fun `filteredBookmarkCount follows the active filter`() = runTest(testDispatcher) {
        val bookmarks = listOf(
            createBookmarkEntity(1, tags = "kotlin"),
            createBookmarkEntity(2, tags = "kotlin", isRead = true),
            createBookmarkEntity(3, tags = "swift"),
            createBookmarkEntity(4)
        )
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(bookmarks)

        val model = createMainScreenModel()
        val job = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()
        assertEquals(4, model.filteredBookmarkCount.value)

        model.applyFilter(FilterConfig(tags = listOf("kotlin")))
        advanceUntilIdle()
        assertEquals(2, model.filteredBookmarkCount.value)

        model.applyFilter(FilterConfig(tags = listOf("kotlin"), readFilter = ReadFilter.UNREAD))
        advanceUntilIdle()
        assertEquals(1, model.filteredBookmarkCount.value)

        job.cancel()
    }

    @Test
    fun `filteredBookmarkCount counts a list view by membership`() = runTest(testDispatcher) {
        val bookmarks = listOf(
            createBookmarkEntity(1, listIds = "list-1"),
            // A single-list view applies no status clause, so its archived members count too.
            createBookmarkEntity(2, listIds = "list-1", isArchived = true),
            createBookmarkEntity(3, listIds = "list-2")
        )
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(bookmarks)

        val model = createMainScreenModel()
        val job = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()

        model.applyFilter(
            FilterConfig(status = FilterStatus.ALL_INCLUDING_ARCHIVED, lists = listOf("list-1"))
        )
        advanceUntilIdle()

        assertEquals(2, model.filteredBookmarkCount.value)
        job.cancel()
    }

    @Test
    fun `filteredBookmarkCount takes the offline view's own count`() = runTest(testDispatcher) {
        // These rows carry no content — the offline condition is a column they were read without.
        every { bookmarkRepository.getBookmarks(any()) } returns
            flowOf((1L..5L).map { createBookmarkEntity(it) })
        every { bookmarkRepository.getOfflineBookmarkCount(any()) } returns flowOf(3)

        val model = createMainScreenModel()
        val job = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()

        model.applyFilter(FilterConfig(status = FilterStatus.OFFLINE))
        advanceUntilIdle()

        assertEquals(3, model.filteredBookmarkCount.value)
        job.cancel()
    }
}
