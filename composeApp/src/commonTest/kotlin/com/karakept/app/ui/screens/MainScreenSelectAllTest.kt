package com.karakept.app.ui.screens

import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.ui.screens.MainScreenModelHarness.Companion.bookmark
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * "Select all" means the whole view, not the part of it that has been read.
 *
 * It used to get there by loading the rest of the view into the list first — the only way to have
 * a row's id was to have the row — which made selecting a large view materialise every row of it.
 * The ids come from the database now, so the selection covers rows the list has never shown and
 * costs one query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenSelectAllTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var harness: MainScreenModelHarness

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        harness = MainScreenModelHarness(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectAll covers rows the list has never read`() = runTest(testDispatcher) {
        val view = (1L..500L).map(::bookmark)
        harness.publish(view)
        coEvery { harness.bookmarkRepository.getViewRemoteIds(any(), any()) } returns
            view.map { it.remoteId }

        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()

        assertTrue(
            model.bookmarkWindow.value.loadedCount < 500,
            "the fixture must leave most of the view unread"
        )

        model.selectAll()
        advanceUntilIdle()

        assertEquals(500, model.selectedBookmarkIds.value.size)
        job.cancel()
    }

    @Test
    fun `selectAll asks for the view on screen`() = runTest(testDispatcher) {
        harness.publish((1L..20L).map(::bookmark))
        coEvery { harness.bookmarkRepository.getViewRemoteIds(any(), any()) } returns emptyList()

        val model = harness.createModel()
        advanceUntilIdle()
        val archived = FilterConfig(status = FilterStatus.ARCHIVED)
        model.applyFilter(archived)
        advanceUntilIdle()

        val asked = slot<FilterConfig>()
        model.selectAll()
        advanceUntilIdle()

        coVerify { harness.bookmarkRepository.getViewRemoteIds(any(), capture(asked)) }
        assertEquals(
            archived,
            asked.captured,
            "selecting everything must mean everything in the view being shown"
        )
    }

    @Test
    fun `selectAll marks the selection as covering the whole view`() = runTest(testDispatcher) {
        // Batch AI actions are withheld from a select-all, so the mark has to be set.
        harness.publish((1L..20L).map(::bookmark))
        coEvery { harness.bookmarkRepository.getViewRemoteIds(any(), any()) } returns
            (1L..20L).map { "remote-$it" }

        val model = harness.createModel()
        advanceUntilIdle()

        model.selectAll()
        advanceUntilIdle()

        assertTrue(model.selectedViaSelectAll.value)
        assertEquals(20, model.selectedBookmarkIds.value.size)
    }

    @Test
    fun `selectAll reads the ids rather than the rows`() = runTest(testDispatcher) {
        harness.publish((1L..500L).map(::bookmark))
        coEvery { harness.bookmarkRepository.getViewRemoteIds(any(), any()) } returns
            (1L..500L).map { "remote-$it" }

        val model = harness.createModel()
        val job = launch { model.bookmarkWindow.collect {} }
        advanceUntilIdle()
        harness.clearReads()

        model.selectAll()
        advanceUntilIdle()

        assertEquals(
            emptyList(),
            harness.offsetsRead(),
            "no page was read to produce the selection"
        )
        job.cancel()
    }
}
