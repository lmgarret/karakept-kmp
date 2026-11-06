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
import com.karakept.app.utils.TestAppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher

/**
 * Tests for enqueue-time deduplication of pending actions (D10): rapid toggles must
 * not enqueue N redundant rows that all replay to the server.
 */
class PendingActionDedupTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testAppDispatchers = TestAppDispatchers(testDispatcher)

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
        settingsRepository = settingsRepository,
        appDispatchers = testAppDispatchers
    )

    init {
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))
        // Offline so triggerAutoSync does nothing and we only observe enqueue behavior
        coEvery { settingsRepository.offlineMode } returns flowOf(true)
    }

    private fun stubBookmark(remoteId: String, listIds: String = "") {
        val entity = BookmarkEntity(
            localId = 1L,
            remoteId = remoteId,
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
    fun archiveThenUnarchive_deletesBothPriorTypesBeforeEnqueue() = runTest(testDispatcher) {
        stubBookmark("orig-1")

        repository.archiveBookmark("orig-1", testServer.id)
        repository.unarchiveBookmark("orig-1", testServer.id)

        // Each enqueue first clears prior archive+unarchive rows for the bookmark
        coVerify(atLeast = 2) { pendingActionDao.deleteActionsForBookmarkByType("orig-1", testServer.id, PendingActionType.ARCHIVE) }
        coVerify(atLeast = 2) { pendingActionDao.deleteActionsForBookmarkByType("orig-1", testServer.id, PendingActionType.UNARCHIVE) }
    }

    @Test
    fun updateTags_deletesPriorTagUpdateBeforeEnqueue() = runTest(testDispatcher) {
        stubBookmark("orig-2")

        repository.updateTags("orig-2", testServer.id, listOf("a", "b"))

        coVerify { pendingActionDao.deleteActionsForBookmarkByType("orig-2", testServer.id, PendingActionType.UPDATE_TAGS) }
    }

    @Test
    fun delete_purgesAllOtherQueuedActionsFirst() = runTest(testDispatcher) {
        stubBookmark("orig-3")

        repository.deleteBookmark(3L, "orig-3", testServer.id)

        coVerify { pendingActionDao.deleteActionsForBookmark("orig-3", testServer.id) }
    }

    @Test
    fun moveThenRemoveSameList_cancelsMatchingMembershipRow() = runTest(testDispatcher) {
        stubBookmark("orig-4")
        // After the move enqueues, the queue contains a MOVE_TO_LIST for list-1.
        coEvery { pendingActionDao.getPendingActionsList(testServer.id) } returns listOf(
            PendingActionEntity(
                id = 10L,
                bookmarkRemoteId = "orig-4",
                serverId = testServer.id,
                actionType = PendingActionType.MOVE_TO_LIST,
                actionData = """{"listId":"list-1"}""",
                createdAt = 1L
            )
        )

        repository.removeFromList("orig-4", testServer.id, "list-1")

        // The matching move row for list-1 is cancelled out before the remove is queued
        coVerify {
            pendingActionDao.deleteAction(match {
                it.actionType == PendingActionType.MOVE_TO_LIST && it.bookmarkRemoteId == "orig-4"
            })
        }
    }
}
