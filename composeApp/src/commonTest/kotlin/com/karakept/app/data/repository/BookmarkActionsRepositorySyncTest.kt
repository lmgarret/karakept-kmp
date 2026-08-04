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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for BookmarkActionsRepositorySync extension functions.
 *
 * Verifies processPendingActions, getPendingActionBookmarkIds,
 * pullReadingProgressFromServer, and executeAction dispatch and retry logic.
 */
class BookmarkActionsRepositorySyncTest : BaseRepositoryTest() {

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
        settingsRepository = settingsRepository
    )

    private val testServer = Server(
        id = "server1",
        url = "https://example.com",
        apiKey = "key",
        label = "Test"
    )

    override fun setup() {
        super.setup()
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))
        coEvery { settingsRepository.offlineMode } returns flowOf(true)
    }

    // ──────────────────────────────────────────────────────────
    // processPendingActions
    // ──────────────────────────────────────────────────────────

    @Test
    fun processPendingActions_noPendingActions_returnsEmptyList() = runTest {
        coEvery { pendingActionDao.getPendingActionsList("server1") } returns emptyList()

        val result = repository.processPendingActions(testServer)

        assertTrue(result.isEmpty())
    }

    @Test
    fun processPendingActions_withActions_returnsProcessedBookmarkIds() = runTest {
        val action1 = makePendingAction(
            actionType = PendingActionType.ARCHIVE,
            bookmarkRemoteId = 10L
        )
        val action2 = makePendingAction(
            actionType = PendingActionType.FAVOURITE,
            bookmarkRemoteId = 20L
        )
        coEvery { pendingActionDao.getProcessableActions("server1", any()) } returns listOf(action1, action2)
        coEvery {
            bookmarkDao.getBookmarkByRemoteId(10L, "server1")
        } returns makeBookmark(remoteId = 10L)
        coEvery {
            bookmarkDao.getBookmarkByRemoteId(20L, "server1")
        } returns makeBookmark(remoteId = 20L)

        val result = repository.processPendingActions(testServer)

        assertEquals(2, result.size)
        assertTrue(result.contains(10L))
        assertTrue(result.contains(20L))
    }

    // ──────────────────────────────────────────────────────────
    // getPendingActionBookmarkIds
    // ──────────────────────────────────────────────────────────

    @Test
    fun getPendingActionBookmarkIds_returnsDistinctIds() = runTest {
        val actions = listOf(
            makePendingAction(bookmarkRemoteId = 10L, actionType = PendingActionType.ARCHIVE),
            makePendingAction(bookmarkRemoteId = 10L, actionType = PendingActionType.FAVOURITE),
            makePendingAction(bookmarkRemoteId = 20L, actionType = PendingActionType.DELETE)
        )
        coEvery { pendingActionDao.getPendingActionsList("server1") } returns actions

        val result = repository.getPendingActionBookmarkIds("server1")

        assertEquals(2, result.size)
        assertTrue(result.contains(10L))
        assertTrue(result.contains(20L))
    }

    // ──────────────────────────────────────────────────────────
    // pullReadingProgressFromServer
    // ──────────────────────────────────────────────────────────

    @Test
    fun pullReadingProgress_serverNotFound_returnsFalse() = runTest {
        coEvery { serverRepository.servers } returns flowOf(emptyList())

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertFalse(result)
    }

    @Test
    fun pullReadingProgress_bookmarkNotFound_returnsFalse() = runTest {
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns null

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertFalse(result)
    }

    @Test
    fun pullReadingProgress_serverProgressHigher_updatesLocal_returnsTrue() = runTest {
        val bookmark = makeBookmark(readingProgress = 0.2f)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.getReadingProgress(testServer, bookmark.originalRemoteId)
        } returns 50

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertTrue(result)
        coVerify {
            bookmarkDao.updateReadingProgress(
                localId = bookmark.localId,
                progress = 0.5f,
                scrollIndex = 0,
                scrollOffset = 0
            )
        }
    }

    @Test
    fun pullReadingProgress_serverProgressLower_doesNotUpdate_returnsFalse() = runTest {
        val bookmark = makeBookmark(readingProgress = 0.8f)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.getReadingProgress(testServer, bookmark.originalRemoteId)
        } returns 30

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertFalse(result)
        coVerify(exactly = 0) { bookmarkDao.updateReadingProgress(any(), any(), any(), any()) }
    }

    @Test
    fun pullReadingProgress_serverReturnsNull_returnsFalse() = runTest {
        val bookmark = makeBookmark()
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.getReadingProgress(testServer, bookmark.originalRemoteId)
        } returns null

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertFalse(result)
    }

    // ──────────────────────────────────────────────────────────
    // executeAction (internal)
    // ──────────────────────────────────────────────────────────

    @Test
    fun executeAction_archiveType_callsUpdateBookmarkWithArchivedTrue() = runTest {
        val action = makePendingAction(actionType = PendingActionType.ARCHIVE)
        val bookmark = makeBookmark()
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark

        repository.executeAction(action, "server1")

        coVerify {
            remoteDataSource.updateBookmark(testServer, bookmark.originalRemoteId, match { it.archived == true })
        }
        coVerify { pendingActionDao.deleteAction(action) }
    }

    @Test
    fun executeAction_deleteType_callsDeleteBookmark() = runTest {
        val action = makePendingAction(
            actionType = PendingActionType.DELETE,
            actionData = """{"originalRemoteId":"remote-42"}"""
        )

        repository.executeAction(action, "server1")

        coVerify { remoteDataSource.deleteBookmark(testServer, "remote-42") }
        coVerify { pendingActionDao.deleteAction(action) }
    }

    @Test
    fun executeAction_actionFails_incrementsRetryCount() = runTest {
        val action = makePendingAction(
            actionType = PendingActionType.ARCHIVE,
            retryCount = 0
        )
        val bookmark = makeBookmark()
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.updateBookmark(any(), any(), any())
        } throws RuntimeException("network error")

        repository.executeAction(action, "server1")

        coVerify {
            pendingActionDao.updateAction(match { it.retryCount == 1 })
        }
    }

    @Test
    fun executeAction_retryCountExceeded_marksFailedInsteadOfDeleting() = runTest {
        // retryCount 4 → the next transient failure hits the 5-retry cap
        val action = makePendingAction(
            actionType = PendingActionType.ARCHIVE,
            retryCount = 4
        )
        val bookmark = makeBookmark()
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.updateBookmark(any(), any(), any())
        } throws RuntimeException("persistent error")

        repository.executeAction(action, "server1")

        coVerify {
            pendingActionDao.updateAction(match {
                it.retryCount == 5 &&
                    it.status == com.karakept.app.data.local.entity.PendingActionEntity.STATUS_FAILED
            })
        }
        coVerify(exactly = 0) { pendingActionDao.deleteAction(action) }
    }

    @Test
    fun executeAction_bookmarkNotFound_deletesOrphanedAction() = runTest {
        val action = makePendingAction(actionType = PendingActionType.ARCHIVE)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns null

        repository.executeAction(action, "server1")

        coVerify { pendingActionDao.deleteAction(action) }
    }

    // ──────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────

    private fun makePendingAction(
        actionType: String = PendingActionType.ARCHIVE,
        bookmarkRemoteId: Long = 42L,
        serverId: String = "server1",
        actionData: String = "{}",
        retryCount: Int = 0
    ) = PendingActionEntity(
        id = 0,
        bookmarkRemoteId = bookmarkRemoteId,
        serverId = serverId,
        actionType = actionType,
        actionData = actionData,
        createdAt = System.currentTimeMillis(),
        retryCount = retryCount
    )

    private fun makeBookmark(
        remoteId: Long = 42L,
        serverId: String = "server1",
        tags: String = "",
        listIds: String = "",
        isRead: Boolean = false,
        readingProgress: Float = 0f
    ) = BookmarkEntity(
        localId = 1L,
        remoteId = remoteId,
        originalRemoteId = "remote-$remoteId",
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
        content = null,
        readingProgress = readingProgress
    )
}
