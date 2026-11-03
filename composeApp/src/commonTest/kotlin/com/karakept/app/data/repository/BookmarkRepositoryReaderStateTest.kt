package com.karakept.app.data.repository

import com.karakept.app.data.cache.ImageCacheManager
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertSame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkRepositoryReaderStateTest : BaseRepositoryTest() {

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val repository = BookmarkRepository(
        bookmarkDao,
        mockk<AssetDao>(relaxed = true),
        mockk<RemoteDataSource>(relaxed = true),
        mockk<BookmarkActionsRepository>(relaxed = true),
        mockk<SettingsRepository>(relaxed = true),
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
    fun observeBookmark_returnsTheDaoFlow() {
        val flow: Flow<BookmarkEntity?> = flowOf(null)
        every { bookmarkDao.observeBookmarkById(7L) } returns flow

        assertSame(flow, repository.observeBookmark(7L))
    }
}
