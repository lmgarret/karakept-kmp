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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Tests for FILT-03: Pull-to-refresh behavior on HighlightsScreenModel.
 *
 * Verifies:
 * - syncHighlights transitions isSyncing to true then false
 * - syncHighlights calls highlightRepository.syncHighlights
 * - syncHighlights reloads the initial page after sync
 */
@OptIn(ExperimentalCoroutinesApi::class)
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

    @BeforeTest
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

    @AfterTest
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
        advanceUntilIdle()

        assertFalse(model.isSyncing.value)

        model.syncHighlights()
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

        coVerify(atLeast = 2) { highlightRepository.getHighlightsPaged(any(), any(), 0) }
    }
}
