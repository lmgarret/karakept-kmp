package com.karakept.app.ui.screens

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.remote.UnsupportedServerActionException
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.ServerCrawlAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the server-side crawl/archive actions exposed in the bookmark details panel.
 *
 * These actions have no optimistic local counterpart — they ask the Karakeep server to run a
 * background job — so the contract under test is: the right request goes out, the in-flight
 * marker is always cleared, and a failure never leaves local state changed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkViewerServerActionsTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var assetDao: AssetDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var bookmarkActionsRepository: BookmarkActionsRepository
    private lateinit var remoteDataSource: RemoteDataSource
    private lateinit var serverRepository: ServerRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var bookmarkActionController: BookmarkActionController
    private lateinit var highlightRepository: HighlightRepository
    private lateinit var snackbarManager: ActionSnackbarManager

    private val testServer = Server(
        id = "server-1",
        url = "https://karakeep.example.com",
        apiKey = "key",
        label = "Test"
    )

    private val testBookmark = BookmarkEntity(
        localId = 1L,
        remoteId = "remote-100",
        serverId = "server-1",
        url = "https://example.com/article",
        title = "Test Article",
        content = "<p>Test content</p>",
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 0L,
        isArchived = false,
        isStarred = false,
        isRead = false
    )

    private val archiveAsset = AssetEntity(
        id = "asset-1",
        bookmarkRemoteId = "remote-100",
        serverId = "server-1",
        assetType = "fullPageArchive",
        fileName = null,
        contentType = null,
        localPath = null
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        bookmarkDao = mockk(relaxed = true)
        assetDao = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        bookmarkActionsRepository = mockk(relaxed = true)
        remoteDataSource = mockk(relaxed = true)
        serverRepository = mockk(relaxed = true)
        bookmarkRepository = mockk(relaxed = true)
        bookmarkActionController = mockk(relaxed = true)
        highlightRepository = mockk(relaxed = true)
        snackbarManager = mockk(relaxed = true)

        every { settingsRepository.viewerMode } returns flowOf(com.karakept.app.data.model.ViewerMode.READER)
        every { settingsRepository.hideArticleThumbnails } returns flowOf(true)
        every { settingsRepository.htmlTextColor } returns flowOf(null)
        every { settingsRepository.htmlBackgroundColor } returns flowOf(null)
        every { settingsRepository.htmlFontSize } returns flowOf(16)
        every { settingsRepository.htmlFontFamily } returns flowOf(com.karakept.app.data.model.ReaderFontFamily.SYSTEM)
        every { settingsRepository.showTagsInViewer } returns flowOf(true)
        every { settingsRepository.dateDisplayMode } returns flowOf(com.karakept.app.data.model.DateDisplayMode.ELAPSED)
        every { settingsRepository.linkOpenMode } returns flowOf(com.karakept.app.data.model.LinkOpenMode.CUSTOM_TAB)
        every { settingsRepository.offlineMode } returns flowOf(false)
        every { settingsRepository.trackReadingProgress } returns flowOf(true)
        every { settingsRepository.preferFullPageHtml } returns flowOf(false)

        every { serverRepository.servers } returns flowOf(listOf(testServer))
        every { bookmarkDao.observeBookmarkById(any()) } returns flowOf(testBookmark)
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns emptyList()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createScreenModel() = BookmarkViewerScreenModel(
        bookmarkDao = bookmarkDao,
        assetDao = assetDao,
        settingsRepository = settingsRepository,
        bookmarkActionsRepository = bookmarkActionsRepository,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        bookmarkRepository = bookmarkRepository,
        bookmarkActionController = bookmarkActionController,
        highlightRepository = highlightRepository,
        snackbarManager = snackbarManager
    )

    // -----------------------------------------------------------------------
    // requestServerCrawl
    // -----------------------------------------------------------------------

    @Test
    fun `refresh sends a recrawl with both flags off`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.REFRESH)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            remoteDataSource.recrawlBookmark(
                server = testServer,
                bookmarkId = "remote-100",
                archiveFullPage = false,
                storePdf = false
            )
        }
    }

    @Test
    fun `preserve archive sends archiveFullPage`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.PRESERVE_ARCHIVE)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            remoteDataSource.recrawlBookmark(
                server = testServer,
                bookmarkId = "remote-100",
                archiveFullPage = true,
                storePdf = false
            )
        }
    }

    @Test
    fun `preserve pdf sends storePdf`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.PRESERVE_PDF)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            remoteDataSource.recrawlBookmark(
                server = testServer,
                bookmarkId = "remote-100",
                archiveFullPage = false,
                storePdf = true
            )
        }
    }

    @Test
    fun `a successful request re-syncs the bookmark and clears the in-flight marker`() =
        runTest(testDispatcher) {
            val screenModel = createScreenModel()

            screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.REFRESH)
            advanceUntilIdle()

            coVerify(exactly = 1) { bookmarkRepository.syncSingleBookmark("remote-100", "server-1") }
            assertNull(screenModel.serverCrawlInFlight.value)
        }

    @Test
    fun `a failed request clears the in-flight marker and does not re-sync`() =
        runTest(testDispatcher) {
            coEvery {
                remoteDataSource.recrawlBookmark(any(), any(), any(), any())
            } throws ApiException("HTTP 500: boom")
            val screenModel = createScreenModel()

            screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.REFRESH)
            advanceUntilIdle()

            assertNull(screenModel.serverCrawlInFlight.value)
            coVerify(exactly = 0) { bookmarkRepository.syncSingleBookmark(any(), any()) }
        }

    @Test
    fun `an unsupported server surfaces the reason instead of a retry prompt`() =
        runTest(testDispatcher) {
            coEvery {
                remoteDataSource.recrawlBookmark(any(), any(), any(), any())
            } throws UnsupportedServerActionException("This Karakeep server doesn't support re-crawling from the app")
            val screenModel = createScreenModel()

            screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.REFRESH)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                snackbarManager.showSnackbar("This Karakeep server doesn't support re-crawling from the app")
            }
            coVerify(exactly = 0) { snackbarManager.showErrorWithRetry(any(), any(), any()) }
        }

    @Test
    fun `a second request is ignored while one is in flight`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.REFRESH)
        // No advanceUntilIdle: the first request is still mid-flight (it waits before re-syncing).
        screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.PRESERVE_ARCHIVE)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            remoteDataSource.recrawlBookmark(any(), any(), archiveFullPage = true, storePdf = false)
        }
    }

    // -----------------------------------------------------------------------
    // deleteAssetOnServer
    // -----------------------------------------------------------------------

    @Test
    fun `deleting an asset on the server detaches it and drops the local row`() =
        runTest(testDispatcher) {
            val screenModel = createScreenModel()

            screenModel.deleteAssetOnServer(archiveAsset, testBookmark)
            advanceUntilIdle()

            coVerify(exactly = 1) { remoteDataSource.detachAsset(testServer, "remote-100", "asset-1") }
            coVerify(exactly = 1) { assetDao.deleteAsset("asset-1") }
        }

    @Test
    fun `a failed server delete keeps the local asset row`() = runTest(testDispatcher) {
        coEvery { remoteDataSource.detachAsset(any(), any(), any()) } throws ApiException("HTTP 500")
        val screenModel = createScreenModel()

        screenModel.deleteAssetOnServer(archiveAsset, testBookmark)
        advanceUntilIdle()

        coVerify(exactly = 0) { assetDao.deleteAsset(any()) }
        coVerify(exactly = 1) { snackbarManager.showSnackbar("Couldn't delete from server") }
    }

    @Test
    fun `deleting the displayed archive falls back to extracted content`() = runTest(testDispatcher) {
        // Asset list is empty after the delete, so nothing is left to display as the archive.
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns emptyList()
        val screenModel = createScreenModel()
        screenModel.setContentSource(
            com.karakept.app.data.model.ContentSource.FULL_PAGE_ARCHIVE,
            testBookmark
        )
        advanceUntilIdle()

        screenModel.deleteAssetOnServer(archiveAsset, testBookmark)
        advanceUntilIdle()

        assertEquals(
            com.karakept.app.data.model.ContentSource.EXTRACTED,
            screenModel.selectedSource.value
        )
    }

    // -----------------------------------------------------------------------
    // Waiting for the crawl job's output
    // -----------------------------------------------------------------------

    @Test
    fun `preserve archive keeps re-syncing until the asset shows up`() = runTest(testDispatcher) {
        // Server finishes the crawl on the third poll.
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returnsMany listOf(
            emptyList(),
            emptyList(),
            listOf(archiveAsset)
        )
        val screenModel = createScreenModel()

        screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.PRESERVE_ARCHIVE)
        advanceUntilIdle()

        // One sync per poll, stopping as soon as the asset lands rather than burning the budget.
        coVerify(exactly = 3) { bookmarkRepository.syncSingleBookmark("remote-100", "server-1") }
        coVerify(exactly = 1) { snackbarManager.showSnackbar("Archive ready") }
        assertNull(screenModel.serverCrawlInFlight.value)
    }

    @Test
    fun `preserve archive says so when the server is still working after the budget`() =
        runTest(testDispatcher) {
            coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns emptyList()
            val screenModel = createScreenModel()

            screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.PRESERVE_ARCHIVE)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                snackbarManager.showSnackbar("Still processing on the server — pull to refresh later")
            }
            assertNull(screenModel.serverCrawlInFlight.value)
        }

    @Test
    fun `refresh does not wait for an asset that will never appear`() = runTest(testDispatcher) {
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns emptyList()
        val screenModel = createScreenModel()

        screenModel.requestServerCrawl(testBookmark, ServerCrawlAction.REFRESH)
        advanceUntilIdle()

        // REFRESH rewrites metadata rather than adding an asset, so one sync is the whole job.
        coVerify(exactly = 1) { bookmarkRepository.syncSingleBookmark("remote-100", "server-1") }
        coVerify(exactly = 0) {
            snackbarManager.showSnackbar("Still processing on the server — pull to refresh later")
        }
    }

    // -----------------------------------------------------------------------
    // Per-asset download
    // -----------------------------------------------------------------------

    @Test
    fun `download requests the asset the user tapped`() = runTest(testDispatcher) {
        // Regression: archives used to route through fetchAndCacheArchive, which re-resolved the
        // asset from the server and could stamp the local path onto a different archive row —
        // leaving the tapped row reading "On server" with no delete-local action.
        val other = archiveAsset.copy(id = "asset-2", assetType = "precrawledArchive")
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns listOf(archiveAsset, other)
        coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } throws ApiException("stop here")
        val screenModel = createScreenModel()

        screenModel.downloadOrRefreshAsset(other, testBookmark)
        advanceUntilIdle()

        coVerify(exactly = 1) { remoteDataSource.downloadAsset(testServer, "asset-2", any()) }
        coVerify(exactly = 0) { remoteDataSource.downloadAsset(any(), "asset-1", any()) }
    }

    @Test
    fun `a second download for the same asset is ignored while one is running`() =
        runTest(testDispatcher) {
            coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } throws ApiException("boom")
            val screenModel = createScreenModel()

            screenModel.downloadOrRefreshAsset(archiveAsset, testBookmark)
            screenModel.downloadOrRefreshAsset(archiveAsset, testBookmark)
            advanceUntilIdle()

            coVerify(exactly = 1) { remoteDataSource.downloadAsset(any(), "asset-1", any()) }
        }

    @Test
    fun `a failed download clears the progress entry`() = runTest(testDispatcher) {
        coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } throws ApiException("boom")
        val screenModel = createScreenModel()

        screenModel.downloadOrRefreshAsset(archiveAsset, testBookmark)
        advanceUntilIdle()

        assertTrue(screenModel.assetDownloads.value.isEmpty())
        coVerify(exactly = 1) { snackbarManager.showSnackbar("Couldn't download asset") }
    }

    @Test
    fun `opening an asset with no local copy tells the user to download it`() =
        runTest(testDispatcher) {
            val screenModel = createScreenModel()

            screenModel.openAssetExternally(archiveAsset)
            advanceUntilIdle()

            coVerify(exactly = 1) { snackbarManager.showSnackbar("Download it first") }
        }

    @Test
    fun `a menu download does not switch the content source`() = runTest(testDispatcher) {
        coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } throws ApiException("boom")
        val screenModel = createScreenModel()

        screenModel.downloadOrRefreshAsset(archiveAsset, testBookmark, useWhenDone = false)
        advanceUntilIdle()

        assertEquals(
            com.karakept.app.data.model.ContentSource.EXTRACTED,
            screenModel.selectedSource.value
        )
    }
}
