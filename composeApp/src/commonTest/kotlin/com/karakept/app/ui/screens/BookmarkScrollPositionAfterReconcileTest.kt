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
 * Regression tests for the bookmark scroll-position bug after smart-list reconciliation.
 *
 * Bug: when the user added a bookmark to a list that caused a smart list to exclude
 * it (e.g. Feeds excludes ReadLater bookmarks), [reconcileBookmarkLists] was calling
 * [resetPaginationAndLoad] which replaced the whole accumulated list with only page 0
 * (~20 items). If the user had scrolled past item 20, LazyColumn could no longer find
 * the previously visible items and jumped to a fixed near-top position.
 *
 * Fix: [reconcileBookmarkLists] now does a targeted single-bookmark update, leaving
 * all other accumulated items (and therefore the scroll position) untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkScrollPositionAfterReconcileTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testServer = Server(
        id = "server-1",
        url = "https://test.example.com",
        apiKey = "test-key",
        label = "Test"
    )

    private val feedsSmartList = KarakeepList(id = "feeds", name = "Feeds", type = KarakeepList.Type.SMART)
    private val readLaterList = KarakeepList(id = "read-later", name = "Read Later", type = KarakeepList.Type.MANUAL)

    private val serversFlow = MutableStateFlow(listOf(testServer))
    private val listsFlow = MutableStateFlow<List<KarakeepList>>(listOf(feedsSmartList, readLaterList))
    private val bookmarkChangedEvents = MutableSharedFlow<Long>()
    private val undoCompletedEvents = MutableSharedFlow<UndoCompletedEvent>()

    private val serverRepository: ServerRepository = mockk(relaxed = true) {
        every { servers } returns serversFlow
    }
    private val bookmarkRepository: BookmarkRepository = mockk(relaxed = true) {
        every { getBookmarks(any()) } returns flowOf(emptyList())
        every { syncReports } returns kotlinx.coroutines.flow.MutableSharedFlow()
    }
    private val bookmarkActionsRepository: BookmarkActionsRepository = mockk(relaxed = true) {
        every { bookmarkChangedEvents } returns this@BookmarkScrollPositionAfterReconcileTest.bookmarkChangedEvents
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
        every { undoCompletedEvents } returns this@BookmarkScrollPositionAfterReconcileTest.undoCompletedEvents
    }
    private val snackbarManager: ActionSnackbarManager = mockk(relaxed = true)
    private val highlightRepository: HighlightRepository = mockk(relaxed = true) {
        every { getHighlightsCount(any()) } returns flowOf(0)
    }

    private lateinit var model: MainScreenModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositorySyncKt")
        coEvery { any<BookmarkActionsRepository>().flushPendingActions(any()) } returns Unit
        coEvery { bookmarkRepository.getBookmarksPaged(any(), any(), any(), any(), any(), any()) } returns emptyList()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositorySyncKt")
    }

    private fun createModel(): MainScreenModel {
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
        return model
    }

    private fun bookmark(id: Long, listIds: String = "feeds") = BookmarkEntity(
        localId = id,
        remoteId = id,
        originalRemoteId = "orig-$id",
        serverId = testServer.id,
        url = "https://example.com/$id",
        title = "Bookmark $id",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = id * 1000L,
        isArchived = false,
        isStarred = false,
        listIds = listIds
    )

    /**
     * Core regression: after adding a bookmark to ReadLater while viewing Feeds,
     * the accumulated list should have exactly one item removed (the moved bookmark)
     * and all other items must be preserved in order — including items beyond page 0.
     *
     * Before the fix: reconcileBookmarkLists called resetPaginationAndLoad which
     * replaced the whole list with only page 0 (e.g. 20 items), discarding page 1+.
     */
    @Test
    fun addToReadLater_onlyMovedBookmarkRemovedFromFeeds_otherItemsPreserved() = runTest(testDispatcher) {
        val model = createModel()
        advanceUntilIdle()

        // Simulate 50 bookmarks already accumulated (pages 0–2 loaded)
        val allBookmarks = (1L..50L).map { bookmark(it, "feeds") }
        val movedBookmark = allBookmarks[30] // item at position 30 (page 1)
        model._accumulatedBookmarks.value = allBookmarks
        model._currentListContext.value = "feeds"

        // After reconciliation: server reports bookmark no longer in Feeds smart list
        val updatedBookmark = movedBookmark.copy(listIds = "read-later")
        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), eq(movedBookmark.localId), any())
        } returns true
        coEvery {
            bookmarkRepository.getBookmarkByRemoteId(eq(movedBookmark.remoteId), any())
        } returns updatedBookmark

        model.moveBookmarkToList(movedBookmark, "read-later")
        advanceUntilIdle()

        val accumulated = model._accumulatedBookmarks.value
        // All 50 items minus the one moved = 49 remain
        assertEquals(49, accumulated.size, "Exactly 49 items should remain after removing the moved bookmark")
        assertFalse(accumulated.any { it.remoteId == movedBookmark.remoteId },
            "Moved bookmark must be absent from Feeds list")
        // Items from positions well beyond page 0 must still be present
        val highPageItems = allBookmarks.filter { it.remoteId > 20L && it.remoteId != movedBookmark.remoteId }
        assertTrue(highPageItems.all { expected -> accumulated.any { it.remoteId == expected.remoteId } },
            "Items from page 1+ must be preserved (scroll position protection)")
    }

    /**
     * When a bookmark is moved but still belongs to the current smart list
     * (e.g. added to an unrelated list that Feeds doesn't exclude), it should be
     * updated in place — not removed.
     */
    @Test
    fun addToUnrelatedList_bookmarkUpdatedInPlace_notRemoved() = runTest(testDispatcher) {
        val model = createModel()
        advanceUntilIdle()

        val items = (1L..10L).map { bookmark(it, "feeds") }
        val targetBookmark = items[5]
        model._accumulatedBookmarks.value = items
        model._currentListContext.value = "feeds"

        // Server: bookmark still in Feeds after the action (also added to another list)
        val updatedBookmark = targetBookmark.copy(listIds = "feeds,other-list")
        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), eq(targetBookmark.localId), any())
        } returns true
        coEvery {
            bookmarkRepository.getBookmarkByRemoteId(eq(targetBookmark.remoteId), any())
        } returns updatedBookmark

        model.moveBookmarkToList(targetBookmark, "other-list")
        advanceUntilIdle()

        val accumulated = model._accumulatedBookmarks.value
        assertEquals(10, accumulated.size, "No items should be removed when bookmark stays in Feeds")
        val refreshed = accumulated.find { it.remoteId == targetBookmark.remoteId }
        assertEquals("feeds,other-list", refreshed?.listIds, "Bookmark should be updated in place with new listIds")
    }

    /**
     * getBookmarkByRemoteId returns null (bookmark deleted server-side) →
     * bookmark is removed from the accumulated list.
     */
    @Test
    fun serverDeletedBookmark_removedFromAccumulatedList() = runTest(testDispatcher) {
        val model = createModel()
        advanceUntilIdle()

        val items = (1L..5L).map { bookmark(it, "feeds") }
        val deletedBookmark = items[2]
        model._accumulatedBookmarks.value = items
        model._currentListContext.value = "feeds"

        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), eq(deletedBookmark.localId), any())
        } returns true
        coEvery {
            bookmarkRepository.getBookmarkByRemoteId(eq(deletedBookmark.remoteId), any())
        } returns null

        model.moveBookmarkToList(deletedBookmark, "read-later")
        advanceUntilIdle()

        assertEquals(4, model._accumulatedBookmarks.value.size)
        assertFalse(model._accumulatedBookmarks.value.any { it.remoteId == deletedBookmark.remoteId })
    }

    /**
     * After the fix, resetPaginationAndLoad must NOT be called during reconciliation
     * (it would replace the list with page 0 and destroy scroll position).
     */
    @Test
    fun reconcileDoesNotCallGetBookmarksPaged() = runTest(testDispatcher) {
        val model = createModel()
        advanceUntilIdle()

        val bk = bookmark(1L, "feeds")
        model._accumulatedBookmarks.value = listOf(bk)
        model._currentListContext.value = "feeds"

        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), any(), any())
        } returns true
        coEvery {
            bookmarkRepository.getBookmarkByRemoteId(eq(bk.remoteId), any())
        } returns bk.copy(listIds = "read-later")

        // Reset the invocation counter after model init (init calls getBookmarksPaged once)
        io.mockk.clearMocks(bookmarkRepository, answers = false)
        coEvery { bookmarkRepository.getBookmarksPaged(any(), any(), any(), any(), any(), any()) } returns emptyList()

        model.moveBookmarkToList(bk, "read-later")
        advanceUntilIdle()

        // getBookmarksPaged is the function resetPaginationAndLoad calls;
        // it must NOT be called during reconciliation.
        coVerify(exactly = 0) { bookmarkRepository.getBookmarksPaged(any(), any(), any(), any(), any(), any()) }
    }

    /**
     * No list context (browsing All Bookmarks): the bookmark should be updated in
     * place regardless of its listIds, because there is no context to filter against.
     */
    @Test
    fun noListContext_bookmarkUpdatedInPlace() = runTest(testDispatcher) {
        val model = createModel()
        advanceUntilIdle()

        val bk = bookmark(1L, "feeds")
        model._accumulatedBookmarks.value = listOf(bk)
        model._currentListContext.value = null // All Bookmarks

        val updated = bk.copy(listIds = "read-later")
        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), any(), any())
        } returns true
        coEvery {
            bookmarkRepository.getBookmarkByRemoteId(eq(bk.remoteId), any())
        } returns updated

        model.moveBookmarkToList(bk, "read-later")
        advanceUntilIdle()

        val accumulated = model._accumulatedBookmarks.value
        assertEquals(1, accumulated.size)
        assertEquals("read-later", accumulated.first().listIds)
    }

    /**
     * Apply the same fix via removeBookmarkFromList — reconcileBookmarkLists is
     * called from both move and remove paths.
     */
    @Test
    fun removeFromList_accumulatedListPreservedExceptRemovedItem() = runTest(testDispatcher) {
        val model = createModel()
        advanceUntilIdle()

        // Simulate 40 items in accumulated list (pages 0–1)
        val allItems = (1L..40L).map { bookmark(it, "feeds,manual-1") }
        val targetBookmark = allItems[35]
        model._accumulatedBookmarks.value = allItems
        model._currentListContext.value = "feeds"

        // After removal from manual-1, server confirms bookmark still in Feeds
        val updatedBookmark = targetBookmark.copy(listIds = "feeds")
        coEvery {
            bookmarkRepository.reconcileBookmarkSmartListMembership(any(), eq(targetBookmark.localId), any())
        } returns true
        coEvery {
            bookmarkRepository.getBookmarkByRemoteId(eq(targetBookmark.remoteId), any())
        } returns updatedBookmark

        model.removeBookmarkFromList(targetBookmark, "manual-1")
        advanceUntilIdle()

        val accumulated = model._accumulatedBookmarks.value
        // Bookmark was removed from manual-1 context but stays in Feeds → still 40 items
        assertEquals(40, accumulated.size, "Bookmark stays in Feeds after removal from manual-1")
        val refreshed = accumulated.find { it.remoteId == targetBookmark.remoteId }
        assertEquals("feeds", refreshed?.listIds)
        // Items beyond page 0 must still be present
        assertTrue(accumulated.any { it.remoteId == 39L }, "Item from page 1 must still be present")
    }

    /**
     * Regression: moveBookmarkToList must synchronously add the bookmark's remoteId to
     * actedOnBookmarkIds before launching the async reconcile coroutine. This prevents
     * MainScreenScrollAction from auto-firing the scroll action on a bookmark that is
     * about to be removed by reconciliation (but is still technically in the list during
     * the network round-trip).
     */
    @Test
    fun moveBookmarkToList_immediatelyPopulatesActedOnBookmarkIds() = runTest(testDispatcher) {
        val model = createModel()
        advanceUntilIdle()

        val bk = bookmark(42L, "feeds")
        model._accumulatedBookmarks.value = listOf(bk)

        // actedOnBookmarkIds must be populated synchronously — before any coroutine runs.
        assertFalse(
            42L in model.actedOnBookmarkIds.value,
            "actedOnBookmarkIds should be empty before the action"
        )

        model.moveBookmarkToList(bk, "read-later")

        // No advanceUntilIdle — the ID must be present synchronously, before coroutines run.
        assertTrue(
            42L in model.actedOnBookmarkIds.value,
            "actedOnBookmarkIds must contain the remoteId immediately after the action is called"
        )
    }

    /**
     * Same guarantee for removeBookmarkFromList.
     */
    @Test
    fun removeBookmarkFromList_immediatelyPopulatesActedOnBookmarkIds() = runTest(testDispatcher) {
        val model = createModel()
        advanceUntilIdle()

        val bk = bookmark(7L, "feeds,manual-1")
        model._accumulatedBookmarks.value = listOf(bk)

        model.removeBookmarkFromList(bk, "manual-1")

        assertTrue(
            7L in model.actedOnBookmarkIds.value,
            "actedOnBookmarkIds must contain the remoteId immediately after removeBookmarkFromList"
        )
    }

    /**
     * resetPaginationAndLoad clears actedOnBookmarkIds so IDs from a previous list
     * session don't linger into the next one and suppress scroll actions incorrectly.
     */
    @Test
    fun resetPaginationAndLoad_clearsActedOnBookmarkIds() = runTest(testDispatcher) {
        val model = createModel()
        advanceUntilIdle()

        // Pre-populate actedOnBookmarkIds via an explicit action
        val bk = bookmark(99L, "feeds")
        model._accumulatedBookmarks.value = listOf(bk)
        model.moveBookmarkToList(bk, "read-later")
        assertTrue(99L in model.actedOnBookmarkIds.value, "pre-condition: ID should be in set")

        // Trigger a full list reload (simulates filter change / sync)
        val server = testServer
        val filter = FilterConfig()
        coEvery { bookmarkRepository.getBookmarksPaged(any(), any(), any(), any(), any(), any()) } returns emptyList()

        model.resetPaginationAndLoad(server, filter)
        advanceUntilIdle()

        assertTrue(
            model.actedOnBookmarkIds.value.isEmpty(),
            "actedOnBookmarkIds must be cleared after a full list reload"
        )
    }
}
