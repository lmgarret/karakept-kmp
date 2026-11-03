package com.karakept.app.data.repository

import com.karakept.app.data.cache.ImageCacheManager
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.toBookmark
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkRepositoryReaderStateTest : BaseRepositoryTest() {

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { contentSyncStrategy } returns flowOf(SyncStrategy.PER_BOOKMARK)
        every { contentSyncTargetLists } returns flowOf(emptySet())
        every { activeOfflineRetentionDays } returns flowOf(null)
    }
    private val repository = BookmarkRepository(
        bookmarkDao,
        mockk<AssetDao>(relaxed = true),
        mockk<RemoteDataSource>(relaxed = true),
        mockk<BookmarkActionsRepository>(relaxed = true),
        settingsRepository,
        mockk<ServerRepository>(relaxed = true),
        mockk<HighlightRepository>(relaxed = true),
        mockk<ImageCacheManager>(relaxed = true),
        mockk<ListDao>(relaxed = true),
        testAppDispatchers
    )

    @Test
    fun saveReadingProgress_writesThroughToTheDao() = runTest(testDispatcher) {
        repository.saveReadingProgress(7L, 0.5f, 3, 12)

        coVerify(exactly = 1) { bookmarkDao.updateReadingProgress(7L, 0.5f, 3, 12) }
    }

    @Test
    fun markOpened_writesThroughToTheDao() = runTest(testDispatcher) {
        repository.markOpened(7L, 1000L)

        coVerify(exactly = 1) { bookmarkDao.markOpened(7L, 1000L) }
    }

    @Test
    fun saveContent_writesThroughToTheDao() = runTest(testDispatcher) {
        repository.saveContent(7L, "<p>x</p>", 2)

        coVerify(exactly = 1) { bookmarkDao.updateContent(7L, "<p>x</p>", 2) }
    }

    @Test
    fun observeBookmark_mapsTheDaoRowToTheDomainModel() = runTest(testDispatcher) {
        val row = entity(listIds = "a,b", offlineEvictedAt = 5L)
        every { bookmarkDao.observeBookmarkById(7L) } returns flowOf(row, null)

        assertEquals(listOf(row.toBookmark(), null), repository.observeBookmark(7L).toList())
        assertEquals("a,b", row.toBookmark().listIds)
    }

    @Test
    fun shouldPersistFetchedContent_followsTheSyncStrategy() = runTest(testDispatcher) {
        coEvery { bookmarkDao.getBookmarkById(7L) } returns entity()

        assertTrue(repository.shouldPersistFetchedContent(7L, NOW))

        every { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.NEVER)
        assertFalse(repository.shouldPersistFetchedContent(7L, NOW))
    }

    @Test
    fun shouldPersistFetchedContent_perListOnlyForTargetLists() = runTest(testDispatcher) {
        every { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.PER_LIST)
        every { settingsRepository.contentSyncTargetLists } returns flowOf(setOf("b"))
        coEvery { bookmarkDao.getBookmarkById(7L) } returns entity(listIds = "a,b")
        coEvery { bookmarkDao.getBookmarkById(8L) } returns entity(listIds = "c")

        assertTrue(repository.shouldPersistFetchedContent(7L, NOW))
        assertFalse(repository.shouldPersistFetchedContent(8L, NOW))
    }

    @Test
    fun shouldPersistFetchedContent_restoresAnEvictedCopy() = runTest(testDispatcher) {
        every { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.NEVER)
        coEvery { bookmarkDao.getBookmarkById(7L) } returns entity(offlineEvictedAt = 1L)

        assertTrue(repository.shouldPersistFetchedContent(7L, NOW))
    }

    @Test
    fun shouldPersistFetchedContent_neverStoresARetiredCopy() = runTest(testDispatcher) {
        every { settingsRepository.activeOfflineRetentionDays } returns flowOf(1)
        coEvery { bookmarkDao.getBookmarkById(7L) } returns
            entity(offlineEvictedAt = 1L).copy(isRead = true, readOrArchivedAt = 0L)

        assertFalse(repository.shouldPersistFetchedContent(7L, NOW))
    }

    @Test
    fun shouldPersistFetchedContent_falseWithoutARow() = runTest(testDispatcher) {
        coEvery { bookmarkDao.getBookmarkById(7L) } returns null

        assertFalse(repository.shouldPersistFetchedContent(7L, NOW))
    }

    private fun entity(listIds: String = "", offlineEvictedAt: Long? = null) = BookmarkEntity(
        localId = 7L,
        remoteId = "remote-7",
        serverId = "server-1",
        url = "https://example.com",
        title = "Title",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 0L,
        isArchived = false,
        isStarred = false,
        listIds = listIds,
        offlineEvictedAt = offlineEvictedAt
    )

    private companion object {
        const val NOW = 10L * 24 * 60 * 60 * 1000
    }
}
