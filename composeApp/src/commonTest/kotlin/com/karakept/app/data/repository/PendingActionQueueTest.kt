package com.karakept.app.data.repository

import com.karakept.api.model.BookmarksBookmarkIdPatchRequest
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the offline-first action queue in [BookmarkActionsRepository].
 *
 * Covers ordering, conflict resolution, retry-threshold deletion, and server
 * rejection retry-increment scenarios for [processPendingActions].
 */
class PendingActionQueueTest {

    // ---- Shared test fixtures ----

    private val testServer = Server(
        id = "server-1",
        url = "https://test.example.com",
        apiKey = "test-key",
        label = "Test"
    )

    private val bookmarkDao: BookmarkDao = mockk(relaxed = true)
    private val pendingActionDao: PendingActionDao = mockk(relaxed = true)
    private val remoteDataSource: RemoteDataSource = mockk(relaxed = true)
    private val serverRepository: ServerRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private val repository = BookmarkActionsRepository(
        bookmarkDao = bookmarkDao,
        pendingActionDao = pendingActionDao,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository
    )

    init {
        // serverRepository.servers must emit a list containing testServer so
        // executeAction can look it up.
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))
    }

    // ---- Helper ----

    private fun pendingAction(
        actionType: String,
        bookmarkRemoteId: Long = 100L,
        createdAt: Long = System.currentTimeMillis(),
        retryCount: Int = 0,
        actionData: String = "{}",
        id: Long = 0L
    ) = PendingActionEntity(
        id = id,
        bookmarkRemoteId = bookmarkRemoteId,
        serverId = testServer.id,
        actionType = actionType,
        actionData = actionData,
        createdAt = createdAt,
        retryCount = retryCount
    )

    /**
     * Stub [bookmarkDao] so that `getBookmarkByRemoteId` returns a minimal
     * bookmark entity with the given `originalRemoteId`.
     */
    private fun stubBookmarkLookup(remoteId: Long, originalRemoteId: String = "orig-$remoteId") {
        val entity = com.karakept.app.data.local.entity.BookmarkEntity(
            localId = remoteId,
            remoteId = remoteId,
            originalRemoteId = originalRemoteId,
            serverId = testServer.id,
            url = "https://test.example.com/$remoteId",
            title = "Test Bookmark $remoteId",
            content = null,
            imageUrl = null,
            bannerImageAssetId = null,
            screenshotAssetId = null,
            description = null,
            createdAt = 1000L,
            isArchived = false,
            isStarred = false
        )
        coEvery { bookmarkDao.getBookmarkByRemoteId(remoteId, testServer.id) } returns entity
    }

    // ---- Test 1: Ordering ----

    @Test
    fun actionsProcessedInCreationOrder() = runTest {
        val action1 = pendingAction(PendingActionType.ARCHIVE, bookmarkRemoteId = 1L, createdAt = 1000, id = 1)
        val action2 = pendingAction(PendingActionType.FAVOURITE, bookmarkRemoteId = 2L, createdAt = 2000, id = 2)
        val action3 = pendingAction(PendingActionType.UNARCHIVE, bookmarkRemoteId = 3L, createdAt = 3000, id = 3)

        coEvery { pendingActionDao.getPendingActionsList(testServer.id) } returns listOf(action1, action2, action3)

        // Stub bookmark lookups so executeAction can resolve originalRemoteId
        stubBookmarkLookup(1L, "orig-1")
        stubBookmarkLookup(2L, "orig-2")
        stubBookmarkLookup(3L, "orig-3")

        // updateBookmark returns a relaxed Bookmark mock by default (relaxed = true)

        repository.processPendingActions(testServer)

        // Verify remote calls happened in createdAt order
        coVerifyOrder {
            remoteDataSource.updateBookmark(testServer, "orig-1", any())  // ARCHIVE
            remoteDataSource.updateBookmark(testServer, "orig-2", any())  // FAVOURITE
            remoteDataSource.updateBookmark(testServer, "orig-3", any())  // UNARCHIVE
        }
    }

    // ---- Test 2: Conflict resolution (last wins) ----

    @Test
    fun conflictingActionsLastWins() = runTest {
        val archiveAction = pendingAction(
            PendingActionType.ARCHIVE, bookmarkRemoteId = 42L, createdAt = 1, id = 1
        )
        val unarchiveAction = pendingAction(
            PendingActionType.UNARCHIVE, bookmarkRemoteId = 42L, createdAt = 2, id = 2
        )

        coEvery { pendingActionDao.getPendingActionsList(testServer.id) } returns listOf(archiveAction, unarchiveAction)
        stubBookmarkLookup(42L, "orig-42")

        val requestSlot = mutableListOf<BookmarksBookmarkIdPatchRequest>()
        coEvery {
            remoteDataSource.updateBookmark(testServer, "orig-42", capture(requestSlot))
        } returns mockk(relaxed = true)

        repository.processPendingActions(testServer)

        // Both calls should have been made
        assertEquals(2, requestSlot.size, "Both ARCHIVE and UNARCHIVE should execute")
        // First call: archived = true
        assertEquals(true, requestSlot[0].archived, "First call should archive")
        // Second call: archived = false (last wins)
        assertEquals(false, requestSlot[1].archived, "Second call should unarchive (last wins)")
    }

    // ---- Test 3: Timeout deletion after max retries ----

    @Test
    fun timeoutActionDeletedAfterMaxRetries() = runTest {
        // Action with retryCount = 5 (meets the >= 5 threshold)
        val staleAction = pendingAction(
            PendingActionType.ARCHIVE, bookmarkRemoteId = 99L, createdAt = 1000, retryCount = 5, id = 1
        )

        coEvery { pendingActionDao.getPendingActionsList(testServer.id) } returns listOf(staleAction)
        stubBookmarkLookup(99L, "orig-99")

        // Simulate server failure so the catch block triggers the retry-threshold check
        coEvery {
            remoteDataSource.updateBookmark(testServer, "orig-99", any())
        } throws RuntimeException("Server unavailable")

        repository.processPendingActions(testServer)

        // retryCount is incremented first, then the action is deleted because retryCount >= 5
        coVerify { pendingActionDao.updateAction(match { it.retryCount == 6 }) }
        coVerify { pendingActionDao.deleteAction(staleAction) }
    }

    // ---- Test 4: Server rejection increments retry ----

    @Test
    fun serverRejectionIncrementsRetry() = runTest {
        val action = pendingAction(
            PendingActionType.FAVOURITE, bookmarkRemoteId = 55L, createdAt = 1000, retryCount = 1, id = 1
        )

        coEvery { pendingActionDao.getPendingActionsList(testServer.id) } returns listOf(action)
        stubBookmarkLookup(55L, "orig-55")

        coEvery {
            remoteDataSource.updateBookmark(testServer, "orig-55", any())
        } throws RuntimeException("500 Internal Server Error")

        repository.processPendingActions(testServer)

        // retryCount should be incremented from 1 to 2
        coVerify {
            pendingActionDao.updateAction(match { it.retryCount == 2 && it.lastError != null })
        }
        // Action should NOT be deleted (retryCount 1 < 5)
        coVerify(exactly = 0) { pendingActionDao.deleteAction(any()) }
    }
}
