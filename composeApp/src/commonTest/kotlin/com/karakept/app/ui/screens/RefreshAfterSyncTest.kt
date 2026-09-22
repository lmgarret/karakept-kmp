package com.karakept.app.ui.screens

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import com.karakept.app.utils.TestAppDispatchers

/**
 * A list opened at startup must still show its bookmarks once the startup sync finishes.
 *
 * The startup sync refreshes the lists first, which can change the *effective* query for the
 * view already on screen: a list with `includeChildListBookmarks` only expands into several
 * list IDs once its children are known, and a multi-list filter is applied client-side on top
 * of paged DB rows. The in-place refresh that follows the sync must handle that the same way
 * the initial load does — by paging until it finds items — instead of publishing the empty
 * first page and blanking the list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshAfterSyncTest {

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

    private val homeList = KarakeepList(id = "list-a", name = "Home", type = KarakeepList.Type.MANUAL)
    private val childList =
        KarakeepList(id = "list-a-1", name = "Child", parentId = "list-a", type = KarakeepList.Type.MANUAL)

    /** Lets a test drive the in-place refresh without the sync's hop to Dispatchers.IO. */
    private val backgroundSyncCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Number of paged DB reads, so a refresh's cost can be asserted. */
    private var queryCount = 0

    /** Rows enough for pages 0..2, so scrolling twice lands the window on its last page. */
    private val threePages = (PAGE_SIZE * 3).toLong()

    /** Ten rows newer than anything in the list, as a sync commits them: at the top. */
    private fun committedMidRefresh() =
        ((threePages + 1)..(threePages + 10)).map { makeBookmark(it, listIds = "list-a") }

    /**
     * Runs after each paged read has been served. Lets a test commit rows the way a sync
     * does — in the middle of a refresh, between two of the reads it is assembled from.
     */
    private var afterQuery: (() -> Unit)? = null

    /** The drawer's lists, empty until the sync refreshes them — as on a cold start. */
    private val listsFlow = MutableStateFlow<List<KarakeepList>>(emptyList())

    // A page and a bit of bookmarks; only the three oldest belong to the home list, so they
    // sit on the second page of the unfiltered query and the first page of the list query.
    // Sized from PAGE_SIZE so that stays true whatever the page size is tuned to.
    private val tableFlow = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    private var allBookmarks: List<BookmarkEntity>
        get() = tableFlow.value
        set(value) { tableFlow.value = value }

    private val initialTable = (1L..(PAGE_SIZE + 5).toLong()).map { id ->
        makeBookmark(id, listIds = if (id <= 3L) "list-a" else "")
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tableFlow.value = initialTable
        queryCount = 0
        afterQuery = null

        serverRepository = mockk(relaxed = true)
        bookmarkRepository = mockk(relaxed = true)
        bookmarkActionsRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        listRepository = mockk(relaxed = true)
        bookmarkActionController = mockk(relaxed = true)
        snackbarManager = mockk(relaxed = true)
        highlightRepository = mockk(relaxed = true)

        every { serverRepository.servers } returns flowOf(listOf(fakeServer))
        every { settingsRepository.allListSettings } returns
            flowOf(mapOf("list-a" to ListSettings(includeChildListBookmarks = true)))
        every { settingsRepository.getListSettings(any()) } returns
            flowOf(ListSettings(includeChildListBookmarks = true))
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
        every { settingsRepository.defaultListType } returns flowOf(DefaultListType.SPECIFIC_LIST)
        every { settingsRepository.defaultListId } returns flowOf("list-a")
        every { settingsRepository.lastActiveFilterStatus } returns flowOf(null)
        every { settingsRepository.lastActiveFilterListId } returns flowOf(null)
        every { listRepository.lists } returns listsFlow
        every { highlightRepository.getHighlightsCount(any()) } returns flowOf(0)
        every { bookmarkRepository.getBookmarks(any()) } returns flowOf(emptyList())
        every { bookmarkActionsRepository.bookmarkChangedEvents } returns MutableSharedFlow<String>()
        every { bookmarkActionsRepository.aiCapabilities } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
        every { bookmarkActionController.undoCompletedEvents } returns MutableSharedFlow<UndoCompletedEvent>()
        every { bookmarkRepository.syncReports } returns MutableSharedFlow()
        every { bookmarkRepository.backgroundSyncCompleted } returns backgroundSyncCompleted
        every { bookmarkRepository.syncProgress } returns MutableStateFlow(
            com.karakept.app.data.model.SyncProgress.Idle
        )
        coEvery { bookmarkRepository.shouldAutoSync(any()) } returns true

        // Step 1 of the sync: the lists land, so the home list's children become known.
        coEvery { listRepository.refreshLists(any()) } answers {
            listsFlow.value = listOf(homeList, childList)
        }

        // Stands in for BookmarkRepository.buildViewPredicate: a single-list view filters on
        // membership and applies no status clause, a multi-list one applies both, and an
        // unfiltered one applies the status.
        every { bookmarkRepository.countBookmarksForViewFlow(any(), any()) } answers {
            val filter = secondArg<FilterConfig>()
            tableFlow.map { viewOf(it, filter).size }
        }
        coEvery { bookmarkRepository.getBookmarkPage(any(), any(), any(), any()) } answers {
            val filter = secondArg<FilterConfig>()
            val offset = thirdArg<Int>()
            val limit = arg<Int>(3)
            queryCount++
            val view = viewOf(allBookmarks, filter)
            afterQuery?.invoke()
            if (offset >= view.size) emptyList()
            else view.subList(offset, minOf(offset + limit, view.size))
        }
    }

    /** [table] narrowed and ordered the way the real query would for [filter]. */
    private fun viewOf(table: List<BookmarkEntity>, filter: FilterConfig): List<BookmarkEntity> {
        val singleListId = filter.lists.singleOrNull()
        return table
            .sortedWith(
                compareByDescending<BookmarkEntity> { it.createdAt }.thenByDescending { it.localId }
            )
            .filter { bookmark ->
                val lists = bookmark.listIds.split(",")
                when {
                    singleListId != null -> singleListId in lists
                    filter.lists.isNotEmpty() ->
                        (filter.status != FilterStatus.ALL || !bookmark.isArchived) &&
                            filter.lists.any { it in lists }
                    else -> filter.status != FilterStatus.ALL || !bookmark.isArchived
                }
            }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeBookmark(id: Long, listIds: String) = BookmarkEntity(
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
        isStarred = false,
        listIds = listIds
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

    @Test
    fun `home list keeps its bookmarks across the startup sync`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()

        assertEquals(
            listOf(3L, 2L, 1L),
            awaitWindow(model) { it.isNotEmpty() },
            "the home list's bookmarks must survive the sync that follows startup"
        )
        // The list is the size of the view, and the view is the home list's three bookmarks —
        // not the whole table the sync just wrote.
        assertEquals(3, model.bookmarkWindow.value.total, "slots in the list")
    }

    @Test
    fun `the list reloads when its effective filter changes under it`() = runTest(testDispatcher) {
        // The reload is what republishes the view: it bumps the list version, which is what makes
        // the LazyColumn drop its scroll anchor and take resetPaginationAndLoad's scroll-to-top.
        // Patching the window in place instead would leave the list holding the outgoing window's
        // index into the incoming one, sitting on arbitrary items until it is re-tapped.
        //
        // Neither the startup sync nor the drawer's own list load brings the lists in here, so
        // the window is loaded while they are still unknown and the change below is the first
        // thing to expand the home list into its child.
        coEvery { bookmarkRepository.shouldAutoSync(any()) } returns false
        coEvery { listRepository.refreshLists(any()) } returns Unit

        val model = createMainScreenModel()
        advanceUntilIdle()
        assertEquals(listOf(3L, 2L, 1L), window(model), "bookmarks at startup")
        assertEquals(listOf("list-a"), model._loadedView.value?.filter?.lists, "loaded view at startup")
        val versionAtStartup = model.bookmarkListVersion.value

        listsFlow.value = listOf(homeList, childList)
        advanceUntilIdle()

        assertEquals(listOf(3L, 2L, 1L), window(model), "bookmarks after the reload")
        assertEquals(
            listOf("list-a", "list-a-1"),
            model._loadedView.value?.filter?.lists,
            "the window must be reloaded for the expanded filter"
        )
        assertEquals(versionAtStartup + 1, model.bookmarkListVersion.value, "list version")
    }

    @Test
    fun `an empty view does not sweep the whole table on every refresh`() = runTest(testDispatcher) {
        // Nothing is in the home list, so the view is legitimately empty, and the library is
        // large: 400 bookmarks, 20 full pages. The refresh must settle for the empty first page
        // instead of paging to the end of the table looking for something to show — the window
        // it leaves behind is re-swept page by page by every later refresh.
        allBookmarks = (1L..400L).map { makeBookmark(it, listIds = "") }

        val model = createMainScreenModel()
        advanceUntilIdle()

        queryCount = 0
        backgroundSyncCompleted.emit(Unit)
        advanceUntilIdle()

        assertEquals(emptyList(), window(model), "bookmarks")
        assertEquals(0, model.bookmarkWindow.value.total, "an empty view has no slots")
        assertTrue(
            queryCount <= 3,
            "an empty view must not sweep the table to establish that it is empty: $queryCount"
        )
    }

    @Test
    fun `a list emptied on the server is shown as empty after the refresh`() = runTest(testDispatcher) {
        val model = createMainScreenModel()
        advanceUntilIdle()
        awaitWindow(model) { it.isNotEmpty() }

        // Every bookmark loses its membership, so the view really is empty now — the refresh
        // must publish that rather than page on until it finds rows to show.
        allBookmarks = allBookmarks.map { it.copy(listIds = "") }
        backgroundSyncCompleted.emit(Unit)
        advanceUntilIdle()

        assertEquals(emptyList(), window(model), "bookmarks after refresh")
    }

    @Test
    fun `bookmarks the sync adds to the current list appear without leaving it`() = runTest(testDispatcher) {
        // The home list's own pass brings in bookmark 4...
        coEvery { bookmarkRepository.syncBookmarksForList(any(), "list-a", any()) } answers {
            allBookmarks = allBookmarks + makeBookmark(4L, listIds = "list-a")
            1
        }
        // ...and a later pass over the other lists brings in bookmark 5, which the smart list
        // also matches. Both must land in the view while the user is still looking at it.
        coEvery { bookmarkRepository.syncBookmarksForList(any(), "list-a-1", any()) } answers {
            allBookmarks = allBookmarks + makeBookmark(5L, listIds = "list-a")
            1
        }

        val model = createMainScreenModel()
        advanceUntilIdle()

        assertEquals(
            listOf(5L, 4L, 3L, 2L, 1L),
            awaitWindow(model) { window -> window.any { it.remoteId == "remote-5" } },
            "bookmarks synced into the current list must appear without navigating away"
        )
        // Whether these raise the "N new" pill depends on whether they landed before or after
        // the list first rendered, which this fixture settles in one step and so cannot pin.
        // The pill's own rule — rows arriving above the row the user has seen are counted — is
        // asserted with the sequence made explicit in MainScreenModelPaginationSortingTest.
    }

    @Test
    fun `rows committed while a page is being read are not stranded`() =
        runTest(testDispatcher) {
            // Three pages of bookmarks, the user scrolled to the end of them.
            allBookmarks = (1L..threePages).map { makeBookmark(it, listIds = "list-a") }
            coEvery { bookmarkRepository.shouldAutoSync(any()) } returns false
            coEvery { listRepository.refreshLists(any()) } returns Unit

            val model = createMainScreenModel()
            val job = launch { model.bookmarkWindow.collect {} }
            advanceUntilIdle()

            // A pass commits ten bookmarks the moment a read has taken its snapshot. Read page by
            // page off an accumulated position, every row committed above that position pushed the
            // pages below it down by ten: the walk re-read rows it already held and never reached
            // the ones it had displaced, so the window came back ten rows short while claiming to
            // be complete (#333). Nothing accumulates now — the list is the size of the view and
            // each page states the offset it wants.
            afterQuery = {
                afterQuery = null
                allBookmarks = allBookmarks + committedMidRefresh()
            }
            // Scrolling is what asks for a page, and the commit lands inside that read.
            model.reportVisibleSlots((threePages - 10).toInt()..(threePages - 1).toInt())
            advanceUntilIdle()

            val window = model.bookmarkWindow.value
            assertEquals(
                (threePages + 10).toInt(),
                window.total,
                "the list is the size of the table, including what was committed mid-read"
            )
            // The ten that used to be stranded are the ten at the top. Every one has a slot.
            for (index in 0 until 10) {
                assertTrue(window.pageOf(index) != null, "row $index is addressable")
            }
            job.cancel()
        }

    @Test
    fun `every row of the view is reachable without scrolling through the ones before it`() =
        runTest(testDispatcher) {
            // What "paging on skips nothing" becomes: there is no paging to skip anything. Any
            // index the list can show is a slot, and the page under it is read directly.
            allBookmarks = (1L..threePages).map { makeBookmark(it, listIds = "list-a") }
            coEvery { bookmarkRepository.shouldAutoSync(any()) } returns false
            coEvery { listRepository.refreshLists(any()) } returns Unit

            val model = createMainScreenModel()
            val job = launch { model.bookmarkWindow.collect {} }
            advanceUntilIdle()

            allBookmarks = allBookmarks + committedMidRefresh()
            advanceUntilIdle()

            val total = (threePages + 10).toInt()
            assertEquals(total, model.bookmarkWindow.value.total)

            // Jump straight to the last row and read it, with nothing read in between.
            model.reportVisibleSlots((total - 5) until total)
            advanceUntilIdle()

            assertEquals(
                1L,
                model.bookmarkWindow.value.bookmarkAt(total - 1)?.localId,
                "the oldest bookmark is at the end of the view and is readable directly"
            )
            job.cancel()
        }

    @Test
    fun `the list on screen syncs its reading progress without waiting for a slot`() =
        runTest(testDispatcher) {
            // The ration is per server, so the list the user just opened would skip its own
            // pull whenever another list's pass took the slot moments earlier — leaving the
            // one view being looked at as the place the counts stay stale.
            val model = createMainScreenModel()
            advanceUntilIdle()
            model.syncBookmarks()
            advanceUntilIdle()

            coVerify {
                bookmarkRepository.syncBookmarksForList(any(), "list-a", isCurrentView = true)
            }
            // The other lists take their turn as before.
            coVerify {
                bookmarkRepository.syncBookmarksForList(any(), "list-a-1", isCurrentView = false)
            }
        }

    /**
     * The loaded window, read straight off the model — no collector needed. Reported as the
     * number each fixture id was built from, so the assertions read as row numbers.
     */
    private fun window(model: MainScreenModel): List<Long> =
        model.bookmarkWindow.value.loadedRows().map { it.remoteId.substringAfter('-').toLong() }

    /**
     * Waits for the loaded window to satisfy [predicate] and returns its bookmark ids.
     *
     * The startup sync hops to `Dispatchers.IO`, which the test scheduler does not control, so
     * [advanceUntilIdle] can return before the refresh that follows it has run. Waiting on the
     * value rather than sampling it makes the assertion independent of that hop; the wait runs
     * on `Dispatchers.Default` so its timeout is measured on the real clock, and a genuine
     * regression still fails as a plain assertion on the value that did settle.
     */
    private suspend fun awaitWindow(
        model: MainScreenModel,
        predicate: (List<BookmarkEntity>) -> Boolean
    ): List<Long> {
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                model.bookmarkWindow.first { predicate(it.loadedRows()) }
            }
        }
        return window(model)
    }
}

private const val SETTLE_TIMEOUT_MS = 5_000L
