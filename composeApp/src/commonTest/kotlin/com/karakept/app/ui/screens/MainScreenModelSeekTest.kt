package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkCursor
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.karakept.app.utils.TestAppDispatchers

/**
 * [MainScreenModel.loadThroughIndex] — the seek behind the fast-scroll cursor.
 *
 * Dragging the thumb to the bottom of a large list asks for a row hundreds of pages past the
 * loaded window. Walking there a page at a time is what the user sees as the list scrolling
 * through everything on the way, so the window is instead read out to the target in a single
 * query and the list moves once, when it lands (#273).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelSeekTest {

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

    /** `createdAt` descends with the id so NEWEST order is simply id ascending. */
    private fun bookmark(id: Long) = BookmarkEntity(
        localId = id,
        remoteId = "remote-$id",
        serverId = "server-1",
        url = "https://example.com/$id",
        title = "Bookmark $id",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 100_000L - id,
        isArchived = false,
        isStarred = false
    )

    private fun rows(range: LongRange) = range.map { bookmark(it) }

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
        appDispatchers = TestAppDispatchers(testDispatcher)
    )

    /** One DB read. Derived so tuning [PAGE_SIZE] does not mean rewriting every fixture. */
    private val page = PAGE_SIZE

    /** The table the stubbed query pages over, newest first. */
    private val table = rows(1L..2_000L)

    /** Every `limit` the model asked the repository for, in order. */
    private val reads = mutableListOf<Int>()

    /** Pages [table] by cursor, exactly as the real query does. */
    private fun stubTable(rows: List<BookmarkEntity> = table) {
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), limit = any(), after = any(),
                sort = any(), listId = any()
            )
        } answers {
            val limit = arg<Int>(2)
            val after = arg<BookmarkCursor?>(3)
            reads += limit
            val start = if (after == null) 0 else rows.indexOfFirst { BookmarkCursor.of(it) == after } + 1
            rows.drop(start).take(limit)
        }
    }

    private fun loadedIds(model: MainScreenModel) =
        model._accumulatedBookmarks.value.map { it.remoteId.substringAfter('-').toLong() }

    @Test
    fun aSeekReadsTheWindowOutToTheTargetInOneQuery() = runTest(testDispatcher) {
        stubTable()
        val model = createMainScreenModel()
        advanceUntilIdle()
        assertEquals(page, model._accumulatedBookmarks.value.size)
        reads.clear()

        // The thumb dragged to row 800 of a 2000-row list.
        model.loadThroughIndex(800)
        advanceUntilIdle()

        val expectedWindow = (seekWindowPage(800, page, 0, page) + 1) * page
        assertEquals(listOf(expectedWindow), reads, "a seek is one read, not a page-at-a-time walk")
        assertTrue(
            model._accumulatedBookmarks.value.size > 800,
            "the window has to hold the row the cursor asked for"
        )
        assertEquals((1L..expectedWindow.toLong()).toList(), loadedIds(model))
    }

    @Test
    fun aSeekLeavesPagingWhereTheWindowNowEnds() = runTest(testDispatcher) {
        stubTable()
        val model = createMainScreenModel()
        advanceUntilIdle()

        model.loadThroughIndex(800)
        advanceUntilIdle()
        reads.clear()

        // Scrolling on from the seeked window resumes after its last row, without re-reading it.
        model.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(page), reads)
        assertEquals(
            (1L..(seekWindowPage(800, page, 0, page) + 1).toLong() * page + page).toList(),
            loadedIds(model)
        )
        assertTrue(model.hasMoreItems.value)
    }

    @Test
    fun aSeekPastTheEndOfTheTableStopsAtIt() = runTest(testDispatcher) {
        val shortTable = rows(1L..120L)
        stubTable(shortTable)
        val model = createMainScreenModel()
        advanceUntilIdle()
        reads.clear()

        model.loadThroughIndex(800)
        advanceUntilIdle()

        assertEquals(shortTable.size, model._accumulatedBookmarks.value.size)
        assertFalse(model.hasMoreItems.value, "the read could not fill the window, so the table ended")
        assertEquals(1, reads.size)
    }

    @Test
    fun aSeekIntoTheLoadedWindowReadsNothing() = runTest(testDispatcher) {
        stubTable()
        val model = createMainScreenModel()
        advanceUntilIdle()
        reads.clear()

        model.loadThroughIndex(page - 2)
        advanceUntilIdle()

        assertEquals(emptyList(), reads)
        assertEquals(page, model._accumulatedBookmarks.value.size)
    }

    @Test
    fun aSeekOnAnExhaustedListReadsNothing() = runTest(testDispatcher) {
        stubTable(rows(1L..20L))
        val model = createMainScreenModel()
        advanceUntilIdle()
        assertFalse(model.hasMoreItems.value)
        reads.clear()

        model.loadThroughIndex(800)
        advanceUntilIdle()

        assertEquals(emptyList(), reads)
    }
}
