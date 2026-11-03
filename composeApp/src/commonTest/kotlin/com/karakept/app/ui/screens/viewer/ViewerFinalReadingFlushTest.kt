package com.karakept.app.ui.screens.viewer

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.karakept.app.data.local.dao.AssetDao
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.AssetRepository
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.utils.TestAppDispatchers
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

// The final flush runs from onCleared, after viewModelScope is cancelled: it only lands
// because BookmarkActionsRepository owns the write on its own scope.
@OptIn(ExperimentalCoroutinesApi::class)
class ViewerFinalReadingFlushTest {

    private val testDispatcher = StandardTestDispatcher()
    private val appDispatchers = TestAppDispatchers(testDispatcher)

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val bookmarkRepository = mockk<BookmarkRepository>(relaxed = true)

    private val actionsRepository = BookmarkActionsRepository(
        bookmarkDao = bookmarkDao,
        pendingActionDao = mockk<PendingActionDao>(relaxed = true),
        remoteDataSource = remoteDataSource,
        serverRepository = mockk(relaxed = true),
        settingsRepository = settingsRepository,
        appDispatchers = appDispatchers
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.viewerMode } returns flowOf(ViewerMode.READER)
        every { settingsRepository.hideArticleThumbnails } returns flowOf(true)
        every { settingsRepository.htmlTextColor } returns flowOf(null)
        every { settingsRepository.htmlBackgroundColor } returns flowOf(null)
        every { settingsRepository.htmlFontSize } returns flowOf(16)
        every { settingsRepository.htmlFontFamily } returns flowOf(ReaderFontFamily.SYSTEM)
        every { settingsRepository.showTagsInViewer } returns flowOf(true)
        every { settingsRepository.dateDisplayMode } returns flowOf(DateDisplayMode.ELAPSED)
        every { settingsRepository.linkOpenMode } returns flowOf(LinkOpenMode.CUSTOM_TAB)
        every { settingsRepository.offlineMode } returns flowOf(true)
        every { settingsRepository.trackReadingProgress } returns flowOf(true)
        every { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.PER_BOOKMARK)
        every { settingsRepository.preferFullPageHtml } returns flowOf(false)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `final flush lands after the screen model's scope is cancelled`() = runTest(testDispatcher) {
        val store = ViewModelStore()
        val screenModel = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    BookmarkViewerScreenModel(
                        assetRepository = AssetRepository(assetDao, remoteDataSource, appDispatchers),
                        settingsRepository = settingsRepository,
                        bookmarkActionsRepository = actionsRepository,
                        serverRepository = mockk(relaxed = true),
                        bookmarkRepository = bookmarkRepository,
                        listRepository = mockk(relaxed = true),
                        bookmarkActionController = mockk(relaxed = true),
                        highlightRepository = mockk(relaxed = true),
                        snackbarManager = mockk(relaxed = true)
                    )
                }
            }
        )[BookmarkViewerScreenModel::class]

        screenModel.onReadingStateChanged(1L, "remote-1", 0.6f, 6, 0)
        runCurrent()

        // Leaves before the debounce fires: cancels viewModelScope, then runs onCleared.
        store.clear()
        advanceUntilIdle()

        coVerify(exactly = 0) { bookmarkRepository.saveReadingProgress(any(), any(), any(), any()) }
        coVerify(exactly = 1) { bookmarkDao.updateReadingProgress(1L, 0.6f, 6, 0) }
    }
}
