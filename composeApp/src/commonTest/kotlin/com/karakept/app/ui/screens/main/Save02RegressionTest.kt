package com.karakept.app.ui.screens.main

import com.karakept.app.testing.testBookmark
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.Server
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertTrue

/**
 * Regression tests for SAVE-02: MainScreenModel must correctly initialise and
 * load bookmarks when created in a fresh Navigator context (e.g. inside
 * BookmarkSavingActivity after a successful bookmark save).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Save02RegressionTest {
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
        bookmarkRepositoryCountsOf(harness.bookmarkRepository, emptyList())
        every { harness.bookmarkRepository.syncProgress } returns MutableStateFlow(
            com.karakept.app.data.model.SyncProgress.Idle
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Makes [rows] the view the repository answers for — its size, and its pages. */
    private fun stubView(rows: List<Bookmark>) {
        every { harness.bookmarkRepository.countBookmarksForViewFlow(any(), any()) } returns flowOf(rows.size)
        coEvery { harness.bookmarkRepository.getBookmarkPage(any(), any(), any(), any()) } answers {
            val offset = thirdArg<Int>()
            val limit = arg<Int>(3)
            if (offset >= rows.size) emptyList()
            else rows.subList(offset, minOf(offset + limit, rows.size))
        }
    }

    @Test
    fun `the list holds rows after init when the server has some`() = runTest(testDispatcher) {
        val fakeBookmarks = (1L..5L).map { testBookmark(it) }
        stubView(fakeBookmarks)

        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        val window = model.bookmarkWindow.value
        assertEquals(5, window.total, "the list is sized by the view")
        assertTrue(window.loadedCount > 0, "and its first page has been read")
        job.cancel()
    }

    @Test
    fun `bookmarks StateFlow is non-empty after init when server available and bookmarks exist`() =
        runTest(testDispatcher) {
            val fakeBookmarks = (1L..3L).map { testBookmark(it) }
            stubView(fakeBookmarks)

            val model = harness.createModel()
            val job = launch { model.bookmarks.collect {} }
            advanceUntilIdle()

            assertTrue(
                model.bookmarks.value.isNotEmpty(),
                "Expected bookmarks.value to be non-empty after init, " +
                    "but was ${model.bookmarks.value.size}"
            )
            job.cancel()
        }

    @Test
    fun `quickFilterCounts reflects loaded bookmarks after init`() = runTest(testDispatcher) {
        val fakeBookmarks = (1L..4L).map { testBookmark(it) }
        coEvery {
            harness.bookmarkRepository.getBookmarksPaged(
                server = any(), status = any(), after = any(), limit = any(),
                listId = any()
            )
        } returns fakeBookmarks

        every { harness.bookmarkRepository.getBookmarks(any()) } returns flowOf(fakeBookmarks)
        bookmarkRepositoryCountsOf(harness.bookmarkRepository, fakeBookmarks)

        val model = harness.createModel()
        val job = launch { model.quickFilterCounts.collect {} }
        advanceUntilIdle()

        val counts = model.quickFilterCounts.value
        assertEquals(
            4,
            counts.all,
            "Expected quickFilterCounts.all = 4 (all non-archived), but was ${counts.all}"
        )
        job.cancel()
    }
}
