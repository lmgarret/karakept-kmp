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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<Long>()
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow<UndoCompletedEvent>()
        every { bookmarkRepository.syncReports } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { bookmarkRepository.backgroundSyncCompleted } returns kotlinx.coroutines.flow.MutableSharedFlow()
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

    @AfterTest
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

    @Test
    fun `subsequent pages are requested with the same sort as the first page`() =
        runTest(testDispatcher) {
            val pageSize = 20
            val page0 = (1..pageSize).map { makeBookmark(id = it.toLong(), title = "A-$it") }
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), offset = 0, limit = any(),
                    sort = any(), listId = any()
                )
            } returns page0
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

            coVerify(atLeast = 2) {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), offset = any(), limit = any(),
                    sort = SortOption.TITLE_AZ, listId = any()
                )
            }
            assertEquals(pageSize + 1, model._accumulatedBookmarks.value.size)
        }

    @Test
    fun `refreshLoadedPagesInPlace keeps the loaded window and does not bump the list version`() =
        runTest(testDispatcher) {
            // A table of 50 rows, served the way the DAO serves it: sliced by offset/limit.
            val table = (1..50).map { makeBookmark(id = it.toLong(), title = "b$it") }
            coEvery {
                bookmarkRepository.getBookmarksPaged(server = any(), status = any(), offset = any(), limit = any(), sort = any(), listId = any())
            } answers {
                table.drop(arg<Int>(2)).take(arg<Int>(3))
            }

            val model = createMainScreenModel()
            advanceUntilIdle()
            // Simulate the user having scrolled through pages 0..2.
            model._currentPage.value = 2
            val versionBefore = model.bookmarkListVersion.value

            model.refreshLoadedPagesInPlace(fakeServer, FilterConfig())
            advanceUntilIdle()

            // Window preserved (all 50 items reloaded), not shrunk to page 0.
            assertEquals(50, model._accumulatedBookmarks.value.size)
            // Version NOT bumped, so PreserveListScrollAnchor keeps the viewport pinned
            // instead of jumping to the top (no blink).
            assertEquals(versionBefore, model.bookmarkListVersion.value)
            // Partial final page → DB exhausted.
            assertEquals(false, model.hasMoreItems.value)
        }

    @Test
    fun `refreshLoadedPagesInPlace counts newly synced bookmarks for the N-new pill`() =
        runTest(testDispatcher) {
            val model = createMainScreenModel()
            advanceUntilIdle()
            // Existing loaded page 0 holds two bookmarks; no pending "new" indicator.
            model._accumulatedBookmarks.value = listOf(makeBookmark(1, "b1"), makeBookmark(2, "b2"))
            model._currentPage.value = 0
            model.clearNewBookmarksAbove()

            // Sync prepends two new bookmarks (ids 10, 11) to the top of page 0.
            val page0 = listOf(
                makeBookmark(10, "new1"), makeBookmark(11, "new2"),
                makeBookmark(1, "b1"), makeBookmark(2, "b2")
            )
            coEvery {
                bookmarkRepository.getBookmarksPaged(server = any(), status = any(), offset = 0, limit = any(), sort = any(), listId = any())
            } returns page0

            model.refreshLoadedPagesInPlace(fakeServer, FilterConfig())
            advanceUntilIdle()

            assertEquals(2, model.newBookmarksAbove.value)

            // Marking the new top row as seen empties the pill. The count is derived from the
            // window rather than held as a counter, so it settles on the next dispatch.
            model.clearNewBookmarksAbove()
            advanceUntilIdle()
            assertEquals(0, model.newBookmarksAbove.value)
        }

    @Test
    fun `N-new pill does not double-count a bookmark that re-enters the loaded window`() =
        runTest(testDispatcher) {
            val model = createMainScreenModel()
            advanceUntilIdle()
            model._accumulatedBookmarks.value =
                listOf(makeBookmark(1, "b1"), makeBookmark(2, "b2"), makeBookmark(3, "b3"))
            model._currentPage.value = 0
            model.clearNewBookmarksAbove()
            advanceUntilIdle()

            // A sync prepends one row; the window is a fixed page range, so b3 falls off the end.
            coEvery {
                bookmarkRepository.getBookmarksPaged(server = any(), status = any(), offset = 0, limit = any(), sort = any(), listId = any())
            } returns listOf(makeBookmark(10, "new1"), makeBookmark(1, "b1"), makeBookmark(2, "b2"))
            model.refreshLoadedPagesInPlace(fakeServer, FilterConfig())
            advanceUntilIdle()
            assertEquals(1, model.newBookmarksAbove.value)

            // The prepended row then leaves, pulling b3 back into the window. b3 is not new —
            // the user saw it before — and only rows above the seen row may be counted.
            coEvery {
                bookmarkRepository.getBookmarksPaged(server = any(), status = any(), offset = 0, limit = any(), sort = any(), listId = any())
            } returns listOf(makeBookmark(1, "b1"), makeBookmark(2, "b2"), makeBookmark(3, "b3"))
            model.refreshLoadedPagesInPlace(fakeServer, FilterConfig())
            advanceUntilIdle()

            assertEquals(0, model.newBookmarksAbove.value)
        }
}
