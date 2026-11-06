package com.karakept.app.ui.screens.main

import com.karakept.app.testing.testBookmark
import com.karakept.app.data.model.Server
import io.mockk.every
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

/**
 * Tests for FILT-02: QuickFilterCounts computation in MainScreenModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickFilterCountsTest {
    private val testDispatcher = StandardTestDispatcher()
    private val harness = MainScreenModelHarness(testDispatcher)

    private val fakeServer = Server(
        id = "server-1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test"
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { harness.serverRepository.servers } returns flowOf(listOf(fakeServer))
        every { harness.settingsRepository.offlineMode } returns flowOf(false)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `quickFilterCounts is zero when no bookmarks`() = runTest(testDispatcher) {
        bookmarkRepositoryCountsOf(harness.bookmarkRepository, emptyList())

        val model = harness.createModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        assertEquals(QuickFilterCounts(0, 0, 0), model.quickFilterCounts.value)
        job.cancel()
    }

    @Test
    fun `quickFilterCounts computes correct values for mixed dataset`() = runTest(testDispatcher) {
        val bookmarks = listOf(
            testBookmark(1, isArchived = true),
            testBookmark(2, isArchived = true),
            testBookmark(3, isArchived = true),
            testBookmark(4, isStarred = true),
            testBookmark(5, isStarred = true),
            testBookmark(6),
            testBookmark(7),
            testBookmark(8),
            testBookmark(9),
            testBookmark(10)
        )
        every { harness.bookmarkRepository.getBookmarks(any()) } returns flowOf(bookmarks)
        bookmarkRepositoryCountsOf(harness.bookmarkRepository, bookmarks)

        val model = harness.createModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        assertEquals(
            QuickFilterCounts(all = 7, favorites = 2, archived = 3, offline = 0),
            model.quickFilterCounts.value
        )
        job.cancel()
    }

    @Test
    fun `quickFilterCounts counts all archived when all bookmarks archived`() = runTest(testDispatcher) {
        val bookmarks = (1L..5L).map { testBookmark(it, isArchived = true) }
        every { harness.bookmarkRepository.getBookmarks(any()) } returns flowOf(bookmarks)
        bookmarkRepositoryCountsOf(harness.bookmarkRepository, bookmarks)

        val model = harness.createModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        assertEquals(
            QuickFilterCounts(all = 0, favorites = 0, archived = 5, offline = 0),
            model.quickFilterCounts.value
        )
        job.cancel()
    }

    @Test
    fun `quickFilterCounts offline reflects repository count`() = runTest(testDispatcher) {
        bookmarkRepositoryCountsOf(harness.bookmarkRepository, emptyList())
        every { harness.bookmarkRepository.getOfflineBookmarkCount(any()) } returns flowOf(4)

        val model = harness.createModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        assertEquals(4, model.quickFilterCounts.value.offline)
        job.cancel()
    }

    @Test
    fun `quickFilterCounts offline is zero when no server`() = runTest(testDispatcher) {
        every { harness.serverRepository.servers } returns flowOf(emptyList())
        bookmarkRepositoryCountsOf(harness.bookmarkRepository, emptyList())

        val model = harness.createModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        assertEquals(0, model.quickFilterCounts.value.offline)
        job.cancel()
    }
}
