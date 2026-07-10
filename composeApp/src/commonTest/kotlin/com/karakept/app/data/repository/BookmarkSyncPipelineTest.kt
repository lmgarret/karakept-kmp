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
        coEvery { highlightRepository.syncHighlights(any()) } returns Unit
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
        tags: String = ""
    ) = BookmarkEntity(
        localId = localId,
        remoteId = remoteId,
        originalRemoteId = originalRemoteId,
        serverId = serverId,
        title = "Test Bookmark",
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
        content = content
    )

    private fun makeBookmarkDto(
        id: String = "remote-42",
        title: String = "Test",
        archived: Boolean = false,
        favourited: Boolean = false,
        tags: List<String> = emptyList(),
        htmlContent: String? = null,
        url: String = "https://example.com"
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
        createdAt = "2026-01-01T00:00:00Z"
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

        // Full sync should delete removed bookmarks
        coVerify { bookmarkDao.deleteBookmark(existingEntity) }
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
        coVerify { bookmarkDao.deleteBookmark(existingEntity) }
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
                readingTimeMinutes = any()
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
                readingTimeMinutes = any()
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
                readingTimeMinutes = 10
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
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.PER_LIST)
        coEvery { settingsRepository.contentSyncConfig } returns flowOf(
            ListSyncConfig(selectedLists = setOf("list-1"), withChildrenMode = emptySet())
        )

        val dto = makeBookmarkDto(id = "bk-1")
        coEvery { remoteDataSource.fetchLists(any()) } returns listOf(
            com.karakept.api.model.KarakeepList(id = "list-1", name = "My List")
        )
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-1", false)
        } returns listOf(makeBookmarkDto(id = "bk-1"))

        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)
        coEvery { fetchRemoteContent(any(), any()) } returns "<p>Content</p>"
        coEvery { imageCacheManager.cacheImagesInHtml(any(), any()) } answers { firstArg() }

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
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
                readingTimeMinutes = 5
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
        coVerify { bookmarkDao.insertBookmarks(match { it.size == 2 }) }
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
                readingTimeMinutes = any()
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
                readingTimeMinutes = any()
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
                readingTimeMinutes = any()
            )
        }
    }

    @Test
    fun fullSync_fetchListsMembership() = runTest(testDispatcher) {
        val dto = makeBookmarkDto(id = "bk-1")
        coEvery { remoteDataSource.fetchLists(any()) } returns listOf(
            com.karakept.api.model.KarakeepList(id = "list-A", name = "List A")
        )
        coEvery {
            remoteDataSource.fetchBookmarksForList(testServer, "list-A", false)
        } returns listOf(makeBookmarkDto(id = "bk-1"))

        coEvery { bookmarkDao.getBookmarksForServer("server1") } returns flowOf(emptyList())
        coEvery {
            remoteDataSource.fetchBookmarks(any(), any(), any(), any(), any(), any())
        } returns PaginatedBookmarks(bookmarks = listOf(dto), nextCursor = null)

        val pipeline = createPipeline(SyncConfiguration.Full(testServer))
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
}
