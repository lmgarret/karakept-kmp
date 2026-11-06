package com.karakept.app.data.repository

import com.karakept.app.testing.testBookmarkEntity
import com.karakept.api.model.Bookmark
import com.karakept.api.model.PaginatedBookmarks
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.ListSyncConfig
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.domain.OfflineRetention
import io.mockk.coEvery
import io.mockk.coVerify
import kotlin.test.Test
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

internal class BookmarkSyncPipelineContentTest : BookmarkSyncPipelineTestBase() {

    // ──────────────────────────────────────────────────────────
    // Content sync strategy
    // ──────────────────────────────────────────────────────────

    @Test
    fun contentSync_strategyNever_doesNotFetchContent() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1")
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.NEVER)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        coVerify(exactly = 0) { fetchRemoteContent(any(), any()) }
    }

    @Test
    fun contentSync_strategyAll_fetchesContentForBookmarksWithoutContent() = runTest(testDispatcher) {
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)

        // Two DTOs: one without content, one with htmlContent
        val dto1 = makeBookmarkDto(id = "bk-1") // no htmlContent -> readingTime=0
        val dto2 = makeBookmarkDto(id = "bk-2", htmlContent = "<p>Has content already</p>")

        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto1, dto2), nextCursor = null)
        coEvery { fetchRemoteContent(any(), any()) } returns "<p>Fetched content</p>"
        coEvery { imageCacheManager.cacheImagesInHtml(any(), any()) } answers { firstArg() }

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Only dto1 (no content, readingTime=0) should trigger content fetch
        // dto2 has htmlContent so readingTime > 0 after mapping
        coVerify(exactly = 1) { fetchRemoteContent(testServer, "bk-1") }
    }

    @Test
    fun contentSync_doesNotRedownloadContentPastItsRetention() = runTest(testDispatcher) {
        // Cleanup dropped these bodies on purpose. Sync must not bring them straight back —
        // except for the one archived too recently to have been dropped.
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)
        coEvery { settingsRepository.activeOfflineRetentionDays } returns flowOf(7)
        val now = System.currentTimeMillis()
        val retired = testBookmarkEntity(localId = 1L, remoteId = "bk-old")
            .copy(isArchived = true, readOrArchivedAt = now - 30 * OfflineRetention.DAY_MILLIS)
        val recent = testBookmarkEntity(localId = 2L, remoteId = "bk-new")
            .copy(isArchived = true, readOrArchivedAt = now - OfflineRetention.DAY_MILLIS)
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(retired, recent)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(
            bookmarks = listOf(
                makeBookmarkDto(id = "bk-old", archived = true),
                makeBookmarkDto(id = "bk-new", archived = true)
            ),
            nextCursor = null
        )
        coEvery { fetchRemoteContent(any(), any()) } returns "<p>Fetched content</p>"
        coEvery { imageCacheManager.cacheImagesInHtml(any(), any()) } answers { firstArg() }

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        coVerify(exactly = 0) { fetchRemoteContent(testServer, "bk-old") }
        coVerify(exactly = 1) { fetchRemoteContent(testServer, "bk-new") }
    }

    @Test
    fun contentSync_leavesCopiesTheStorageCapEvictedEvicted() = runTest(testDispatcher) {
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)
        val evicted = testBookmarkEntity(localId = 1L, remoteId = "bk-1").copy(offlineEvictedAt = 1L)
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(evicted)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-1")), nextCursor = null)

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        coVerify(exactly = 0) { fetchRemoteContent(any(), any()) }
    }

    @Test
    fun contentSync_downloadsAgainOnceARetiredBookmarkIsUnarchived() = runTest(testDispatcher) {
        // The stamp is only cleared by the next cleanup; the flags the server just sent win.
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)
        coEvery { settingsRepository.activeOfflineRetentionDays } returns flowOf(7)
        val stale = testBookmarkEntity(localId = 1L, remoteId = "bk-1")
            .copy(isArchived = true, readOrArchivedAt = 1L)
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(stale)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(makeBookmarkDto(id = "bk-1", archived = false)), nextCursor = null)
        coEvery { fetchRemoteContent(any(), any()) } returns "<p>Fetched content</p>"
        coEvery { imageCacheManager.cacheImagesInHtml(any(), any()) } answers { firstArg() }

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        coVerify(exactly = 1) { fetchRemoteContent(testServer, "bk-1") }
    }

    @Test
    fun contentSync_strategyPerList_fetchesOnlyForTargetLists() = runTest(testDispatcher) {
        // After the membership-fetch removal (G1), PER_LIST content is fetched during the
        // ForList pass, where the bookmark's list membership is known.
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.PER_LIST)
        coEvery { settingsRepository.contentSyncConfig } returns flowOf(
            ListSyncConfig(selectedLists = setOf("list-1"), withChildrenMode = emptySet())
        )

        coEvery { remoteDataSource.fetchLists(any()) } returns listOf(
            com.karakept.api.model.KarakeepList(id = "list-1", name = "My List")
        )
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-1", false)
        } returns listOf(makeBookmarkDto(id = "bk-1"))

        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery { fetchRemoteContent(any(), any()) } returns "<p>Content</p>"
        coEvery { imageCacheManager.cacheImagesInHtml(any(), any()) } answers { firstArg() }

        val pipeline = createPipeline(SyncConfiguration.ForList(testServer, "list-1"))
        pipeline.execute()

        // Bookmark is in list-1 which is in target lists -> should fetch content
        coVerify(atLeast = 1) { fetchRemoteContent(testServer, "bk-1") }
    }

    @Test
    fun contentSync_strategyPerList_skipsBookmarksNotInTargetList() = runTest(testDispatcher) {
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.PER_LIST)
        coEvery { settingsRepository.contentSyncConfig } returns flowOf(
            ListSyncConfig(selectedLists = setOf("list-1"), withChildrenMode = emptySet())
        )

        val dto = makeBookmarkDto(id = "bk-99")
        coEvery { remoteDataSource.fetchLists(any()) } returns emptyList()
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Bookmark is NOT in target list -> should NOT fetch content
        coVerify(exactly = 0) { fetchRemoteContent(any(), any()) }
    }

    // ──────────────────────────────────────────────────────────
    // ForList content sync with per-list syncOffline setting
    // ──────────────────────────────────────────────────────────

    @Test
    fun forListSync_listWithSyncOffline_downloadsContent() = runTest(testDispatcher) {
        // Global strategy is NEVER, but list-123 has syncOffline = true
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.NEVER)
        coEvery { settingsRepository.allListSettings } returns flowOf(
            mapOf("list-123" to ListSettings(syncOffline = true))
        )

        val dto = makeBookmarkDto(id = "bk-offline")
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-123", false)
        } returns listOf(dto)
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery { fetchRemoteContent(any(), any()) } returns "<p>Offline content</p>"
        coEvery { imageCacheManager.cacheImagesInHtml(any(), any()) } answers { firstArg() }

        val pipeline = createPipeline(SyncConfiguration.ForList(testServer, "list-123"))
        pipeline.execute()

        // syncOffline = true for list-123 → content must be fetched
        coVerify(atLeast = 1) { fetchRemoteContent(testServer, "bk-offline") }
    }

    @Test
    fun forListSync_listWithoutSyncOffline_skipsContent() = runTest(testDispatcher) {
        // Global strategy is NEVER, and list-123 has syncOffline = false
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.NEVER)
        coEvery { settingsRepository.allListSettings } returns flowOf(
            mapOf("list-123" to ListSettings(syncOffline = false))
        )

        val dto = makeBookmarkDto(id = "bk-no-offline")
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-123", false)
        } returns listOf(dto)
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())

        val pipeline = createPipeline(SyncConfiguration.ForList(testServer, "list-123"))
        pipeline.execute()

        // syncOffline = false → content must NOT be fetched
        coVerify(exactly = 0) { fetchRemoteContent(any(), any()) }
    }
}
