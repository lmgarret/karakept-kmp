package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SortOption
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Tests that the [FilterConfig.sort] option is propagated to [BookmarkRepository.getBookmarksPaged]
 * so that the database cursor starts in the correct order rather than sorting in memory after
 * pagination has already sliced the collection.
 *
 * The key invariant: when the user selects OLDEST or TITLE_AZ, the very first page returned by
 * the DB must contain the globally-oldest / alphabetically-first bookmarks, not just the first 20
 * in the DB's default createdAt-DESC order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class MainScreenModelPaginationSortingTest {

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
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<Long>()
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow<UndoCompletedEvent>()
        every { bookmarkRepository.syncProgress } returns MutableStateFlow(
            com.karakept.app.data.model.SyncProgress.Idle
        )
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = any(), limit = any(),
                sort = any(), listId = any()
            )
        } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeBookmark(id: Long, title: String, createdAt: Long = id) = BookmarkEntity(
        localId = id,
        remoteId = id,
        originalRemoteId = "remote-$id",
        serverId = "server-1",
        url = "https://example.com/$id",
        title = title,
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = createdAt,
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
     * On initialization the model uses the default filter (NEWEST), so the repository
     * must be called with sort = NEWEST to get the DB cursor pointing at newest-first.
     */
    @Test
    fun `NEWEST sort is passed to repository on initialization`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        coVerify(atLeast = 1) {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = any(), limit = any(),
                sort = SortOption.NEWEST, listId = any()
            )
        }
    }

    /**
     * When the user switches to OLDEST sort, the repository call must carry
     * sort = OLDEST so the DB cursor starts at the oldest bookmark rather than
     * sorting an already-paginated slice in memory.
     */
    @Test
    fun `OLDEST sort is forwarded to repository when filter changes`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        model.applyFilter(FilterConfig(sort = SortOption.OLDEST))
        advanceUntilIdle()

        coVerify(atLeast = 1) {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = any(), limit = any(),
                sort = SortOption.OLDEST, listId = any()
            )
        }
    }

    /**
     * When the user switches to TITLE_AZ sort, the repository call must carry
     * sort = TITLE_AZ so the DB cursor yields bookmarks in alphabetical order
     * from the very first page.
     */
    @Test
    fun `TITLE_AZ sort is forwarded to repository when filter changes`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        model.applyFilter(FilterConfig(sort = SortOption.TITLE_AZ))
        advanceUntilIdle()

        coVerify(atLeast = 1) {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = any(), limit = any(),
                sort = SortOption.TITLE_AZ, listId = any()
            )
        }
    }

    /**
     * Each change to the sort option must produce a repository call with the new
     * sort — including subsequent changes in the same session.
     */
    @Test
    fun `sort option change is reflected in every subsequent repository call`() =
        runTest(testDispatcher) {
            val model = createMainScreenModel()
            advanceUntilIdle()

            model.applyFilter(FilterConfig(sort = SortOption.TITLE_AZ))
            advanceUntilIdle()

            model.applyFilter(FilterConfig(sort = SortOption.READING_TIME_SHORT))
            advanceUntilIdle()

            coVerify(atLeast = 1) {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), offset = any(), limit = any(),
                    sort = SortOption.READING_TIME_SHORT, listId = any()
                )
            }
        }

    /**
     * Verifies that loadNextPage also forwards the current sort so that each
     * successive page continues from the right position in the sorted DB cursor.
     */
    @Test
    fun `subsequent pages are requested with the same sort as the first page`() =
        runTest(testDispatcher) {
            val pageSize = 20
            // Page 0: full page so the model considers more data available
            val page0 = (1..pageSize).map { makeBookmark(id = it.toLong(), title = "A-$it") }
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), offset = 0, limit = any(),
                    sort = any(), listId = any()
                )
            } returns page0
            // Page 1: partial page to signal DB exhaustion
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), offset = pageSize, limit = any(),
                    sort = any(), listId = any()
                )
            } returns listOf(makeBookmark(id = 99, title = "Z-1"))

            val model = createMainScreenModel()
            advanceUntilIdle()

            model.applyFilter(FilterConfig(sort = SortOption.TITLE_AZ))
            advanceUntilIdle()

            model.loadNextPage()
            advanceUntilIdle()

            // Both the initial load (offset=0) and the next page (offset=20) must use TITLE_AZ
            coVerify(atLeast = 2) {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), offset = any(), limit = any(),
                    sort = SortOption.TITLE_AZ, listId = any()
                )
            }
            assertEquals(pageSize + 1, model._accumulatedBookmarks.value.size)
        }
}
