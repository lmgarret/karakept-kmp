package com.karakept.app.data.repository

import com.karakept.api.model.Bookmark as ApiBookmark
import com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response
import com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarkAssetCacheTest : BaseRepositoryTest() {

    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val cache = BookmarkAssetCache(assetDao, remoteDataSource)

    private val server = Server(id = "server-1", url = "https://k.example.com", apiKey = "key", label = "Test")

    @Test
    fun heroAssetAlreadyCached_isNotDownloadedAgain() = runTest(testDispatcher) {
        coEvery { assetDao.getAssetsForBookmark("b1", "server-1") } returns listOf(
            AssetEntity(
                id = "banner-1", bookmarkRemoteId = "b1", serverId = "server-1",
                assetType = "bannerImage", fileName = "x", contentType = null, localPath = "/cache/x"
            )
        )

        cache.cacheHeroAssets(server, "b1", "server-1", bannerImageAssetId = "banner-1", screenshotAssetId = null)

        coVerify(exactly = 0) { remoteDataSource.downloadAsset(any(), any()) }
        coVerify(exactly = 0) { assetDao.insertAssets(any()) }
    }

    @Test
    fun failedDownload_isSwallowedAndRecordsNothing() = runTest(testDispatcher) {
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns emptyList()
        coEvery { remoteDataSource.downloadAsset(any(), any()) } throws Exception("offline")

        cache.cacheHeroAssets(server, "b1", "server-1", bannerImageAssetId = "banner-1", screenshotAssetId = "shot-1")
        cache.cacheArchiveAsset(
            server, "b1", "server-1",
            BookmarksBookmarkIdAssetsPost201Response(id = "arch-1", assetType = AssetType.FULL_PAGE_ARCHIVE)
        )

        coVerify(exactly = 3) { remoteDataSource.downloadAsset(any(), any()) }
        coVerify(exactly = 0) { assetDao.insertAssets(any()) }
    }

    @Test
    fun contentAssetMetadata_recordsKnownTypesWithoutAFile() = runTest(testDispatcher) {
        val dto = mockk<ApiBookmark> {
            every { assets } returns listOf(
                BookmarksBookmarkIdAssetsPost201Response(id = "html-1", assetType = AssetType.LINK_HTML_CONTENT),
                BookmarksBookmarkIdAssetsPost201Response(id = "pdf-1", assetType = AssetType.PDF),
                BookmarksBookmarkIdAssetsPost201Response(id = "shot-1", assetType = AssetType.SCREENSHOT),
                BookmarksBookmarkIdAssetsPost201Response(id = null, assetType = AssetType.PDF),
            )
        }
        val inserted = slot<List<AssetEntity>>()
        coEvery { assetDao.insertAssetMetadataOnly(capture(inserted)) } returns Unit

        cache.insertContentAssetMetadata(dto, "b1", "server-1")

        assertEquals(
            listOf("html-1" to "linkHtmlContent", "pdf-1" to "pdf"),
            inserted.captured.map { it.id to it.assetType }
        )
        assertEquals(listOf(null, null), inserted.captured.map { it.localPath })
    }
}
