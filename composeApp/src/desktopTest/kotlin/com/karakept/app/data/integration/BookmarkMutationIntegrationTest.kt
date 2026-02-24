package com.karakept.app.data.integration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // ──────────────────────────────────────────────────────────
    // List membership mutations
    // ──────────────────────────────────────────────────────────

    @Test
    fun testMoveToList_updatesLocalDbImmediately() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val bookmarkUrl = "https://example.com/move-to-list-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, bookmarkUrl)
        val bookmark = insertLocalBookmark(remoteId, url = bookmarkUrl)

        val listName = "Target List ${System.currentTimeMillis()}"
        val listId = seedListViaTrpc(baseUrl, apiKey, listName)
        listRepository.refreshLists(testServer)

        // Before: bookmark has no list membership
        val before = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        assertTrue(before?.listIds.isNullOrBlank(), "Bookmark should not be in any list before moveToList")

        bookmarkActionsRepository.moveToList(bookmark.remoteId, testServer.id, listId, isOnline = true)

        // After: local DB should be updated immediately (optimistic)
        val afterLocal = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        val localListIds = afterLocal?.listIds?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        assertTrue(localListIds.contains(listId), "Local DB should contain listId immediately after moveToList")

        // Wait for auto-sync to push to server
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        // Verify server state
        val serverBookmarks = remoteDataSource.fetchBookmarksForList(testServer, listId, includeContent = false)
        assertTrue(
            serverBookmarks.any { it.id == remoteId },
            "Bookmark should be in list on server after sync"
        )
    }

    @Test
    fun testRemoveFromList_updatesLocalDbImmediately() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val bookmarkUrl = "https://example.com/remove-from-list-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, bookmarkUrl)

        val listName = "Remove Test List ${System.currentTimeMillis()}"
        val listId = seedListViaTrpc(baseUrl, apiKey, listName)
        listRepository.refreshLists(testServer)

        // Seed the bookmark into the list on the server
        val addUrl = "$baseUrl/api/trpc/lists.addBookmark?batch=1"
        val addBody = """{"0": {"json": {"listId": "$listId", "bookmarkId": "$remoteId"}}}"""
        postJson(addUrl, addBody, apiKey)

        // Insert locally with the list membership already set
        val bookmark = insertLocalBookmark(remoteId, url = bookmarkUrl, listIds = listId)

        // Verify starting state
        val before = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        val beforeListIds = before?.listIds?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        assertTrue(beforeListIds.contains(listId), "Bookmark should be in list before removeFromList")

        bookmarkActionsRepository.removeFromList(bookmark.remoteId, testServer.id, listId, isOnline = true)

        // After: local DB should be updated immediately (optimistic)
        val afterLocal = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        val localListIds = afterLocal?.listIds?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        assertFalse(localListIds.contains(listId), "Local DB should not contain listId immediately after removeFromList")

        // Wait for auto-sync to push to server
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        // Verify server state
        val serverBookmarks = remoteDataSource.fetchBookmarksForList(testServer, listId, includeContent = false)
        assertFalse(
            serverBookmarks.any { it.id == remoteId },
            "Bookmark should not be in list on server after sync"
        )
    }

    @Test
    fun testUpdateTags_updatesLocalDbImmediately() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val bookmarkUrl = "https://example.com/update-tags-${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, bookmarkUrl)
        val bookmark = insertLocalBookmark(remoteId, url = bookmarkUrl, tags = "old-tag")

        val newTags = listOf("new-tag-1", "new-tag-2")
        bookmarkActionsRepository.updateTags(bookmark.remoteId, testServer.id, newTags, isOnline = true)

        // Local DB should be updated immediately
        val afterLocal = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        val localTags = afterLocal?.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        assertTrue(newTags.all { it in localTags }, "New tags should be in local DB immediately")
        assertFalse(localTags.contains("old-tag"), "Old tag should be removed from local DB")

        // Wait for auto-sync
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(3000) }

        // Verify server has new tags
        val remote = remoteDataSource.fetchBookmark(testServer, remoteId)
        val remoteTags = remote.tags?.mapNotNull { it.name }?.filter { it.isNotBlank() } ?: emptyList()
        assertTrue(newTags.all { it in remoteTags }, "New tags should be on server after sync: $remoteTags")
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
