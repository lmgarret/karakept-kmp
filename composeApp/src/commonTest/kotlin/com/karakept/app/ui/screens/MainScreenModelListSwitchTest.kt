package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkCursor
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.karakept.app.utils.TestAppDispatchers

/**
 * Regression tests for the rendering glitches seen when switching between lists quickly:
 * items from the previous list interleaved with the new one, and the previous list staying
 * on screen under the new list's title.
 *
 * The common cause is that `_currentFilter` flips synchronously when the user taps a list
 * while the reload it triggers runs on an observer coroutine — so for a short window the
 * pagination state on screen belongs to a different view than the one being requested.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelListSwitchTest {

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

    private val listAFilter = FilterConfig(lists = listOf("list-a"))
    private val listBFilter = FilterConfig(lists = listOf("list-b"))

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
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), after = any(), limit = any(),
                sort = any(), listId = any()
            )
        } returns emptyList()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeBookmark(id: Long) = BookmarkEntity(
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
        createdAt = id,
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
        highlightRepository = highlightRepository,
        appDispatchers = TestAppDispatchers(testDispatcher)
    )

    /** Stub a full first page so the model has a loaded window to append to. */
    private fun stubFullFirstPage(): List<BookmarkEntity> {
        val page0 = (1L..PAGE_SIZE.toLong()).map { makeBookmark(it) }
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), after = null, limit = any(),
                sort = any(), listId = any()
            )
        } returns page0
        return page0
    }

    @Test
    fun `loadNextPage does not append a page into a window loaded for another list`() =
        runTest(testDispatcher) {
            val page0 = stubFullFirstPage()
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(),
                    after = BookmarkCursor.of(page0.last()), limit = any(),
                    sort = any(), listId = any()
                )
            } returns listOf(makeBookmark(99))

            val model = createMainScreenModel()
            advanceUntilIdle()
            assertEquals(page0.size, model._accumulatedBookmarks.value.size)

            // The user taps another list: _currentFilter flips now, the reload it triggers
            // has not run yet, so the loaded window still belongs to the previous view.
            model._currentFilter.value = listBFilter

            model.loadNextPage()

            // No fetch is even started — otherwise its page would be appended below the
            // previous list's items and both lists would render in the same LazyColumn.
            assertEquals(false, model.isLoadingMore.value)
            coVerify(exactly = 0) {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(),
                    after = BookmarkCursor.of(page0.last()), limit = any(),
                    sort = any(), listId = any()
                )
            }
        }

    @Test
    fun `loadNextPage still appends while the view is unchanged`() = runTest(testDispatcher) {
        val page0 = stubFullFirstPage()
        coEvery {
            bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(),
                after = BookmarkCursor.of(page0.last()), limit = any(),
                sort = any(), listId = any()
            )
        } returns listOf(makeBookmark(99))

        val model = createMainScreenModel()
        advanceUntilIdle()

        model.loadNextPage()
        advanceUntilIdle()

        assertEquals(page0.size + 1, model._accumulatedBookmarks.value.size)
        assertTrue(model._accumulatedBookmarks.value.any { it.remoteId == "remote-99" })
    }

    @Test
    fun `an in-place refresh does not discard a reset that is already loading`() =
        runTest(testDispatcher) {
            val page0 = (1L..PAGE_SIZE.toLong()).map { makeBookmark(it) }
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), after = null, limit = any(),
                    sort = any(), listId = any()
                )
            } coAnswers {
                delay(100)
                page0
            }

            val model = createMainScreenModel()
            advanceUntilIdle()
            val versionBefore = model.bookmarkListVersion.value

            // A list switch starts a reset...
            launch { model.resetPaginationAndLoad(fakeServer, FilterConfig()) }
            // ...and a background sync completing mid-flight refreshes the same view.
            launch { model.refreshLoadedPagesInPlace(fakeServer, FilterConfig()) }
            advanceUntilIdle()

            // The reset must win: bumping the version is what tells the UI to drop the
            // previous list's scroll anchor and jump back to the top.
            assertEquals(versionBefore + 1, model.bookmarkListVersion.value)
            assertEquals(page0.size, model._accumulatedBookmarks.value.size)
        }

    @Test
    fun `a reset for a list the user already left does not publish its page`() =
        runTest(testDispatcher) {
            val listAPage = listOf(makeBookmark(1), makeBookmark(2))
            val listBPage = listOf(makeBookmark(50))
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), after = null, limit = any(),
                    sort = any(), listId = "list-a"
                )
            } coAnswers {
                delay(100)
                listAPage
            }
            coEvery {
                bookmarkRepository.getBookmarksPaged(
                    server = any(), status = any(), after = null, limit = any(),
                    sort = any(), listId = "list-b"
                )
            } returns listBPage

            val model = createMainScreenModel()
            advanceUntilIdle()

            model._currentFilter.value = listAFilter
            launch { model.resetPaginationAndLoad(fakeServer, listAFilter) }
            advanceTimeBy(50)

            // The user switches to list B before list A's page comes back.
            model._currentFilter.value = listBFilter
            advanceUntilIdle()

            assertEquals(listBFilter, model._loadedView.value?.filter)
            assertEquals(listBPage.map { it.remoteId }, model._accumulatedBookmarks.value.map { it.remoteId })
        }
}
