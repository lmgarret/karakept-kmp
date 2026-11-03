package com.karakept.app.ui.screens.viewer

import com.karakept.app.data.model.BookmarkAsset
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.ContentSource
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.repository.AssetRepository
import com.karakept.app.data.repository.DownloadedAsset
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.domain.action.ActionSnackbarManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerAssetsControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val assetRepository = mockk<AssetRepository>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val snackbarManager = mockk<ActionSnackbarManager>(relaxed = true)

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

    private val asset = BookmarkAsset(
        id = "asset-1",
        bookmarkRemoteId = "remote-100",
        serverId = "server-1",
        assetType = "pdf",
        fileName = null,
        contentType = null,
        localPath = null
    )

    private fun createController(scope: CoroutineScope) = ViewerAssetsController(
        scope = scope,
        assetRepository = assetRepository,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository,
        snackbarManager = snackbarManager,
        viewerMode = MutableStateFlow(ViewerMode.READER),
        isOffline = { false },
        currentBookmark = { bookmark }
    )

    @Test
    fun downloadIsCancelledWithScope() = runTest(testDispatcher) {
        // Arrange
        every { serverRepository.servers } returns flowOf(listOf(server))
        coEvery { assetRepository.download(any(), any(), any()) } coAnswers { awaitCancellation() }
        val job = Job(coroutineContext[Job])
        val controller = createController(CoroutineScope(coroutineContext + job))

        controller.downloadOrRefreshAsset(asset, bookmark)
        runCurrent()
        assertTrue(controller.assetDownloads.value.containsKey("asset-1"))

        // Act
        job.cancel()
        advanceUntilIdle()

        // Assert
        assertFalse(controller.assetDownloads.value.containsKey("asset-1"))
        coVerify(exactly = 0) { snackbarManager.showSnackbar(any()) }
    }

    @Test
    fun reloadAssetsPublishesTheCachedPaths() = runTest(testDispatcher) {
        // Arrange
        val archive = asset.copy(id = "a", assetType = "precrawledArchive", localPath = "/archive")
        val banner = asset.copy(id = "b", assetType = "bannerImage", localPath = "/banner")
        coEvery { assetRepository.assetsFor(bookmark) } returns listOf(archive, banner)
        val controller = createController(backgroundScope)

        // Act
        controller.reloadAssets(bookmark)

        // Assert
        assertEquals(listOf(archive, banner), controller.assets.value)
        assertEquals("/archive", controller.precrawledAssetPath.value)
        assertTrue(controller.archiveAvailable.value)
        assertEquals("/banner", controller.bannerImageLocalPath.value)
        assertEquals(null, controller.screenshotLocalPath.value)
    }

    @Test
    fun fetchedArchiveSwitchesTheSourceToTheArchive() = runTest(testDispatcher) {
        // Arrange
        val bytes = "<html>archived</html>".encodeToByteArray()
        every { serverRepository.servers } returns flowOf(listOf(server))
        val fetched = asset.copy(id = "archive-1", assetType = "precrawledArchive", localPath = "/cache/archive_archive-1")
        coEvery { assetRepository.fetchArchive(server, bookmark) } returns DownloadedAsset(fetched, bytes)
        val controller = createController(backgroundScope)

        // Act
        controller.setContentSource(ContentSource.FULL_PAGE_ARCHIVE, bookmark)
        runCurrent()

        // Assert
        assertEquals(ContentSource.FULL_PAGE_ARCHIVE, controller.selectedSource.value)
        assertEquals("<html>archived</html>", controller.sourceContentOverride.value)
        assertEquals("/cache/archive_archive-1", controller.precrawledAssetPath.value)
    }

    @Test
    fun deletedArchiveFallsBackToExtracted() = runTest(testDispatcher) {
        // Arrange: the archive is cached and showing
        val archive = asset.copy(id = "a", assetType = "precrawledArchive", localPath = "/archive")
        every { serverRepository.servers } returns flowOf(listOf(server))
        coEvery { assetRepository.download(server, archive, any()) } returns
            DownloadedAsset(archive, "<html/>".encodeToByteArray())
        coEvery { assetRepository.assetsFor(bookmark) } returns listOf(archive)
        val controller = createController(backgroundScope)
        controller.downloadOrRefreshAsset(archive, bookmark, useWhenDone = true)
        runCurrent()
        assertEquals(ContentSource.FULL_PAGE_ARCHIVE, controller.selectedSource.value)
        assertEquals("<html/>", controller.sourceContentOverride.value)

        // Act: its local copy goes away
        coEvery { assetRepository.assetsFor(bookmark) } returns listOf(archive.copy(localPath = null))
        controller.deleteAssetLocal(archive)
        runCurrent()

        // Assert
        assertEquals(ContentSource.EXTRACTED, controller.selectedSource.value)
        assertEquals(null, controller.sourceContentOverride.value)
    }
}
