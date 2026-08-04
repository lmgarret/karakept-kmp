package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.ImageCacheManager
import io.mockk.coEvery
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
