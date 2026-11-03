package com.karakept.app.data.repository

import com.karakept.api.model.Bookmark as ApiBookmark
import com.karakept.api.model.BookmarkContent
import com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.local.entity.toAsset
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class AssetRepositoryTest : BaseRepositoryTest() {

    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val files = mockk<AssetFiles>(relaxed = true)
    private val repository = AssetRepository(assetDao, remoteDataSource, testAppDispatchers, files)

    private val server = Server(
        id = "server-1",
        url = "https://karakeep.example.com",
        apiKey = "key",
        label = "Test"
    )

    private val bookmark = Bookmark(
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
        repository.detachOnServer(server, bookmark, asset.toAsset())

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
        assertFailsWith<ApiException> { repository.detachOnServer(server, bookmark, asset.toAsset()) }

        // Assert
        coVerify(exactly = 0) { assetDao.deleteAsset(any()) }
    }

    @Test
    fun clearLocalCopyClearsTheLocalPath() = runTest(testDispatcher) {
        // Act
        repository.clearLocalCopy(asset.toAsset())

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
        assertEquals(listOf(asset.toAsset()), assets)
    }

    private fun remoteBookmark(assets: List<BookmarksBookmarkIdAssetsPost201Response>) = ApiBookmark(
        id = "remote-100",
        title = "Test Article",
        archived = false,
        favourited = false,
        tags = emptyList(),
        content = BookmarkContent(type = BookmarkContent.Type.LINK, url = "https://example.com/article"),
        createdAt = "2026-01-01T00:00:00Z",
        modifiedAt = null,
        assets = assets
    )

    @Test
    fun fetchArchiveIsNullWhenTheServerHoldsNoArchive() = runTest(testDispatcher) {
        // Arrange
        coEvery { remoteDataSource.fetchBookmark(server, "remote-100") } returns remoteBookmark(emptyList())

        // Act
        val result = repository.fetchArchive(server, bookmark)

        // Assert
        assertNull(result)
        coVerify(exactly = 0) { remoteDataSource.downloadAsset(any(), any()) }
        coVerify(exactly = 0) { assetDao.insertAssets(any()) }
    }

    @Test
    fun fetchArchiveDownloadsCachesAndStoresTheArchive() = runTest(testDispatcher) {
        // Arrange
        val bytes = "<html>archived</html>".encodeToByteArray()
        coEvery { remoteDataSource.fetchBookmark(server, "remote-100") } returns remoteBookmark(
            listOf(
                BookmarksBookmarkIdAssetsPost201Response(
                    id = "pdf-1",
                    assetType = BookmarksBookmarkIdAssetsPost201Response.AssetType.PDF
                ),
                BookmarksBookmarkIdAssetsPost201Response(
                    id = "archive-1",
                    assetType = BookmarksBookmarkIdAssetsPost201Response.AssetType.PRECRAWLED_ARCHIVE
                )
            )
        )
        coEvery { remoteDataSource.downloadAsset(server, "archive-1", any()) } returns bytes
        every { files.save("archive_archive-1", bytes) } returns "/cache/archive_archive-1"

        // Act
        val result = repository.fetchArchive(server, bookmark)

        // Assert
        val expected = AssetEntity(
            id = "archive-1",
            bookmarkRemoteId = "remote-100",
            serverId = "server-1",
            assetType = "precrawledArchive",
            fileName = "archive_archive-1",
            contentType = null,
            localPath = "/cache/archive_archive-1"
        )
        coVerifyOrder {
            remoteDataSource.downloadAsset(server, "archive-1", any())
            files.save("archive_archive-1", bytes)
            assetDao.insertAssets(listOf(expected))
        }
        assertEquals(expected.toAsset(), result?.asset)
        assertContentEquals(bytes, result?.bytes)
    }

    @Test
    fun downloadReplacesTheLocalCopyInOrder() = runTest(testDispatcher) {
        // Arrange
        val cached = asset.copy(localPath = "/cache/old")
        val bytes = byteArrayOf(1, 2, 3)
        coEvery { remoteDataSource.downloadAsset(server, "asset-1", any()) } returns bytes
        every { files.save("archive_asset-1", bytes) } returns "/cache/archive_asset-1"

        // Act
        val result = repository.download(server, cached.toAsset()) {}

        // Assert
        val expected = asset.copy(localPath = "/cache/archive_asset-1")
        coVerifyOrder {
            files.delete("/cache/old")
            assetDao.clearLocalPath("asset-1")
            remoteDataSource.downloadAsset(server, "asset-1", any())
            files.save("archive_asset-1", bytes)
            assetDao.insertAssets(listOf(expected))
        }
        assertEquals(expected.toAsset(), result.asset)
        assertContentEquals(bytes, result.bytes)
    }

    @Test
    fun downloadWithoutALocalCopyClearsNothing() = runTest(testDispatcher) {
        // Arrange
        coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } returns byteArrayOf(1)

        // Act
        repository.download(server, asset.toAsset()) {}

        // Assert
        verify(exactly = 0) { files.delete(any()) }
        coVerify(exactly = 0) { assetDao.clearLocalPath(any()) }
    }

    @Test
    fun downloadForwardsProgress() = runTest(testDispatcher) {
        // Arrange
        coEvery { remoteDataSource.downloadAsset(server, "asset-1", any()) } answers {
            val onProgress = thirdArg<((Float?) -> Unit)?>()
            onProgress?.invoke(0.5f)
            onProgress?.invoke(null)
            byteArrayOf(1)
        }
        val reported = mutableListOf<Float?>()

        // Act
        repository.download(server, asset.toAsset()) { reported += it }

        // Assert
        assertEquals(listOf(0.5f, null), reported)
    }

    @Test
    fun aFailedDownloadInsertsNothing() = runTest(testDispatcher) {
        // Arrange
        coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } throws ApiException("HTTP 500")

        // Act
        assertFailsWith<ApiException> { repository.download(server, asset.copy(localPath = "/cache/old").toAsset()) {} }

        // Assert
        verify(exactly = 0) { files.save(any(), any()) }
        coVerify(exactly = 0) { assetDao.insertAssets(any()) }
    }

    @Test
    fun downloadNamesTheFileAfterTheAssetType() = runTest(testDispatcher) {
        // Arrange
        coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } returns byteArrayOf(1)

        // Act
        repository.download(server, asset.copy(id = "pdf-1", assetType = "pdf").toAsset()) {}

        // Assert
        verify { files.save("asset_pdf-1.pdf", any()) }
    }

    @Test
    fun cacheFileNamesPerAssetType() {
        assertEquals("archive_a", AssetRepository.cacheFileNameFor("a", "fullPageArchive"))
        assertEquals("archive_a", AssetRepository.cacheFileNameFor("a", "precrawledArchive"))
        assertEquals("hero_banner_a", AssetRepository.cacheFileNameFor("a", "bannerImage"))
        assertEquals("hero_screenshot_a", AssetRepository.cacheFileNameFor("a", "screenshot"))
        assertEquals("asset_a.pdf", AssetRepository.cacheFileNameFor("a", "pdf"))
        assertEquals("asset_a", AssetRepository.cacheFileNameFor("a", "video"))
    }
}
