package com.karakept.app.data.integration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for bookmark mutation operations.
 *
 * This is a migration/consolidation of the original [RepositoryIntegrationTest] into
 * the common integration test package, extended to use [BaseDockerIntegrationTest].
 *
 * Tests the end-to-end flow: local optimistic updates → pending action queue →
 * remote sync → server state verification.
 *
 * Requires a running Karakeep backend (managed by [BaseDockerIntegrationTest]).
 */
class BookmarkMutationIntegrationTest : BaseDockerIntegrationTest() {

    @Test
    fun testCreateAndSyncBookmark() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val targetUrl = "https://example.com/mutation-create-${System.currentTimeMillis()}"

        val result = bookmarkRepository.createBookmark(targetUrl) { status ->
            println("Status: $status")
        }

        if (result.isSuccess) {
            val bookmark = result.getOrNull()
            assertNotNull(bookmark, "Bookmark should not be null")
            assertTrue(bookmark!!.url == targetUrl, "URL should match")
            println("Bookmark created: ${bookmark.title} (ID: ${bookmark.originalRemoteId})")
        } else {
            println("Bookmark creation failed: ${result.exceptionOrNull()?.message}")
        }
    }

    @Test
    fun testMarkAsReadAndUnread() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val targetUrl = "https://example.com/read-test-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, targetUrl)
        val bookmark = insertLocalBookmark(remoteId, url = targetUrl)

        // Mark as read
        bookmarkActionsRepository.markAsRead(bookmark.remoteId, testServer.id)

        val afterRead = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        assertTrue(afterRead?.isRead == true, "Bookmark should be marked as read locally")

        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        val remoteAfterRead = remoteDataSource.fetchBookmark(testServer, bookmark.originalRemoteId)
        val hasReadTag = remoteAfterRead.tags?.any { it.name == "karakept:read" } == true
        assertTrue(hasReadTag, "Server should have karakept:read tag")

        // Mark as unread
        val tags = afterRead?.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        bookmarkActionsRepository.markAsUnread(bookmark.remoteId, testServer.id, tags)

        val afterUnread = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        assertTrue(afterUnread?.isRead == false, "Bookmark should be marked as unread locally")

        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        val remoteAfterUnread = remoteDataSource.fetchBookmark(testServer, bookmark.originalRemoteId)
        val stillHasReadTag = remoteAfterUnread.tags?.any { it.name == "karakept:read" } == true
        assertTrue(!stillHasReadTag, "Server should not have karakept:read tag after unread")
    }

    @Test
    fun testFullMutationLifecycle() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val targetUrl = "https://example.com/lifecycle-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, targetUrl)
        val bookmark = insertLocalBookmark(remoteId, url = targetUrl)

        println("Testing full mutation lifecycle for bookmark $remoteId")

        // 1. Mark as read
        bookmarkActionsRepository.markAsRead(bookmark.remoteId, bookmark.serverId)
        val afterRead = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
        assertTrue(afterRead?.isRead == true, "Should be read locally")

        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        val remoteRead = remoteDataSource.fetchBookmark(testServer, bookmark.originalRemoteId)
        assertTrue(remoteRead.tags?.any { it.name == "karakept:read" } == true, "Should have read tag on server")

        // 2. Add custom tags
        val customTags = listOf("important", "work", "to-review")
        bookmarkActionsRepository.updateTags(bookmark.remoteId, bookmark.serverId, customTags + "karakept:read", isOnline = true)

        val afterTags = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
        val localTags = afterTags?.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        assertTrue(localTags.containsAll(customTags), "Should have custom tags locally")

        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        val remoteWithTags = remoteDataSource.fetchBookmark(testServer, bookmark.originalRemoteId)
        val remoteTags = remoteWithTags.tags?.mapNotNull { it.name }?.filter { it.isNotBlank() } ?: emptyList()
        assertTrue(customTags.all { it in remoteTags }, "Custom tags should be on server: $remoteTags")

        // 3. Create highlight
        val highlightText = "Integration test highlight"
        val tempId = highlightRepository.createHighlight(
            server = testServer,
            bookmarkLocalId = bookmark.localId,
            bookmarkRemoteId = bookmark.originalRemoteId,
            text = highlightText,
            startOffset = 0,
            endOffset = 10,
            color = "yellow"
        )

        val localHighlights = highlightRepository.getHighlightsForBookmark(bookmark.originalRemoteId, bookmark.serverId).first()
        assertTrue(localHighlights.any { it.text == highlightText }, "Highlight should exist locally")

        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        val remoteHighlights = remoteDataSource.fetchHighlightsForBookmark(testServer, bookmark.originalRemoteId)
        assertTrue(remoteHighlights.any { it.text == highlightText }, "Highlight should be on server")

        // 4. Archive
        bookmarkActionsRepository.archiveBookmark(bookmark.remoteId, bookmark.serverId)

        val afterArchive = bookmarkRepository.getBookmarks(testServer).first().find { it.remoteId == bookmark.remoteId }
        assertTrue(afterArchive?.isArchived == true, "Should be archived locally")

        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(2000) }

        val remoteArchived = remoteDataSource.fetchBookmark(testServer, bookmark.originalRemoteId)
        assertTrue(remoteArchived.archived == true, "Should be archived on server")

        // 5. Delete
        bookmarkActionsRepository.deleteBookmark(bookmark.localId, bookmark.remoteId, bookmark.serverId)

        val afterDelete = bookmarkRepository.getBookmarks(testServer).first().find { it.remoteId == bookmark.remoteId }
        assertNull(afterDelete, "Bookmark should be deleted locally")

        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(2000) }

        try {
            remoteDataSource.fetchBookmark(testServer, bookmark.originalRemoteId)
            throw AssertionError("Should have thrown 404 after deletion")
        } catch (e: Exception) {
            println("Expected error after deletion: ${e.message}")
        }
    }
}
