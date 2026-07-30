package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for enqueue-time deduplication of pending actions (D10): rapid toggles must
 * not enqueue N redundant rows that all replay to the server.
 */
class PendingActionDedupTest {

    private val testServer = Server(
        id = "server-1",
        url = "https://test.example.com",
        apiKey = "test-key",
        label = "Test"
    )

    private val bookmarkDao: BookmarkDao = mockk(relaxed = true)
    private val pendingActionDao: PendingActionDao = mockk(relaxed = true)
    private val remoteDataSource: RemoteDataSource = mockk(relaxed = true)
    private val serverRepository: ServerRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)

    private val repository = BookmarkActionsRepository(
        bookmarkDao = bookmarkDao,
        pendingActionDao = pendingActionDao,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository
    )

    init {
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))
        // Offline so triggerAutoSync does nothing and we only observe enqueue behavior
        coEvery { settingsRepository.offlineMode } returns flowOf(true)
    }

    private fun stubBookmark(remoteId: Long, listIds: String = "") {
        val entity = BookmarkEntity(
            localId = remoteId,
            remoteId = remoteId,
            originalRemoteId = "orig-$remoteId",
            serverId = testServer.id,
            url = "https://test.example.com/$remoteId",
            title = "Bookmark $remoteId",
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
        coEvery { bookmarkDao.getBookmarkByRemoteId(remoteId, testServer.id) } returns entity
    }

    @Test
    fun archiveThenUnarchive_deletesBothPriorTypesBeforeEnqueue() = runTest {
        stubBookmark(1L)

        repository.archiveBookmark(1L, testServer.id)
        repository.unarchiveBookmark(1L, testServer.id)

        // Each enqueue first clears prior archive+unarchive rows for the bookmark
        coVerify(atLeast = 2) { pendingActionDao.deleteActionsForBookmarkByType(1L, testServer.id, PendingActionType.ARCHIVE) }
        coVerify(atLeast = 2) { pendingActionDao.deleteActionsForBookmarkByType(1L, testServer.id, PendingActionType.UNARCHIVE) }
    }

    @Test
    fun updateTags_deletesPriorTagUpdateBeforeEnqueue() = runTest {
        stubBookmark(2L)

        repository.updateTags(2L, testServer.id, listOf("a", "b"), isOnline = false)

        coVerify { pendingActionDao.deleteActionsForBookmarkByType(2L, testServer.id, PendingActionType.UPDATE_TAGS) }
    }

    @Test
    fun delete_purgesAllOtherQueuedActionsFirst() = runTest {
        stubBookmark(3L)

        repository.deleteBookmark(3L, 3L, testServer.id)

        coVerify { pendingActionDao.deleteActionsForBookmark(3L, testServer.id) }
    }

    @Test
    fun moveThenRemoveSameList_cancelsMatchingMembershipRow() = runTest {
        stubBookmark(4L)
        // After the move enqueues, the queue contains a MOVE_TO_LIST for list-1.
        coEvery { pendingActionDao.getPendingActionsList(testServer.id) } returns listOf(
            PendingActionEntity(
                id = 10L,
                bookmarkRemoteId = 4L,
                serverId = testServer.id,
                actionType = PendingActionType.MOVE_TO_LIST,
                actionData = """{"listId":"list-1"}""",
                createdAt = 1L
            )
        )

        repository.removeFromList(4L, testServer.id, "list-1", isOnline = false)

        // The matching move row for list-1 is cancelled out before the remove is queued
        coVerify {
            pendingActionDao.deleteAction(match {
                it.actionType == PendingActionType.MOVE_TO_LIST && it.bookmarkRemoteId == 4L
            })
        }
    }
}
