package com.karakept.app.ui.screens.main

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.batchAddToList
import com.karakept.app.data.repository.batchRemoveFromList
import com.karakept.app.data.repository.flushPendingActions
import com.karakept.app.domain.ListMembershipChange
import com.karakept.app.testing.testBookmark
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelListMembershipTest {
    private val testDispatcher = StandardTestDispatcher()
    private val harness = MainScreenModelHarness(testDispatcher)
    private val repository get() = harness.bookmarkActionsRepository

    private val server = Server(id = "server-1", url = "https://example.com", apiKey = "key", label = "Test")
    private val lists = listOf(
        KarakeepList(id = "a", name = "Read Later", type = KarakeepList.Type.MANUAL),
        KarakeepList(id = "b", name = "Work", type = KarakeepList.Type.MANUAL)
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(BATCH_KT, SYNC_KT)
        coEvery { any<BookmarkActionsRepository>().flushPendingActions(any()) } returns Unit
        coEvery { repository.batchAddToList(any(), any()) } returns Unit
        coEvery { repository.batchRemoveFromList(any(), any()) } returns Unit
        every { harness.serverRepository.servers } returns flowOf(listOf(server))
        every { harness.settingsRepository.offlineMode } returns flowOf(false)
        every { harness.listRepository.lists } returns MutableStateFlow(lists)
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(BATCH_KT, SYNC_KT)
        Dispatchers.resetMain()
    }

    @Test
    fun `adding to a list leaves the bookmark's other lists alone`() = runTest(testDispatcher) {
        val bookmark = testBookmark(1L, listIds = "a")
        val model = harness.createModel()
        advanceUntilIdle()

        model.updateBookmarkLists(listOf(bookmark), ListMembershipChange(added = setOf("b")))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.batchAddToList(listOf(bookmark), "b") }
        coVerify(exactly = 0) { repository.batchRemoveFromList(any(), any()) }
    }

    @Test
    fun `a change touches only the bookmarks it changes`() = runTest(testDispatcher) {
        val inA = testBookmark(1L, listIds = "a")
        val inNone = testBookmark(2L, listIds = "")
        val model = harness.createModel()
        advanceUntilIdle()

        model.updateBookmarkLists(
            listOf(inA, inNone),
            ListMembershipChange(added = setOf("b"), removed = setOf("a"))
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.batchAddToList(listOf(inA, inNone), "b") }
        coVerify(exactly = 1) { repository.batchRemoveFromList(listOf(inA), "a") }
    }

    @Test
    fun `undo puts back exactly the memberships that were there`() = runTest(testDispatcher) {
        val inA = testBookmark(1L, listIds = "a")
        val inNone = testBookmark(2L, listIds = "")
        val undo = slot<suspend () -> Unit>()
        val message = slot<String>()
        coEvery { harness.snackbarManager.showSnackbarWithUndo(capture(message), capture(undo), any()) } returns Unit
        val model = harness.createModel()
        advanceUntilIdle()

        model.updateBookmarkLists(listOf(inA, inNone), ListMembershipChange(removed = setOf("a")))
        advanceUntilIdle()
        undo.captured.invoke()

        assertEquals("Removed 2 bookmarks from 'Read Later'", message.captured)
        coVerify(exactly = 1) { repository.batchRemoveFromList(listOf(inA), "a") }
        coVerify(exactly = 1) { repository.batchAddToList(listOf(inA), "a") }
    }

    @Test
    fun `undo asks the server about smart lists again`() = runTest(testDispatcher) {
        val bookmark = testBookmark(1L, listIds = "a")
        val undo = slot<suspend () -> Unit>()
        coEvery { harness.snackbarManager.showSnackbarWithUndo(any(), capture(undo), any()) } returns Unit
        val model = harness.createModel()
        advanceUntilIdle()

        model.updateBookmarkLists(listOf(bookmark), ListMembershipChange(added = setOf("b")))
        advanceUntilIdle()
        coVerify(exactly = 1) { harness.bookmarkRepository.reconcileBookmarkSmartListMembership(server, bookmark.localId, any()) }

        undo.captured.invoke()
        advanceUntilIdle()

        coVerify(exactly = 2) { harness.bookmarkRepository.reconcileBookmarkSmartListMembership(server, bookmark.localId, any()) }
    }

    @Test
    fun `a batch edit reads the selection's memberships from the repository, not the rows on screen`() =
        runTest(testDispatcher) {
            val onScreen = testBookmark(1L, listIds = "a")
            val notLoaded = testBookmark(2L, listIds = "a,b")
            coEvery { harness.bookmarkRepository.getBookmarksByRemoteIds(any(), any()) } returns listOf(onScreen, notLoaded)
            val model = harness.createModel()
            advanceUntilIdle()
            model.toggleBookmarkSelection(onScreen)
            model.toggleBookmarkSelection(notLoaded)

            assertEquals(listOf(setOf("a"), setOf("a", "b")), model.selectedBookmarksListIds())
        }

    @Test
    fun `a batch edit applies to the selection and clears it`() = runTest(testDispatcher) {
        val first = testBookmark(1L, listIds = "")
        val second = testBookmark(2L, listIds = "b")
        coEvery { harness.bookmarkRepository.getBookmarksByRemoteIds(any(), any()) } returns listOf(first, second)
        val model = harness.createModel()
        advanceUntilIdle()
        model.toggleBookmarkSelection(first)
        model.toggleBookmarkSelection(second)

        model.batchUpdateLists(ListMembershipChange(added = setOf("b")))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.batchAddToList(listOf(first), "b") }
        assertEquals(emptySet(), model.selectedBookmarkIds.value)
    }

    @Test
    fun `a change that changes nothing does nothing`() = runTest(testDispatcher) {
        val bookmark = testBookmark(1L, listIds = "a")
        val model = harness.createModel()
        advanceUntilIdle()

        model.updateBookmarkLists(listOf(bookmark), ListMembershipChange(added = setOf("a")))
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.batchAddToList(any(), any()) }
        coVerify(exactly = 0) { harness.snackbarManager.showSnackbarWithUndo(any(), any(), any()) }
    }

    private companion object {
        const val BATCH_KT = "com.karakept.app.data.repository.BookmarkActionsRepositoryBatchKt"
        const val SYNC_KT = "com.karakept.app.data.repository.BookmarkActionsRepositorySyncKt"
    }
}
