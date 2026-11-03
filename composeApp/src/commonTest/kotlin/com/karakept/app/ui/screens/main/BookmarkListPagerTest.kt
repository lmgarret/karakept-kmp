package com.karakept.app.ui.screens.main

import com.karakept.app.data.model.BookmarkWindow
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.Server
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.ui.screens.main.MainScreenModelHarness.Companion.bookmark
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkListPagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val pageSize = BookmarkWindow.DEFAULT_PAGE_SIZE
    private val server = Server(id = "server-1", url = "https://example.com", apiKey = "k", label = "Test")
    private val rows = (1L..500L).map { bookmark(it) }
    private val bookmarkRepository: BookmarkRepository = mockk(relaxed = true)

    private fun createPager() = BookmarkListPager(
        bookmarkRepository = bookmarkRepository,
        selectedServer = MutableStateFlow(server),
        effectiveFilter = MutableStateFlow(FilterConfig()),
        effectiveFilterNow = { FilterConfig() },
        listVersion = MutableStateFlow(0),
        pageSize = pageSize
    )

    @Test
    fun readsOnlyMissingPages() = runTest(testDispatcher) {
        // Arrange
        every { bookmarkRepository.countBookmarksForViewFlow(any(), any()) } returns
            MutableStateFlow(rows.size)
        coEvery { bookmarkRepository.getBookmarkPage(any(), any(), any(), any()) } answers {
            val offset = thirdArg<Int>()
            rows.subList(offset, minOf(offset + pageSize, rows.size))
        }
        val pager = createPager()
        val job = launch { pager.window("").collect {} }

        // Act
        pager.reportVisibleSlots(0 until pageSize)
        advanceUntilIdle()
        pager.reportVisibleSlots(0 until 2 * pageSize)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { bookmarkRepository.getBookmarkPage(server, any(), 0, pageSize) }
        coVerify(exactly = 1) { bookmarkRepository.getBookmarkPage(server, any(), pageSize, pageSize) }
        job.cancel()
    }

    @Test
    fun searchWindowUsesSearchQuery() = runTest(testDispatcher) {
        // Arrange
        every { bookmarkRepository.countBookmarksForViewFlow(any(), any()) } returns
            MutableStateFlow(rows.size)
        coEvery { bookmarkRepository.searchBookmarks(any(), any(), "kotlin") } returns rows.take(2)
        val pager = createPager()
        var window = BookmarkWindow.EMPTY
        val job = launch { pager.window("kotlin").collect { window = it } }

        // Act
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { bookmarkRepository.searchBookmarks(server, any(), "kotlin") }
        coVerify(exactly = 0) { bookmarkRepository.getBookmarkPage(any(), any(), any(), any()) }
        assertEquals(rows.take(2), window.loadedRows())
        job.cancel()
    }
}
