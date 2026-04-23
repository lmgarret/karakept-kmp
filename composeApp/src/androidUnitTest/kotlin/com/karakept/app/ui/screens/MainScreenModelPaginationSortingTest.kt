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
 * Regression tests for the bug where sorting was applied per-page rather than
 * across the entire accumulated bookmark list.
 *
 * When the DB returns bookmarks in creation-date order and the user selects a
 * different sort (e.g. TITLE_AZ or OLDEST), each loaded page was sorted in
 * isolation. Items from page N+1 were appended after page N without a global
 * re-sort, so the overall list was only ordered within each page window.
 *
 * The fix re-sorts the full accumulated list after every page append.
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
     * Page 0 returns 20 bookmarks titled "Z-01"…"Z-20" (full page → more pages exist).
     * Page 1 returns 3 bookmarks titled "A-01"…"A-03" (partial page → DB exhausted).
     *
     * Without the fix the accumulated list would be [Z-01…Z-20, A-01…A-03].
     * With the fix it must be globally sorted: [A-01…A-03, Z-01…Z-20].
     */
    @Test
    fun `titleAz sort is applied globally across page boundaries`() = runTest(testDispatcher) {
        val pageSize = 20
        val page0 = (1..pageSize).map { makeBookmark(id = it.toLong(), title = "Z-%02d".format(it)) }
        val page1 = (1..3).map { makeBookmark(id = (pageSize + it).toLong(), title = "A-%02d".format(it)) }

        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = 0, limit = any(), listId = any()
            )
        } returns page0
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = pageSize, limit = any(), listId = any()
            )
        } returns page1

        val model = createMainScreenModel()
        advanceUntilIdle()

        model.applyFilter(FilterConfig(sort = SortOption.TITLE_AZ))
        advanceUntilIdle()

        model.loadNextPage()
        advanceUntilIdle()

        val titles = model._accumulatedBookmarks.value.map { it.title }
        assertEquals(
            "A-01", titles.first(),
            "Expected globally sorted list to start with 'A-01', but got: $titles"
        )
        assertEquals(
            "Z-20", titles.last(),
            "Expected globally sorted list to end with 'Z-20', but got: $titles"
        )
        assertEquals(pageSize + 3, titles.size)
    }

    /**
     * Page 0 returns 20 bookmarks with createdAt in range 1001–1020 (newer).
     * Page 1 returns 3 bookmarks with createdAt 1, 2, 3 (older).
     *
     * Without the fix the accumulated list stays [newer…, older…].
     * With the fix and OLDEST sort the list must be globally ordered oldest-first.
     */
    @Test
    fun `oldest sort is applied globally across page boundaries`() = runTest(testDispatcher) {
        val pageSize = 20
        val page0 = (1..pageSize).map {
            makeBookmark(id = it.toLong(), title = "Bookmark $it", createdAt = 1000L + it)
        }
        val page1 = (1..3).map {
            makeBookmark(id = (pageSize + it).toLong(), title = "Old $it", createdAt = it.toLong())
        }

        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = 0, limit = any(), listId = any()
            )
        } returns page0
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = pageSize, limit = any(), listId = any()
            )
        } returns page1

        val model = createMainScreenModel()
        advanceUntilIdle()

        model.applyFilter(FilterConfig(sort = SortOption.OLDEST))
        advanceUntilIdle()

        model.loadNextPage()
        advanceUntilIdle()

        val timestamps = model._accumulatedBookmarks.value.map { it.createdAt }
        assertEquals(
            1L, timestamps.first(),
            "Expected oldest bookmark first, but got timestamps: $timestamps"
        )
        assertEquals(
            1020L, timestamps.last(),
            "Expected newest bookmark last, but got timestamps: $timestamps"
        )
        assertEquals(pageSize + 3, timestamps.size)
    }

    /**
     * Sanity check: the default NEWEST sort (createdAt DESC) must also be
     * globally consistent after a page append, even though it matches the DB's
     * own ordering.
     */
    @Test
    fun `newest sort is consistent across page boundaries`() = runTest(testDispatcher) {
        val pageSize = 20
        // Page 0: createdAt 21..40 (newer, as if DB returned them first)
        val page0 = (1..pageSize).map {
            makeBookmark(id = it.toLong(), title = "Bookmark $it", createdAt = (20 + it).toLong())
        }
        // Page 1: createdAt 1..3 (older, second DB page)
        val page1 = (1..3).map {
            makeBookmark(id = (pageSize + it).toLong(), title = "Old $it", createdAt = it.toLong())
        }

        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = 0, limit = any(), listId = any()
            )
        } returns page0
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = pageSize, limit = any(), listId = any()
            )
        } returns page1

        val model = createMainScreenModel()
        advanceUntilIdle()

        model.loadNextPage()
        advanceUntilIdle()

        val timestamps = model._accumulatedBookmarks.value.map { it.createdAt }
        assertEquals(
            40L, timestamps.first(),
            "Expected newest bookmark first, but got timestamps: $timestamps"
        )
        assertEquals(
            1L, timestamps.last(),
            "Expected oldest bookmark last, but got timestamps: $timestamps"
        )
    }
}
