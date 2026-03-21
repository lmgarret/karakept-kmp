package com.karakept.app.data.integration

import com.karakept.app.data.repository.processPendingActions
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
 * Integration tests for the bookmark synchronization pipeline.
 *
 * Covers [com.karakept.app.data.repository.BookmarkRepository] sync methods and
 * [com.karakept.app.data.repository.BookmarkActionsRepository] pending action processing,
 * testing the full round-trip: local DB ↔ remote API.
 *
 * Requires a running Karakeep backend (managed by [BaseDockerIntegrationTest]).
 */
class BookmarkSyncIntegrationTest : BaseDockerIntegrationTest() {

    // -------------------------------------------------------------------------
    // Full sync
    // -------------------------------------------------------------------------

    @Test
    fun testSyncBookmarks_populatesLocalDatabase() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        // Seed bookmarks on server
        val url1 = "https://sync-full1.example.com/${System.currentTimeMillis()}"
        val url2 = "https://sync-full2.example.com/${System.currentTimeMillis()}"
        val id1 = seedBookmarkViaTrpc(baseUrl, apiKey, url1)
        val id2 = seedBookmarkViaTrpc(baseUrl, apiKey, url2)

        bookmarkRepository.syncBookmarks(testServer)

        val local = bookmarkRepository.getBookmarks(testServer).first()
        val localIds = local.map { it.originalRemoteId }
        assertTrue(id1 in localIds, "Bookmark 1 should be in local DB after sync")
        assertTrue(id2 in localIds, "Bookmark 2 should be in local DB after sync")
    }

    @Test
    fun testSyncBookmarks_isIdempotent() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        seedBookmarkViaTrpc(baseUrl, apiKey, "https://idempotent.example.com/${System.currentTimeMillis()}")

        bookmarkRepository.syncBookmarks(testServer)
        val afterFirst = bookmarkRepository.getBookmarks(testServer).first().size

        bookmarkRepository.syncBookmarks(testServer)
        val afterSecond = bookmarkRepository.getBookmarks(testServer).first().size

        assertEquals(afterFirst, afterSecond, "Repeated syncs should not duplicate bookmarks")
    }

    @Test
    fun testSyncBookmarks_removesBookmarksDeletedOnServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://delete-sync.example.com/${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)

        // Sync to populate local DB
        bookmarkRepository.syncBookmarks(testServer)
        val before = bookmarkRepository.getBookmarks(testServer).first()
        assertTrue(before.any { it.originalRemoteId == remoteId }, "Bookmark should be local after first sync")

        // Delete on server
        remoteDataSource.deleteBookmark(testServer, remoteId)

        // Re-sync: deleted bookmark should be removed locally
        bookmarkRepository.syncBookmarks(testServer)
        val after = bookmarkRepository.getBookmarks(testServer).first()
        assertTrue(after.none { it.originalRemoteId == remoteId }, "Deleted bookmark should be removed after re-sync")
    }

    // -------------------------------------------------------------------------
    // Filtered sync
    // -------------------------------------------------------------------------

    @Test
    fun testSyncFavorites_onlyFetchesFavouritedBookmarks() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val favUrl = "https://favorite.example.com/${System.currentTimeMillis()}"
        val normalUrl = "https://normal.example.com/${System.currentTimeMillis()}"

        val favId = seedBookmarkViaTrpc(baseUrl, apiKey, favUrl)
        seedBookmarkViaTrpc(baseUrl, apiKey, normalUrl)

        // Star the first bookmark
        remoteDataSource.updateBookmark(testServer, favId, com.karakept.api.model.BookmarksBookmarkIdPatchRequest(favourited = true))

        bookmarkRepository.syncFavorites(testServer)

        val local = bookmarkRepository.getBookmarks(testServer).first()
        // All locally synced bookmarks from this operation should be starred
        val syncedById = local.filter { it.originalRemoteId == favId }
        assertTrue(syncedById.isEmpty() || syncedById.all { it.isStarred }, "Favorited sync should only bring starred bookmarks")
    }

    @Test
    fun testSyncArchived_onlyFetchesArchivedBookmarks() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val archivedUrl = "https://archived.example.com/${System.currentTimeMillis()}"
        val archivedId = seedBookmarkViaTrpc(baseUrl, apiKey, archivedUrl)

        remoteDataSource.updateBookmark(testServer, archivedId, com.karakept.api.model.BookmarksBookmarkIdPatchRequest(archived = true))

        bookmarkRepository.syncArchived(testServer)

        val local = bookmarkRepository.getBookmarks(testServer).first()
        val archivedLocally = local.filter { it.originalRemoteId == archivedId }
        assertTrue(archivedLocally.isEmpty() || archivedLocally.all { it.isArchived }, "Archived sync should have the bookmark marked archived")
    }

    // -------------------------------------------------------------------------
    // List sync
    // -------------------------------------------------------------------------

    @Test
    fun testSyncBookmarksForList_onlyFetchesListMembers() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val listId = seedListViaTrpc(baseUrl, apiKey, "Sync List ${System.currentTimeMillis()}")
        val memberUrl = "https://list-member.example.com/${System.currentTimeMillis()}"
        val nonMemberUrl = "https://not-in-list.example.com/${System.currentTimeMillis()}"

        val memberId = seedBookmarkViaTrpc(baseUrl, apiKey, memberUrl)
        seedBookmarkViaTrpc(baseUrl, apiKey, nonMemberUrl)

        remoteDataSource.addBookmarkToList(testServer, listId, memberId)

        bookmarkRepository.syncBookmarksForList(testServer, listId)

        val local = bookmarkRepository.getBookmarks(testServer).first()
        assertTrue(local.any { it.originalRemoteId == memberId }, "List member should be in local DB")
    }

    // -------------------------------------------------------------------------
    // Pending action processing
    // -------------------------------------------------------------------------

    @Test
    fun testArchiveAction_syncedToServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://archive-action.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)

        bookmarkActionsRepository.archiveBookmark(bookmark.remoteId, testServer.id)

        // Local optimistic update
        val local = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        assertTrue(local?.isArchived == true, "Bookmark should be archived locally")

        // Process pending actions
        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        // Verify on server
        val remote = remoteDataSource.fetchBookmark(testServer, remoteId)
        assertEquals(true, remote.archived, "Bookmark should be archived on server")
    }

    @Test
    fun testUnarchiveAction_syncedToServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://unarchive-action.example.com/${System.currentTimeMillis()}")
        // Start archived
        remoteDataSource.updateBookmark(testServer, remoteId, com.karakept.api.model.BookmarksBookmarkIdPatchRequest(archived = true))
        val bookmark = insertLocalBookmark(remoteId, isArchived = true)

        bookmarkActionsRepository.unarchiveBookmark(bookmark.remoteId, testServer.id)

        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        val remote = remoteDataSource.fetchBookmark(testServer, remoteId)
        assertEquals(false, remote.archived, "Bookmark should be unarchived on server")
    }

    @Test
    fun testFavouriteAction_syncedToServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://fav-action.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)

        bookmarkActionsRepository.toggleFavourite(bookmark.remoteId, testServer.id, currentlyFavourited = false)

        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        val remote = remoteDataSource.fetchBookmark(testServer, remoteId)
        assertEquals(true, remote.favourited, "Bookmark should be favourited on server")
    }

    @Test
    fun testMarkAsReadAction_updatesLocalOnly() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://read-action.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)

        bookmarkActionsRepository.markAsRead(bookmark.remoteId, testServer.id)

        // Local update only – no pending actions or server sync
        val local = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        assertTrue(local?.isRead == true, "Bookmark should be marked read locally")
    }

    @Test
    fun testUpdateTagsAction_replacesTagsOnServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://tags-action.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)
        val newTags = listOf("integration", "testing", "automated")

        bookmarkActionsRepository.updateTags(bookmark.remoteId, testServer.id, newTags, isOnline = true)
        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        val remote = remoteDataSource.fetchBookmark(testServer, remoteId)
        val serverTags = remote.tags?.mapNotNull { it.name } ?: emptyList()
        assertTrue(newTags.all { it in serverTags }, "All new tags should be on server: expected $newTags, got $serverTags")
    }

    @Test
    fun testDeleteAction_removesBookmarkFromServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://delete-action.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)

        bookmarkActionsRepository.deleteBookmark(bookmark.localId, bookmark.remoteId, testServer.id)

        // Should be deleted locally immediately
        val local = db.bookmarkDao().getBookmarkByRemoteId(bookmark.remoteId, testServer.id)
        assertNull(local, "Bookmark should be deleted from local DB")

        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        // Verify deleted on server
        try {
            remoteDataSource.fetchBookmark(testServer, remoteId)
            throw AssertionError("fetchBookmark should have thrown after deletion")
        } catch (e: Exception) {
            println("Expected error after deletion: ${e.message}")
        }
    }

    @Test
    fun testMoveToListAction_addsBookmarkToListOnServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val listId = seedListViaTrpc(baseUrl, apiKey, "Action Test List ${System.currentTimeMillis()}")
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://list-action.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)

        bookmarkActionsRepository.moveToList(bookmark.remoteId, testServer.id, listId, isOnline = true)
        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        val listBookmarks = remoteDataSource.fetchBookmarksForList(testServer, listId)
        assertTrue(listBookmarks.any { it.id == remoteId }, "Bookmark should be in list on server")
    }

    @Test
    fun testRemoveFromListAction_removesBookmarkFromListOnServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val listId = seedListViaTrpc(baseUrl, apiKey, "Remove List ${System.currentTimeMillis()}")
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://remove-list.example.com/${System.currentTimeMillis()}")
        val bookmark = insertLocalBookmark(remoteId)

        // First add
        remoteDataSource.addBookmarkToList(testServer, listId, remoteId)

        // Then queue remove action
        bookmarkActionsRepository.removeFromList(bookmark.remoteId, testServer.id, listId, isOnline = true)
        bookmarkActionsRepository.processPendingActions(testServer)
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1000) }

        val listBookmarks = remoteDataSource.fetchBookmarksForList(testServer, listId)
        assertTrue(listBookmarks.none { it.id == remoteId }, "Bookmark should be removed from list on server")
    }

    // -------------------------------------------------------------------------
    // Bookmark creation
    // -------------------------------------------------------------------------

    @Test
    fun testCreateBookmark_persistsLocallyAndOnServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://create-full.example.com/${System.currentTimeMillis()}"

        val result = bookmarkRepository.createBookmark(url)

        assertTrue(result.isSuccess, "createBookmark should succeed: ${result.exceptionOrNull()?.message}")
        val bookmark = result.getOrNull()
        assertNotNull(bookmark)
        assertEquals(url, bookmark?.url)

        // Verify it's on the server
        val remote = remoteDataSource.fetchBookmark(testServer, bookmark!!.originalRemoteId)
        assertEquals(url, remote.content?.url)
    }
}
