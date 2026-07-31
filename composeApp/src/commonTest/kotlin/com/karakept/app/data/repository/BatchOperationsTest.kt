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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for batch operations in [BookmarkActionsRepository].
 *
 * Verifies that batch operations correctly update local DB and queue
 * pending actions for all bookmarks in the batch.
 */
class BatchOperationsTest : BaseRepositoryTest() {

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
    // batchArchive
    // ──────────────────────────────────────────────────────────

    @Test
    fun batchArchive_archivesEachBookmarkLocally() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, isArchived = false)
        val b2 = makeBookmark(remoteId = 2L, isArchived = false)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchArchive(listOf(b1, b2))

        val slots = mutableListOf<BookmarkEntity>()
        coVerify(exactly = 2) { bookmarkDao.insertBookmark(capture(slots)) }
        assertTrue(slots.all { it.isArchived })
    }

    @Test
    fun batchArchive_queuesPendingActionForEachBookmark() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L)
        val b2 = makeBookmark(remoteId = 2L)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchArchive(listOf(b1, b2))

        val slots = mutableListOf<PendingActionEntity>()
        coVerify(exactly = 2) { pendingActionDao.insertAction(capture(slots)) }
        assertTrue(slots.all { it.actionType == PendingActionType.ARCHIVE })
    }

    // ──────────────────────────────────────────────────────────
    // batchUnarchive
    // ──────────────────────────────────────────────────────────

    @Test
    fun batchUnarchive_unarchivesEachBookmarkLocally() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, isArchived = true)
        val b2 = makeBookmark(remoteId = 2L, isArchived = true)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchUnarchive(listOf(b1, b2))

        val slots = mutableListOf<BookmarkEntity>()
        coVerify(exactly = 2) { bookmarkDao.insertBookmark(capture(slots)) }
        assertTrue(slots.all { !it.isArchived })
    }

    @Test
    fun batchUnarchive_queuesPendingActionForEachBookmark() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, isArchived = true)
        val b2 = makeBookmark(remoteId = 2L, isArchived = true)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchUnarchive(listOf(b1, b2))

        val slots = mutableListOf<PendingActionEntity>()
        coVerify(exactly = 2) { pendingActionDao.insertAction(capture(slots)) }
        assertTrue(slots.all { it.actionType == PendingActionType.UNARCHIVE })
    }

    // ──────────────────────────────────────────────────────────
    // batchMarkRead
    // ──────────────────────────────────────────────────────────

    @Test
    fun batchMarkRead_marksEachBookmarkAsReadLocally() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, isRead = false)
        val b2 = makeBookmark(remoteId = 2L, isRead = false)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2

        repository.batchMarkRead(listOf(b1, b2))

        val slots = mutableListOf<BookmarkEntity>()
        coVerify(exactly = 2) { bookmarkDao.insertBookmark(capture(slots)) }
        assertTrue(slots.all { it.isRead })
    }

    @Test
    fun batchMarkRead_queuesReadingProgressPendingAction() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, isRead = false)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1

        repository.batchMarkRead(listOf(b1))

        val slot = slot<PendingActionEntity>()
        coVerify(exactly = 1) { pendingActionDao.insertAction(capture(slot)) }
        assertEquals(PendingActionType.UPDATE_READING_PROGRESS, slot.captured.actionType)
    }

    // ──────────────────────────────────────────────────────────
    // batchMarkUnread
    // ──────────────────────────────────────────────────────────

    @Test
    fun batchMarkUnread_marksEachBookmarkAsUnreadLocally() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, isRead = true)
        val b2 = makeBookmark(remoteId = 2L, isRead = true)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2

        repository.batchMarkUnread(listOf(b1, b2))

        val slots = mutableListOf<BookmarkEntity>()
        coVerify(exactly = 2) { bookmarkDao.insertBookmark(capture(slots)) }
        assertTrue(slots.all { !it.isRead })
    }

    @Test
    fun batchMarkUnread_withResetProgress_clearsProgressForEachBookmark() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, isRead = true, readingProgress = 0.8f)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1

        repository.batchMarkUnread(listOf(b1), resetProgress = true)

        val savedSlot = slot<BookmarkEntity>()
        coVerify { bookmarkDao.insertBookmark(capture(savedSlot)) }
        assertFalse(savedSlot.captured.isRead)
        assertEquals(0f, savedSlot.captured.readingProgress)
    }

    // ──────────────────────────────────────────────────────────
    // batchSetFavourite
    // ──────────────────────────────────────────────────────────

    @Test
    fun batchSetFavourite_makeFavourite_setsStarredOnEachBookmark() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, isStarred = false)
        val b2 = makeBookmark(remoteId = 2L, isStarred = false)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchSetFavourite(listOf(b1, b2), makeFavourite = true)

        val slots = mutableListOf<BookmarkEntity>()
        coVerify(exactly = 2) { bookmarkDao.insertBookmark(capture(slots)) }
        assertTrue(slots.all { it.isStarred })
    }

    @Test
    fun batchSetFavourite_makeFavourite_queuesFavouriteAction() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, isStarred = false)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchSetFavourite(listOf(b1), makeFavourite = true)

        val actionSlot = slot<PendingActionEntity>()
        coVerify { pendingActionDao.insertAction(capture(actionSlot)) }
        assertEquals(PendingActionType.FAVOURITE, actionSlot.captured.actionType)
    }

    @Test
    fun batchSetFavourite_removeFavourite_queuesUnfavouriteAction() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, isStarred = true)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchSetFavourite(listOf(b1), makeFavourite = false)

        val actionSlot = slot<PendingActionEntity>()
        coVerify { pendingActionDao.insertAction(capture(actionSlot)) }
        assertEquals(PendingActionType.UNFAVOURITE, actionSlot.captured.actionType)
    }

    // ──────────────────────────────────────────────────────────
    // batchDelete
    // ──────────────────────────────────────────────────────────

    @Test
    fun batchDelete_deletesEachBookmarkFromLocalDb() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L)
        val b2 = makeBookmark(remoteId = 2L)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchDelete(listOf(b1, b2))

        coVerify(exactly = 2) { bookmarkDao.deleteBookmark(any()) }
    }

    @Test
    fun batchDelete_queuesPendingDeleteActionForEachBookmark() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L)
        val b2 = makeBookmark(remoteId = 2L)
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchDelete(listOf(b1, b2))

        val slots = mutableListOf<PendingActionEntity>()
        coVerify(exactly = 2) { pendingActionDao.insertAction(capture(slots)) }
        assertTrue(slots.all { it.actionType == PendingActionType.DELETE })
    }

    // ──────────────────────────────────────────────────────────
    // batchMoveToList
    // ──────────────────────────────────────────────────────────

    @Test
    fun batchMoveToList_addsListIdToEachBookmark() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, listIds = "")
        val b2 = makeBookmark(remoteId = 2L, listIds = "list-existing")
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchMoveToList(listOf(b1, b2), "list-99")

        val slots = mutableListOf<BookmarkEntity>()
        coVerify(exactly = 2) { bookmarkDao.insertBookmark(capture(slots)) }
        assertTrue(slots.all { bookmark ->
            bookmark.listIds.split(",").map { it.trim() }.contains("list-99")
        })
    }

    @Test
    fun batchMoveToList_queuesPendingActionForEachBookmark() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, listIds = "")
        val b2 = makeBookmark(remoteId = 2L, listIds = "")
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { bookmarkDao.getBookmarkByRemoteId(2L, "server1") } returns b2
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchMoveToList(listOf(b1, b2), "list-99")

        val slots = mutableListOf<PendingActionEntity>()
        coVerify(exactly = 2) { pendingActionDao.insertAction(capture(slots)) }
        assertTrue(slots.all { it.actionType == PendingActionType.MOVE_TO_LIST })
        assertTrue(slots.all { it.actionData.contains("list-99") })
    }

    @Test
    fun batchMoveToList_doesNotDuplicateExistingListId() = runTest(testDispatcher) {
        val b1 = makeBookmark(remoteId = 1L, listIds = "list-99")
        coEvery { bookmarkDao.getBookmarkByRemoteId(1L, "server1") } returns b1
        coEvery { settingsRepository.offlineMode } returns flowOf(true)

        repository.batchMoveToList(listOf(b1), "list-99")

        // insertBookmark should NOT be called since the listId already exists
        coVerify(exactly = 0) { bookmarkDao.insertBookmark(any()) }
    }

    // ──────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────

    private fun makeBookmark(
        remoteId: Long = 42L,
        serverId: String = "server1",
        tags: String = "",
        listIds: String = "",
        isRead: Boolean = false,
        isArchived: Boolean = false,
        isStarred: Boolean = false,
        readingProgress: Float = 0f
    ) = BookmarkEntity(
        localId = remoteId,
        remoteId = remoteId,
        originalRemoteId = "remote-$remoteId",
        serverId = serverId,
        title = "Test $remoteId",
        url = "https://example.com/$remoteId",
        description = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        tags = tags,
        listIds = listIds,
        isStarred = isStarred,
        isArchived = isArchived,
        isRead = isRead,
        createdAt = 0L,
        readingTimeMinutes = 0,
        readingProgress = readingProgress,
        content = null
    )
}
