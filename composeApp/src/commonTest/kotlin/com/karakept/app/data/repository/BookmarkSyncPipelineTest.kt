package com.karakept.app.data.repository

import com.karakept.api.model.Bookmark
import com.karakept.api.model.BookmarkContent
import com.karakept.api.model.BookmarkTagsInner
import com.karakept.api.model.PaginatedBookmarks
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.ListSyncConfig
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncProgress
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.ImageCacheManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [BookmarkSyncPipeline].
 *
 * Covers all 3 SyncConfiguration types (Full, Filtered, ForList),
 * differential sync logic (insert/update/delete/skip-pending),
 * content sync strategy dispatch, entity mapping with data preservation,
 * paginated fetch, and progress state transitions.
 *
 * BookmarkActionsRepository is constructed with mocked DAOs (not mocked itself)
 * because its extension functions (processPendingActions, getPendingActionBookmarkIds)
 * access internal members (actionMutex, pendingActionDao) which conflict with relaxed mocking.
 */
class BookmarkSyncPipelineTest : BaseRepositoryTest() {

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val pendingActionDao = mockk<PendingActionDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val highlightRepository = mockk<HighlightRepository>(relaxed = true)
    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val listDao = mockk<ListDao>(relaxed = true)
    private val syncProgress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    private val fetchRemoteContent: suspend (Server, String) -> String? = mockk(relaxed = true)
    private val cacheHeroAssetsForBookmark: suspend (Server, Long, String, String?, String?) -> Unit =
        mockk(relaxed = true)

    // Construct a real BookmarkActionsRepository with mocked DAOs so extension functions work
    private val bookmarkActionsRepository = BookmarkActionsRepository(
        bookmarkDao = bookmarkDao,
        pendingActionDao = pendingActionDao,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository
    )

    private val testServer = Server(
        id = "server1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test Server"
    )

    @BeforeTest
    override fun setup() {
        super.setup()
        // Default mock setup -- most tests override specific mocks

        // Extension functions on BookmarkActionsRepository use underlying DAOs
        coEvery { pendingActionDao.getPendingActionsList(any()) } returns emptyList()
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))

        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = emptyList(), nextCursor = null)
        coEvery { remoteDataSource.fetchBookmarksForList(any(), any(), any()) } returns emptyList()
        coEvery { bookmarkDao.getBookmarksForServer(any()) } returns flowOf(emptyList())
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo(any()) } returns emptyList()
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.NEVER)
        coEvery { settingsRepository.contentSyncConfig } returns flowOf(
            ListSyncConfig(emptySet(), emptySet())
        )
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(false)
        coEvery { settingsRepository.offlineMode } returns flowOf(false)
        coEvery { settingsRepository.allListSettings } returns flowOf(emptyMap())
        coEvery { highlightRepository.syncHighlights(any()) } returns true
        coEvery { remoteDataSource.fetchLists(any()) } returns emptyList()
        coEvery { listDao.getListsForServerOnce(any()) } returns emptyList()
    }

    // ──────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────

    private fun createPipeline(
        config: SyncConfiguration,
        onProgress: ((ListSyncStatus) -> Unit)? = null
    ): BookmarkSyncPipeline {
        return BookmarkSyncPipeline(
            config = config,
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
            onProgress = onProgress
        )
    }

    private fun makeBookmarkEntity(
        localId: Long = 1L,
        remoteId: Long = 42L,
        originalRemoteId: String = "remote-42",
        serverId: String = "server1",
        listIds: String = "",
        content: String? = null,
        readingTimeMinutes: Int = 0,
        readingProgress: Float = 0f,
        tags: String = "",
        title: String = "Test Bookmark",
        modifiedAt: Long? = null,
        progressSyncedAt: Long = 0L
    ) = BookmarkEntity(
        localId = localId,
        remoteId = remoteId,
        originalRemoteId = originalRemoteId,
        serverId = serverId,
        title = title,
        url = "https://example.com",
        description = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        tags = tags,
        listIds = listIds,
        isStarred = false,
        isArchived = false,
        isRead = false,
        createdAt = 1000L,
        readingTimeMinutes = readingTimeMinutes,
        readingProgress = readingProgress,
        readingScrollIndex = 0,
        readingScrollOffset = 0,
        content = content,
        modifiedAt = modifiedAt,
        progressSyncedAt = progressSyncedAt
    )

    private fun makeBookmarkDto(
        id: String = "remote-42",
        title: String = "Test",
        archived: Boolean = false,
        favourited: Boolean = false,
        tags: List<String> = emptyList(),
        htmlContent: String? = null,
        url: String = "https://example.com",
        modifiedAt: String? = null
    ) = Bookmark(
        id = id,
        title = title,
        archived = archived,
        favourited = favourited,
        tags = tags.map { BookmarkTagsInner(name = it) },
        content = BookmarkContent(
            type = BookmarkContent.Type.LINK,
            url = url,
            htmlContent = htmlContent
        ),
        createdAt = "2026-01-01T00:00:00Z",
        modifiedAt = modifiedAt
    )

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
        val existingRemoteId = "bk-existing".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 10L,
            remoteId = existingRemoteId,
            originalRemoteId = "bk-existing"
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
        val existingRemoteId = "bk-removed".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 10L,
            remoteId = existingRemoteId,
            originalRemoteId = "bk-removed"
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
        val pendingRemoteId = "bk-pending".hashCode().toLong()
        val pendingEntity = makeBookmarkEntity(
            localId = 11L,
            remoteId = pendingRemoteId,
            originalRemoteId = "bk-pending"
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
        val remoteId = "bk-1".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 5L,
            remoteId = remoteId,
            originalRemoteId = "bk-1"
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
                modifiedAt = any()
            )
        }
    }

    // ──────────────────────────────────────────────────────────
    // Filtered sync configuration
    // ──────────────────────────────────────────────────────────

    @Test
    fun filteredSync_doesNotDeleteRemovedBookmarks() = runTest(testDispatcher) {
        val existingRemoteId = "bk-existing".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 10L,
            remoteId = existingRemoteId,
            originalRemoteId = "bk-existing"
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
    // Differential sync
    // ──────────────────────────────────────────────────────────

    @Test
    fun differentialSync_skipsBookmarksWithPendingActions() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1", title = "Should Be Skipped")
        val remoteId = "bk-1".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 5L,
            remoteId = remoteId,
            originalRemoteId = "bk-1"
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
                modifiedAt = any()
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
        val remoteId = "bk-1".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 5L,
            remoteId = remoteId,
            originalRemoteId = "bk-1",
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
                modifiedAt = any()
            )
        }
    }

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
    // Entity mapping
    // ──────────────────────────────────────────────────────────

    @Test
    fun mapDtoToEntity_preservesExistingContentWhenNoNewContent() = runTest(testDispatcher) {
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)

        val dto = makeBookmarkDto(id = "bk-1") // no htmlContent
        val remoteId = "bk-1".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 5L,
            remoteId = remoteId,
            originalRemoteId = "bk-1",
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
                modifiedAt = any()
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
        coVerify(exactly = 1) { bookmarkDao.insertBookmarks(match { it.singleOrNull()?.originalRemoteId == "bk-1" }) }
        coVerify(exactly = 1) { bookmarkDao.insertBookmarks(match { it.singleOrNull()?.originalRemoteId == "bk-2" }) }
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
            events += "insert-${batch.single().originalRemoteId}"
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
        val existingEntity = makeBookmarkEntity(
            localId = 10L,
            remoteId = "bk-existing".hashCode().toLong(),
            originalRemoteId = "bk-existing"
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
        coVerify { bookmarkDao.insertBookmarks(match { it.singleOrNull()?.originalRemoteId == "bk-1" }) }
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
        coEvery { bookmarkDao.getReadingProgressPullCandidates(any(), any()) } coAnswers {
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

    // ──────────────────────────────────────────────────────────
    // Additional coverage
    // ──────────────────────────────────────────────────────────

    @Test
    fun fullSync_existingBookmarkWithContent_updatesViaUpdateBookmarks() = runTest(testDispatcher) {
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.ALL)

        val dto = makeBookmarkDto(id = "bk-1", htmlContent = "<p>New content here</p>")
        val remoteId = "bk-1".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 5L,
            remoteId = remoteId,
            originalRemoteId = "bk-1"
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
        val remoteId = "bk-1".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 5L,
            remoteId = remoteId,
            originalRemoteId = "bk-1",
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
                modifiedAt = any()
            )
        }
    }

    @Test
    fun fullSync_processedPendingActionsAlsoSkipped() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1")
        val remoteId = "bk-1".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 5L,
            remoteId = remoteId,
            originalRemoteId = "bk-1"
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
                modifiedAt = any()
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
        val remoteId = "bk-1".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 5L,
            remoteId = remoteId,
            originalRemoteId = "bk-1",
            listIds = "list-preserved"
        )
        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(listOf(existingEntity))
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo("server1") } returns listOf(existingEntity)
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        // Filtered sync does not fetch lists (shouldFetchLists = false)
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
                modifiedAt = any()
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
        val existingRemoteId = "existing-bk".hashCode().toLong()
        val existingEntity = makeBookmarkEntity(
            localId = 5L,
            remoteId = existingRemoteId,
            originalRemoteId = "existing-bk"
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
        val remoteId = "bk-1".hashCode().toLong()
        val existing = makeBookmarkEntity(
            localId = 5L,
            remoteId = remoteId,
            originalRemoteId = "bk-1",
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
                modifiedAt = any()
            )
        }
    }

    @Test
    fun fullSync_writesWhenModifiedAtChanged() = runTest(testDispatcher) {
        val remoteId = "bk-1".hashCode().toLong()
        val existing = makeBookmarkEntity(
            localId = 5L,
            remoteId = remoteId,
            originalRemoteId = "bk-1",
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
                modifiedAt = any()
            )
        }
    }

    @Test
    fun forListSync_doesNotPullReadingProgress() = runTest(testDispatcher) {
        // Phase 6 is scoped to Full sync — ForList passes must not multiply tRPC calls.
        val dto = makeBookmarkDto(id = "bk-1")
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-1", false)
        } returns listOf(dto)
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(true)

        val pipeline = createPipeline(SyncConfiguration.ForList(testServer, "list-1"))
        pipeline.execute()

        coVerify(exactly = 0) { bookmarkDao.getReadingProgressPullCandidates(any(), any()) }
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
        val candidate = makeBookmarkEntity(localId = 7L, remoteId = 99L, originalRemoteId = "bk-cur")
        coEvery { bookmarkDao.getReadingProgressPullCandidates("server1", 50) } returns listOf(candidate)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
        pipeline.execute()

        // Candidates come from the rotating-cursor query and get stamped after the pull
        coVerify { bookmarkDao.getReadingProgressPullCandidates("server1", 50) }
        coVerify { bookmarkDao.updateProgressSyncedAt(7L, any()) }
    }
}
