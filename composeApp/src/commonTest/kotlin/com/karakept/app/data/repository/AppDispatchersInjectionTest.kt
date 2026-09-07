package com.karakept.app.data.repository

import com.karakept.api.model.BookmarkContent
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.ImageCacheManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The data layer used to start background work on hardcoded real dispatchers (and, in two
 * places, on `GlobalScope`), which no test scheduler can drain. These tests pin the
 * replacement down: every background coroutine the repositories start must be owned by the
 * injected [com.karakept.app.utils.AppDispatchers], so it is invisible before
 * `advanceUntilIdle()` and guaranteed complete after it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppDispatchersInjectionTest : BaseRepositoryTest() {

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val pendingActionDao = mockk<PendingActionDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val highlightRepository = mockk<HighlightRepository>(relaxed = true)
    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val listDao = mockk<ListDao>(relaxed = true)

    private val testServer = Server("s1", "http://localhost", "key", "Label")

    private val storedBookmark = BookmarkEntity(
        localId = 1L,
        remoteId = "remote-1",
        serverId = testServer.id,
        title = "Created",
        url = "https://example.com",
        description = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        tags = "",
        listIds = "",
        isStarred = false,
        isArchived = false,
        isRead = false,
        createdAt = 0L,
        readingTimeMinutes = 0,
        content = ""
    )

    private val actionsRepository = BookmarkActionsRepository(
        bookmarkDao = bookmarkDao,
        pendingActionDao = pendingActionDao,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository,
        appDispatchers = testAppDispatchers
    )

    private val bookmarkRepository = BookmarkRepository(
        bookmarkDao,
        assetDao,
        remoteDataSource,
        actionsRepository,
        settingsRepository,
        serverRepository,
        highlightRepository,
        imageCacheManager,
        listDao,
        testAppDispatchers
    )

    override fun setup() {
        super.setup()
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))
        coEvery { settingsRepository.offlineMode } returns flowOf(true)
    }

    /**
     * `createBookmark` kicks off a background fetch of the new bookmark's full content.
     * That used to run on `GlobalScope.launch(Dispatchers.Default)` — fire-and-forget onto a
     * real thread pool. It now runs on the repository's own scope, on the injected dispatcher.
     */
    @Test
    fun createBookmark_backgroundContentFetch_runsOnInjectedDispatcher() = runTest(testDispatcher) {
        val dto = mockk<com.karakept.api.model.Bookmark>(relaxed = true) {
            every { id } returns "remote-1"
            every { title } returns "Created"
            every { content } returns mockk(relaxed = true) {
                every { crawlStatus } returns BookmarkContent.CrawlStatus.SUCCESS
            }
        }
        coEvery { remoteDataSource.createBookmark(any(), any()) } returns dto
        coEvery { remoteDataSource.fetchBookmark(any(), any()) } returns dto
        coEvery { bookmarkDao.getBookmarkByRemoteId(any(), any()) } returns storedBookmark
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.NEVER)

        bookmarkRepository.createBookmark("https://example.com")

        // Still parked on the test scheduler — nothing has escaped to a real thread.
        coVerify(exactly = 0) { remoteDataSource.fetchBookmark(any(), any()) }

        advanceUntilIdle()

        coVerify(exactly = 1) { remoteDataSource.fetchBookmark(any(), any()) }
    }

    /**
     * `BookmarkViewerScreenModel.flushOnDispose` must survive its ViewModel being cleared,
     * so the write is owned by the repository rather than `GlobalScope`.
     */
    @Test
    fun persistFinalReadingProgress_writesAndQueuesOnInjectedDispatcher() = runTest(testDispatcher) {
        actionsRepository.persistFinalReadingProgress(
            bookmarkLocalId = 7L,
            bookmarkRemoteId = "remote-42",
            serverId = testServer.id,
            progress = 0.5f,
            scrollIndex = 3,
            scrollOffset = 12
        )

        coVerify(exactly = 0) { bookmarkDao.updateReadingProgress(any(), any(), any(), any()) }

        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkDao.updateReadingProgress(7L, 0.5f, 3, 12) }
        coVerify(exactly = 1) {
            pendingActionDao.insertAction(
                match { it.bookmarkRemoteId == "remote-42" && it.actionType == PendingActionType.UPDATE_READING_PROGRESS }
            )
        }
    }

    /** With no server id there is nothing to sync, but the local write must still land. */
    @Test
    fun persistFinalReadingProgress_withoutServerId_writesLocallyOnly() = runTest(testDispatcher) {
        actionsRepository.persistFinalReadingProgress(
            bookmarkLocalId = 8L,
            bookmarkRemoteId = "remote-43",
            serverId = null,
            progress = 1f,
            scrollIndex = 0,
            scrollOffset = 0
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkDao.updateReadingProgress(8L, 1f, 0, 0) }
        coVerify(exactly = 0) { pendingActionDao.insertAction(any()) }
    }

    /**
     * `triggerAutoSync` launches on the repository's scope after every action; it used to be
     * pinned to `Dispatchers.IO`, which left action tests racing a real thread pool.
     */
    @Test
    fun triggerAutoSync_isDrainedByAdvanceUntilIdle() = runTest(testDispatcher) {
        coEvery { settingsRepository.offlineMode } returns flowOf(false)
        coEvery { bookmarkDao.getBookmarkByRemoteId(any(), any()) } returns null
        coEvery { pendingActionDao.getProcessableActions(any(), any()) } returns emptyList()

        actionsRepository.archiveBookmark("remote-1", testServer.id)

        coVerify(exactly = 0) { pendingActionDao.getProcessableActions(any(), any()) }

        advanceUntilIdle()

        coVerify(atLeast = 1) { pendingActionDao.getProcessableActions(testServer.id, any()) }
    }
}
