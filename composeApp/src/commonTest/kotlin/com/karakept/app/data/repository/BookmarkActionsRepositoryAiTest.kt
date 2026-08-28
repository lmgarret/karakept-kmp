package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.remote.SummarizeResult
import com.karakept.app.data.remote.UnsupportedServerActionException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the AI actions.
 *
 * These deliberately bypass the pending-action queue, so the things worth pinning down are that
 * the result is persisted and announced, that a polled re-tag gives up rather than hanging, and
 * that the capability cache narrows only on the one rejection that means "this server never will".
 */
class BookmarkActionsRepositoryAiTest : BaseRepositoryTest() {

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val pendingActionDao = mockk<PendingActionDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val bookmarkRepository = mockk<BookmarkRepository>(relaxed = true)

    private val testServer = Server(id = "server1", url = "https://kk.example.com", apiKey = "K", label = "t")

    private val repository = BookmarkActionsRepository(
        bookmarkDao = bookmarkDao,
        pendingActionDao = pendingActionDao,
        remoteDataSource = remoteDataSource,
        serverRepository = serverRepository,
        settingsRepository = settingsRepository,
        appDispatchers = testAppDispatchers
    )

    private fun withConfiguredServer() {
        every { serverRepository.servers } returns flowOf(listOf(testServer))
    }

    // ── summarize ──────────────────────────────────────────────

    @Test
    fun summarize_persistsTheGeneratedSummary() = runTest(testDispatcher) {
        withConfiguredServer()
        val bookmark = makeBookmark()
        coEvery { remoteDataSource.summarizeBookmark(testServer, "remote-42") } returns
            SummarizeResult(summary = "A short summary.", summarizationStatus = "success")

        val returned = repository.summarizeBookmark(bookmark)

        assertEquals("A short summary.", returned)
        coVerify { bookmarkDao.updateSummary(bookmark.localId, "A short summary.", "success") }
    }

    @Test
    fun summarize_announcesTheChangeSoOtherScreensRefresh() = runTest(testDispatcher) {
        withConfiguredServer()
        val bookmark = makeBookmark()
        coEvery { remoteDataSource.summarizeBookmark(any(), any()) } returns
            SummarizeResult("Summary", "success")

        var emitted: Long? = null
        val job = launch { repository.bookmarkChangedEvents.collect { emitted = it } }
        advanceUntilIdle() // SharedFlow has replay 0 — subscribe before the emit

        repository.summarizeBookmark(bookmark)
        advanceUntilIdle()

        assertEquals(bookmark.remoteId, emitted)
        job.cancel()
    }

    @Test
    fun summarize_stopsOfferingItselfOnceTheServerSaysItHasNoModel() = runTest(testDispatcher) {
        withConfiguredServer()
        val bookmark = makeBookmark()
        coEvery { remoteDataSource.summarizeBookmark(any(), any()) } throws
            UnsupportedServerActionException("This Karakeep server has no AI model configured")

        assertTrue(repository.aiCapabilitiesFor("server1").canSummarize)
        assertFailsWith<UnsupportedServerActionException> { repository.summarizeBookmark(bookmark) }

        assertFalse(repository.aiCapabilitiesFor("server1").canSummarize)
    }

    @Test
    fun summarize_keepsOfferingItselfAfterAnOrdinaryFailure() = runTest(testDispatcher) {
        withConfiguredServer()
        val bookmark = makeBookmark()
        coEvery { remoteDataSource.summarizeBookmark(any(), any()) } throws ApiException("HTTP 500")

        assertFailsWith<ApiException> { repository.summarizeBookmark(bookmark) }

        // A blip is not a verdict about the server.
        assertTrue(repository.aiCapabilitiesFor("server1").canSummarize)
    }

    @Test
    fun summarize_failsWhenTheServerIsNoLongerConfigured() = runTest(testDispatcher) {
        every { serverRepository.servers } returns flowOf(emptyList())

        assertFailsWith<Exception> { repository.summarizeBookmark(makeBookmark()) }
        coVerify(exactly = 0) { remoteDataSource.summarizeBookmark(any(), any()) }
    }

    // ── re-tag ─────────────────────────────────────────────────

    @Test
    fun retag_reportsSuccessOnceTheNewTagsLand() = runTest(testDispatcher) {
        withConfiguredServer()
        repository.setBookmarkRepository(bookmarkRepository)
        val bookmark = makeBookmark(tags = "old")
        // Two polls of nothing, then the inference job's output shows up.
        coEvery { bookmarkDao.getBookmarkByRemoteId(42L, "server1") } returnsMany listOf(
            bookmark, bookmark, bookmark.copy(tags = "old,ai-generated")
        )

        val landed = repository.requestAiRetag(bookmark)

        assertTrue(landed)
        coVerify { remoteDataSource.requestAiRetag(testServer, "remote-42") }
        coVerify(atLeast = 1) { bookmarkRepository.syncSingleBookmark(42L, "server1") }
    }

    @Test
    fun retag_givesUpRatherThanPollingForeverWhenTagsNeverChange() = runTest(testDispatcher) {
        withConfiguredServer()
        repository.setBookmarkRepository(bookmarkRepository)
        val bookmark = makeBookmark(tags = "old")
        coEvery { bookmarkDao.getBookmarkByRemoteId(any(), any()) } returns bookmark

        // "Still working on it" is not a failure — the caller says so rather than throwing.
        assertFalse(repository.requestAiRetag(bookmark))
    }

    @Test
    fun retag_surfacesAnUnsupportedServerRatherThanPolling() = runTest(testDispatcher) {
        withConfiguredServer()
        repository.setBookmarkRepository(bookmarkRepository)
        coEvery { remoteDataSource.requestAiRetag(any(), any()) } throws
            UnsupportedServerActionException("needs an admin account")

        assertFailsWith<UnsupportedServerActionException> { repository.requestAiRetag(makeBookmark()) }
        coVerify(exactly = 0) { bookmarkRepository.syncSingleBookmark(any(), any()) }
    }

    // ── capability probe ───────────────────────────────────────

    @Test
    fun refreshCapabilities_recordsAdminStatus() = runTest(testDispatcher) {
        coEvery { remoteDataSource.isServerAdmin(testServer) } returns true

        repository.refreshAiCapabilities(testServer)

        assertTrue(repository.aiCapabilitiesFor("server1").isAdmin)
    }

    @Test
    fun refreshCapabilities_leavesTheEntryAloneWhenTheProbeCannotBeReached() = runTest(testDispatcher) {
        coEvery { remoteDataSource.isServerAdmin(testServer) } returns true
        repository.refreshAiCapabilities(testServer)
        coEvery { remoteDataSource.isServerAdmin(testServer) } throws ApiException("offline")

        repository.refreshAiCapabilities(testServer)

        // A failed probe must not demote a known admin — the actions would vanish mid-session.
        assertTrue(repository.aiCapabilitiesFor("server1").isAdmin)
    }

    @Test
    fun capabilitiesDefaultToSummarizeAllowedAndNotAdmin() {
        val defaults = repository.aiCapabilitiesFor("never-probed")

        assertTrue(defaults.canSummarize)
        assertFalse(defaults.isAdmin)
        assertNull(repository.aiCapabilities.value["never-probed"])
    }

    private fun makeBookmark(
        remoteId: Long = 42L,
        serverId: String = "server1",
        tags: String = ""
    ) = BookmarkEntity(
        localId = 1L,
        remoteId = remoteId,
        originalRemoteId = "remote-$remoteId",
        serverId = serverId,
        title = "Test",
        url = "https://example.com",
        description = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        tags = tags,
        listIds = "",
        isStarred = false,
        isArchived = false,
        isRead = false,
        createdAt = 0L,
        readingTimeMinutes = 0,
        content = null
    )
}
