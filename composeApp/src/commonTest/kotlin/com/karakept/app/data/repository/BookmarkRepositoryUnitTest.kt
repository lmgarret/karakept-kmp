package com.karakept.app.data.repository

import com.karakept.api.model.BookmarkContent
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.ImageCacheManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookmarkRepositoryUnitTest : BaseRepositoryTest() {

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val bookmarkActionsRepository = mockk<BookmarkActionsRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val highlightRepository = mockk<HighlightRepository>(relaxed = true)
    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private val listDao = mockk<ListDao>(relaxed = true)

    private val repository = BookmarkRepository(
        bookmarkDao,
        assetDao,
        remoteDataSource,
        bookmarkActionsRepository,
        settingsRepository,
        serverRepository,
        highlightRepository,
        imageCacheManager,
        listDao,
        testAppDispatchers
    )

    @Test
    fun testCreateBookmark_Success() = runTest(testDispatcher) {
        // Setup
        val testServer = Server("1", "http://localhost", "key", "Label")
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))
        
        val testUrl = "https://example.com"
        val mockDto = mockk<com.karakept.api.model.Bookmark>(relaxed = true) {
            every { id } returns "remote-id"
            every { title } returns "Mock Title"
            every { content } returns mockk(relaxed = true) {
                every { crawlStatus } returns BookmarkContent.CrawlStatus.SUCCESS
            }
        }
        coEvery { remoteDataSource.createBookmark(any(), any()) } returns mockDto
        coEvery { remoteDataSource.fetchBookmark(any(), any()) } returns mockDto
        coEvery { bookmarkDao.getBookmarkByRemoteId(any(), any()) } returns null

        // Execute
        val result = repository.createBookmark(testUrl)

        // Verify
        assertTrue(result.isSuccess)
        val bookmark = result.getOrNull()
        assertEquals("Mock Title", bookmark?.title)
        coVerify(exactly = 0) { remoteDataSource.fetchBookmark(any(), any()) }
    }

    @Test
    fun testCreateBookmark_WaitsForCrawlToSettleBeforeAcceptingInterimTitle() = runTest(testDispatcher) {
        // Setup: the server's crawler first reports an interstitial/challenge page title
        // (e.g. an anti-bot "Client Challenge" page) while the crawl is still pending, then
        // later settles on the real title. The notification/title must reflect the settled one.
        val testServer = Server("1", "http://localhost", "key", "Label")
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))

        val testUrl = "https://example.com"
        val interimDto = mockk<com.karakept.api.model.Bookmark>(relaxed = true) {
            every { id } returns "remote-id"
            every { title } returns "Client Challenge"
            every { content } returns mockk(relaxed = true) {
                every { crawlStatus } returns BookmarkContent.CrawlStatus.PENDING
            }
        }
        val settledDto = mockk<com.karakept.api.model.Bookmark>(relaxed = true) {
            every { id } returns "remote-id"
            every { title } returns "Real Article Title"
            every { content } returns mockk(relaxed = true) {
                every { crawlStatus } returns BookmarkContent.CrawlStatus.SUCCESS
            }
        }
        coEvery { remoteDataSource.createBookmark(any(), any()) } returns interimDto
        coEvery { remoteDataSource.fetchBookmark(any(), any()) } returnsMany listOf(interimDto, settledDto)
        coEvery { bookmarkDao.getBookmarkByRemoteId(any(), any()) } returns null

        // Execute
        val result = repository.createBookmark(testUrl)

        // Verify
        assertTrue(result.isSuccess)
        assertEquals("Real Article Title", result.getOrNull()?.title)
        coVerify(atLeast = 2) { remoteDataSource.fetchBookmark(any(), any()) }
    }

    @Test
    fun testCreateBookmark_AcceptsTitleImmediatelyWhenCrawlStatusMissing() = runTest(testDispatcher) {
        // Setup: response variants that never populate crawlStatus must keep the old
        // fast-path behavior (accept the title right away, no polling delay added).
        val testServer = Server("1", "http://localhost", "key", "Label")
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))

        val testUrl = "https://example.com"
        val mockDto = mockk<com.karakept.api.model.Bookmark>(relaxed = true) {
            every { id } returns "remote-id"
            every { title } returns "Mock Title"
            every { content } returns mockk(relaxed = true) {
                every { crawlStatus } returns null
            }
        }
        coEvery { remoteDataSource.createBookmark(any(), any()) } returns mockDto
        coEvery { remoteDataSource.fetchBookmark(any(), any()) } returns mockDto
        coEvery { bookmarkDao.getBookmarkByRemoteId(any(), any()) } returns null

        // Execute
        val result = repository.createBookmark(testUrl)

        // Verify
        assertTrue(result.isSuccess)
        assertEquals("Mock Title", result.getOrNull()?.title)
        coVerify(exactly = 0) { remoteDataSource.fetchBookmark(any(), any()) }
    }

    @Test
    fun testCreateBookmark_Failure() = runTest(testDispatcher) {
        // Setup
        val testServer = Server("1", "http://localhost", "key", "Label")
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))
        
        val testUrl = "https://example.com"
        coEvery { remoteDataSource.createBookmark(any(), any()) } throws Exception("Network Error")

        // Execute
        val result = repository.createBookmark(testUrl)

        // Verify
        assertTrue(result.isFailure)
        assertEquals("Network Error", result.exceptionOrNull()?.message)
    }
}
