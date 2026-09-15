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
import io.mockk.coEvery
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
import com.karakept.app.utils.TestAppDispatchers

/**
 * Tests for [MainScreenModel.filteredBookmarkCount] — the denominator the fast-scroll cursor maps
 * its thumb over.
 *
 * It has to describe the whole filtered view, not the window paged in so far: a denominator that
 * grows with the window walks the thumb back up the track every time a page lands (#273).
 *
 * The counting itself is now a `COUNT(*)` over the view's own predicate, so what it *means* to
 * count a tag, a list or the offline view is proved against real SQLite in
 * `BookmarkViewPredicateTest` — including that the count and the rows agree for every filter
 * shape. What is left here is the wiring: that the model asks for the count of the filter it is
 * actually showing, and re-asks when that filter changes.
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

        countedFilters.clear()
        every { bookmarkRepository.countBookmarksForViewFlow(any(), any()) } answers {
            val filter = secondArg<FilterConfig>()
            countedFilters += filter
            flowOf(countsByFilter[filter] ?: 0)
        }
    }

    /** Every filter the model has asked the database to count, in order. */
    private val countedFilters = mutableListOf<FilterConfig>()

    /** What the database answers for a given filter. */
    private var countsByFilter: Map<FilterConfig, Int> = emptyMap()

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
        createdAt = remoteId,
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
    fun `filteredBookmarkCount counts the whole filtered view, not the loaded window`() =
        runTest(testDispatcher) {
            // Far more than one page. The count is answered by the database for the whole view,
            // so nothing about how far paging has walked can reach it (#273).
            countsByFilter = mapOf(FilterConfig() to 250)
            every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())
            // Paging has reached 20 rows of the 250 the view holds.
            val window = (1L..20L).map { createBookmarkEntity(it) }
            coEvery {
                bookmarkRepository.getBookmarksPaged(any(), any(), any(), any(), any(), any())
            } returns window

            val model = createMainScreenModel()
            val job = launch { model.filteredBookmarkCount.collect {} }
            val windowJob = launch { model.bookmarks.collect {} }
            advanceUntilIdle()

            assertEquals(20, model.bookmarks.value.size, "the window is what paging has reached")
            assertEquals(
                250,
                model.filteredBookmarkCount.value,
                "and the count is the whole view regardless — a denominator that grew with the " +
                    "window walked the thumb back up the track on every page (#273)"
            )
            job.cancel()
            windowJob.cancel()
        }

    @Test
    fun `filteredBookmarkCount follows the active filter`() = runTest(testDispatcher) {
        val kotlinTag = FilterConfig(tags = listOf("kotlin"))
        val kotlinUnread = FilterConfig(tags = listOf("kotlin"), readFilter = ReadFilter.UNREAD)
        countsByFilter = mapOf(FilterConfig() to 4, kotlinTag to 2, kotlinUnread to 1)
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())

        val model = createMainScreenModel()
        val job = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()
        assertEquals(4, model.filteredBookmarkCount.value)

        model.applyFilter(kotlinTag)
        advanceUntilIdle()
        assertEquals(2, model.filteredBookmarkCount.value)

        model.applyFilter(kotlinUnread)
        advanceUntilIdle()
        assertEquals(1, model.filteredBookmarkCount.value)

        // Every narrowing was asked of the database rather than derived from the previous answer.
        assertEquals(
            listOf(FilterConfig(), kotlinTag, kotlinUnread),
            countedFilters.distinct(),
            "the count must be re-read for the filter on screen"
        )
        job.cancel()
    }

    @Test
    fun `a list view is counted by the list filter, not by a status the drawer chose`() =
        runTest(testDispatcher) {
            // That a single-list view counts its archived members is a property of the predicate
            // and is proved against SQLite in BookmarkViewPredicateTest. What matters here is
            // that the list reaches the query at all.
            val listView =
                FilterConfig(status = FilterStatus.ALL_INCLUDING_ARCHIVED, lists = listOf("list-1"))
            countsByFilter = mapOf(listView to 2)
            every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())

            val model = createMainScreenModel()
            val job = launch { model.filteredBookmarkCount.collect {} }
            advanceUntilIdle()

            model.applyFilter(listView)
            advanceUntilIdle()

            assertEquals(2, model.filteredBookmarkCount.value)
            assertEquals(listView, countedFilters.last())
            job.cancel()
        }

    @Test
    fun `the offline view is counted like every other view`() = runTest(testDispatcher) {
        // It used to need its own count, because the offline condition reads a `content` column
        // the in-memory rows are loaded without, and the in-memory view stood on a
        // reading-time proxy instead. Counting in SQL reads the column it means, so the offline
        // view goes through the same path as the rest and the special case is gone.
        val offline = FilterConfig(status = FilterStatus.OFFLINE)
        countsByFilter = mapOf(offline to 3)
        every { bookmarkRepository.getBookmarks(any()) } returns
            flowOf((1L..5L).map { createBookmarkEntity(it) })

        val model = createMainScreenModel()
        val job = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()

        model.applyFilter(offline)
        advanceUntilIdle()

        assertEquals(3, model.filteredBookmarkCount.value)
        assertEquals(offline, countedFilters.last())
        job.cancel()
    }

    @Test
    fun `filteredBookmarks names the row at an absolute position`() = runTest(testDispatcher) {
        // What the scroll cursor's tooltip asks of it: the row a thumb points at, whether or not
        // paging has reached it.
        val bookmarks = (1L..300L).map { createBookmarkEntity(it) }
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(bookmarks)

        val model = createMainScreenModel()
        val job = launch { model.filteredBookmarks.collect {} }
        advanceUntilIdle()

        val view = model.filteredBookmarks.value
        assertEquals(300, view.size)
        assertEquals("remote-300", view.first().remoteId, "NEWEST puts the newest row first")
        assertEquals("remote-1", view.last().remoteId)
        job.cancel()
    }

    @Test
    fun `the count is asked for the same filter the rows are read for`() = runTest(testDispatcher) {
        // A thumb at the end of the track must point at a row the list can name, so the count and
        // the rows have to be about one view. They agree because they share a predicate — that
        // agreement is checked directly against SQLite in BookmarkViewPredicateTest; here it is
        // that the model does not hand the two different filters.
        val kotlinTag = FilterConfig(tags = listOf("kotlin"))
        countsByFilter = mapOf(kotlinTag to 2)
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())

        val model = createMainScreenModel()
        val job = launch { model.filteredBookmarks.collect {} }
        val countJob = launch { model.filteredBookmarkCount.collect {} }
        advanceUntilIdle()

        model.applyFilter(kotlinTag)
        advanceUntilIdle()

        assertEquals(2, model.filteredBookmarkCount.value)
        assertEquals(
            model.effectiveFilterNow(),
            countedFilters.last(),
            "the count must describe the view on screen, not the one requested before it resolved"
        )
        job.cancel()
        countJob.cancel()
    }
}
