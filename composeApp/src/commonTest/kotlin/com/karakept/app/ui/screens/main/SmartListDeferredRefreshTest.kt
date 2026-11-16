package com.karakept.app.ui.screens.main

import com.karakept.app.testing.testBookmark
import com.karakept.app.domain.action.TagFilterRequests
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.Server
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.karakept.app.utils.TestAppDispatchers

/**
 * Tests for the deferred smart-list refresh mechanism (LIST-02 fix).
 *
 * A list-membership action marks every smart list for refresh: Karakeep evaluates a smart list as
 * a query and records no membership, so GET /bookmarks/{id}/lists never names one and only a sync
 * of the smart list itself (GET /lists/{id}/bookmarks) says what is in it. When the user navigates
 * to a marked list, syncBookmarksForList is called.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmartListDeferredRefreshTest {
    private val testDispatcher = StandardTestDispatcher()

    private val testServer = Server(
        id = "server-1",
        url = "https://test.example.com",
        apiKey = "test-key",
        label = "Test"
    )

    private val serversFlow = MutableStateFlow(listOf(testServer))
    private val listsFlow = MutableStateFlow<List<KarakeepList>>(emptyList())
    private val undoCompletedEvents = MutableSharedFlow<UndoCompletedEvent>()

    private val serverRepository: ServerRepository = mockk(relaxed = true) {
        every { servers } returns serversFlow
    }
    private val bookmarkRepository: BookmarkRepository = mockk(relaxed = true) {
        every { getBookmarks(any()) } returns flowOf(emptyList())
        every { syncReports } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { backgroundSyncCompleted } returns kotlinx.coroutines.flow.MutableSharedFlow()
    }
    private val bookmarkActionsRepository: BookmarkActionsRepository = mockk(relaxed = true) {
        every { aiCapabilities } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
    }
    private val settingsRepository: SettingsRepository = mockk(relaxed = true) {
        every { defaultListType } returns flowOf(DefaultListType.ALL_BOOKMARKS)
        every { defaultListId } returns flowOf(null)
        every { lastActiveFilterStatus } returns flowOf(null)
        every { lastActiveFilterListId } returns flowOf(null)
        every { offlineMode } returns flowOf(true)
        every { resetProgressOnMarkUnread } returns flowOf(false)
        every { allListSettings } returns flowOf(emptyMap())
        every { getListSettings(any()) } returns flowOf(ListSettings())
    }
    private val listRepository: ListRepository = mockk(relaxed = true) {
        every { lists } returns listsFlow
    }
    private val bookmarkActionController: BookmarkActionController = mockk(relaxed = true) {
        every { undoCompletedEvents } returns this@SmartListDeferredRefreshTest.undoCompletedEvents
    }
    private val snackbarManager: ActionSnackbarManager = mockk(relaxed = true)
    private val highlightRepository: HighlightRepository = mockk(relaxed = true)

    private lateinit var model: MainScreenModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        coEvery {
            bookmarkRepository.getBookmarksPaged(any(), any(), any(), any(), any(), any())
        } returns emptyList()
    }

    /** Create the model and let the init block complete. */
    private fun createModel() {
        model = MainScreenModel(
            serverRepository = serverRepository,
            bookmarkRepository = bookmarkRepository,
            bookmarkActionsRepository = bookmarkActionsRepository,
            settingsRepository = settingsRepository,
            listRepository = listRepository,
            bookmarkActionController = bookmarkActionController,
            snackbarManager = snackbarManager,
            highlightRepository = highlightRepository,
            appDispatchers = TestAppDispatchers(testDispatcher),
            tagFilterRequests = TagFilterRequests()
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun bookmarkEntity(localId: Long = 1L, listIds: String = "") =
        testBookmark(localId, remoteId = "orig-$localId", serverId = testServer.id, createdAt = 1000L, listIds = listIds)

    private fun smartList(id: String) =
        KarakeepList(id = id, name = "Smart $id", type = KarakeepList.Type.SMART)

    private fun manualList(id: String) =
        KarakeepList(id = id, name = "Manual $id", type = KarakeepList.Type.MANUAL)

    // ---- Flag-setting tests (markSmartListsStale → _smartListsNeedingRefresh) ----

    @Test
    fun listEdit_marksEverySmartListForRefresh() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        listsFlow.value = listOf(smartList("smartA"), smartList("smartB"), manualList("manualC"))

        val bookmark = bookmarkEntity(localId = 1L, listIds = "manualC")

        model.removeBookmarkFromList(bookmark, "manualC")
        advanceUntilIdle()

        val needingRefresh = model._smartListsNeedingRefresh.value
        assertTrue("smartA" in needingRefresh, "smartA should be marked for refresh")
        assertTrue("smartB" in needingRefresh, "smartB should be marked for refresh")
        assertFalse("manualC" in needingRefresh, "Manual list should NOT be marked")
    }

    @Test
    fun addToList_marksSmartLists() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        listsFlow.value = listOf(smartList("smartA"), manualList("manualC"))

        val bookmark = bookmarkEntity(localId = 3L, listIds = "")

        model.addBookmarkToList(bookmark, "manualC")
        advanceUntilIdle()

        assertTrue("smartA" in model._smartListsNeedingRefresh.value)
    }

    // ---- Navigation triggers sync for flagged smart lists ----

    @Test
    fun navigateToFlaggedSmartList_triggersSyncBookmarksForList() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        model._smartListsNeedingRefresh.value = setOf("smartA", "smartB")

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            bookmarkRepository.syncBookmarksForList(testServer, "smartA", any())
        }
    }

    @Test
    fun navigateToFlaggedSmartList_clearsOnlyThatFlag() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        model._smartListsNeedingRefresh.value = setOf("smartA", "smartB")

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()

        val remaining = model._smartListsNeedingRefresh.value
        assertFalse("smartA" in remaining, "smartA should be cleared after sync")
        assertTrue("smartB" in remaining, "smartB should still be pending")
    }

    @Test
    fun navigateToUnflaggedList_doesNotTriggerSync() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        model._smartListsNeedingRefresh.value = setOf("smartA")

        model.applyFilter(FilterConfig(lists = listOf("otherList")))
        advanceUntilIdle()

        coVerify(exactly = 0) {
            bookmarkRepository.syncBookmarksForList(any(), eq("otherList"), any())
        }
    }

    @Test
    fun navigateToAllBookmarks_doesNotTriggerSync() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        model._smartListsNeedingRefresh.value = setOf("smartA")

        model.applyFilter(FilterConfig())
        advanceUntilIdle()

        coVerify(exactly = 0) {
            bookmarkRepository.syncBookmarksForList(any(), any(), any())
        }
        assertTrue("smartA" in model._smartListsNeedingRefresh.value,
            "Flag should be preserved for later navigation")
    }

    @Test
    fun syncFailure_clearsFlag_andStillLoadsFromLocalDb() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        model._smartListsNeedingRefresh.value = setOf("smartA")
        coEvery {
            bookmarkRepository.syncBookmarksForList(any(), eq("smartA"), any())
        } throws RuntimeException("Network error")

        // The list reads the pages of a view it has a size for, while something is watching it.
        every { bookmarkRepository.countBookmarksForViewFlow(any(), any()) } returns flowOf(5)
        coEvery { bookmarkRepository.getBookmarkPage(any(), any(), any(), any()) } returns
            listOf(bookmarkEntity(localId = 1L, listIds = "smartA"))
        val job = launch { model.bookmarkWindow.collect {} }
        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()

        assertFalse("smartA" in model._smartListsNeedingRefresh.value,
            "Flag should be cleared even on failure")
        coVerify(atLeast = 1) {
            bookmarkRepository.getBookmarkPage(any(), any(), any(), any())
        }
        job.cancel()
    }

    // ---- End-to-end flows ----

    @Test
    fun endToEnd_removeFromList_thenNavigate_triggersSync() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        listsFlow.value = listOf(smartList("smartA"), manualList("manualB"))
        val bookmark = bookmarkEntity(localId = 5L, listIds = "manualB")
        model._currentListContext.value = "manualB"


        model.removeBookmarkFromList(bookmark, "manualB")
        advanceUntilIdle()
        assertTrue("smartA" in model._smartListsNeedingRefresh.value)

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.syncBookmarksForList(testServer, "smartA", any()) }
        assertTrue(model._smartListsNeedingRefresh.value.isEmpty())
    }

    // ---- Flag accumulation ----

    @Test
    fun multipleActions_accumulateFlags() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        listsFlow.value = listOf(smartList("smartA"), smartList("smartB"), manualList("manualC"))

        val bk1 = bookmarkEntity(localId = 10L, listIds = "manualC")
        val bk2 = bookmarkEntity(localId = 11L, listIds = "manualC")

        model.removeBookmarkFromList(bk1, "manualC")
        advanceUntilIdle()
        model.removeBookmarkFromList(bk2, "manualC")
        advanceUntilIdle()

        assertEquals(setOf("smartA", "smartB"), model._smartListsNeedingRefresh.value)
    }

    @Test
    fun navigatingToEachSmartList_clearsIndividually() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        model._smartListsNeedingRefresh.value = setOf("smartA", "smartB")

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()
        assertEquals(setOf("smartB"), model._smartListsNeedingRefresh.value)

        model.applyFilter(FilterConfig(lists = listOf("smartB")))
        advanceUntilIdle()
        assertEquals(emptySet(), model._smartListsNeedingRefresh.value)

        coVerify(exactly = 1) { bookmarkRepository.syncBookmarksForList(testServer, "smartA", any()) }
        coVerify(exactly = 1) { bookmarkRepository.syncBookmarksForList(testServer, "smartB", any()) }
    }

    /**
     * Regression: two consecutive list switches used to be non-deterministic. The
     * `_currentFilter` observer uses `collectLatest`, so the second switch must cancel and
     * join the first block before starting its own. While that block hopped to
     * `Dispatchers.IO`, the join happened on a real thread pool that `advanceUntilIdle()`
     * cannot wait for, and the test sampled state the model had not reached yet.
     *
     * Every assertion here is made immediately after `advanceUntilIdle()` with no real-clock
     * grace period, so it fails outright if any of this work escapes the test scheduler.
     */
    @Test
    fun twoConsecutiveListSwitches_settleWithinTheTestScheduler() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        model._smartListsNeedingRefresh.value = setOf("smartA", "smartB")

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.syncBookmarksForList(testServer, "smartA", any()) }
        coVerify(exactly = 0) { bookmarkRepository.syncBookmarksForList(testServer, "smartB", any()) }
        assertEquals(setOf("smartB"), model._smartListsNeedingRefresh.value)

        model.applyFilter(FilterConfig(lists = listOf("smartB")))
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.syncBookmarksForList(testServer, "smartB", any()) }
        assertTrue(
            model._smartListsNeedingRefresh.value.isEmpty(),
            "Both flags should be cleared once the scheduler is drained"
        )
        assertEquals("smartB", model.currentListContext.value)
    }

    /**
     * The observer's smart-list refresh must not leak onto a real dispatcher: before the
     * scheduler is drained, no sync has run at all.
     */
    @Test
    fun listSwitch_performsNoWorkUntilTheTestSchedulerIsDrained() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        model._smartListsNeedingRefresh.value = setOf("smartA")
        model.applyFilter(FilterConfig(lists = listOf("smartA")))

        coVerify(exactly = 0) { bookmarkRepository.syncBookmarksForList(any(), any(), any()) }
        assertEquals(setOf("smartA"), model._smartListsNeedingRefresh.value)

        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.syncBookmarksForList(testServer, "smartA", any()) }
        assertTrue(model._smartListsNeedingRefresh.value.isEmpty())
    }
}
