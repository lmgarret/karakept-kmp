package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkSlot
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
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
import com.karakept.app.utils.TestAppDispatchers
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
import kotlin.test.assertTrue

/**
 * The list is sized by the view and reads the pages under the viewport.
 *
 * What this pins is the property the whole design rests on: an index means a position in the view
 * from the first frame, so a jump needs no read to *arrive*, and the read it does make is for the
 * page it landed on rather than for everything on the way there. The list used to be indexed by
 * the rows read so far, which is why reaching an arbitrary row meant walking to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelWindowTest {

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

    /** The view's size, as the database reports it. */
    private lateinit var viewTotal: MutableStateFlow<Int>

    /** Every page read, as (offset, limit), in order. */
    private val pageReads = mutableListOf<Pair<Int, Int>>()

    /** Every filter a page was read for, in order. */
    private val readFilters = mutableListOf<FilterConfig>()

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
        every { bookmarkRepository.getOfflineBookmarkCount(any()) } returns flowOf(0)
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns
            kotlinx.coroutines.flow.MutableSharedFlow()
        every { bookmarkActionsRepository.aiCapabilities } returns MutableStateFlow(emptyMap())
        every { bookmarkActionController.undoCompletedEvents } returns
            kotlinx.coroutines.flow.MutableSharedFlow()
        every { bookmarkRepository.syncReports } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { bookmarkRepository.backgroundSyncCompleted } returns
            kotlinx.coroutines.flow.MutableSharedFlow()

        viewTotal = MutableStateFlow(4397)
        pageReads.clear()
        readFilters.clear()
        every { bookmarkRepository.countBookmarksForViewFlow(any(), any()) } returns viewTotal
        coEvery { bookmarkRepository.getBookmarkPage(any(), any(), any(), any()) } answers {
            val filter = secondArg<FilterConfig>()
            val offset = thirdArg<Int>()
            val limit = arg<Int>(3)
            pageReads += offset to limit
            readFilters += filter
            (offset until minOf(offset + limit, viewTotal.value)).map { row(it) }
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createModel() = MainScreenModel(
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

    private fun row(index: Int) = BookmarkEntity(
        localId = index.toLong(),
        remoteId = "remote-$index",
        serverId = "server-1",
        url = "https://example.com/$index",
        title = "Bookmark $index",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = index.toLong(),
        isArchived = false,
        isStarred = false
    )

    /** Offsets read since the last [clearReads]. */
    private fun offsetsRead() = pageReads.map { it.first }.distinct().sorted()

    private fun clearReads() {
        pageReads.clear()
        readFilters.clear()
    }

    @Test
    fun `the list is sized by the view before any row has been read`() = runTest(testDispatcher) {
        val model = createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        val window = model.bookmarkWindow.value
        assertEquals(4397, window.total, "every row the view holds has a slot")
        assertTrue(
            window.loadedCount < window.total,
            "and only the rows under the viewport have been read"
        )
        job.cancel()
    }

    @Test
    fun `opening a view reads the first page, not the whole view`() = runTest(testDispatcher) {
        val model = createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(0, 50), offsetsRead(), "page 0 and the margin page after it")
        assertEquals(BookmarkSlot.Loaded(row(0)), model.bookmarkWindow.value[0])
        job.cancel()
    }

    @Test
    fun `a jump reads the page it lands on and nothing on the way`() = runTest(testDispatcher) {
        val model = createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        clearReads()

        // The thumb dropped near the end of a 4397-row view.
        model.reportVisibleSlots(4000..4010)
        advanceUntilIdle()

        assertEquals(
            listOf(3950, 4000, 4050),
            offsetsRead(),
            "the page holding row 4000 and its margins — a forward walk would have read 80 pages"
        )
        assertEquals(BookmarkSlot.Loaded(row(4000)), model.bookmarkWindow.value[4000])
        job.cancel()
    }

    @Test
    fun `a row outside the pages on screen is a placeholder, not a wrong row`() =
        runTest(testDispatcher) {
            val model = createModel()
            val job = launch { model.bookmarkWindow.collect {} }
            advanceUntilIdle()

            model.reportVisibleSlots(4000..4010)
            advanceUntilIdle()

            val window = model.bookmarkWindow.value
            assertEquals(BookmarkSlot.Loaded(row(4000)), window[4000])
            assertEquals(BookmarkSlot.Placeholder, window[100], "scrolled away from, so dropped")
            assertEquals(4397, window.total, "and the list is still the size of the view")
            job.cancel()
        }

    @Test
    fun `scrolling within a page reads nothing`() = runTest(testDispatcher) {
        val model = createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        clearReads()

        // Still inside page 0 throughout.
        model.reportVisibleSlots(1..11)
        advanceUntilIdle()
        model.reportVisibleSlots(5..15)
        advanceUntilIdle()

        assertEquals(emptyList(), offsetsRead(), "no page boundary was crossed")
        job.cancel()
    }

    @Test
    fun `a write to the table re-reads the pages on screen`() = runTest(testDispatcher) {
        // Room re-emits the count on every write, which is what brings a row edited elsewhere —
        // or a page a sync has just committed — back onto the screen. There is no refresh call:
        // the rows on screen are simply read again.
        val model = createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        clearReads()

        viewTotal.value = 4398
        advanceUntilIdle()

        assertEquals(listOf(0, 50), offsetsRead(), "the pages on screen were read again")
        assertEquals(4398, model.bookmarkWindow.value.total)
        job.cancel()
    }

    @Test
    fun `switching filter reads the new view, not the old one`() = runTest(testDispatcher) {
        val model = createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        clearReads()

        val unread = FilterConfig(readFilter = ReadFilter.UNREAD)
        model.applyFilter(unread)
        advanceUntilIdle()

        assertTrue(readFilters.isNotEmpty(), "the new view was read")
        assertTrue(
            readFilters.all { it == unread },
            "and every read was for it — a page read with the previous filter would land the " +
                "previous list's rows in the new list's slots"
        )
        job.cancel()
    }

    @Test
    fun `a view with nothing in it has no slots and reads no pages`() = runTest(testDispatcher) {
        viewTotal.value = 0

        val model = createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        assertEquals(0, model.bookmarkWindow.value.total)
        assertTrue(model.bookmarkWindow.value.isEmpty)
        assertEquals(emptyList(), offsetsRead())
        job.cancel()
    }

    @Test
    fun `the last page is short and the list stops at the view's end`() = runTest(testDispatcher) {
        viewTotal.value = 120

        val model = createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        model.reportVisibleSlots(110..119)
        advanceUntilIdle()

        val window = model.bookmarkWindow.value
        assertEquals(120, window.total)
        assertEquals(BookmarkSlot.Loaded(row(119)), window[119])
        assertEquals(BookmarkSlot.Placeholder, window[120], "there is no row 120")
        job.cancel()
    }
}
