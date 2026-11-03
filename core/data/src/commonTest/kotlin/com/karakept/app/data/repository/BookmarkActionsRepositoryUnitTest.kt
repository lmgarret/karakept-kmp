package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [BookmarkActionsRepository].
 *
 * Verifies optimistic local DB updates and pending-action queuing for
 * list and tag mutation operations.
 */
class BookmarkActionsRepositoryUnitTest : BaseRepositoryTest() {

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val pendingActionDao = mockk<PendingActionDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    private val repository = BookmarkActionsRepository(
        bookmarkDao = bookmarkDao,
        pendingActionDao = pendingActionDao,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository,
        appDispatchers = testAppDispatchers
    )

    // ──────────────────────────────────────────────────────────
    // moveToList
    // ──────────────────────────────────────────────────────────

    @Test
    fun moveToList_updatesLocalDbWithNewListId() = runTest(testDispatcher) {
        val bookmark = makeBookmark(listIds = "")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true) // skip auto-sync

        repository.moveToList(bookmark.remoteId, bookmark.serverId, "list-99")

        val savedSlot = slot<BookmarkEntity>()
        coVerify { bookmarkDao.insertBookmark(capture(savedSlot)) }
        assertEquals("list-99", savedSlot.captured.listIds)
    }

    @Test
    fun moveToList_appendsListIdToExistingListIds() = runTest(testDispatcher) {
        val bookmark = makeBookmark(listIds = "list-1,list-2")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.moveToList(bookmark.remoteId, bookmark.serverId, "list-3")

        val savedSlot = slot<BookmarkEntity>()
        coVerify { bookmarkDao.insertBookmark(capture(savedSlot)) }
        val resultIds = savedSlot.captured.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
        assertTrue(resultIds.containsAll(listOf("list-1", "list-2", "list-3")))
    }

    @Test
    fun moveToList_stripsSmartListMembershipOptimistically() = runTest(testDispatcher) {
        // Bookmark is in a smart list ("all-feeds") and a manual list ("keep-manual").
        val bookmark = makeBookmark(listIds = "all-feeds,keep-manual")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.moveToList(
            bookmark.remoteId, bookmark.serverId, "read-later",
            smartListIds = setOf("all-feeds")
        )

        val savedSlot = slot<BookmarkEntity>()
        coVerify { bookmarkDao.insertBookmark(capture(savedSlot)) }
        val ids = savedSlot.captured.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        // Smart list dropped, manual list kept, target list added.
        assertEquals(setOf("keep-manual", "read-later"), ids)
    }

    @Test
    fun moveToList_doesNotDuplicateExistingListId() = runTest(testDispatcher) {
        val bookmark = makeBookmark(listIds = "list-1")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.moveToList(bookmark.remoteId, bookmark.serverId, "list-1")

        // insertBookmark should NOT have been called since the listId already exists
        coVerify(exactly = 0) { bookmarkDao.insertBookmark(any()) }
    }

    @Test
    fun moveToList_queuesPendingAction() = runTest(testDispatcher) {
        val bookmark = makeBookmark(listIds = "")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.moveToList(bookmark.remoteId, bookmark.serverId, "list-99")

        val actionSlot = slot<PendingActionEntity>()
        coVerify { pendingActionDao.insertAction(capture(actionSlot)) }
        assertEquals(PendingActionType.MOVE_TO_LIST, actionSlot.captured.actionType)
        assertTrue(actionSlot.captured.actionData.contains("list-99"))
    }

    @Test
    fun moveToList_emitsBookmarkChangedEvent() = runTest(testDispatcher) {
        val bookmark = makeBookmark(listIds = "")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        var emittedId: String? = null
        val job = launch {
            repository.bookmarkChangedEvents.collect { emittedId = it }
        }
        // Advance scheduler so the collector coroutine starts and subscribes to the SharedFlow
        // before moveToList emits. Without this, the emission races ahead of the subscription
        // and the value is lost (SharedFlow has replay=0).
        testDispatcher.scheduler.advanceUntilIdle()

        repository.moveToList(bookmark.remoteId, bookmark.serverId, "list-99")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(bookmark.remoteId, emittedId)
        job.cancel()
    }

    // ──────────────────────────────────────────────────────────
    // removeFromList
    // ──────────────────────────────────────────────────────────

    @Test
    fun removeFromList_removesListIdFromLocalDb() = runTest(testDispatcher) {
        val bookmark = makeBookmark(listIds = "list-1,list-2")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.removeFromList(bookmark.remoteId, bookmark.serverId, "list-1")

        val savedSlot = slot<BookmarkEntity>()
        coVerify { bookmarkDao.insertBookmark(capture(savedSlot)) }
        val resultIds = savedSlot.captured.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
        assertFalse(resultIds.contains("list-1"), "list-1 should have been removed")
        assertTrue(resultIds.contains("list-2"), "list-2 should remain")
    }

    @Test
    fun removeFromList_resultsInEmptyListIdsWhenOnlyEntry() = runTest(testDispatcher) {
        val bookmark = makeBookmark(listIds = "list-1")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.removeFromList(bookmark.remoteId, bookmark.serverId, "list-1")

        val savedSlot = slot<BookmarkEntity>()
        coVerify { bookmarkDao.insertBookmark(capture(savedSlot)) }
        val resultIds = savedSlot.captured.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
        assertTrue(resultIds.isEmpty(), "listIds should be empty after removing the last list")
    }

    @Test
    fun removeFromList_queuesPendingAction() = runTest(testDispatcher) {
        val bookmark = makeBookmark(listIds = "list-1")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.removeFromList(bookmark.remoteId, bookmark.serverId, "list-1")

        val actionSlot = slot<PendingActionEntity>()
        coVerify { pendingActionDao.insertAction(capture(actionSlot)) }
        assertEquals(PendingActionType.REMOVE_FROM_LIST, actionSlot.captured.actionType)
        assertTrue(actionSlot.captured.actionData.contains("list-1"))
    }

    @Test
    fun removeFromList_emitsBookmarkChangedEvent() = runTest(testDispatcher) {
        val bookmark = makeBookmark(listIds = "list-1")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        var emittedId: String? = null
        val job = launch {
            repository.bookmarkChangedEvents.collect { emittedId = it }
        }
        // Advance scheduler so the collector coroutine starts and subscribes to the SharedFlow
        // before removeFromList emits. Without this, the emission races ahead of the subscription
        // and the value is lost (SharedFlow has replay=0).
        testDispatcher.scheduler.advanceUntilIdle()

        repository.removeFromList(bookmark.remoteId, bookmark.serverId, "list-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(bookmark.remoteId, emittedId)
        job.cancel()
    }

    // ──────────────────────────────────────────────────────────
    // updateTags
    // ──────────────────────────────────────────────────────────

    @Test
    fun updateTags_updatesLocalDbWithNewTags() = runTest(testDispatcher) {
        val bookmark = makeBookmark(tags = "old-tag")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        val newTags = listOf("new-tag-1", "new-tag-2")
        repository.updateTags(bookmark.remoteId, bookmark.serverId, newTags)

        val savedSlot = slot<BookmarkEntity>()
        coVerify { bookmarkDao.insertBookmark(capture(savedSlot)) }
        val resultTags = savedSlot.captured.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        assertTrue(resultTags.containsAll(newTags))
        assertFalse(resultTags.contains("old-tag"), "old-tag should have been replaced")
    }

    @Test
    fun updateTags_queuesPendingAction() = runTest(testDispatcher) {
        val bookmark = makeBookmark(tags = "")
        coEvery { bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId) } returns bookmark
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.updateTags(bookmark.remoteId, bookmark.serverId, listOf("tag-a"))

        val actionSlot = slot<PendingActionEntity>()
        coVerify { pendingActionDao.insertAction(capture(actionSlot)) }
        assertEquals(PendingActionType.UPDATE_TAGS, actionSlot.captured.actionType)
        assertTrue(actionSlot.captured.actionData.contains("tag-a"))
    }

    private fun makeBookmark(
        localId: Long = 1L,
        remoteId: String = "remote-42",
        serverId: String = "server1",
        tags: String = "",
        listIds: String = "",
        isRead: Boolean = false
    ) = BookmarkEntity(
        localId = 1L,
        remoteId = remoteId,
        serverId = serverId,
        title = "Test",
        url = "https://example.com",
        description = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        tags = tags,
        listIds = listIds,
        isStarred = false,
        isArchived = false,
        isRead = isRead,
        createdAt = 0L,
        readingTimeMinutes = 0,
        content = null
    )
}
