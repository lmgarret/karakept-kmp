package com.karakept.app.data.repository

import com.karakept.api.model.Highlight
import com.karakept.app.data.local.dao.HighlightDao
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [HighlightRepository.syncHighlights] delete-reconciliation safety:
 * local highlights must only be deleted against a fully fetched remote set.
 */
class HighlightRepositorySyncTest : BaseRepositoryTest() {

    private val highlightDao = mockk<HighlightDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val bookmarkActionsRepository = mockk<BookmarkActionsRepository>(relaxed = true)

    private val repository = HighlightRepository(
        highlightDao = highlightDao,
        remoteDataSource = remoteDataSource,
        bookmarkActionsRepository = bookmarkActionsRepository
    )

    private val testServer = Server(
        id = "server1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test Server"
    )

    private fun makeHighlight(id: String) = Highlight(
        bookmarkId = "bk-1",
        startOffset = 0.0,
        endOffset = 10.0,
        text = "text-$id",
        note = null,
        id = id,
        userId = "u1",
        createdAt = "2026-01-01T00:00:00Z"
    )

    @Test
    fun syncHighlights_reconcilesAgainstFullRemoteSet() = runTest(testDispatcher) {
        // Arrange: more highlights than the old single-page limit of 100
        val remote = (1..130).map { makeHighlight("h$it") }
        coEvery { remoteDataSource.fetchAllHighlights(testServer) } returns remote

        // Act
        repository.syncHighlights(testServer)

        // Assert: delete-reconciliation received every remote id, not just the first page
        val idsSlot = slot<List<String>>()
        coVerify { highlightDao.deleteHighlightsNotInList("server1", capture(idsSlot)) }
        assertEquals(130, idsSlot.captured.size)
        coVerify { highlightDao.insertHighlights(match { it.size == 130 }) }
    }

    @Test
    fun syncHighlights_fetchFailurePerformsNoDeletes() = runTest(testDispatcher) {
        // Arrange: mid-pagination failure surfaces as an exception from the fetch
        coEvery { remoteDataSource.fetchAllHighlights(testServer) } throws ApiException("HTTP 500: boom")

        // Act
        repository.syncHighlights(testServer)

        // Assert: no local data touched on a partial fetch
        coVerify(exactly = 0) { highlightDao.deleteHighlightsNotInList(any(), any()) }
        coVerify(exactly = 0) { highlightDao.deleteAllNonTempHighlightsForServer(any()) }
        coVerify(exactly = 0) { highlightDao.insertHighlights(any()) }
    }
}
