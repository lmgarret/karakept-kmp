package com.karakept.app.ui.screens.main

import com.karakept.app.testing.testBookmark
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.Server
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A cold start used to render an empty LazyColumn, which looks exactly like a list that has
 * genuinely resolved to zero bookmarks. [MainScreenModel.isLoadingInitialPage] is what lets
 * the UI tell those apart — it holds the empty state back until the first page has actually
 * resolved, so a cold start doesn't flash "nothing here" before the list arrives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenModelInitialLoadingStateTest {
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
        every { harness.bookmarkRepository.pageCommitted } returns MutableSharedFlow()
        every { harness.bookmarkRepository.syncProgress } returns MutableStateFlow(
            com.karakept.app.data.model.SyncProgress.Idle
        )
        coEvery { harness.bookmarkRepository.shouldAutoSync(any()) } returns false
        stubView(emptyList())
    }

    /**
     * Makes [rows] the view the database reports — its size, then its pages.
     *
     * The size is what settles whether the list has resolved: until it arrives, an empty window
     * is a view nobody has counted rather than a view with nothing in it.
     */
    private fun stubView(rows: List<Bookmark>) {
        every { harness.bookmarkRepository.countBookmarksForViewFlow(any(), any()) } returns flowOf(rows.size)
        coEvery { harness.bookmarkRepository.getBookmarkPage(any(), any(), any(), any()) } answers {
            val offset = thirdArg<Int>()
            val limit = arg<Int>(3)
            if (offset >= rows.size) emptyList()
            else rows.subList(offset, minOf(offset + limit, rows.size))
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `is loading before the view has been counted`() = runTest(testDispatcher) {
        val model = harness.createModel()

        assertTrue(model.isLoadingInitialPage.value, "should report loading before init completes")
    }

    @Test
    fun `stops loading once an empty result resolves so the empty state can show`() =
        runTest(testDispatcher) {
            val model = harness.createModel()
            val job = launch { model.bookmarkWindow.collect {} }
            advanceUntilIdle()

            assertFalse(
                model.isLoadingInitialPage.value,
                "an empty library must resolve so the empty state can show, not stay loading forever"
            )
            job.cancel()
        }

    @Test
    fun `stops loading once bookmarks are available`() = runTest(testDispatcher) {
        stubView(listOf(testBookmark(1), testBookmark(2)))

        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        assertFalse(model.isLoadingInitialPage.value)
        job.cancel()
    }

    @Test
    fun `bottom spinner stays hidden while a view switch reloads page zero`() =
        runTest(testDispatcher) {
            val model = harness.createModel()
            advanceUntilIdle()

            // resetPaginationAndLoad raises _isLoadingMore as a mutex against loadNextPage.
            // That must not surface as the bottom "loading more" spinner.
            model._isLoadingMore.value = true
            model._isResettingPagination.value = true
            advanceUntilIdle()

            assertFalse(model.isLoadingMore.value)

            model._isResettingPagination.value = false
            advanceUntilIdle()

            assertTrue(model.isLoadingMore.value)
        }
}
