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
 * A list-membership action asks the repository to probe the edited bookmarks' smart lists
 * (`reconcileSmartListsAfterEdit`); whatever the probe cannot answer it flags, and when the user
 * navigates to a flagged list, syncBookmarksForList is called. The flags themselves are the
 * repository's (`SmartListMembershipProbeTest`); here they are a set behind `takeSmartListRefresh`.
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
    private val flagged = mutableSetOf<String>()
    private val bookmarkActionsRepository: BookmarkActionsRepository = mockk(relaxed = true) {
        every { aiCapabilities } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
        every { takeSmartListRefresh(any()) } answers { flagged.remove(firstArg<String>()) }
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

    // ---- Every list edit asks for a probe of the edited bookmark ----

    @Test
    fun removeFromList_probesTheBookmarksSmartLists() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()
        val lists = listOf(smartList("smartA"), smartList("smartB"), manualList("manualC"))
        listsFlow.value = lists
        val bookmark = bookmarkEntity(localId = 1L, listIds = "manualC")

        model.removeBookmarkFromList(bookmark, "manualC")
        advanceUntilIdle()

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            bookmarkActionsRepository.removeFromList(bookmark.remoteId, testServer.id, "manualC")
            bookmarkActionsRepository.reconcileSmartListsAfterEdit(testServer.id, listOf(bookmark.remoteId), lists)
        }
    }

    @Test
    fun addToList_probesTheBookmarksSmartLists() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()
        val lists = listOf(smartList("smartA"), manualList("manualC"))
        listsFlow.value = lists
        val bookmark = bookmarkEntity(localId = 3L, listIds = "")

        model.addBookmarkToList(bookmark, "manualC")
        advanceUntilIdle()

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            bookmarkActionsRepository.addToList(bookmark.remoteId, testServer.id, "manualC")
            bookmarkActionsRepository.reconcileSmartListsAfterEdit(testServer.id, listOf(bookmark.remoteId), lists)
        }
    }

    @Test
    fun swipeUndo_probesAgain() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()
        listsFlow.value = listOf(smartList("smartA"), manualList("manualC"))
        val bookmark = bookmarkEntity(localId = 4L, listIds = "manualC")

        model.restoreAndAddBookmarkToList(bookmark, "manualC")
        advanceUntilIdle()
        model.restoreAndRemoveBookmarkFromList(bookmark, "manualC")
        advanceUntilIdle()

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            bookmarkActionsRepository.addToList(bookmark.remoteId, testServer.id, "manualC")
            bookmarkActionsRepository.reconcileSmartListsAfterEdit(testServer.id, listOf(bookmark.remoteId), any())
            bookmarkActionsRepository.removeFromList(bookmark.remoteId, testServer.id, "manualC")
            bookmarkActionsRepository.reconcileSmartListsAfterEdit(testServer.id, listOf(bookmark.remoteId), any())
        }
    }

    // ---- Navigation triggers sync for flagged smart lists ----

    @Test
    fun navigateToFlaggedSmartList_triggersSyncBookmarksForList() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        flagged += setOf("smartA", "smartB")

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

        flagged += setOf("smartA", "smartB")

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()

        val remaining = flagged.toSet()
        assertFalse("smartA" in remaining, "smartA should be cleared after sync")
        assertTrue("smartB" in remaining, "smartB should still be pending")
    }

    @Test
    fun navigateToUnflaggedList_doesNotTriggerSync() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        flagged += setOf("smartA")

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

        flagged += setOf("smartA")

        model.applyFilter(FilterConfig())
        advanceUntilIdle()

        coVerify(exactly = 0) {
            bookmarkRepository.syncBookmarksForList(any(), any(), any())
        }
        assertTrue("smartA" in flagged.toSet(),
            "Flag should be preserved for later navigation")
    }

    @Test
    fun syncFailure_clearsFlag_andStillLoadsFromLocalDb() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        flagged += setOf("smartA")
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

        assertFalse("smartA" in flagged.toSet(),
            "Flag should be cleared even on failure")
        coVerify(atLeast = 1) {
            bookmarkRepository.getBookmarkPage(any(), any(), any(), any())
        }
        job.cancel()
    }

    @Test
    fun navigatingToEachSmartList_clearsIndividually() = runTest(testDispatcher) {
        createModel()
        advanceUntilIdle()

        flagged += setOf("smartA", "smartB")

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()
        assertEquals(setOf("smartB"), flagged.toSet())

        model.applyFilter(FilterConfig(lists = listOf("smartB")))
        advanceUntilIdle()
        assertEquals(emptySet(), flagged.toSet())

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

        flagged += setOf("smartA", "smartB")

        model.applyFilter(FilterConfig(lists = listOf("smartA")))
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.syncBookmarksForList(testServer, "smartA", any()) }
        coVerify(exactly = 0) { bookmarkRepository.syncBookmarksForList(testServer, "smartB", any()) }
        assertEquals(setOf("smartB"), flagged.toSet())

        model.applyFilter(FilterConfig(lists = listOf("smartB")))
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.syncBookmarksForList(testServer, "smartB", any()) }
        assertTrue(
            flagged.toSet().isEmpty(),
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

        flagged += setOf("smartA")
        model.applyFilter(FilterConfig(lists = listOf("smartA")))

        coVerify(exactly = 0) { bookmarkRepository.syncBookmarksForList(any(), any(), any()) }
        assertEquals(setOf("smartA"), flagged.toSet())

        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.syncBookmarksForList(testServer, "smartA", any()) }
        assertTrue(flagged.toSet().isEmpty())
    }
}
