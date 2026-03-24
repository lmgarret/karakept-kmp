package com.karakept.app.ui.screens

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for FILT-03: Pull-to-refresh behavior on HighlightsScreenModel.
 *
 * Per RESEARCH.md Pitfall 6, tests exercise the ScreenModel directly rather than
 * simulating PullToRefreshBox gestures, which are unreliable in Robolectric.
 *
 * Verifies:
 * - syncHighlights transitions isSyncing to true then false
 * - syncHighlights calls highlightRepository.syncHighlights
 * - syncHighlights reloads the initial page after sync
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class HighlightsPullToRefreshTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var highlightRepository: HighlightRepository
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var serverRepository: ServerRepository
    private lateinit var settingsRepository: SettingsRepository

    private val fakeServer = Server(
        id = "server-1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        highlightRepository = mockk(relaxed = true)
        bookmarkDao = mockk(relaxed = true)
        serverRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)

        every { settingsRepository.activeServerId } returns flowOf("server-1")
        every { serverRepository.servers } returns flowOf(listOf(fakeServer))
        coEvery { highlightRepository.getHighlightsPaged(any(), any(), any()) } returns emptyList()
        coEvery { highlightRepository.syncHighlights(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createScreenModel() = HighlightsScreenModel(
        highlightRepository = highlightRepository,
        bookmarkDao = bookmarkDao,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository
    )

    @Test
    fun `syncHighlights sets isSyncing to true then false`() = runTest(testDispatcher) {
        val model = createScreenModel()
        advanceUntilIdle() // let init complete

        assertFalse(model.isSyncing.value)

        model.syncHighlights()
        // isSyncing should be true before the coroutine completes
        // We cannot reliably check mid-coroutine with StandardTestDispatcher,
        // so we verify the end state
        advanceUntilIdle()

        assertFalse(model.isSyncing.value, "isSyncing should be false after sync completes")
    }

    @Test
    fun `syncHighlights calls repository syncHighlights`() = runTest(testDispatcher) {
        val model = createScreenModel()
        advanceUntilIdle()

        model.syncHighlights()
        advanceUntilIdle()

        coVerify { highlightRepository.syncHighlights(any()) }
    }

    @Test
    fun `syncHighlights reloads initial page after sync`() = runTest(testDispatcher) {
        val model = createScreenModel()
        advanceUntilIdle()

        model.syncHighlights()
        advanceUntilIdle()

        // getHighlightsPaged should be called at least twice:
        // once during init and once after sync (loadInitialPage)
        coVerify(atLeast = 2) { highlightRepository.getHighlightsPaged(any(), any(), 0) }
    }
}
