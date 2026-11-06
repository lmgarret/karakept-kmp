package com.karakept.app.data.repository

import com.karakept.app.testing.testBookmarkEntity
import com.karakept.api.model.Bookmark
import com.karakept.api.model.BookmarkContent
import com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response
import com.karakept.api.model.PaginatedBookmarks
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.SyncStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

internal class BookmarkSyncPipelineMappingTest : BookmarkSyncPipelineTestBase() {

    // ──────────────────────────────────────────────────────────
    // AI summary
    // ──────────────────────────────────────────────────────────

    @Test
    fun fullSync_storesASummaryGeneratedElsewhere() = runTest(testDispatcher) {
        // A summary made in the web UI (or by the crawler's auto-summarization) has to reach the
        // app through the ordinary sync — nothing else pulls it down.
        val dto = makeBookmarkDto(
            id = "bk-1",
            summary = "The server's summary.",
            summarizationStatus = Bookmark.SummarizationStatus.SUCCESS
        )
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L, title = any(), url = any(), description = any(), imageUrl = any(),
                bannerImageAssetId = any(), screenshotAssetId = any(), tags = any(), listIds = any(),
                isStarred = any(), isArchived = any(), isRead = any(), readingTimeMinutes = any(),
                modifiedAt = any(), crawlStatus = any(), crawledAt = any(),
                summary = "The server's summary.",
                summarizationStatus = "success"
            )
        }
    }

    @Test
    fun fullSync_doesNotSwallowASummaryOnlyChangeAsUnchanged() = runTest(testDispatcher) {
        // Karakeep does not always bump modifiedAt for a summary, so the unchanged check has to
        // compare the summary itself — otherwise the write is skipped and the summary never lands.
        val modifiedAt = "2026-01-02T00:00:00Z"
        val dto = makeBookmarkDto(id = "bk-1", modifiedAt = modifiedAt, summary = "Fresh summary.")
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1"
        ).copy(
            title = "Test",
            modifiedAt = Instant.parse(modifiedAt).toEpochMilliseconds(),
            summary = null
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L, title = any(), url = any(), description = any(), imageUrl = any(),
                bannerImageAssetId = any(), screenshotAssetId = any(), tags = any(), listIds = any(),
                isStarred = any(), isArchived = any(), isRead = any(), readingTimeMinutes = any(),
                modifiedAt = any(), crawlStatus = any(), crawledAt = any(),
                summary = "Fresh summary.", summarizationStatus = any()
            )
        }
    }

    @Test
    fun fullSync_keepsAKnownSummaryWhenAFilteredSyncOmitsIt() = runTest(testDispatcher) {
        // Same reasoning as crawlStatus: a response without the field must not blank what we know.
        val dto = makeBookmarkDto(id = "bk-1", summary = null)
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1"
        ).copy(summary = "Previously stored.", summarizationStatus = "success")
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        coVerify {
            bookmarkDao.updateBookmarkMetadata(
                localId = 5L, title = any(), url = any(), description = any(), imageUrl = any(),
                bannerImageAssetId = any(), screenshotAssetId = any(), tags = any(), listIds = any(),
                isStarred = any(), isArchived = any(), isRead = any(), readingTimeMinutes = any(),
                modifiedAt = any(), crawlStatus = any(), crawledAt = any(),
                summary = "Previously stored.", summarizationStatus = "success"
            )
        }
    }

    // ──────────────────────────────────────────────────────────
    // Entity mapping
    // ──────────────────────────────────────────────────────────

    @Test
    fun mapDtoToEntity_preservesExistingContentWhenNoNewContent() = runTest(testDispatcher) {
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)

        val dto = makeBookmarkDto(id = "bk-1") // no htmlContent
        val remoteId = "bk-1"
        val existingEntity = testBookmarkEntity(
            localId = 5L,
            remoteId = "bk-1",
            content = "HAS_CONTENT",
            readingTimeMinutes = 5
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // readingTimeMinutes should be preserved since existing has content
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
                listIds = any(),
                isStarred = any(),
                isArchived = any(),
                isRead = any(),
                readingTimeMinutes = 5,
                modifiedAt = any(),
                crawlStatus = any(),
                crawledAt = any(),
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    @Test
    fun mapDtoToEntity_setsReadingTimeWhenContentProvided() = runTest(testDispatcher) {
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)

        val longContent = "<p>" + "word ".repeat(500) + "</p>"
        val dto = makeBookmarkDto(id = "bk-1", htmlContent = longContent)
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Inserted bookmark should have non-zero readingTimeMinutes because it has content
        coVerify {
            bookmarkDao.insertBookmarks(match { bookmarks ->
                bookmarks.isNotEmpty() && bookmarks.first().readingTimeMinutes > 0
            })
        }
    }

    // ──────────────────────────────────────────────────────────
    // Crawl metadata
    // ──────────────────────────────────────────────────────────

    @Test
    fun fullSync_mapsCrawlStatusAndCrawledAt() = runTest(testDispatcher) {
        val base = makeBookmarkDto(id = "bk-crawl")
        val dto = base.copy(
            content = base.content?.copy(
                crawlStatus = BookmarkContent.CrawlStatus.FAILURE,
                crawledAt = "2026-02-03T04:05:06Z"
            )
        )
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val inserted = slot<List<BookmarkEntity>>()
        coEvery { bookmarkDao.insertBookmarks(capture(inserted)) } returns listOf(1L)

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        val entity = inserted.captured.single()
        assertEquals("failure", entity.crawlStatus)
        assertEquals(
            Instant.parse("2026-02-03T04:05:06Z").toEpochMilliseconds(),
            entity.crawledAt
        )
    }

    @Test
    fun fullSync_preservesCrawlMetadataWhenDtoOmitsIt() = runTest(testDispatcher) {
        // Filtered/list syncs can come back without content, which must not blank the crawl state.
        val existing = testBookmarkEntity(
            localId = 7L,
            remoteId = "bk-crawl"
        ).copy(crawlStatus = "success", crawledAt = 999L)
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existing))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existing)

        val dto = makeBookmarkDto(id = "bk-crawl") // no crawlStatus / crawledAt
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        coVerify(exactly = 1) {
            bookmarkDao.updateBookmarkMetadata(
                localId = 7L,
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
                crawlStatus = "success",
                crawledAt = 999L,
                summary = any(),
                summarizationStatus = any()
            )
        }
    }

    // ──────────────────────────────────────────────────────────
    // Asset metadata
    // ──────────────────────────────────────────────────────────

    @Test
    fun fullSync_tracksPdfAssetsAndIgnoresUntrackedTypes() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-assets").copy(
            assets = listOf(
                BookmarksBookmarkIdAssetsPost201Response(
                    id = "pdf-1",
                    assetType = BookmarksBookmarkIdAssetsPost201Response.AssetType.PDF
                ),
                BookmarksBookmarkIdAssetsPost201Response(
                    id = "archive-1",
                    assetType = BookmarksBookmarkIdAssetsPost201Response.AssetType.FULL_PAGE_ARCHIVE
                ),
                // Not persisted as an AssetEntity — banner/screenshot live on the bookmark row
                BookmarksBookmarkIdAssetsPost201Response(
                    id = "banner-1",
                    assetType = BookmarksBookmarkIdAssetsPost201Response.AssetType.BANNER_IMAGE
                )
            )
        )
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val metadata = slot<List<com.karakept.app.data.local.entity.AssetEntity>>()
        coEvery { assetDao.insertAssetMetadataOnly(capture(metadata)) } returns Unit

        createPipeline(SyncConfiguration.Full(testServer)).execute()

        assertEquals(
            setOf("pdf" to "pdf-1", "fullPageArchive" to "archive-1"),
            metadata.captured.map { it.assetType to it.id }.toSet()
        )
    }
}
