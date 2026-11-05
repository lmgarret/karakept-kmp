package com.karakept.app.ui.screens

import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ContentSource
import com.karakept.app.data.model.SANITIZED_ASSET_NAME
import com.karakept.app.data.model.SANITIZED_ASSET_TYPE
import com.karakept.app.data.model.SanitizeJob
import com.karakept.app.data.model.SanitizeJobStatus
import com.karakept.app.data.model.SanitizeOutcome
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.remote.SidecarUnavailableException
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ReadingProgressPullResult
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.SidecarRepository
import com.karakept.app.data.repository.pullReadingProgressFromServer
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.domain.action.BookmarkActionController
import com.karakept.app.domain.action.SidecarAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the sidecar integration in [BookmarkViewerScreenModel]: the on-demand cleanup action,
 * and choosing the sanitized copy as the content source.
 *
 * The sidecar is optional, so two things are as important as the happy path: the action stays
 * hidden unless a service has said it will run the work, and nothing a misconfigured sidecar does
 * can stop a bookmark from opening.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkViewerSidecarTest {

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
    private lateinit var sidecarRepository: SidecarRepository

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

    private val sanitizedAsset = AssetEntity(
        id = "asset-sanitized",
        bookmarkRemoteId = "remote-100",
        serverId = "server-1",
        assetType = SANITIZED_ASSET_TYPE,
        fileName = SANITIZED_ASSET_NAME,
        contentType = null,
        localPath = null
    )

    private val archiveAsset = AssetEntity(
        id = "asset-archive",
        bookmarkRemoteId = "remote-100",
        serverId = "server-1",
        assetType = "fullPageArchive",
        fileName = "archive_asset-archive",
        contentType = null,
        localPath = "/cache/archive_asset-archive"
    )

    /** Another hand-attached upload, which the app must keep ignoring. */
    private val otherUpload = AssetEntity(
        id = "asset-other",
        bookmarkRemoteId = "remote-100",
        serverId = "server-1",
        assetType = SANITIZED_ASSET_TYPE,
        fileName = "my-notes.html",
        contentType = null,
        localPath = null
    )

    private fun runningJob() = SanitizeJob(
        jobId = "job-1",
        bookmarkId = "remote-100",
        status = SanitizeJobStatus.RUNNING
    )

    private fun doneJob(outcome: SanitizeOutcome) = SanitizeJob(
        jobId = "job-1",
        bookmarkId = "remote-100",
        status = SanitizeJobStatus.DONE,
        outcome = outcome
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositorySyncKt")

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
        sidecarRepository = mockk(relaxed = true)

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
        every { settingsRepository.contentSyncStrategy } returns flowOf(com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK)
        every { settingsRepository.preferFullPageHtml } returns flowOf(false)
        every { settingsRepository.preferSanitizedHtml } returns flowOf(false)

        // Without the mockkStatic above, this stub has no receiver to record against and MockK
        // spirals into millions of stubs — which looks exactly like a hung test.
        coEvery {
            bookmarkActionsRepository.pullReadingProgressFromServer(any(), any())
        } returns ReadingProgressPullResult.SKIPPED

        every { serverRepository.servers } returns flowOf(listOf(testServer))
        every { bookmarkDao.observeBookmarkById(any()) } returns flowOf(testBookmark)
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns emptyList()

        // A relaxed mock returns a Flow that never emits; sidecar lookups call first() on it.
        every { sidecarRepository.sidecars } returns flowOf(emptyMap())
        coEvery { sidecarRepository.isUsable(any(), any()) } returns false
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("com.karakept.app.data.repository.BookmarkActionsRepositorySyncKt")
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
        snackbarManager = snackbarManager,
        sidecarRepository = sidecarRepository
    )

    // -----------------------------------------------------------------------
    // Offering the action at all
    // -----------------------------------------------------------------------

    @Test
    fun `the action stays hidden without a usable sidecar`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertFalse(screenModel.sanitizeActionAvailable.value)
    }

    @Test
    fun `a usable sidecar reveals the action`() = runTest(testDispatcher) {
        coEvery { sidecarRepository.isUsable(any(), any()) } returns true
        val screenModel = createScreenModel()

        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertTrue(screenModel.sanitizeActionAvailable.value)
    }

    @Test
    fun `a sidecar that throws on probe cannot stop the bookmark opening`() = runTest(testDispatcher) {
        coEvery { sidecarRepository.refreshCapabilities(any(), any()) } throws RuntimeException("boom")
        val screenModel = createScreenModel()

        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertTrue(screenModel.loadingState.value is BookmarkLoadingState.FullyLoaded)
        assertFalse(screenModel.sanitizeActionAvailable.value)
    }

    // -----------------------------------------------------------------------
    // Running the action
    // -----------------------------------------------------------------------

    @Test
    fun `a request is sent and the in-flight marker always clears`() = runTest(testDispatcher) {
        coEvery { sidecarRepository.requestSanitize(any(), any()) } returns runningJob()
        coEvery { sidecarRepository.fetchSanitizeStatus(any(), any()) } returns
            doneJob(SanitizeOutcome.UPLOADED)
        val screenModel = createScreenModel()

        screenModel.runSidecarAction(testBookmark, SidecarAction.SANITIZE_HTML)
        advanceUntilIdle()

        coVerify(exactly = 1) { sidecarRepository.requestSanitize(testBookmark, any()) }
        assertNull(screenModel.sidecarActionInFlight.value)
    }

    @Test
    fun `a second request is ignored while one is in flight`() = runTest(testDispatcher) {
        coEvery { sidecarRepository.requestSanitize(any(), any()) } returns runningJob()
        coEvery { sidecarRepository.fetchSanitizeStatus(any(), any()) } returns runningJob()
        val screenModel = createScreenModel()

        screenModel.runSidecarAction(testBookmark, SidecarAction.SANITIZE_HTML)
        screenModel.runSidecarAction(testBookmark, SidecarAction.SANITIZE_HTML)
        advanceUntilIdle()

        coVerify(exactly = 1) { sidecarRepository.requestSanitize(any(), any()) }
    }

    @Test
    fun `an uploaded outcome syncs the bookmark and shows the cleaned copy`() = runTest(testDispatcher) {
        coEvery { sidecarRepository.requestSanitize(any(), any()) } returns runningJob()
        coEvery { sidecarRepository.fetchSanitizeStatus(any(), any()) } returns
            doneJob(SanitizeOutcome.UPLOADED)
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns listOf(sanitizedAsset)
        coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } returns
            "<p>clean</p>".encodeToByteArray()
        val screenModel = createScreenModel()

        screenModel.runSidecarAction(testBookmark, SidecarAction.SANITIZE_HTML)
        advanceUntilIdle()

        coVerify(exactly = 1) { bookmarkRepository.syncSingleBookmark("remote-100", "server-1") }
        coVerify(exactly = 1) { remoteDataSource.downloadAsset(testServer, "asset-sanitized", any()) }
        assertEquals(ContentSource.SANITIZED, screenModel.selectedSource.value)
        coVerify { snackbarManager.showSnackbar(SanitizeOutcome.UPLOADED.message) }
    }

    @Test
    fun `a terminal outcome stops polling and says why`() = runTest(testDispatcher) {
        coEvery { sidecarRepository.requestSanitize(any(), any()) } returns runningJob()
        coEvery { sidecarRepository.fetchSanitizeStatus(any(), any()) } returns
            doneJob(SanitizeOutcome.TOO_LARGE)
        val screenModel = createScreenModel()

        screenModel.runSidecarAction(testBookmark, SidecarAction.SANITIZE_HTML)
        advanceUntilIdle()

        coVerify { snackbarManager.showSnackbar(SanitizeOutcome.TOO_LARGE.message) }
        // No asset is ever coming, so re-syncing for one would be pure waste.
        coVerify(exactly = 0) { bookmarkRepository.syncSingleBookmark(any(), any()) }
        assertNull(screenModel.sidecarActionInFlight.value)
    }

    @Test
    fun `a job that never finishes reports that it is still working`() = runTest(testDispatcher) {
        coEvery { sidecarRepository.requestSanitize(any(), any()) } returns runningJob()
        coEvery { sidecarRepository.fetchSanitizeStatus(any(), any()) } returns runningJob()
        val screenModel = createScreenModel()

        screenModel.runSidecarAction(testBookmark, SidecarAction.SANITIZE_HTML)
        advanceUntilIdle()

        coVerify { snackbarManager.showSnackbar("Still cleaning up — pull to refresh later") }
        assertNull(screenModel.sidecarActionInFlight.value)
    }

    @Test
    fun `an unavailable service surfaces its reason instead of a retry prompt`() = runTest(testDispatcher) {
        coEvery { sidecarRepository.requestSanitize(any(), any()) } throws
            SidecarUnavailableException("The cleanup service has no model configured")
        val screenModel = createScreenModel()

        screenModel.runSidecarAction(testBookmark, SidecarAction.SANITIZE_HTML)
        advanceUntilIdle()

        coVerify { snackbarManager.showSnackbar("The cleanup service has no model configured") }
        coVerify(exactly = 0) { snackbarManager.showErrorWithRetry(any(), any(), any()) }
    }

    @Test
    fun `a transport failure offers a retry and clears the marker`() = runTest(testDispatcher) {
        coEvery { sidecarRepository.requestSanitize(any(), any()) } throws RuntimeException("boom")
        val screenModel = createScreenModel()

        screenModel.runSidecarAction(testBookmark, SidecarAction.SANITIZE_HTML)
        advanceUntilIdle()

        coVerify { snackbarManager.showErrorWithRetry("Couldn't reach the cleanup service", any(), any()) }
        assertNull(screenModel.sidecarActionInFlight.value)
    }

    // -----------------------------------------------------------------------
    // Choosing the sanitized copy as the content source
    // -----------------------------------------------------------------------

    @Test
    fun `a sanitized asset is detected by name`() = runTest(testDispatcher) {
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns
            listOf(otherUpload, sanitizedAsset)
        val screenModel = createScreenModel()

        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertTrue(screenModel.sanitizedAvailable.value)
    }

    @Test
    fun `another hand-attached upload is not mistaken for one`() = runTest(testDispatcher) {
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns listOf(otherUpload)
        val screenModel = createScreenModel()

        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertFalse(screenModel.sanitizedAvailable.value)
    }

    @Test
    fun `the preference opens the bookmark on the cleaned copy`() = runTest(testDispatcher) {
        every { settingsRepository.preferSanitizedHtml } returns flowOf(true)
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns listOf(sanitizedAsset)
        coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } returns
            "<p>clean</p>".encodeToByteArray()
        val screenModel = createScreenModel()

        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertEquals(ContentSource.SANITIZED, screenModel.selectedSource.value)
        // The preference means "open on it", so the bytes are fetched rather than merely offered.
        coVerify(exactly = 1) { remoteDataSource.downloadAsset(testServer, "asset-sanitized", any()) }
    }

    @Test
    fun `without the preference the bookmark opens on extracted content`() = runTest(testDispatcher) {
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns listOf(sanitizedAsset)
        val screenModel = createScreenModel()

        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertEquals(ContentSource.EXTRACTED, screenModel.selectedSource.value)
        coVerify(exactly = 0) { remoteDataSource.downloadAsset(any(), any(), any()) }
    }

    @Test
    fun `the cleaned copy wins over the archive when both are preferred`() = runTest(testDispatcher) {
        every { settingsRepository.preferSanitizedHtml } returns flowOf(true)
        every { settingsRepository.preferFullPageHtml } returns flowOf(true)
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns
            listOf(archiveAsset, sanitizedAsset)
        coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } returns
            "<p>clean</p>".encodeToByteArray()
        val screenModel = createScreenModel()

        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertEquals(ContentSource.SANITIZED, screenModel.selectedSource.value)
    }

    @Test
    fun `the archive is still preferred when no cleaned copy exists`() = runTest(testDispatcher) {
        every { settingsRepository.preferSanitizedHtml } returns flowOf(true)
        every { settingsRepository.preferFullPageHtml } returns flowOf(true)
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns listOf(archiveAsset)
        val screenModel = createScreenModel()

        screenModel.loadBookmark(1L)
        advanceUntilIdle()

        assertEquals(ContentSource.FULL_PAGE_ARCHIVE, screenModel.selectedSource.value)
    }

    @Test
    fun `selecting the cleaned copy offline falls back to extracted`() = runTest(testDispatcher) {
        every { settingsRepository.offlineMode } returns flowOf(true)
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns listOf(sanitizedAsset)
        val screenModel = createScreenModel()
        // The model exposes offlineMode through stateIn(WhileSubscribed), so its value stays at
        // the default until something collects it — as the real UI does.
        backgroundScope.launch { screenModel.offlineMode.collect {} }
        advanceUntilIdle()

        screenModel.setContentSource(ContentSource.SANITIZED, testBookmark)
        advanceUntilIdle()

        assertEquals(ContentSource.EXTRACTED, screenModel.selectedSource.value)
        coVerify { snackbarManager.showSnackbar("Cleaned-up copy not available offline") }
    }

    @Test
    fun `the cleaned copy is loaded as a string in web mode too`() = runTest(testDispatcher) {
        // The archive is a whole document and WEB mode loads it as a file instead; a sanitized
        // fragment has no such path, so without the string the viewer would silently fall back
        // to extracted content.
        every { settingsRepository.viewerMode } returns
            flowOf(com.karakept.app.data.model.ViewerMode.WEB)
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns listOf(sanitizedAsset)
        coEvery { remoteDataSource.downloadAsset(any(), any(), any()) } returns
            "<p>clean</p>".encodeToByteArray()
        val screenModel = createScreenModel()
        advanceUntilIdle()

        screenModel.setContentSource(ContentSource.SANITIZED, testBookmark)
        advanceUntilIdle()

        assertEquals(ContentSource.SANITIZED, screenModel.selectedSource.value)
        assertEquals("<p>clean</p>", screenModel.sourceContentOverride.value)
    }

    @Test
    fun `a cleaned copy that disappears demotes the reader to extracted`() = runTest(testDispatcher) {
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns
            listOf(sanitizedAsset.copy(localPath = "/cache/asset-sanitized"))
        val screenModel = createScreenModel()
        // Loading is what publishes the cached path, which is what lets the source be selected
        // without a download.
        screenModel.loadBookmark(1L)
        advanceUntilIdle()
        screenModel.setContentSource(ContentSource.SANITIZED, testBookmark)
        advanceUntilIdle()
        assertEquals(ContentSource.SANITIZED, screenModel.selectedSource.value)

        // The asset was deleted on the server, so the next reload finds no row at all.
        coEvery { assetDao.getAssetsForBookmark(any(), any()) } returns emptyList()
        screenModel.deleteAssetLocal(sanitizedAsset)
        advanceUntilIdle()

        assertEquals(ContentSource.EXTRACTED, screenModel.selectedSource.value)
    }
}
