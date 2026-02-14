package com.karakept.app.data.integration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [com.karakept.app.data.repository.HighlightRepository].
 *
 * Verifies the full highlight lifecycle: local optimistic updates, queuing via
 * [com.karakept.app.data.repository.BookmarkActionsRepository], remote sync, and
 * reconciliation between local DB and server state.
 *
 * Requires a running Karakeep backend (managed by [BaseDockerIntegrationTest]).
 */
class HighlightRepositoryIntegrationTest : BaseDockerIntegrationTest() {

    @Test
    fun testCreateHighlight_optimisticallyInsertsLocally() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://hl-create-local.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)

        val highlightText = "Optimistic local highlight"
        val tempId = highlightRepository.createHighlight(
            server = testServer,
            bookmarkLocalId = bookmark.localId,
            bookmarkRemoteId = bookmark.originalRemoteId,
            text = highlightText,
            startOffset = 0,
            endOffset = highlightText.length,
            color = "yellow"
        )

        // Should be inserted optimistically with a temp ID
        assertTrue(tempId.startsWith("temp_"), "Temp ID should start with 'temp_'")
        val localHighlights = highlightRepository.getHighlightsForBookmark(remoteId, testServer.id).first()
        assertTrue(localHighlights.any { it.text == highlightText }, "Highlight should exist locally with temp ID")
    }

    @Test
    fun testCreateHighlight_syncsToRemoteAfterProcessing() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://hl-create-remote.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)

        val highlightText = "Highlight synced to remote"
        highlightRepository.createHighlight(
            server = testServer,
            bookmarkLocalId = bookmark.localId,
            bookmarkRemoteId = bookmark.originalRemoteId,
            text = highlightText,
            startOffset = 0,
            endOffset = highlightText.length,
            color = "blue"
        )

        // Process pending actions to push highlight to server
        bookmarkActionsRepository.processPendingActions(testServer)

        // Allow time for server processing
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        // Verify highlight on server
        val serverHighlights = remoteDataSource.fetchHighlightsForBookmark(testServer, remoteId)
        assertTrue(serverHighlights.any { it.text == highlightText }, "Highlight should be on server after sync")
    }

    @Test
    fun testCreateHighlight_tempIdReplacedWithRealId() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://hl-tempid.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)

        val highlightText = "Temp ID replacement test"
        val tempId = highlightRepository.createHighlight(
            server = testServer,
            bookmarkLocalId = bookmark.localId,
            bookmarkRemoteId = bookmark.originalRemoteId,
            text = highlightText,
            startOffset = 5,
            endOffset = 5 + highlightText.length
        )

        // Temp ID should exist before sync
        val beforeSync = highlightRepository.getHighlightsForBookmark(remoteId, testServer.id).first()
        assertTrue(beforeSync.any { it.id == tempId }, "Temp ID should exist before sync")

        // Process actions
        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        // After sync, temp ID should be replaced with real server ID
        val afterSync = highlightRepository.getHighlightsForBookmark(remoteId, testServer.id).first()
        assertTrue(afterSync.none { it.id == tempId }, "Temp ID should be gone after sync")
        assertTrue(afterSync.any { it.text == highlightText && !it.id.startsWith("temp_") }, "Real server ID should be present")
    }

    @Test
    fun testUpdateHighlight_updatesLocalDbAndSyncsToRemote() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://hl-update.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)
        val highlightText = "Highlight to update"

        // Create and sync highlight to get a real server ID
        highlightRepository.createHighlight(
            server = testServer,
            bookmarkLocalId = bookmark.localId,
            bookmarkRemoteId = remoteId,
            text = highlightText,
            startOffset = 0,
            endOffset = highlightText.length,
            color = "yellow"
        )
        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        // Get the real server ID from local DB
        val synced = highlightRepository.getHighlightsForBookmark(remoteId, testServer.id).first()
        val realHighlight = synced.firstOrNull { !it.id.startsWith("temp_") }
        assertNotNull(realHighlight, "Should have a synced highlight with real ID")

        // Now update it
        highlightRepository.updateHighlight(
            server = testServer,
            bookmarkLocalId = bookmark.localId,
            highlightRemoteId = realHighlight.id,
            note = "Updated note",
            color = "red"
        )

        // Check local update was optimistic
        val localUpdated = highlightRepository.getHighlightsForBookmark(remoteId, testServer.id).first()
            .find { it.id == realHighlight.id }
        assertNotNull(localUpdated)
        assertEquals("Updated note", localUpdated.note)
        assertEquals("red", localUpdated.color)

        // Process and verify on server
        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        val serverHighlights = remoteDataSource.fetchHighlightsForBookmark(testServer, remoteId)
        val serverHighlight = serverHighlights.find { it.id == realHighlight.id }
        assertNotNull(serverHighlight, "Updated highlight should still be on server")
        assertEquals("Updated note", serverHighlight.note)
    }

    @Test
    fun testDeleteHighlight_removesLocallyAndSyncsToRemote() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://hl-delete.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)
        val highlightText = "Highlight to delete"

        // Create and sync
        highlightRepository.createHighlight(
            server = testServer,
            bookmarkLocalId = bookmark.localId,
            bookmarkRemoteId = remoteId,
            text = highlightText,
            startOffset = 0,
            endOffset = highlightText.length
        )
        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        val synced = highlightRepository.getHighlightsForBookmark(remoteId, testServer.id).first()
        val highlight = synced.firstOrNull { !it.id.startsWith("temp_") }
        assertNotNull(highlight, "Should have synced highlight")

        // Delete it
        highlightRepository.deleteHighlight(testServer, bookmark.localId, highlight.id)

        // Should be optimistically removed from local DB
        val afterDelete = highlightRepository.getHighlightsForBookmark(remoteId, testServer.id).first()
        assertTrue(afterDelete.none { it.id == highlight.id }, "Highlight should be deleted locally")

        // Process and verify on server
        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        val serverHighlights = remoteDataSource.fetchHighlightsForBookmark(testServer, remoteId)
        assertTrue(serverHighlights.none { it.id == highlight.id }, "Highlight should be deleted from server")
    }

    @Test
    fun testSyncHighlightsForBookmark_reconcilesDifferences() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://hl-sync-bookmark.example.com/${System.currentTimeMillis()}")

        // Create highlights directly on server
        val text1 = "Server highlight 1"
        val text2 = "Server highlight 2"
        remoteDataSource.createHighlight(testServer, remoteId, text1, 0, text1.length)
        remoteDataSource.createHighlight(testServer, remoteId, text2, 20, 20 + text2.length)

        // Sync to local
        highlightRepository.syncHighlightsForBookmark(testServer, remoteId)

        val local = highlightRepository.getHighlightsForBookmark(remoteId, testServer.id).first()
        assertTrue(local.any { it.text == text1 }, "Highlight 1 should be synced locally")
        assertTrue(local.any { it.text == text2 }, "Highlight 2 should be synced locally")
    }

    @Test
    fun testSyncHighlights_removesHighlightsDeletedOnServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://hl-sync-remove.example.com/${System.currentTimeMillis()}")

        // Create a highlight on server
        val text = "Highlight that will be deleted on server"
        val created = remoteDataSource.createHighlight(testServer, remoteId, text, 0, text.length)
        val highlightId = created.id!!

        // Sync to populate local DB
        highlightRepository.syncHighlightsForBookmark(testServer, remoteId)
        val before = highlightRepository.getHighlightsForBookmark(remoteId, testServer.id).first()
        assertTrue(before.any { it.id == highlightId }, "Highlight should exist locally after sync")

        // Delete on server directly
        remoteDataSource.deleteHighlight(testServer, highlightId)

        // Re-sync: local DB should reflect deletion
        highlightRepository.syncHighlightsForBookmark(testServer, remoteId)
        val after = highlightRepository.getHighlightsForBookmark(remoteId, testServer.id).first()
        assertTrue(after.none { it.id == highlightId }, "Deleted highlight should be removed from local DB after re-sync")
    }

    @Test
    fun testSyncHighlights_fullSync_coversAllBookmarks() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId1 = seedBookmarkViaTrpc(baseUrl, apiKey, "https://hl-full1.example.com/${System.currentTimeMillis()}")
        val remoteId2 = seedBookmarkViaTrpc(baseUrl, apiKey, "https://hl-full2.example.com/${System.currentTimeMillis()}")

        val text1 = "Full sync highlight for bookmark 1"
        val text2 = "Full sync highlight for bookmark 2"
        val h1 = remoteDataSource.createHighlight(testServer, remoteId1, text1, 0, text1.length)
        val h2 = remoteDataSource.createHighlight(testServer, remoteId2, text2, 0, text2.length)

        // Full sync
        highlightRepository.syncHighlights(testServer)

        val allLocal = remoteDataSource.fetchAllHighlights(testServer)
        assertTrue(allLocal.any { it.id == h1.id }, "Highlight for bookmark 1 should be in all highlights")
        assertTrue(allLocal.any { it.id == h2.id }, "Highlight for bookmark 2 should be in all highlights")
    }
}
