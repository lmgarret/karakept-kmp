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

        coEvery { pendingActionDao.getProcessableActions(testServer.id, any()) } returns listOf(action1, action2, action3)

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

        coEvery { pendingActionDao.getProcessableActions(testServer.id, any()) } returns listOf(archiveAction, unarchiveAction)
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

    // ---- Test 3: Exhausted retries mark the action failed (never silently dropped) ----

    @Test
    fun exhaustedRetriesMarkActionFailedInsteadOfDeleting() = runTest {
        // Action with retryCount = 4: the next transient failure reaches the 5-retry cap
        val staleAction = pendingAction(
            PendingActionType.ARCHIVE, bookmarkRemoteId = 99L, createdAt = 1000, retryCount = 4, id = 1
        )

        coEvery { pendingActionDao.getProcessableActions(testServer.id, any()) } returns listOf(staleAction)
        stubBookmarkLookup(99L, "orig-99")

        // Transient failure (no HTTP status) so it counts against the retry budget
        coEvery {
            remoteDataSource.updateBookmark(testServer, "orig-99", any())
        } throws RuntimeException("Server unavailable")

        repository.processPendingActions(testServer)

        // Marked failed and preserved — the user can retry/discard, not silently lost
        coVerify {
            pendingActionDao.updateAction(match {
                it.retryCount == 5 && it.status == PendingActionEntity.STATUS_FAILED
            })
        }
        coVerify(exactly = 0) { pendingActionDao.deleteAction(any()) }
    }

    // ---- Test 4: Transient server rejection increments retry with backoff ----

    @Test
    fun transientRejectionIncrementsRetryWithBackoff() = runTest {
        val action = pendingAction(
            PendingActionType.FAVOURITE, bookmarkRemoteId = 55L, createdAt = 1000, retryCount = 1, id = 1
        )

        coEvery { pendingActionDao.getProcessableActions(testServer.id, any()) } returns listOf(action)
        stubBookmarkLookup(55L, "orig-55")

        coEvery {
            remoteDataSource.updateBookmark(testServer, "orig-55", any())
        } throws RuntimeException("connection reset")

        repository.processPendingActions(testServer)

        // retryCount 1 → 2, still pending, backoff window set into the future
        coVerify {
            pendingActionDao.updateAction(match {
                it.retryCount == 2 && it.lastError != null &&
                    it.status == PendingActionEntity.STATUS_PENDING && it.nextAttemptAt > 0
            })
        }
        coVerify(exactly = 0) { pendingActionDao.deleteAction(any()) }
    }

    // ---- Test 5: Permanent server rejection fails on the first attempt ----

    @Test
    fun permanentRejectionMarksFailedImmediately() = runTest {
        val action = pendingAction(
            PendingActionType.MOVE_TO_LIST, bookmarkRemoteId = 77L, createdAt = 1000, retryCount = 0, id = 1,
            actionData = """{"listId":"list-1"}"""
        )

        coEvery { pendingActionDao.getProcessableActions(testServer.id, any()) } returns listOf(action)
        stubBookmarkLookup(77L, "orig-77")

        coEvery {
            remoteDataSource.addBookmarkToList(testServer, "list-1", "orig-77")
        } throws com.karakept.app.data.remote.ApiException("HTTP 422: unprocessable", statusCode = 422)

        repository.processPendingActions(testServer)

        // Permanent 4xx → failed on first attempt, no retry burn, not deleted
        coVerify {
            pendingActionDao.updateAction(match { it.status == PendingActionEntity.STATUS_FAILED })
        }
        coVerify(exactly = 0) { pendingActionDao.deleteAction(any()) }
    }
}
