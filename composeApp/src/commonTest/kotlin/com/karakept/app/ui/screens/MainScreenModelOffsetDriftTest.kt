package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
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
        remoteId = id,
        originalRemoteId = "remote-$id",
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
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<Long>()
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
        highlightRepository = highlightRepository
    )

    @Test
    fun aPageOfEntirelyNewRowsIsAppendedWithoutRereadingTheWindow() = runTest(testDispatcher) {
        // The undisturbed case: no repeats, so no shift, and the walk simply appends.
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = 0, limit = 20, sort = any(), listId = any()
            )
        } returns rows(1L..20L)
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = 20, limit = 20, sort = any(), listId = any()
            )
        } returns rows(21L..40L)

        val model = createMainScreenModel()
        advanceUntilIdle()

        model.loadNextPage()
        advanceUntilIdle()

        assertEquals((1L..40L).toList(), model._accumulatedBookmarks.value.map { it.remoteId }.sorted())
        assertEquals(1, model._currentPage.value)
    }

    @Test
    fun aReReadHoldingFewerRowsThanTheWindowIsNotPublished() = runTest(testDispatcher) {
        // The walk has just appended what it found, so a re-read that comes back smaller cannot
        // be a heal — publishing it would drop rows that are legitimately loaded. This is the
        // shape a delete lands in mid-walk: the re-read sees the table after it, the window
        // still holds what the walk read before it.
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = 0, limit = 20, sort = any(), listId = any()
            )
        } returns rows(1L..20L)
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = 20, limit = 20, sort = any(), listId = any()
            )
        } returns rows(21L..30L)
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), offset = 0, limit = 40, sort = any(), listId = any()
            )
        } returns rows(1L..25L)

        val model = createMainScreenModel()
        advanceUntilIdle()

        model.loadNextPage()
        advanceUntilIdle()

        assertEquals(
            (1L..30L).toList(),
            model._accumulatedBookmarks.value.map { it.remoteId }.sorted(),
            "a smaller re-read must not replace the rows the walk already loaded"
        )
    }

    @Test
    fun aDbExhaustedWalkReReadsTheWindowRecoveringRowsACommitDuringTheWalkDisplaced() =
        runTest(testDispatcher) {
            // Page 0 loads 20 rows. The walk at page 1 reads to the end of the table: 4 rows
            // survive, dbExhausted is true, and _hasMoreItems is set to false. A sync committed
            // 4 more rows above the read position during the walk — they shifted everything
            // below, and the walk's OFFSET-based read never reached them. Without the re-read
            // the window would sit at 24 rows claiming to be complete, hiding the 4 the sync
            // added. The inline re-read on dbExhausted reads the whole window as one query
            // against a consistent snapshot, recovering all 28 (#333).
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), offset = 0, limit = 20, sort = any(), listId = any()
                )
            } returns rows(1L..20L)
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), offset = 20, limit = 20, sort = any(), listId = any()
                )
            } returns rows(21L..24L)
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), offset = 0, limit = 40, sort = any(), listId = any()
                )
            } returns rows(1L..28L)

            val model = createMainScreenModel()
            advanceUntilIdle()
            assertEquals(20, model._accumulatedBookmarks.value.size)

            model.loadNextPage()
            advanceUntilIdle()

            assertEquals(
                (1L..28L).toList(),
                model._accumulatedBookmarks.value.map { it.remoteId }.sorted(),
                "the dbExhausted re-read must recover rows a mid-walk commit pushed past the walk"
            )
            assertEquals(false, model._hasMoreItems.value, "the table is exhausted after the re-read")
        }
}
