package com.karakept.app.ui.screens

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.entity.BookmarkEntity
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
import com.karakept.app.data.repository.flushPendingActions
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.UndoCompletedEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

/**
 * Tests for the deferred smart-list refresh mechanism (LIST-02 fix).
 *
 * When a list-membership action triggers reconciliation and the server returns stale
 * smart-list data, smart lists are marked for deferred refresh. When the user navigates
 * to one of those lists, syncBookmarksForList is called (GET /lists/{id}/bookmarks is
 * always fresh, unlike GET /bookmarks/{id}/lists).
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
    private val bookmarkChangedEvents = MutableSharedFlow<String>()
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
        every { bookmarkChangedEvents } returns this@SmartListDeferredRefreshTest.bookmarkChangedEvents
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

        mockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositorySyncKt")
        coEvery { any<BookmarkActionsRepository>().flushPendingActions(any()) } returns Unit

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
            highlightRepository = highlightRepository
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositorySyncKt")
    }

    private fun bookmarkEntity(
        localId: Long = 1L,
        listIds: String = ""
    ) = BookmarkEntity(
        localId = localId,
        remoteId = "orig-$localId",
        serverId = testServer.id,
        url = "https://example.com/$localId",
        title = "Bookmark $localId",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 1000L,
        isArchived = false,
        isStarred = false,
        listIds = listIds
    )

    private fun smartList(id: String) =
        KarakeepList(id = id, name = "Smart $id", type = KarakeepList.Type.SMART)

    private fun manualList(id: String) =
        KarakeepList(id = id, name = "Manual $id", type = KarakeepList.Type.MANUAL)

    // ---- Flag-setting tests (reconcileBookmarkLists → _smartListsNeedingRefresh) ----

    @Test
    fun staleReconciliation_marksSmartListsForRefresh() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        listsFlow.value = listOf(smartList("smartA"), smartList("smartB"), manualList("manualC"))
        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), any(), any())
        } returns false

        val bookmark = bookmarkEntity(localId = 1L, listIds = "manualC")
        model._accumulatedBookmarks.value = listOf(bookmark)

        model.removeBookmarkFromList(bookmark, "manualC")
        advanceUntilIdle()

        val needingRefresh = model._smartListsNeedingRefresh.value
        assertTrue("smartA" in needingRefresh, "smartA should be marked for refresh")
        assertTrue("smartB" in needingRefresh, "smartB should be marked for refresh")
        assertFalse("manualC" in needingRefresh, "Manual list should NOT be marked")
    }

    @Test
    fun freshReconciliation_doesNotMarkSmartListsForRefresh() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        listsFlow.value = listOf(smartList("smartA"), manualList("manualC"))
        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), any(), any())
        } returns true

        val bookmark = bookmarkEntity(localId = 2L, listIds = "smartA,manualC")
        model._accumulatedBookmarks.value = listOf(bookmark)

        model.removeBookmarkFromList(bookmark, "manualC")
        advanceUntilIdle()

        assertTrue(model._smartListsNeedingRefresh.value.isEmpty(),
            "No smart lists should be marked when reconciliation returned fresh data")
    }

    @Test
    fun moveToList_marksSmartListsWhenStale() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        listsFlow.value = listOf(smartList("smartA"), manualList("manualC"))
        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), any(), any())
        } returns false

        val bookmark = bookmarkEntity(localId = 3L, listIds = "")
        model._accumulatedBookmarks.value = listOf(bookmark)

        model.moveBookmarkToList(bookmark, "manualC")
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

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()

        assertFalse("smartA" in model._smartListsNeedingRefresh.value,
            "Flag should be cleared even on failure")
        coVerify(atLeast = 1) {
            bookmarkRepository.getBookmarksPaged(any(), any(), any(), any(), any(), any())
        }
    }

    // ---- End-to-end flows ----

    @Test
    fun endToEnd_removeFromList_stale_thenNavigate_triggersSync() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        listsFlow.value = listOf(smartList("smartA"), manualList("manualB"))
        val bookmark = bookmarkEntity(localId = 5L, listIds = "manualB")
        model._accumulatedBookmarks.value = listOf(bookmark)
        model._currentListContext.value = "manualB"

        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), eq(5L), any())
        } returns false

        model.removeBookmarkFromList(bookmark, "manualB")
        advanceUntilIdle()
        assertTrue("smartA" in model._smartListsNeedingRefresh.value)

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.syncBookmarksForList(testServer, "smartA", any()) }
        assertTrue(model._smartListsNeedingRefresh.value.isEmpty())
    }

    @Test
    fun endToEnd_freshReconciliation_navigateDoesNotSync() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        listsFlow.value = listOf(smartList("smartA"), manualList("manualB"))
        val bookmark = bookmarkEntity(localId = 6L, listIds = "smartA,manualB")
        model._accumulatedBookmarks.value = listOf(bookmark)

        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), eq(6L), any())
        } returns true

        model.removeBookmarkFromList(bookmark, "manualB")
        advanceUntilIdle()
        assertTrue(model._smartListsNeedingRefresh.value.isEmpty())

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()

        coVerify(exactly = 0) { bookmarkRepository.syncBookmarksForList(any(), eq("smartA"), any()) }
    }

    // ---- Flag accumulation ----

    @Test
    fun multipleStaleActions_accumulateFlags() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        listsFlow.value = listOf(smartList("smartA"), smartList("smartB"), manualList("manualC"))
        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), any(), any())
        } returns false

        val bk1 = bookmarkEntity(localId = 10L, listIds = "manualC")
        val bk2 = bookmarkEntity(localId = 11L, listIds = "manualC")
        model._accumulatedBookmarks.value = listOf(bk1, bk2)

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
