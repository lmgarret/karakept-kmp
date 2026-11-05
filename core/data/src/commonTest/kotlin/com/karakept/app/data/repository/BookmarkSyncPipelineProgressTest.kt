package com.karakept.app.data.repository

import com.karakept.app.testing.testBookmarkEntity
import com.karakept.api.model.PaginatedBookmarks
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.SyncProgress
import com.karakept.app.data.model.SyncStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

internal class BookmarkSyncPipelineProgressTest : BookmarkSyncPipelineTestBase() {

    // ──────────────────────────────────────────────────────────
    // Sync progress
    // ──────────────────────────────────────────────────────────

    @Test
    fun execute_updatesProgressFlow() = runTest(testDispatcher) {
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = emptyList(), nextCursor = null)

        val onProgressValues = mutableListOf<ListSyncStatus>()
        val pipeline = createPipeline(
            SyncConfiguration.Full(testServer),
            onProgress = { status -> onProgressValues.add(status) }
        )

        assertEquals(SyncProgress.Idle, syncProgress.value, "Should start at Idle")

        pipeline.execute()

        assertEquals(SyncProgress.Idle, syncProgress.value, "Should end at Idle")
        // onProgress must have been called at least once with FetchingMetadata
        assertTrue(
            onProgressValues.any { it is ListSyncStatus.FetchingMetadata },
            "onProgress should have been invoked with FetchingMetadata. Got: $onProgressValues"
        )
    }

    // ──────────────────────────────────────────────────────────
    // Efficiency (Group G)
    // ──────────────────────────────────────────────────────────

    @Test
    fun fullSync_doesNotFetchPerListMembership() = runTest(testDispatcher) {
        // Full sync no longer does the O(lists) N+1 membership fetch; membership is
        // authored by the ForList passes that run afterwards.
        val dto = makeBookmarkDto(id = "bk-1")
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify(exactly = 0) { remoteDataSource.fetchBookmarksForList(any(), any(), any()) }
    }

    @Test
    fun fullSync_skipsWriteWhenModifiedAtUnchanged() = runTest(testDispatcher) {
        val remoteId = "bk-1"
        val existing = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1",
            title = "Same",
            modifiedAt = 1_700_000_000_000L
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existing))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existing)
        // DTO with the same modifiedAt and identical user-visible fields
        val dto = makeBookmarkDto(id = "bk-1", title = "Same", modifiedAt = "2023-11-14T22:13:20Z")
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify(exactly = 0) { bookmarkDao.updateBookmarks(any()) }
        coVerify(exactly = 0) {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L, title = any(), url = any(), description = any(), imageUrl = any(),
                bannerImageAssetId = any(), screenshotAssetId = any(), tags = any(), listIds = any(),
                isStarred = any(), isArchived = any(), isRead = any(), readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    @Test
    fun fullSync_writesWhenModifiedAtChanged() = runTest(testDispatcher) {
        val remoteId = "bk-1"
        val existing = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1",
            title = "Old",
            modifiedAt = 1_600_000_000_000L
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existing))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existing)
        val dto = makeBookmarkDto(id = "bk-1", title = "New", modifiedAt = "2023-11-14T22:13:20Z")
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L, title = "New", url = any(), description = any(), imageUrl = any(),
                bannerImageAssetId = any(), screenshotAssetId = any(), tags = any(), listIds = any(),
                isStarred = any(), isArchived = any(), isRead = any(), readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    @Test
    fun forListSync_pullsReadingProgressWhenGateAllows() = runTest(testDispatcher) {
        // A user who only ever opens a list would never see progress from another device
        // back when phase 6 was scoped to Full syncs.
        val dto = makeBookmarkDto(id = "bk-1")
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-1", false)
        } returns listOf(dto)
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)

        val pipeline = createPipeline(
            SyncConfiguration.ForList(testServer, "list-1"),
            shouldPullReadingProgress = { true }
        )
        pipeline.execute()

        coVerify { bookmarkDao.getReadingProgressPullCandidates(any(), any(), any()) }
    }

    @Test
    fun forListSync_skipsReadingProgressWhenGateDeclines() = runTest(testDispatcher) {
        // The gate is what keeps a fan-out of list syncs from multiplying the per-bookmark
        // tRPC calls by the number of lists.
        val dto = makeBookmarkDto(id = "bk-1")
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-1", false)
        } returns listOf(dto)
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)

        val pipeline = createPipeline(
            SyncConfiguration.ForList(testServer, "list-1"),
            shouldPullReadingProgress = { false }
        )
        pipeline.execute()

        coVerify(exactly = 0) { bookmarkDao.getReadingProgressPullCandidates(any(), any(), any()) }
    }

    // ──────────────────────────────────────────────────────────
    // Sync warnings (Group H)
    // ──────────────────────────────────────────────────────────

    @Test
    fun contentFetchFailure_recordedAsWarning_syncStillCompletes() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1")
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)
        coEvery { fetchRemoteContent(any(), any()) } throws RuntimeException("network down")

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        val newCount = pipeline.execute()

        assertEquals(1, newCount, "Sync completes despite the content failure")
        assertTrue(pipeline.warnings.any { it.phase == "content" }, "Content failure should be recorded as a warning")
    }

    @Test
    fun highlightSyncFailure_recordedAsWarning() = runTest(testDispatcher) {
        coEvery { highlightRepository.syncHighlights(any()) } returns false

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        assertTrue(pipeline.warnings.any { it.phase == "highlights" }, "Highlight failure should be recorded as a warning")
    }

    @Test
    fun successfulSync_hasNoWarnings() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1")
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        assertTrue(pipeline.warnings.isEmpty(), "A clean sync must produce no warnings")
    }

    @Test
    fun fullSync_readingProgressUsesRotatingCursor() = runTest(testDispatcher) {
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)
        coEvery {
            bookmarkDao.getReadingProgressPullCandidates("server1", null, PROGRESS_PULL_ROTATION)
        } returns listOf(makeTarget(localId = 7L, remoteId = "bk-cur"))

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Candidates come from the rotating-cursor query and get stamped after the pull
        coVerify { bookmarkDao.getReadingProgressPullCandidates("server1", null, PROGRESS_PULL_ROTATION) }
        coVerify { bookmarkDao.markProgressSyncedAt(match { 7L in it }, any()) }
    }

    @Test
    fun readingProgressRunsBeforeContentDownload() = runTest(testDispatcher) {
        // Progress decides what the user sees — the unread count, which rows are faded.
        // A content download changes nothing on the list and runs for minutes on a list
        // configured for offline reading; behind it, the state the screen is judged by
        // arrived last.
        val events = mutableListOf<String>()
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)
        coEvery { bookmarkDao.getReadingProgressPullCandidates(any(), any(), any()) } coAnswers {
            events += "reading-progress"
            emptyList()
        }
        coEvery { fetchRemoteContent(any(), any()) } coAnswers {
            events += "content"
            "<p>body</p>"
        }
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-1")), nextCursor = null)

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        assertEquals(listOf("reading-progress", "content"), events)
    }

    @Test
    fun listSync_asksAboutTheListBeingSyncedFirst() = runTest(testDispatcher) {
        // A pass covers a bounded slice of the library, so which slice decides whether the
        // list the user is looking at agrees with the server.
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)
        coEvery { remoteDataSource.fetchBookmarksForList(any(), any(), any()) } returns emptyList()

        createPipeline(SyncConfiguration.ForList(testServer, "list-a")).execute()

        coVerify { bookmarkDao.getReadingProgressPullCandidates("server1", "list-a", PROGRESS_PULL_ROTATION) }
    }

    @Test
    fun readingProgressPull_asksForEveryCandidateInOneBatch() = runTest(testDispatcher) {
        // One query per bookmark is the server's only shape, but tRPC batches at the
        // transport: a pass must not cost one round trip per row.
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)
        val targets = (1L..30L).map { makeTarget(localId = it, remoteId = "bk-$it") }
        coEvery {
            bookmarkDao.getReadingProgressPullCandidates(any(), any(), any())
        } returns targets

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        coVerify(exactly = 1) {
            remoteDataSource.getReadingProgressBatch(testServer, targets.map { it.remoteId }, any())
        }
        coVerify(exactly = 1) { remoteDataSource.getReadingProgressBatch(any(), any(), any()) }
    }

    @Test
    fun fullSync_backfillsEveryNeverPulledBookmark() = runTest(testDispatcher) {
        // The rotating cursor alone converged 50 rows per sync, so a library of hundreds
        // needed hundreds of syncs before the unread count was right.
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)
        val firstBatch = (1..50).map { makeTarget(localId = it.toLong(), remoteId = "bk-$it") }
        val secondBatch = (51..70).map { makeTarget(localId = it.toLong(), remoteId = "bk-$it") }
        coEvery {
            bookmarkDao.getNeverProgressSyncedTargets("server1", PROGRESS_PULL_BATCH)
        } returnsMany listOf(firstBatch, secondBatch, emptyList())

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify { bookmarkDao.markProgressSyncedAt(match { 1L in it }, any()) }
        coVerify { bookmarkDao.markProgressSyncedAt(match { 70L in it }, any()) }
        // The rotation is for steady state — a backfill pass does not also run it.
        coVerify(exactly = 0) { bookmarkDao.getReadingProgressPullCandidates(any(), any(), any()) }
    }

    @Test
    fun fullSync_backfillStopsWhenEveryPullInABatchFails() = runTest(testDispatcher) {
        // Nothing gets stamped when the whole batch fails, so the same rows come back —
        // without this guard the loop would never end.
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)
        val batch = listOf(makeTarget(localId = 7L, remoteId = "bk-cur"))
        coEvery { bookmarkDao.getNeverProgressSyncedTargets("server1", PROGRESS_PULL_BATCH) } returns batch
        coEvery {
            remoteDataSource.getReadingProgressBatch(any(), any(), any())
        } throws com.karakept.app.data.remote.ApiException("HTTP 500", statusCode = 500)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify(exactly = 0) { bookmarkDao.markProgressSyncedAt(any(), any()) }
    }

    @Test
    fun fullSync_failedProgressPullDoesNotAdvanceCursor() = runTest(testDispatcher) {
        // Stamping a bookmark whose pull never reached the server would rotate it to the
        // back of the queue for a whole cycle with its progress still missing.
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)
        coEvery {
            bookmarkDao.getReadingProgressPullCandidates("server1", null, PROGRESS_PULL_ROTATION)
        } returns listOf(makeTarget(localId = 7L, remoteId = "bk-cur"))
        coEvery {
            remoteDataSource.getReadingProgressBatch(any(), any(), any())
        } throws com.karakept.app.data.remote.ApiException("HTTP 500", statusCode = 500)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify(exactly = 0) { bookmarkDao.markProgressSyncedAt(any(), any()) }
    }
}
