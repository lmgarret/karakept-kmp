package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.model.ListSyncStatus
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.ImageCacheManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for BookmarkRepository per-key deduplication.
 * Uses a real BookmarkActionsRepository (with mocked DAOs) so extension functions work correctly.
 */
class BookmarkRepositoryDeduplicationTest : BaseRepositoryTest() {

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val pendingActionDao = mockk<PendingActionDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val highlightRepository = mockk<HighlightRepository>(relaxed = true)
    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val listDao = mockk<ListDao>(relaxed = true)

    // Real BookmarkActionsRepository with mocked DAOs so extension functions work
    private val bookmarkActionsRepository = BookmarkActionsRepository(
        bookmarkDao = bookmarkDao,
        pendingActionDao = pendingActionDao,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository
    )

    private val repository = BookmarkRepository(
        bookmarkDao,
        assetDao,
        remoteDataSource,
        bookmarkActionsRepository,
        settingsRepository,
        serverRepository,
        highlightRepository,
        imageCacheManager,
        listDao
    )

    private val testServer = Server("server1", "https://example.com", "test-key", "Test Server")

    @BeforeTest
    override fun setup() {
        super.setup()
        coEvery { pendingActionDao.getPendingActionsList(any()) } returns emptyList()
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))
        coEvery { bookmarkDao.getBookmarksForServer(any()) } returns flowOf(emptyList())
        coEvery { bookmarkDao.getBookmarksForServerWithContentInfo(any()) } returns emptyList()
        coEvery { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.NEVER)
        coEvery { settingsRepository.allListSettings } returns flowOf(emptyMap())
        coEvery { settingsRepository.trackReadingProgress } returns flowOf(false)
        coEvery { highlightRepository.syncHighlights(any()) } returns Unit
        coEvery { remoteDataSource.fetchLists(any()) } returns emptyList()
        coEvery { listDao.getListsForServerOnce(any()) } returns emptyList()
        coEvery { remoteDataSource.fetchBookmarksForList(any(), any(), any()) } returns emptyList()
    }

    /**
     * If a sync for the same list key is already running, a second concurrent call must
     * return 0 immediately without starting a new pipeline.
     */
    @Test
    fun syncForKey_alreadyActive_returnsZeroAndSkips() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { remoteDataSource.fetchBookmarksForList(any(), "list-1", any()) } coAnswers {
            gate.await()
            emptyList()
        }

        // First sync — blocks at fetchBookmarksForList once tryAcquireKey has run
        val firstJob = launch { repository.syncBookmarksForList(testServer, "list-1") }
        advanceUntilIdle() // advance until first sync suspends (gate.await in Phase 2)

        // Second call: key is already active → must return 0 immediately
        val secondResult = repository.syncBookmarksForList(testServer, "list-1")
        assertEquals(0, secondResult, "Duplicate sync key must return 0 without running a pipeline")

        // Clean up: release first sync
        gate.complete(Unit)
        advanceUntilIdle()
        firstJob.join()
    }

    /**
     * perKeyProgress must contain the list key while its sync is in progress,
     * and the key must be absent (Idle) after the sync completes.
     */
    @Test
    fun perKeyProgress_updatedDuringSyncAndClearedAfter() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { remoteDataSource.fetchBookmarksForList(any(), "list-1", any()) } coAnswers {
            gate.await()
            emptyList()
        }

        val firstJob = launch { repository.syncBookmarksForList(testServer, "list-1") }
        advanceUntilIdle()

        // While the first sync is in progress, key must appear in perKeyProgress
        assertTrue(
            repository.perKeyProgress.value.containsKey("list-1"),
            "perKeyProgress must contain the active key during sync"
        )
        val midStatus = repository.perKeyProgress.value["list-1"]
        assertTrue(
            midStatus is ListSyncStatus.FetchingMetadata || midStatus is ListSyncStatus.FetchingContent,
            "Status during sync must be FetchingMetadata or FetchingContent, got: $midStatus"
        )

        // Release the sync
        gate.complete(Unit)
        advanceUntilIdle()
        firstJob.join()

        // After completion, key must be removed (treated as Idle)
        assertFalse(
            repository.perKeyProgress.value.containsKey("list-1"),
            "perKeyProgress must remove the key after sync completes"
        )
    }

    /**
     * When a pipeline throws, releaseKey must still be called so subsequent syncs for
     * the same key are not permanently blocked by deduplication.
     */
    @Test
    fun syncForKey_onFailure_keyClearedFromActiveKeys() = runTest(testDispatcher) {
        coEvery {
            remoteDataSource.fetchBookmarksForList(any(), "list-1", any())
        } throws Exception("Network error")

        // First sync fails
        try {
            repository.syncBookmarksForList(testServer, "list-1")
        } catch (_: Exception) { /* expected */ }

        // After failure, key should be cleared — second sync must execute normally
        coEvery { remoteDataSource.fetchBookmarksForList(any(), "list-1", any()) } returns emptyList()
        repository.syncBookmarksForList(testServer, "list-1")

        // Both calls must have reached fetchBookmarksForList (second was NOT blocked by dedup)
        coVerify(exactly = 2) { remoteDataSource.fetchBookmarksForList(any(), "list-1", any()) }
    }
}
