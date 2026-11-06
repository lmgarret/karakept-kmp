package com.karakept.app.data.repository

import com.karakept.app.testing.testBookmarkEntity
import com.karakept.api.model.Bookmark
import com.karakept.api.model.PaginatedBookmarks
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncProgress
import com.karakept.app.data.model.SyncStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

internal class BookmarkSyncPipelineDifferentialTest : BookmarkSyncPipelineTestBase() {

    // ──────────────────────────────────────────────────────────
    // Differential sync
    // ──────────────────────────────────────────────────────────

    @Test
    fun differentialSync_skipsBookmarksWithPendingActions() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1", title = "Should Be Skipped")
        val remoteId = "bk-1"
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        // Simulate pending action for this bookmark
        coEvery { pendingActionDao.getPendingActionsList("server1") } returns listOf(
            com.karakept.app.data.local.entity.PendingActionEntity(
                id = 1L,
                bookmarkRemoteId = remoteId,
                serverId = "server1",
                actionType = com.karakept.app.data.local.entity.PendingActionType.ARCHIVE,
                actionData = "",
                createdAt = 0L,
                retryCount = 0
            )
        )
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Should NOT update metadata for bookmark with pending actions
        coVerify(exactly = 0) {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L,
                title = any(),
                url = any(),
                description = any(),
                imageUrl = any(),
                bannerImageAssetId = any(),
                screenshotAssetId = any(),
                tags = any(),
                listIds = any(),
                isStarred = any(),
                isArchived = any(),
                isRead = any(),
                readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
        coVerify(exactly = 0) { bookmarkDao.updateBookmarks(any()) }
    }

    @Test
    fun differentialSync_insertsNewBookmarks() = runTest(testDispatcher) {
        val dto1 = makeBookmarkDto(id = "bk-1")
        val dto2 = makeBookmarkDto(id = "bk-2")
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto1, dto2), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify {
            bookmarkDao.insertBookmarks(match { it.size == 2 })
        }
    }

    @Test
    fun differentialSync_preservesLocalReadingProgress() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1", title = "Updated")
        val remoteId = "bk-1"
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1",
            readingProgress = 0.5f,
            readingTimeMinutes = 10,
            content = "HAS_CONTENT"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Since the existing bookmark has content but DTO has no htmlContent,
        // the entity should preserve the existing readingTimeMinutes
        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L,
                title = "Updated",
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
                readingTimeMinutes = 10,
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    // ──────────────────────────────────────────────────────────
    // Edge cases
    // ──────────────────────────────────────────────────────────

    @Test
    fun execute_emptyRemoteBookmarks_completesWithoutError() = runTest(testDispatcher) {
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = emptyList(), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        assertEquals(SyncProgress.Idle, syncProgress.value)
    }

    @Test
    fun fullSync_paginatedFetch_fetchesAllPages() = runTest(testDispatcher) {
        val dto1 = makeBookmarkDto(id = "bk-1")
        val dto2 = makeBookmarkDto(id = "bk-2")

        coEvery {
            remoteDataSource.fetchBookmarks(testServer, null, any(), false, null, null)
        } returns PaginatedBookmarks(bookmarks = listOf(dto1), nextCursor = "page2")

        coEvery {
            remoteDataSource.fetchBookmarks(testServer, "page2", any(), false, null, null)
        } returns PaginatedBookmarks(bookmarks = listOf(dto2), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify(exactly = 2) { remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any()) }
        // Pages are committed as they arrive rather than accumulated, so each one is its
        // own insert — that is what lets the first page reach the UI after one round trip.
        coVerify(exactly = 1) { bookmarkDao.insertBookmarks(match { it.singleOrNull()?.remoteId == "bk-1" }) }
        coVerify(exactly = 1) { bookmarkDao.insertBookmarks(match { it.singleOrNull()?.remoteId == "bk-2" }) }
    }

    @Test
    fun fullSync_commitsEachPageBeforeTheNextIsFetched() = runTest(testDispatcher) {
        // The point of streaming: rows from page 1 must be in the DB before page 2 is even
        // requested, so a large library fills in progressively instead of all at the end.
        val events = mutableListOf<String>()

        coEvery {
            remoteDataSource.fetchBookmarks(testServer, null, any(), false, null, null)
        } coAnswers {
            events += "fetch-page-1"
            PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-1")), nextCursor = "page2")
        }
        coEvery {
            remoteDataSource.fetchBookmarks(testServer, "page2", any(), false, null, null)
        } coAnswers {
            events += "fetch-page-2"
            PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-2")), nextCursor = null)
        }
        coEvery { bookmarkDao.insertBookmarks(any()) } coAnswers {
            val batch = firstArg<List<BookmarkEntity>>()
            events += "insert-${batch.single().remoteId}"
            listOf(1L)
        }

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        assertEquals(
            listOf("fetch-page-1", "insert-bk-1", "fetch-page-2", "insert-bk-2"),
            events
        )
    }

    @Test
    fun fullSync_pageCommittedFiresPerPage() = runTest(testDispatcher) {
        var pagesCommitted = 0
        coEvery {
            remoteDataSource.fetchBookmarks(testServer, null, any(), false, null, null)
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-1")), nextCursor = "page2")
        coEvery {
            remoteDataSource.fetchBookmarks(testServer, "page2", any(), false, null, null)
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-2")), nextCursor = null)

        BookmarkSyncPipeline(
            config = SyncConfiguration.Full(testServer),
            bookmarkDao = bookmarkDao,
            assetDao = assetDao,
            remoteDataSource = remoteDataSource,
            bookmarkActionsRepository = bookmarkActionsRepository,
            settingsRepository = settingsRepository,
            highlightRepository = highlightRepository,
            imageCacheManager = imageCacheManager,
            listDao = listDao,
            syncProgress = syncProgress,
            fetchRemoteContent = fetchRemoteContent,
            cacheHeroAssetsForBookmark = cacheHeroAssetsForBookmark,
            onPageCommitted = { pagesCommitted++ }
        ).execute()

        assertEquals(2, pagesCommitted)
    }

    @Test
    fun fullSync_failedMidFetch_keepsCommittedPagesAndDeletesNothing() = runTest(testDispatcher) {
        // A fetch that dies part-way must not be read as "the server dropped everything we
        // didn't see" — the pages that did land stay, and no deletion pass runs.
        val existingEntity = testBookmarkEntity(
            localId = 10L,
            remoteId = "bk-existing"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))

        coEvery {
            remoteDataSource.fetchBookmarks(testServer, null, any(), false, null, null)
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-1")), nextCursor = "page2")
        coEvery {
            remoteDataSource.fetchBookmarks(testServer, "page2", any(), false, null, null)
        } throws RuntimeException("connection reset")

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        var thrown: Exception? = null
        try {
            pipeline.execute()
        } catch (e: Exception) {
            thrown = e
        }

        assertEquals("connection reset", thrown?.message)
        // Page 1 was committed before the failure...
        coVerify { bookmarkDao.insertBookmarks(match { it.singleOrNull()?.remoteId == "bk-1" }) }
        // ...and nothing was deleted on the strength of a partial view of the server.
        coVerify(exactly = 0) { bookmarkDao.deleteBookmarks(any()) }
        coVerify(exactly = 0) { bookmarkDao.deleteBookmark(any()) }
    }

    @Test
    fun fullSync_foregroundCompletesBeforeEnrichmentPhases() = runTest(testDispatcher) {
        // The busy indicator is released once the visible rows land. Highlights and reading
        // progress must run strictly after that, or the spinner outlives the useful work.
        val events = mutableListOf<String>()
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)
        coEvery { bookmarkDao.getReadingProgressPullCandidates(any(), any(), any()) } coAnswers {
            events += "reading-progress"
            emptyList()
        }
        coEvery { highlightRepository.syncHighlights(any()) } coAnswers {
            events += "highlights"
            true
        }
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-1")), nextCursor = null)

        BookmarkSyncPipeline(
            config = SyncConfiguration.Full(testServer),
            bookmarkDao = bookmarkDao,
            assetDao = assetDao,
            remoteDataSource = remoteDataSource,
            bookmarkActionsRepository = bookmarkActionsRepository,
            settingsRepository = settingsRepository,
            highlightRepository = highlightRepository,
            imageCacheManager = imageCacheManager,
            listDao = listDao,
            syncProgress = syncProgress,
            fetchRemoteContent = fetchRemoteContent,
            cacheHeroAssetsForBookmark = cacheHeroAssetsForBookmark,
            onForegroundComplete = { events += "foreground-complete" }
        ).execute()

        assertEquals(listOf("foreground-complete", "highlights", "reading-progress"), events)
    }

    @Test
    fun fullSync_readsLocalStateOncePerSyncNotOncePerPage() = runTest(testDispatcher) {
        // Committing page by page must not turn one table read into one per page.
        coEvery {
            remoteDataSource.fetchBookmarks(testServer, null, any(), false, null, null)
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-1")), nextCursor = "page2")
        coEvery {
            remoteDataSource.fetchBookmarks(testServer, "page2", any(), false, null, null)
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-2")), nextCursor = null)

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        coVerify(exactly = 1) { bookmarkDao.getBookmarksForServerWithContentInfo("server1") }
    }

    @Test
    fun listSync_stripsListIdFromBookmarkRemovedOnServer() = runTest(testDispatcher) {
        // computeStaleListRemovals is well covered as a pure function, but nothing asserted
        // that the pipeline actually wires it up — that the fetched ids reach it and the
        // stripped listIds reach the DAO. This pins the end-to-end path.
        val stale = testBookmarkEntity(
            localId = 77L,
            remoteId = "bk-stale",
            listIds = "list-1"
        )
        // Server no longer returns bk-stale for list-1
        coEvery { remoteDataSource.fetchBookmarksForList(testServer, "list-1", any()) } returns emptyList()
        coEvery { bookmarkDao.getAllBookmarksForList("server1", "list-1") } returns listOf(stale)
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(stale)
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(stale))

        createPipeline(SyncConfiguration.ForList(testServer, "list-1")).execute()

        coVerify { bookmarkDao.updateBookmarkMetadata(localId = 77L, listIds = "", title = any(), url = any(), description = any(), imageUrl = any(), bannerImageAssetId = any(), screenshotAssetId = any(), tags = any(), isStarred = any(), isArchived = any(), isRead = any(), readingTimeMinutes = any(), modifiedAt = any(), crawlStatus = any(), crawledAt = any(), summary = any(), summarizationStatus = any()) }
    }

    @Test
    fun fullSync_skipsEnrichmentWhenGateDeclines() = runTest(testDispatcher) {
        // The sync key is released once the foreground stage finishes, so a second sync can
        // start while the first is still enriching. The gate is what stops the two of them
        // downloading the same content twice.
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-1")), nextCursor = null)

        var foregroundCompleted = false
        BookmarkSyncPipeline(
            config = SyncConfiguration.Full(testServer),
            bookmarkDao = bookmarkDao,
            assetDao = assetDao,
            remoteDataSource = remoteDataSource,
            bookmarkActionsRepository = bookmarkActionsRepository,
            settingsRepository = settingsRepository,
            highlightRepository = highlightRepository,
            imageCacheManager = imageCacheManager,
            listDao = listDao,
            syncProgress = syncProgress,
            fetchRemoteContent = fetchRemoteContent,
            cacheHeroAssetsForBookmark = cacheHeroAssetsForBookmark,
            onForegroundComplete = { foregroundCompleted = true },
            shouldRunEnrichment = { false }
        ).execute()

        // The visible rows still landed — only the enrichment tail was skipped.
        assertTrue(foregroundCompleted)
        coVerify { bookmarkDao.insertBookmarks(any()) }
        coVerify(exactly = 0) { highlightRepository.syncHighlights(any()) }
        coVerify(exactly = 0) { bookmarkDao.getReadingProgressPullCandidates(any(), any(), any()) }
    }

    @Test
    fun fullSync_runsEnrichmentWhenGateAllows() = runTest(testDispatcher) {
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-1")), nextCursor = null)

        BookmarkSyncPipeline(
            config = SyncConfiguration.Full(testServer),
            bookmarkDao = bookmarkDao,
            assetDao = assetDao,
            remoteDataSource = remoteDataSource,
            bookmarkActionsRepository = bookmarkActionsRepository,
            settingsRepository = settingsRepository,
            highlightRepository = highlightRepository,
            imageCacheManager = imageCacheManager,
            listDao = listDao,
            syncProgress = syncProgress,
            fetchRemoteContent = fetchRemoteContent,
            cacheHeroAssetsForBookmark = cacheHeroAssetsForBookmark,
            shouldRunEnrichment = { true }
        ).execute()

        coVerify { highlightRepository.syncHighlights(any()) }
    }

    // ──────────────────────────────────────────────────────────
    // Additional coverage
    // ──────────────────────────────────────────────────────────

    @Test
    fun fullSync_existingBookmarkWithContent_updatesViaUpdateBookmarks() = runTest(testDispatcher) {
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)

        val dto = makeBookmarkDto(id = "bk-1", htmlContent = "<p>New content here</p>")
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

        // When entity has content, it should use updateBookmarks (full update) instead of updateBookmarkMetadata
        coVerify { bookmarkDao.updateBookmarks(any()) }
    }

    @Test
    fun forListSync_mergesListMembershipWithExisting() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1")
        val remoteId = "bk-1"
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1",
            listIds = "list-old"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-new", false)
        } returns listOf(dto)

        val pipeline = createPipeline(SyncConfiguration.ForList(testServer, "list-new"))
        pipeline.execute()

        // ForList sync should merge list IDs: existing "list-old" + new "list-new"
        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L,
                title = any(),
                url = any(),
                description = any(),
                imageUrl = any(),
                bannerImageAssetId = any(),
                screenshotAssetId = any(),
                tags = any(),
                listIds = match { it.contains("list-old") && it.contains("list-new") },
                isStarred = any(),
                isArchived = any(),
                isRead = any(),
                readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    @Test
    fun fullSync_processedPendingActionsAlsoSkipped() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1")
        val remoteId = "bk-1"
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1"
        )
        // processPendingActions processes the action and returns the bookmarkRemoteId
        coEvery { pendingActionDao.getPendingActionsList("server1") } returns listOf(
            com.karakept.app.data.local.entity.PendingActionEntity(
                id = 1L,
                bookmarkRemoteId = remoteId,
                serverId = "server1",
                actionType = com.karakept.app.data.local.entity.PendingActionType.ARCHIVE,
                actionData = "",
                createdAt = 0L,
                retryCount = 0
            )
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Bookmark should be skipped because it was in the processed set
        coVerify(exactly = 0) {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L,
                title = any(),
                url = any(),
                description = any(),
                imageUrl = any(),
                bannerImageAssetId = any(),
                screenshotAssetId = any(),
                tags = any(),
                listIds = any(),
                isStarred = any(),
                isArchived = any(),
                isRead = any(),
                readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    @Test
    fun fullSync_newBookmarksEmitsSyncComplete() = runTest(testDispatcher) {
        val progressValues = mutableListOf<SyncProgress>()
        val collectJob = launch {
            syncProgress.collect { progressValues.add(it) }
        }

        val dto = makeBookmarkDto(id = "bk-new")
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        collectJob.cancel()

        // Should have emitted SyncComplete with count = 1
        val syncComplete = progressValues.filterIsInstance<SyncProgress.SyncComplete>()
        assertTrue(syncComplete.isNotEmpty(), "Should have emitted SyncComplete")
        assertEquals(1, syncComplete.first().newBookmarksCount)
    }

    @Test
    fun fullSync_tagsPreservedFromDto() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1", tags = listOf("kotlin", "android"))
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify {
            bookmarkDao.insertBookmarks(match { bookmarks ->
                bookmarks.isNotEmpty() &&
                    bookmarks.first().tags.contains("kotlin") &&
                    bookmarks.first().tags.contains("android")
            })
        }
    }

    @Test
    fun filteredSync_preservesExistingListIds() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1")
        val remoteId = "bk-1"
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1",
            listIds = "list-preserved"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Filtered(testServer, archived = true))
        pipeline.execute()

        // Should preserve existing listIds for filtered syncs
        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L,
                title = any(),
                url = any(),
                description = any(),
                imageUrl = any(),
                bannerImageAssetId = any(),
                screenshotAssetId = any(),
                tags = any(),
                listIds = "list-preserved",
                isStarred = any(),
                isArchived = any(),
                isRead = any(),
                readingTimeMinutes = any(),
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    @Test
    fun forListSync_populatesListMembership() = runTest(testDispatcher) {
        // Membership is now authored by the ForList pass (Full no longer does the N+1).
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-A", false)
        } returns listOf(makeBookmarkDto(id = "bk-1"))
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())

        val pipeline = createPipeline(SyncConfiguration.ForList(testServer, "list-A"))
        pipeline.execute()

        // The inserted bookmark should have listIds containing "list-A"
        coVerify {
            bookmarkDao.insertBookmarks(match { bookmarks ->
                bookmarks.isNotEmpty() && bookmarks.first().listIds.contains("list-A")
            })
        }
    }
}
