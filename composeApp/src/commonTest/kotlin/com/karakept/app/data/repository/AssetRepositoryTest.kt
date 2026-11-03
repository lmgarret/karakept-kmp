package com.karakept.app.data.repository

import com.karakept.api.model.Bookmark
import com.karakept.api.model.BookmarkContent
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AssetRepositoryTest : BaseRepositoryTest() {

    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val repository = AssetRepository(assetDao, remoteDataSource, testAppDispatchers)

    private val server = Server(
        id = "server-1",
        url = "https://karakeep.example.com",
        apiKey = "key",
        label = "Test"
    )

    private val bookmark = BookmarkEntity(
        localId = 1L,
        remoteId = "remote-100",
        serverId = "server-1",
        url = "https://example.com/article",
        title = "Test Article",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 0L,
        isArchived = false,
        isStarred = false,
        isRead = false
    )

    private val asset = AssetEntity(
        id = "asset-1",
        bookmarkRemoteId = "remote-100",
        serverId = "server-1",
        assetType = "fullPageArchive",
        fileName = null,
        contentType = null,
        localPath = null
    )

    @Test
    fun detachOnServerDetachesRemotelyThenDeletesTheRow() = runTest(testDispatcher) {
        // Act
        repository.detachOnServer(server, bookmark, asset)

        // Assert
        coVerifyOrder {
            remoteDataSource.detachAsset(server, "remote-100", "asset-1")
            assetDao.deleteAsset("asset-1")
        }
    }

    @Test
    fun aFailedDetachKeepsTheRow() = runTest(testDispatcher) {
        // Arrange
        coEvery { remoteDataSource.detachAsset(any(), any(), any()) } throws ApiException("HTTP 500")

        // Act
        assertFailsWith<ApiException> { repository.detachOnServer(server, bookmark, asset) }

        // Assert
        coVerify(exactly = 0) { assetDao.deleteAsset(any()) }
    }

    @Test
    fun clearLocalCopyClearsTheLocalPath() = runTest(testDispatcher) {
        // Act
        repository.clearLocalCopy(asset)

        // Assert
        coVerify(exactly = 1) { assetDao.clearLocalPath("asset-1") }
    }

    @Test
    fun assetsForReadsTheBookmarksRows() = runTest(testDispatcher) {
        // Arrange
        coEvery { assetDao.getAssetsForBookmark("remote-100", "server-1") } returns listOf(asset)

        // Act
        val assets = repository.assetsFor(bookmark)

        // Assert
        assertEquals(listOf(asset), assets)
    }

    @Test
    fun fetchArchiveIsNullWhenTheServerHoldsNoArchive() = runTest(testDispatcher) {
        // Arrange
        coEvery { remoteDataSource.fetchBookmark(server, "remote-100") } returns Bookmark(
            id = "remote-100",
            title = "Test Article",
            archived = false,
            favourited = false,
            tags = emptyList(),
            content = BookmarkContent(type = BookmarkContent.Type.LINK, url = "https://example.com/article"),
            createdAt = "2026-01-01T00:00:00Z",
            modifiedAt = null,
            assets = emptyList()
        )

        // Act
        val result = repository.fetchArchive(server, bookmark)

        // Assert
        assertNull(result)
        coVerify(exactly = 0) { remoteDataSource.downloadAsset(any(), any()) }
        coVerify(exactly = 0) { assetDao.insertAssets(any()) }
    }
}
