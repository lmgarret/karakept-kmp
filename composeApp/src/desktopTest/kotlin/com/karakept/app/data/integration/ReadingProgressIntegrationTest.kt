package com.karakept.app.data.integration

import com.karakept.app.data.repository.processPendingActions
import com.karakept.app.data.repository.ReadingProgressPullResult
import com.karakept.app.data.repository.pullReadingProgressFromServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the reading progress synchronization feature.
 *
 * Tests the full round-trip:
 *   local reading progress → pending action queue → server tRPC call → server storage
 *   server reading progress → local DB restore (cross-device sync)
 *
 * Requires a running Karakeep backend (managed by [BaseDockerIntegrationTest]).
 *
 * The karakeep server must be at or after the commit that added the
 * `bookmarks.updateReadingProgress` and `bookmarks.getReadingProgress` tRPC endpoints
 * (introduced in v0.31.0 / PR #2302).
 */
class ReadingProgressIntegrationTest : BaseDockerIntegrationTest() {

    // -----------------------------------------------------------------------
    // Push: local → server
    // -----------------------------------------------------------------------

    @Test
    fun testPushReadingProgress_syncedToServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://example.com/reading-progress-push-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)
        val bookmark = insertLocalBookmark(remoteId, url = url)

        // Queue a 42% reading progress update
        bookmarkActionsRepository.queueReadingProgressUpdate(
            bookmarkRemoteId = bookmark.remoteId,
            serverId = testServer.id,
            progressPercent = 42
        )

        // Process pending actions (pushes to server)
        bookmarkActionsRepository.processPendingActions(testServer)

        // Verify reading progress on server via tRPC
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }
        val serverPercent = remoteDataSource.getReadingProgressBatch(testServer, listOf(remoteId))[remoteId]
        assertNotNull(serverPercent, "Server should have reading progress after sync")
        assertEquals(42, serverPercent, "Server reading progress should match pushed value")
    }

    @Test
    fun testPushReadingProgress_deduplicatesStaleUpdates() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://example.com/reading-dedup-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)
        val bookmark = insertLocalBookmark(remoteId, url = url)

        // Queue multiple updates – only the last should be pushed
        bookmarkActionsRepository.queueReadingProgressUpdate(
            bookmark.remoteId, testServer.id, progressPercent = 10
        )
        bookmarkActionsRepository.queueReadingProgressUpdate(
            bookmark.remoteId, testServer.id, progressPercent = 30
        )
        bookmarkActionsRepository.queueReadingProgressUpdate(
            bookmark.remoteId, testServer.id, progressPercent = 55
        )

        // Only one pending action should remain (latest wins)
        val pending = db.pendingActionDao().getPendingActionsList(testServer.id)
        val progressActions = pending.filter {
            it.bookmarkRemoteId == bookmark.remoteId &&
                it.actionType == "update_reading_progress"
        }
        assertEquals(1, progressActions.size, "Should have exactly one pending reading progress action")

        bookmarkActionsRepository.processPendingActions(testServer)

        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }
        val serverPercent = remoteDataSource.getReadingProgressBatch(testServer, listOf(remoteId))[remoteId]
        assertEquals(55, serverPercent, "Server should have the latest (55%) progress")
    }

    @Test
    fun testPushReadingProgress_afterMarkAsRead() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://example.com/mark-as-read-sync-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)
        val bookmark = insertLocalBookmark(remoteId, url = url)

        // Mark as read (should queue 100% progress update)
        bookmarkActionsRepository.markAsRead(bookmark.remoteId, testServer.id)

        // Wait for auto-sync to complete
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        val serverPercent = remoteDataSource.getReadingProgressBatch(testServer, listOf(remoteId))[remoteId]
        assertNotNull(serverPercent, "Server should have reading progress after markAsRead")
        assertEquals(100, serverPercent, "Server should show 100% after marking as read")
    }

    @Test
    fun testPushReadingProgress_afterMarkAsUnreadWithReset() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://example.com/mark-unread-reset-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)
        val bookmark = insertLocalBookmark(remoteId, url = url)

        // First mark as read (100%)
        bookmarkActionsRepository.markAsRead(bookmark.remoteId, testServer.id)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        val afterRead = remoteDataSource.getReadingProgressBatch(testServer, listOf(remoteId))[remoteId]
        assertEquals(100, afterRead, "Server should show 100% after markAsRead")

        // Then mark as unread with progress reset (0%)
        bookmarkActionsRepository.markAsUnread(bookmark.remoteId, testServer.id, resetProgress = true)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        val afterUnread = remoteDataSource.getReadingProgressBatch(testServer, listOf(remoteId))[remoteId]
        assertEquals(0, afterUnread, "Server should show 0% after markAsUnread with reset")
    }

    // -----------------------------------------------------------------------
    // Pull: server → local (cross-device restore)
    // -----------------------------------------------------------------------

    @Test
    fun testPullReadingProgress_restoresFromServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://example.com/reading-progress-pull-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)

        // Seed server-side reading progress directly via tRPC
        seedReadingProgressViaTrpc(baseUrl, apiKey, remoteId, progressPercent = 68)

        // Insert local bookmark with zero progress (simulating first open on new device)
        val bookmark = insertLocalBookmark(remoteId, url = url)
        assertEquals(0f, bookmark.readingProgress, "Local progress should start at 0")

        // Pull reading progress from server
        val updated = bookmarkActionsRepository.pullReadingProgressFromServer(
            bookmark.remoteId, testServer.id
        )

        assertEquals(
            ReadingProgressPullResult.APPLIED, updated,
            "pullReadingProgressFromServer should apply progress when the server has some"
        )

        val afterPull = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        assertNotNull(afterPull)
        assertEquals(0.68f, afterPull.readingProgress, 0.01f, "Local progress should be restored from server (68%)")
    }

    @Test
    fun testPullReadingProgress_doesNotOverwriteHigherLocalProgress() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://example.com/reading-no-overwrite-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)

        // Seed server with 30%
        seedReadingProgressViaTrpc(baseUrl, apiKey, remoteId, progressPercent = 30)

        // Insert local bookmark with 70% (user has read more locally)
        val bookmark = insertLocalBookmark(remoteId, url = url, readingProgress = 0.70f)

        // Pull should NOT overwrite since local progress is higher
        val updated = bookmarkActionsRepository.pullReadingProgressFromServer(
            bookmark.remoteId, testServer.id
        )

        assertEquals(
            ReadingProgressPullResult.SKIPPED, updated,
            "pullReadingProgressFromServer should not overwrite higher local progress"
        )

        val afterPull = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        assertNotNull(afterPull)
        assertEquals(0.70f, afterPull.readingProgress, 0.01f, "Local progress should remain 70%")
    }

    @Test
    fun testPullReadingProgress_returnsNullWhenNoServerProgress() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://example.com/reading-no-server-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)
        val bookmark = insertLocalBookmark(remoteId, url = url)

        // No reading progress seeded on server
        val serverPercent = remoteDataSource.getReadingProgressBatch(testServer, listOf(remoteId))[remoteId]
        // Server returns null for readingProgressPercent when no progress is stored
        // getReadingProgress returns null in that case
        assertTrue(serverPercent == null || serverPercent == 0,
            "Server should return null or 0 when no progress is stored")

        val updated = bookmarkActionsRepository.pullReadingProgressFromServer(
            bookmark.remoteId, testServer.id
        )
        assertEquals(
            ReadingProgressPullResult.SKIPPED, updated,
            "Should not update local progress when server has none"
        )
    }

    // -----------------------------------------------------------------------
    // End-to-end: push then pull (cross-device simulation)
    // -----------------------------------------------------------------------

    @Test
    fun testCrossDeviceSync_pushThenPullOnNewDevice() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://example.com/cross-device-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)

        // --- Device A: user reads to 75% ---
        val bookmarkA = insertLocalBookmark(remoteId, url = url)
        bookmarkActionsRepository.queueReadingProgressUpdate(
            bookmarkRemoteId = bookmarkA.remoteId,
            serverId = testServer.id,
            progressPercent = 75
        )
        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        // Verify device A pushed 75% to server
        val serverPercent = remoteDataSource.getReadingProgressBatch(testServer, listOf(remoteId))[remoteId]
        assertEquals(75, serverPercent, "Server should have 75% after device A push")

        // --- Device B: fresh DB entry, pulls progress from server ---
        // Simulate device B by deleting and re-inserting the bookmark with 0 progress
        db.bookmarkDao().deleteBookmark(bookmarkA)
        val bookmarkB = insertLocalBookmark(remoteId, url = url) // fresh, 0% progress

        val restored = bookmarkActionsRepository.pullReadingProgressFromServer(
            bookmarkB.remoteId, testServer.id
        )
        assertEquals(
            ReadingProgressPullResult.APPLIED, restored,
            "Device B should restore progress from server"
        )

        val afterRestore = db.bookmarkDao().getBookmarkByRemoteId(bookmarkB.remoteId, testServer.id)
        assertEquals(0.75f, afterRestore!!.readingProgress, 0.01f,
            "Device B should show 75% after cross-device restore")
    }

    // -----------------------------------------------------------------------
    // Pull with non-zero local progress
    // -----------------------------------------------------------------------

    @Test
    fun testPullReadingProgress_updatesWhenServerIsHigherThanNonZeroLocal() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://example.com/reading-nonzero-pull-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)

        // Seed server with 70%
        seedReadingProgressViaTrpc(baseUrl, apiKey, remoteId, progressPercent = 70)

        // Insert local bookmark with 30% (user read less on this device)
        val bookmark = insertLocalBookmark(remoteId, url = url, readingProgress = 0.30f)

        // Pull should update since server (70%) > local (30%)
        val updated = bookmarkActionsRepository.pullReadingProgressFromServer(
            bookmark.remoteId, testServer.id
        )

        assertEquals(
            ReadingProgressPullResult.APPLIED, updated,
            "pullReadingProgressFromServer should update when server progress is higher"
        )

        val afterPull = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        assertNotNull(afterPull)
        assertEquals(0.70f, afterPull.readingProgress, 0.01f, "Local progress should be updated to 70%")
    }

    // -----------------------------------------------------------------------
    // Auto-sync after queue
    // -----------------------------------------------------------------------

    @Test
    fun testQueueReadingProgress_triggersAutoSync() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://example.com/reading-autosync-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)
        val bookmark = insertLocalBookmark(remoteId, url = url)

        // Queue a reading progress update — triggerAutoSync should push it automatically
        bookmarkActionsRepository.queueReadingProgressUpdate(
            bookmarkRemoteId = bookmark.remoteId,
            serverId = testServer.id,
            progressPercent = 63
        )

        // Wait for auto-sync to complete (triggerAutoSync is fire-and-forget)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        // Verify the progress reached the server without a manual processPendingActions call
        val serverPercent = remoteDataSource.getReadingProgressBatch(testServer, listOf(remoteId))[remoteId]
        assertNotNull(serverPercent, "Server should have reading progress after auto-sync")
        assertEquals(63, serverPercent, "Server reading progress should match queued value after auto-sync")
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    companion object {
        /**
         * Seed reading progress for a bookmark directly via tRPC, bypassing the local app.
         * Used to simulate progress set by another client (cross-device scenario).
         */
        fun seedReadingProgressViaTrpc(
            apiBaseUrl: String,
            token: String,
            bookmarkId: String,
            progressPercent: Int
        ) {
            val trpcUrl = "$apiBaseUrl/api/trpc/bookmarks.updateReadingProgress?batch=1"
            val body = """{"0":{"json":{"bookmarkId":"$bookmarkId","readingProgressOffset":0,"readingProgressAnchor":null,"readingProgressPercent":$progressPercent}}}"""
            println("Seeding reading progress via tRPC: bookmarkId=$bookmarkId, percent=$progressPercent")
            val response = postJson(trpcUrl, body, token)
            if (!response.contains("\"result\"") && !response.contains("null")) {
                println("Warning: reading progress seed response: $response")
            }
        }
    }

    /**
     * Override of [insertLocalBookmark] that also supports setting an initial reading progress.
     */
    private suspend fun insertLocalBookmark(
        remoteId: String,
        url: String = "https://example.com/$remoteId",
        title: String = "Test Bookmark",
        readingProgress: Float = 0f
    ): com.karakept.app.data.local.entity.BookmarkEntity {
        val entity = com.karakept.app.data.local.entity.BookmarkEntity(
            localId = 0L,
            remoteId = remoteId,
            serverId = testServer.id,
            url = url,
            title = title,
            content = null,
            imageUrl = null,
            bannerImageAssetId = null,
            screenshotAssetId = null,
            description = null,
            createdAt = System.currentTimeMillis(),
            isArchived = false,
            isStarred = false,
            isRead = false,
            tags = "",
            listIds = "",
            readingProgress = readingProgress
        )
        db.bookmarkDao().insertBookmark(entity)
        return db.bookmarkDao().getBookmarkByRemoteId(remoteId, testServer.id)
            ?: throw IllegalStateException("Failed to insert bookmark with remoteId=$remoteId")
    }
}
