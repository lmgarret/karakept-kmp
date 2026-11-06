package com.karakept.app.data.repository

import com.karakept.app.testing.testBookmarkEntity
import com.karakept.api.model.PaginatedBookmarks
import com.karakept.app.data.model.SyncProgress
import io.mockk.coEvery
import io.mockk.coVerify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

internal class BookmarkSyncPipelineConfigurationTest : BookmarkSyncPipelineTestBase() {

    // ──────────────────────────────────────────────────────────
    // Full sync configuration
    // ──────────────────────────────────────────────────────────

    @Test
    fun fullSync_execute_processesAllPhases() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1")
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Phase 1: pending actions processed (accesses pendingActionDao)
        coVerify { pendingActionDao.getPendingActionsList("server1") }
        // Phase 2: metadata fetched
        coVerify { remoteDataSource.fetchBookmarks(testServer, null, any(), false, null, null) }
        // Phase 2.5: highlights synced
        coVerify { highlightRepository.syncHighlights(testServer) }
        // Phase 4: new bookmark inserted
        coVerify { bookmarkDao.insertBookmarks(any()) }
        // Progress ends at Idle
        assertEquals(SyncProgress.Idle, syncProgress.value)
    }

    @Test
    fun fullSync_deletesRemovedBookmarks() = runTest(testDispatcher) {
        val existingRemoteId = "bk-existing"
        val existingEntity = testBookmarkEntity(
            localId = 10L,
            remoteId = "bk-existing"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))

        // Remote returns empty list -- bookmark was deleted on server
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = emptyList(), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Full sync should delete removed bookmarks, in one batched call
        coVerify { bookmarkDao.deleteBookmarks(listOf(existingEntity)) }
    }

    @Test
    fun fullSync_deletesRemovedBookmarksEvenWhenInserting() = runTest(testDispatcher) {
        // Regression: deletions used to be skipped whenever the same sync inserted bookmarks
        val existingRemoteId = "bk-removed"
        val existingEntity = testBookmarkEntity(
            localId = 10L,
            remoteId = "bk-removed"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))

        // Remote no longer has bk-removed but has a brand-new bookmark
        val newDto = makeBookmarkDto(id = "bk-new")
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(newDto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify { bookmarkDao.insertBookmarks(any()) }
        coVerify { bookmarkDao.deleteBookmarks(listOf(existingEntity)) }
    }

    @Test
    fun fullSync_doesNotDeleteBookmarksWithPendingActions() = runTest(testDispatcher) {
        val pendingRemoteId = "bk-pending"
        val pendingEntity = testBookmarkEntity(
            localId = 11L,
            remoteId = "bk-pending"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(pendingEntity))
        coEvery { pendingActionDao.getPendingActionsList("server1") } returns listOf(
            com.karakept.app.data.local.entity.PendingActionEntity(
                id = 1L,
                bookmarkRemoteId = pendingRemoteId,
                serverId = "server1",
                actionType = com.karakept.app.data.local.entity.PendingActionType.ARCHIVE,
                actionData = "",
                createdAt = 0L,
                retryCount = 0
            )
        )

        // Remote no longer returns the bookmark (e.g. archived filter server-side),
        // but a local action is still queued for it — it must survive the sync.
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = emptyList(), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify(exactly = 0) { bookmarkDao.deleteBookmark(pendingEntity) }
    }

    @Test
    fun fullSync_updatesExistingBookmarks() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1", title = "Updated Title")
        val remoteId = "bk-1"
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Should update metadata (no content in DTO)
        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L,
                title = "Updated Title",
                url = any(),
                description = any(),
                imageUrl = any(),
                bannerImageAssetId = any(),
                screenshotAssetId = any(),
                tags = any(),
                listIds = any(),
                isStarred = false,
                isArchived = false,
                isRead = false,
                readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    // ──────────────────────────────────────────────────────────
    // Filtered sync configuration
    // ──────────────────────────────────────────────────────────

    @Test
    fun filteredSync_doesNotDeleteRemovedBookmarks() = runTest(testDispatcher) {
        val existingRemoteId = "bk-existing"
        val existingEntity = testBookmarkEntity(
            localId = 10L,
            remoteId = "bk-existing"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))

        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = emptyList(), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Filtered(testServer, archived = true))
        pipeline.execute()

        // Filtered sync should NOT delete
        coVerify(exactly = 0) { bookmarkDao.deleteBookmark(any()) }
    }

    @Test
    fun filteredSync_passesFilterToApi() = runTest(testDispatcher) {
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = emptyList(), nextCursor = null)

        val pipeline = createPipeline(
            SyncConfiguration.Filtered(testServer, archived = true, favourited = null)
        )
        pipeline.execute()

        coVerify {
            remoteDataSource.fetchBookmarks(
                server = testServer,
                cursor = null,
                includeContent = false,
                archived = true,
                favourited = null,
                limit = any()
            )
        }
    }

    // ──────────────────────────────────────────────────────────
    // ForList sync configuration
    // ──────────────────────────────────────────────────────────

    @Test
    fun forListSync_usesFetchBookmarksForListEndpoint() = runTest(testDispatcher) {
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-123", false)
        } returns emptyList()

        val pipeline = createPipeline(SyncConfiguration.ForList(testServer, "list-123"))
        pipeline.execute()

        coVerify { remoteDataSource.fetchBookmarksForList(testServer, "list-123", includeContent = false) }
        // Should NOT use the generic fetchBookmarks endpoint
        coVerify(exactly = 0) { remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any()) }
    }

    // ──────────────────────────────────────────────────────────
    // Return value propagation (NOTIF-01)
    // ──────────────────────────────────────────────────────────

    @Test
    fun execute_returnsNewBookmarkCount_whenThreeNewBookmarksInserted() = runTest(testDispatcher) {
        // Remote returns 3 new bookmarks, none in local DB
        val dtos = listOf(
            makeBookmarkDto(id = "new-1"),
            makeBookmarkDto(id = "new-2"),
            makeBookmarkDto(id = "new-3")
        )
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = dtos, nextCursor = null)
        // Local DB is empty — all 3 are new
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns emptyList()

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        val result = pipeline.execute()

        assertEquals(3, result)
    }

    @Test
    fun execute_returnsZero_whenNoNewBookmarks() = runTest(testDispatcher) {
        // Remote returns 1 bookmark that already exists in local DB
        val dto = makeBookmarkDto(id = "existing-bk")
        val existingRemoteId = "existing-bk"
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "existing-bk"
        )
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        val result = pipeline.execute()

        assertEquals(0, result)
    }
}
