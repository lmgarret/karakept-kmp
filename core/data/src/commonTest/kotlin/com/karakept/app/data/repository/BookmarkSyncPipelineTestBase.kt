package com.karakept.app.data.repository

import com.karakept.api.model.Bookmark
import com.karakept.api.model.BookmarkContent
import com.karakept.api.model.BookmarkTagsInner
import com.karakept.api.model.PaginatedBookmarks
import com.karakept.app.data.cache.ImageCacheManager
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.dao.ProgressPullTarget
import com.karakept.app.data.model.ListSyncConfig
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncProgress
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Mocks, defaults and builders shared by the [BookmarkSyncPipeline] suites, which are split by
 * concern: `Configuration`, `Differential`, `Content`, `Mapping`, `Progress`.
 *
 * BookmarkActionsRepository is constructed with mocked DAOs (not mocked itself)
 * because its extension functions (processPendingActions, getPendingActionBookmarkIds)
 * access internal members (actionMutex, pendingActionDao) which conflict with relaxed mocking.
 */
internal abstract class BookmarkSyncPipelineTestBase : BaseRepositoryTest() {
    protected val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    protected val assetDao = mockk<AssetDao>(relaxed = true)
    protected val pendingActionDao = mockk<PendingActionDao>(relaxed = true)
    protected val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    protected val serverRepository = mockk<ServerRepository>(relaxed = true)
    protected val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    protected val highlightRepository = mockk<HighlightRepository>(relaxed = true)
    protected val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    protected val listDao = mockk<ListDao>(relaxed = true)
    protected val syncProgress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    protected val fetchRemoteContent: suspend (Server, String) -> String? = mockk(relaxed = true)
    protected val cacheHeroAssetsForBookmark: suspend (Server, String, String, String?, String?) -> Unit =
        mockk(relaxed = true)

    // Construct a real BookmarkActionsRepository with mocked DAOs so extension functions work
    protected val bookmarkActionsRepository = BookmarkActionsRepository(
        bookmarkDao = bookmarkDao,
        pendingActionDao = pendingActionDao,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository,
        appDispatchers = testAppDispatchers
    )

    protected val testServer = Server(
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
        // The server answers every batch, holding no progress for any of them: the common
        // case, and the one that still advances the rotation.
        coEvery { remoteDataSource.getReadingProgressBatch(any(), any(), any()) } answers {
            arg<List<String>>(1).associateWith { null }
        }

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

    protected fun createPipeline(
        config: SyncConfiguration,
        onProgress: ((ListSyncStatus) -> Unit)? = null,
        shouldPullReadingProgress: (suspend () -> Boolean)? = null
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
            onProgress = onProgress,
            shouldPullReadingProgress = shouldPullReadingProgress
        )
    }

    protected fun makeTarget(
        localId: Long = 1L,
        remoteId: String = "remote-42",
        readingProgress: Float = 0f
    ) = ProgressPullTarget(
        localId = localId,
        remoteId = remoteId,
        readingProgress = readingProgress
    )

    protected fun makeBookmarkDto(
        id: String = "remote-42",
        title: String = "Test",
        archived: Boolean = false,
        favourited: Boolean = false,
        tags: List<String> = emptyList(),
        htmlContent: String? = null,
        url: String = "https://example.com",
        modifiedAt: String? = null,
        summary: String? = null,
        summarizationStatus: Bookmark.SummarizationStatus? = null
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
        modifiedAt = modifiedAt,
        summary = summary,
        summarizationStatus = summarizationStatus
    )
}
