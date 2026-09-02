package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
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
 * Regression tests for #274: the accumulated bookmark list feeds a LazyColumn keyed
 * on remoteId, so any duplicate remoteId crashes the app. Duplicates used to slip in
 * when a background sync inserted rows mid-pagination (page overlap via OFFSET drift)
 * or when undo re-insertions raced other transforms.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelAccumulatedListTest {

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
        every { settingsRepository.offlineMode } returns flowOf(true)
        every { settingsRepository.activeServerId } returns flowOf("server-1")
        every { settingsRepository.defaultListType } returns flowOf(DefaultListType.ALL_BOOKMARKS)
        every { settingsRepository.defaultListId } returns flowOf(null)
        every { settingsRepository.lastActiveFilterStatus } returns flowOf(null)
        every { settingsRepository.lastActiveFilterListId } returns flowOf(null)
        every { listRepository.lists } returns MutableStateFlow(emptyList())
        every { highlightRepository.getHighlightsCount(any()) } returns flowOf(0)
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<Long>()
        every { bookmarkActionsRepository.aiCapabilities } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow<com.karakept.app.domain.action.UndoCompletedEvent>()
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

    private fun bookmark(remoteId: Long) = BookmarkEntity(
        localId = remoteId,
        remoteId = remoteId,
        originalRemoteId = "remote-$remoteId",
        serverId = "server-1",
        url = "https://example.com/$remoteId",
        title = "Bookmark $remoteId",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 0L,
        isArchived = false,
        isStarred = false
    )

    private fun List<BookmarkEntity>.remoteIds() = map { it.remoteId }

    @Test
    fun updateAccumulatedBookmarks_dropsDuplicatesIntroducedByTransform() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        // Simulate a page overlap: the transform naively appends items already present
        model.updateAccumulatedBookmarks { listOf(bookmark(1), bookmark(2)) }
        model.updateAccumulatedBookmarks { current -> current + listOf(bookmark(2), bookmark(3)) }

        assertEquals(listOf(1L, 2L, 3L), model._accumulatedBookmarks.value.remoteIds())
    }

    @Test
    fun updateAccumulatedBookmarks_undoReinsertionOfPresentBookmarkStaysUnique() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        model.updateAccumulatedBookmarks { listOf(bookmark(1), bookmark(2)) }
        // Undo restores a bookmark at its original position even though a concurrent
        // sync already brought it back
        model.updateAccumulatedBookmarks { current ->
            val mutable = current.toMutableList()
            mutable.add(0, bookmark(2))
            mutable
        }

        assertEquals(listOf(2L, 1L), model._accumulatedBookmarks.value.remoteIds())
    }

    @Test
    fun updateAccumulatedBookmarks_concurrentAppendsNeverProduceDuplicates() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        // Many concurrent appenders re-adding overlapping windows (sync + pagination + undo)
        val jobs = (0 until 20).map { i ->
            launch {
                model.updateAccumulatedBookmarks { current ->
                    current + (0L..10L).map { bookmark(it + i) }
                }
            }
        }
        jobs.forEach { it.join() }

        val ids = model._accumulatedBookmarks.value.remoteIds()
        assertEquals(ids.toSet().size, ids.size, "Accumulated list must never contain duplicate remoteIds")
    }

    @Test
    fun bookmarksFlow_pendingAndAccumulatedNeverOverlap() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        val collected = mutableListOf<List<BookmarkEntity>>()
        val collector = launch { model.bookmarks.collect { collected.add(it) } }

        // A just-created bookmark can briefly exist in both pending and accumulated
        model._pendingBookmarks.value = listOf(bookmark(42))
        model.updateAccumulatedBookmarks { listOf(bookmark(42), bookmark(43)) }
        advanceUntilIdle()

        val latest = collected.last()
        assertEquals(listOf(42L, 43L), latest.remoteIds())
        collector.cancel()
    }

    /**
     * The loaded window is a snapshot of its rows, so a change written straight to the table
     * is invisible to it. The reading-progress pull writes the read flag that way — it clears
     * it below 100% — while the drawer's unread count reads the table live, so the count
     * reported bookmarks the list went on drawing as read (#333).
     */
    @Test
    fun bookmarkChangedEvent_refreshesTheRowTheListIsHolding() = runTest(testDispatcher) {
        val events = MutableSharedFlow<Long>(extraBufferCapacity = 4)
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns events

        val model = createMainScreenModel()
        advanceUntilIdle()
        model.updateAccumulatedBookmarks { listOf(bookmark(42).copy(isRead = true), bookmark(43)) }

        // The pull takes the server's 0% and clears the read flag in the table.
        io.mockk.coEvery { bookmarkRepository.getBookmarkByRemoteId(42L, "server-1") } returns
            bookmark(42).copy(isRead = false, readingProgress = 0f)
        events.emit(42L)
        advanceUntilIdle()

        assertEquals(
            false,
            model._accumulatedBookmarks.value.first { it.remoteId == 42L }.isRead,
            "the list must follow the table, or it shows a read row the count calls unread"
        )
    }

    @Test
    fun bookmarkChangedEvent_forARowOutsideTheWindow_isNotReadBack() = runTest(testDispatcher) {
        // A backfill notifies for rows across the whole library; only the window needs re-reading.
        val events = MutableSharedFlow<Long>(extraBufferCapacity = 4)
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns events

        val model = createMainScreenModel()
        advanceUntilIdle()
        model.updateAccumulatedBookmarks { listOf(bookmark(42)) }

        events.emit(99L)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 0) { bookmarkRepository.getBookmarkByRemoteId(99L, any()) }
        assertEquals(listOf(42L), model._accumulatedBookmarks.value.remoteIds())
    }
}
