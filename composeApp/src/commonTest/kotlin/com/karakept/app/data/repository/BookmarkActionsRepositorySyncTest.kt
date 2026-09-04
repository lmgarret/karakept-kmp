package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.dao.ProgressPullTarget
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.PendingActionEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for BookmarkActionsRepositorySync extension functions.
 *
 * Verifies processPendingActions, getPendingActionBookmarkIds,
 * pullReadingProgressFromServer, and executeAction dispatch and retry logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
        settingsRepository = settingsRepository,
        appDispatchers = testAppDispatchers
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
        // The compare-and-set write reports rows-affected; default to "the row hadn't moved"
        // so existing tests keep seeing APPLIED unless a test deliberately simulates the race.
        coEvery { bookmarkDao.applyServerReadingProgress(any(), any(), any(), any(), any()) } returns 1
    }

    // ──────────────────────────────────────────────────────────
    // processPendingActions
    // ──────────────────────────────────────────────────────────

    @Test
    fun processPendingActions_noPendingActions_returnsEmptyList() = runTest(testDispatcher) {
        coEvery { pendingActionDao.getPendingActionsList("server1") } returns emptyList()

        val result = repository.processPendingActions(testServer)

        assertTrue(result.isEmpty())
    }

    @Test
    fun processPendingActions_withActions_returnsProcessedBookmarkIds() = runTest(testDispatcher) {
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
    fun getPendingActionBookmarkIds_returnsDistinctIds() = runTest(testDispatcher) {
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
    fun pullReadingProgress_serverNotFound_reportsFailure() = runTest(testDispatcher) {
        coEvery { serverRepository.servers } returns flowOf(emptyList())

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertEquals(ReadingProgressPullResult.FAILED, result)
    }

    @Test
    fun pullReadingProgress_bookmarkNotFound_reportsFailure() = runTest(testDispatcher) {
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns null

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertEquals(ReadingProgressPullResult.FAILED, result)
    }

    @Test
    fun pullReadingProgress_serverProgressHigher_updatesLocal_reportsApplied() = runTest(testDispatcher) {
        val bookmark = makeBookmark(readingProgress = 0.2f)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.getReadingProgressBatch(any(), any(), any())
        } returns mapOf(bookmark.originalRemoteId to 50)

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertEquals(ReadingProgressPullResult.APPLIED, result)
        coVerify {
            bookmarkDao.applyServerReadingProgress(
                localId = bookmark.localId,
                expectedProgress = 0.2f,
                progress = 0.5f,
                scrollIndex = 0,
                scrollOffset = 0
            )
        }
    }

    // Read state has no server-side field: the percentage carries it, so a bookmark marked
    // unread elsewhere arrives as a *lower* value and must still be applied.
    @Test
    fun pullReadingProgress_serverProgressLower_stillApplied() = runTest(testDispatcher) {
        val bookmark = makeBookmark(readingProgress = 0.8f)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.getReadingProgressBatch(any(), any(), any())
        } returns mapOf(bookmark.originalRemoteId to 0)

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertEquals(ReadingProgressPullResult.APPLIED, result)
        coVerify {
            bookmarkDao.applyServerReadingProgress(
                localId = bookmark.localId,
                expectedProgress = 0.8f,
                progress = 0f,
                scrollIndex = 0,
                scrollOffset = 0
            )
        }
    }

    // ...but only when this device has nothing of its own still queued, which is the one
    // signal that the local value is the newer of the two.
    @Test
    fun pullReadingProgress_unsyncedLocalProgress_keepsLocal() = runTest(testDispatcher) {
        val bookmark = makeBookmark(readingProgress = 0.8f)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery { pendingActionDao.getPendingActionsList("server1") } returns listOf(
            makePendingAction(
                bookmarkRemoteId = 42L,
                actionType = PendingActionType.UPDATE_READING_PROGRESS
            )
        )

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertEquals(ReadingProgressPullResult.SKIPPED, result)
        coVerify(exactly = 0) { bookmarkDao.applyServerReadingProgress(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { remoteDataSource.getReadingProgressBatch(any(), any(), any()) }
    }

    // The write moves the read flag, and the list holds a snapshot of its rows while the
    // drawer's unread count reads the table live. Without an event the two disagree: the
    // count reports a bookmark the pull turned back to unread and the list keeps drawing it
    // as read, so there is nothing on screen to scroll to (#333).
    @Test
    fun pullReadingProgress_applied_asksTheListToReload() = runTest(testDispatcher) {
        val bookmark = makeBookmark(readingProgress = 1f)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.getReadingProgressBatch(any(), any(), any())
        } returns mapOf(bookmark.originalRemoteId to 0)

        var reloads = 0
        val collector = backgroundScope.launch {
            repository.bookmarksReloaded.collect { reloads++ }
        }
        runCurrent()

        val result = repository.pullReadingProgressFromServer(42L, "server1")
        runCurrent()
        collector.cancel()

        assertEquals(ReadingProgressPullResult.APPLIED, result)
        assertEquals(1, reloads, "the rows the list is holding are now out of date")
    }

    @Test
    fun pullReadingProgress_skipped_asksForNoReload() = runTest(testDispatcher) {
        val bookmark = makeBookmark(readingProgress = 0.3f)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.getReadingProgressBatch(any(), any(), any())
        } returns mapOf(bookmark.originalRemoteId to 30)

        var reloads = 0
        val collector = backgroundScope.launch {
            repository.bookmarksReloaded.collect { reloads++ }
        }
        runCurrent()

        repository.pullReadingProgressFromServer(42L, "server1")
        runCurrent()
        collector.cancel()

        // A backfill walks the whole library; only a pass that changed something may ask.
        assertEquals(0, reloads, "nothing changed, so nothing to re-read")
    }

    // ──────────────────────────────────────────────────────────
    // Batched pull + conflict rule
    // ──────────────────────────────────────────────────────────

    private fun target(
        localId: Long = 1L,
        remoteId: Long = 42L,
        originalRemoteId: String = "remote-$remoteId",
        readingProgress: Float = 0f
    ) = ProgressPullTarget(localId, remoteId, originalRemoteId, readingProgress)

    @Test
    fun batchPull_asksForEveryTargetInOneCall() = runTest(testDispatcher) {
        val targets = listOf(target(1L, 10L, "a"), target(2L, 20L, "b"), target(3L, 30L, "c"))
        coEvery { remoteDataSource.getReadingProgressBatch(any(), any(), any()) } returns
            mapOf("a" to 100, "b" to null, "c" to 40)

        val outcomes = repository.pullReadingProgressForTargets(targets, "server1")

        coVerify(exactly = 1) {
            remoteDataSource.getReadingProgressBatch(testServer, listOf("a", "b", "c"), any())
        }
        assertEquals(ReadingProgressPullResult.APPLIED, outcomes[10L])
        // The server holding nothing is the absence of a statement, not a claim of unread.
        assertEquals(ReadingProgressPullResult.SKIPPED, outcomes[20L])
        assertEquals(ReadingProgressPullResult.APPLIED, outcomes[30L])
        coVerify(exactly = 0) { bookmarkDao.applyServerReadingProgress(2L, any(), any(), any(), any()) }
    }

    @Test
    fun batchPull_neverAsksAboutABookmarkWithAPushOutstanding() = runTest(testDispatcher) {
        // The conflict rule: a bookmark this device still has something to say about is left
        // out of the request, so the answer can never arrive and overwrite it. The count
        // covers failed pushes as well as queued ones — a push that ran out of retries is
        // still local state the server has never heard.
        coEvery { pendingActionDao.getPendingActionsList("server1") } returns listOf(
            makePendingAction(
                bookmarkRemoteId = 20L,
                actionType = PendingActionType.UPDATE_READING_PROGRESS
            )
        )
        val targets = listOf(target(1L, 10L, "a"), target(2L, 20L, "b", readingProgress = 1f))
        coEvery { remoteDataSource.getReadingProgressBatch(any(), any(), any()) } returns mapOf("a" to 10)

        val outcomes = repository.pullReadingProgressForTargets(targets, "server1")

        coVerify(exactly = 1) {
            remoteDataSource.getReadingProgressBatch(testServer, listOf("a"), any())
        }
        assertEquals(ReadingProgressPullResult.SKIPPED, outcomes[20L])
        coVerify(exactly = 0) { bookmarkDao.applyServerReadingProgress(2L, any(), any(), any(), any()) }
    }

    @Test
    fun batchPull_unansweredIdsReportFailedSoTheCursorHolds() = runTest(testDispatcher) {
        // A partial answer must not stamp the rows it left out: they would rotate to the back
        // of the queue without the server ever having been asked about them.
        val targets = listOf(target(1L, 10L, "a"), target(2L, 20L, "b"))
        coEvery { remoteDataSource.getReadingProgressBatch(any(), any(), any()) } returns mapOf("a" to 50)

        val outcomes = repository.pullReadingProgressForTargets(targets, "server1")

        assertEquals(ReadingProgressPullResult.APPLIED, outcomes[10L])
        assertEquals(ReadingProgressPullResult.FAILED, outcomes[20L])
    }

    @Test
    fun batchPull_requestFailure_reportsFailedForEveryMember() = runTest(testDispatcher) {
        val targets = listOf(target(1L, 10L, "a"), target(2L, 20L, "b"))
        coEvery {
            remoteDataSource.getReadingProgressBatch(any(), any(), any())
        } throws com.karakept.app.data.remote.ApiException("HTTP 500", statusCode = 500)

        val outcomes = repository.pullReadingProgressForTargets(targets, "server1")

        assertEquals(ReadingProgressPullResult.FAILED, outcomes[10L])
        assertEquals(ReadingProgressPullResult.FAILED, outcomes[20L])
        coVerify(exactly = 0) { bookmarkDao.applyServerReadingProgress(any(), any(), any(), any(), any()) }
    }

    @Test
    fun batchPull_asksForOneReloadPerPassNotPerRow() = runTest(testDispatcher) {
        val targets = listOf(target(1L, 10L, "a"), target(2L, 20L, "b"))
        coEvery { remoteDataSource.getReadingProgressBatch(any(), any(), any()) } returns
            mapOf("a" to 100, "b" to 0)

        var reloads = 0
        val collector = backgroundScope.launch {
            repository.bookmarksReloaded.collect { reloads++ }
        }
        runCurrent()

        repository.pullReadingProgressForTargets(targets, "server1")
        runCurrent()
        collector.cancel()

        // Two rows in the batch, one of which moved — and one signal either way. Per-row
        // events would cost the list a read and a rebuild apiece, and a pass applies hundreds.
        assertEquals(1, reloads)
    }

    // A pass snapshots [target]'s progress before the network round trip, which can take
    // seconds. If the row is scroll-marked (or otherwise changed) locally in that window,
    // the row the DAO's WHERE clause matches against has already moved, so the compare-and-set
    // affects zero rows — simulated here by stubbing 0 back instead of the default 1. The
    // pull must treat that the same as "nothing new to apply", not clobber the local write (#333).
    @Test
    fun batchPull_localChangedDuringTheRoundTrip_doesNotClobberIt() = runTest(testDispatcher) {
        val target = target(1L, 10L, "a", readingProgress = 0.6f)
        coEvery { remoteDataSource.getReadingProgressBatch(any(), any(), any()) } returns mapOf("a" to 40)
        coEvery {
            bookmarkDao.applyServerReadingProgress(
                localId = 1L,
                expectedProgress = 0.6f,
                progress = 0.4f,
                scrollIndex = 0,
                scrollOffset = 0
            )
        } returns 0

        val outcomes = repository.pullReadingProgressForTargets(listOf(target), "server1")

        assertEquals(ReadingProgressPullResult.SKIPPED, outcomes[10L])
    }

    @Test
    fun pullReadingProgress_serverMatchesLocal_reportsSkipped() = runTest(testDispatcher) {
        val bookmark = makeBookmark(readingProgress = 0.3f)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.getReadingProgressBatch(any(), any(), any())
        } returns mapOf(bookmark.originalRemoteId to 30)

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertEquals(ReadingProgressPullResult.SKIPPED, result)
        coVerify(exactly = 0) { bookmarkDao.applyServerReadingProgress(any(), any(), any(), any(), any()) }
    }

    @Test
    fun pullReadingProgress_serverHasNoProgress_reportsSkipped() = runTest(testDispatcher) {
        val bookmark = makeBookmark()
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.getReadingProgressBatch(any(), any(), any())
        } returns mapOf(bookmark.originalRemoteId to null)

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertEquals(ReadingProgressPullResult.SKIPPED, result)
    }

    // A failed request must stay distinguishable from "nothing to apply": the sync
    // pipeline advances its rotating cursor on the pull having reached the server.
    @Test
    fun pullReadingProgress_requestFails_reportsFailure() = runTest(testDispatcher) {
        val bookmark = makeBookmark()
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.getReadingProgressBatch(any(), any(), any())
        } throws com.karakept.app.data.remote.ApiException("HTTP 500", statusCode = 500)

        val result = repository.pullReadingProgressFromServer(42L, "server1")

        assertEquals(ReadingProgressPullResult.FAILED, result)
        coVerify(exactly = 0) { bookmarkDao.applyServerReadingProgress(any(), any(), any(), any(), any()) }
    }

    // ──────────────────────────────────────────────────────────
    // markAsUnread
    // ──────────────────────────────────────────────────────────

    // Read state has no field of its own on the server; the reading percentage is the only
    // thing carrying it, so an unread that clears the position is the one that can travel.
    @Test
    fun markAsUnread_withResetProgress_pushesClearedProgress() = runTest(testDispatcher) {
        val bookmark = makeBookmark(readingProgress = 1f, isRead = true)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark

        repository.markAsUnread(42L, "server1", resetProgress = true)

        coVerify {
            pendingActionDao.insertAction(match {
                it.actionType == PendingActionType.UPDATE_READING_PROGRESS &&
                    it.actionData.contains("\"0\"")
            })
        }
        coVerify {
            bookmarkDao.insertBookmark(match {
                !it.isRead && it.readingProgress == 0f &&
                    it.readingScrollIndex == 0 && it.readingScrollOffset == 0
            })
        }
    }

    // Keeping the position is an explicit user setting, and it is incompatible with telling
    // the server: there is no value meaning "unread but 80% in". The unread stays local.
    @Test
    fun markAsUnread_keepingProgress_staysLocal() = runTest(testDispatcher) {
        val bookmark = makeBookmark(readingProgress = 1f, isRead = true)
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark

        repository.markAsUnread(42L, "server1", resetProgress = false)

        coVerify { bookmarkDao.insertBookmark(match { !it.isRead && it.readingProgress == 1f }) }
        coVerify(exactly = 0) {
            pendingActionDao.insertAction(match {
                it.actionType == PendingActionType.UPDATE_READING_PROGRESS
            })
        }
    }

    // ──────────────────────────────────────────────────────────
    // executeAction (internal)
    // ──────────────────────────────────────────────────────────

    @Test
    fun executeAction_archiveType_callsUpdateBookmarkWithArchivedTrue() = runTest(testDispatcher) {
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
    fun executeAction_deleteType_callsDeleteBookmark() = runTest(testDispatcher) {
        val action = makePendingAction(
            actionType = PendingActionType.DELETE,
            actionData = """{"originalRemoteId":"remote-42"}"""
        )

        repository.executeAction(action, "server1")

        coVerify { remoteDataSource.deleteBookmark(testServer, "remote-42") }
        coVerify { pendingActionDao.deleteAction(action) }
    }

    @Test
    fun executeAction_actionFails_incrementsRetryCount() = runTest(testDispatcher) {
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
    fun executeAction_retryCountExceeded_marksFailedInsteadOfDeleting() = runTest(testDispatcher) {
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
    fun executeAction_readingProgressPushed_deletesAction() = runTest(testDispatcher) {
        val action = makePendingAction(
            actionType = PendingActionType.UPDATE_READING_PROGRESS,
            actionData = """{"progressPercent":"73"}"""
        )
        val bookmark = makeBookmark()
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.updateReadingProgress(testServer, bookmark.originalRemoteId, 73)
        } returns true

        repository.executeAction(action, "server1")

        coVerify { pendingActionDao.deleteAction(action) }
    }

    // A rejected push used to be dropped as if it had synced, leaving the progress local
    // forever with nothing surfaced to the user.
    @Test
    fun executeAction_readingProgressRejected_keepsActionForRetry() = runTest(testDispatcher) {
        val action = makePendingAction(
            actionType = PendingActionType.UPDATE_READING_PROGRESS,
            actionData = """{"progressPercent":"73"}"""
        )
        val bookmark = makeBookmark()
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.updateReadingProgress(any(), any(), any())
        } throws com.karakept.app.data.remote.ApiException("HTTP 500", statusCode = 500)

        repository.executeAction(action, "server1")

        coVerify(exactly = 0) { pendingActionDao.deleteAction(action) }
        coVerify { pendingActionDao.updateAction(match { it.retryCount == 1 }) }
    }

    // A server that has no reading-progress route at all can never accept this action —
    // park it as failed so the user sees it instead of retrying forever.
    @Test
    fun executeAction_readingProgressRouteMissing_marksActionFailed() = runTest(testDispatcher) {
        val action = makePendingAction(
            actionType = PendingActionType.UPDATE_READING_PROGRESS,
            actionData = """{"progressPercent":"73"}"""
        )
        val bookmark = makeBookmark()
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.updateReadingProgress(any(), any(), any())
        } throws com.karakept.app.data.remote.ApiException("HTTP 404", statusCode = 404)

        repository.executeAction(action, "server1")

        coVerify {
            pendingActionDao.updateAction(match {
                it.status == com.karakept.app.data.local.entity.PendingActionEntity.STATUS_FAILED
            })
        }
        coVerify(exactly = 0) { pendingActionDao.deleteAction(action) }
    }

    // Karakeep only keeps progress for link bookmarks; that rejection is final and there is
    // nothing to retry, so the queue must not hold the action forever.
    @Test
    fun executeAction_readingProgressNotStorableForBookmarkType_dropsAction() = runTest(testDispatcher) {
        val action = makePendingAction(
            actionType = PendingActionType.UPDATE_READING_PROGRESS,
            actionData = """{"progressPercent":"73"}"""
        )
        val bookmark = makeBookmark()
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returns bookmark
        coEvery {
            remoteDataSource.updateReadingProgress(any(), any(), any())
        } returns false

        repository.executeAction(action, "server1")

        coVerify { pendingActionDao.deleteAction(action) }
    }

    @Test
    fun executeAction_bookmarkNotFound_deletesOrphanedAction() = runTest(testDispatcher) {
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
