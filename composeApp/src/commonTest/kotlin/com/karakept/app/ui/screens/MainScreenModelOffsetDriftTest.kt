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
import com.karakept.app.utils.TestAppDispatchers

/**
 * The forward walk pages by OFFSET, and a sync commits rows while the user scrolls. Rows that
 * sort above the read position shift everything below them down, so the next read starts that
 * many rows back: the page re-returns rows the window already holds, and an equal number of rows
 * are pushed *past* the read position, where a walk that only moves forward never returns for
 * them. The window is then short of the table with `_hasMoreItems` false, so scrolling cannot
 * recover anything (#333) — the missing rows being the freshly synced, still-unread ones the
 * user went looking for.
 *
 * The walk therefore re-reads the whole window as one query once it reaches the end of the
 * table, which is the last point at which a short window still has a way back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelOffsetDriftTest {

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

    /** Pages 0..1, the window the walk leaves behind after one loadNextPage. */
    private val window = PAGE_SIZE * 2

    /** The cursor a walk holds once it has read up to and including bookmark [id]. */
    private fun after(id: Long) = BookmarkCursor.of(bookmark(id))

    private fun stubPage(after: BookmarkCursor?, limit: Int, returns: List<BookmarkEntity>) {
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), limit = limit, after = after,
                sort = any(), listId = any()
            )
        } returns returns
    }

    @Test
    fun aPageOfEntirelyNewRowsIsAppendedWithoutRereadingTheWindow() = runTest(testDispatcher) {
        // The undisturbed case: the second read comes back full, so the table has not ended and
        // there is nothing to re-read — the walk simply appends.
        stubPage(after = null, limit = page, returns = rows(1L..page.toLong()))
        stubPage(after = after(page.toLong()), limit = page, returns = rows((page + 1L)..window.toLong()))

        val model = createMainScreenModel()
        advanceUntilIdle()

        model.loadNextPage()
        advanceUntilIdle()

        assertEquals(
            (1L..window.toLong()).toList(),
            model._accumulatedBookmarks.value.map { it.remoteId.substringAfter('-').toLong() }.sorted()
        )
        assertEquals(1, model._currentPage.value)
    }

    @Test
    fun aReReadHoldingFewerRowsThanTheWindowIsNotPublished() = runTest(testDispatcher) {
        // The walk has just appended what it found, so a re-read that comes back smaller cannot
        // be a heal — publishing it would drop rows that are legitimately loaded. This is the
        // shape a delete lands in mid-walk: the re-read sees the table after it, the window
        // still holds what the walk read before it.
        val heldByWalk = page + 10L
        stubPage(after = null, limit = page, returns = rows(1L..page.toLong()))
        stubPage(after = after(page.toLong()), limit = page, returns = rows((page + 1L)..heldByWalk))
        stubPage(after = null, limit = window, returns = rows(1L..(page + 5L)))

        val model = createMainScreenModel()
        advanceUntilIdle()

        model.loadNextPage()
        advanceUntilIdle()

        assertEquals(
            (1L..heldByWalk).toList(),
            model._accumulatedBookmarks.value.map { it.remoteId.substringAfter('-').toLong() }.sorted(),
            "a smaller re-read must not replace the rows the walk already loaded"
        )
    }

    @Test
    fun aDbExhaustedWalkReReadsTheWindowRecoveringRowsACommitDuringTheWalkDisplaced() =
        runTest(testDispatcher) {
            // The second read returns a short page, so the walk concludes the table has ended and
            // sets _hasMoreItems false. But a sync committed rows above the read position while it
            // ran: they shifted everything below, and the OFFSET-based read never reached the ones
            // the shift displaced past it. Without the re-read the window would sit at what the
            // walk saw, claiming to be complete and hiding what the sync added. Re-reading the
            // window as one query against a consistent snapshot recovers them (#333).
            val seenByWalk = page + 4L
            val actuallyInTable = page + 8L
            stubPage(after = null, limit = page, returns = rows(1L..page.toLong()))
            stubPage(after = after(page.toLong()), limit = page, returns = rows((page + 1L)..seenByWalk))
            stubPage(after = null, limit = window, returns = rows(1L..actuallyInTable))

            val model = createMainScreenModel()
            advanceUntilIdle()
            assertEquals(page, model._accumulatedBookmarks.value.size)

            model.loadNextPage()
            advanceUntilIdle()

            assertEquals(
                (1L..actuallyInTable).toList(),
                model._accumulatedBookmarks.value.map { it.remoteId.substringAfter('-').toLong() }.sorted(),
                "the dbExhausted re-read must recover rows a mid-walk commit pushed past the walk"
            )
            assertEquals(false, model._hasMoreItems.value, "the table is exhausted after the re-read")
        }

    @Test
    fun aFullWindowDoesNotReopenPagingTheWalkAlreadyClosed() = runTest(testDispatcher) {
        // The walk pages to the end of the table and finds nothing, so paging is closed. The
        // re-read that follows then comes back holding a *full* window — which says only that
        // the window is full, never that there is anything past it.
        //
        // Deciding hasMoreItems from that reopened paging every time, and the list has few
        // enough rows that the load-more trigger fires at once: the walk runs again, reaches
        // the same conclusion, and the re-read reopens it again. The trace behind this test
        // showed thirty-eight pages walked roughly four times a second, indefinitely.
        stubPage(after = null, limit = page, returns = rows(1L..page.toLong()))
        stubPage(after = after(page.toLong()), limit = page, returns = emptyList())
        // Same size as the window, so `reachedEnd` is false — the shape that reopened paging.
        stubPage(after = null, limit = page, returns = rows(1L..page.toLong()))

        val model = createMainScreenModel()
        advanceUntilIdle()

        model.loadNextPage()
        advanceUntilIdle()

        assertEquals(
            false,
            model._hasMoreItems.value,
            "the walk found nothing past the window, so a full window must not reopen paging"
        )
    }
}
