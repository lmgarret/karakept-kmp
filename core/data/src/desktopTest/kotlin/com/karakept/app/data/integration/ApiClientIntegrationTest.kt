package com.karakept.app.data.integration

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest

/**
 * Integration tests for [com.karakept.app.data.remote.RemoteDataSource].
 *
 * Verifies that every API method on RemoteDataSource correctly communicates with the
 * Karakeep backend, handling serialization/deserialization and authentication.
 *
 * Requires a running Karakeep backend (managed by [BaseDockerIntegrationTest]).
 */
class ApiClientIntegrationTest : BaseDockerIntegrationTest() {

    // -------------------------------------------------------------------------
    // Connection
    // -------------------------------------------------------------------------

    @Test
    fun testConnection_withValidCredentials_returnsTrue() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val result = remoteDataSource.testConnection(baseUrl, apiKey)

        assertTrue(result, "testConnection should return true for valid credentials")
    }

    @Test
    fun testConnection_withInvalidApiKey_returnsFalse() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val result = remoteDataSource.testConnection(baseUrl, "invalid-api-key-000")

        assertTrue(!result, "testConnection should return false for an invalid API key")
    }

    // -------------------------------------------------------------------------
    // Bookmarks – fetch
    // -------------------------------------------------------------------------

    @Test
    fun testFetchBookmarks_returnsPagedResult() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        // Seed at least one bookmark so the list is non-empty
        seedBookmarkViaTrpc(baseUrl, apiKey, "https://fetch-test.example.com/${System.currentTimeMillis()}")

        val result = remoteDataSource.fetchBookmarks(testServer, limit = 10)

        assertNotNull(result.bookmarks, "bookmarks list should not be null")
        assertTrue(result.bookmarks!!.isNotEmpty(), "should have at least one bookmark")
    }

    @Test
    fun testFetchBookmarks_withCursor_returnsDifferentPage() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        // Seed enough bookmarks for pagination
        repeat(3) { i ->
            seedBookmarkViaTrpc(baseUrl, apiKey, "https://page-test-$i.example.com/${System.currentTimeMillis()}")
        }

        val firstPage = remoteDataSource.fetchBookmarks(testServer, limit = 2)
        assertNotNull(firstPage.bookmarks)
        assertTrue(firstPage.bookmarks!!.isNotEmpty())

        // If there is a next cursor, fetch the next page
        val cursor = firstPage.nextCursor
        if (cursor != null) {
            val secondPage = remoteDataSource.fetchBookmarks(testServer, cursor = cursor, limit = 2)
            assertNotNull(secondPage.bookmarks)
            // Pages should have different bookmark IDs
            val firstIds = firstPage.bookmarks!!.mapNotNull { it.id }.toSet()
            val secondIds = secondPage.bookmarks!!.mapNotNull { it.id }.toSet()
            assertTrue(firstIds.intersect(secondIds).isEmpty(), "Pages should not contain duplicate bookmarks")
        }
    }

    @Test
    fun testFetchBookmarks_filterByArchived() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val result = remoteDataSource.fetchBookmarks(testServer, archived = true, limit = 50)

        assertNotNull(result.bookmarks)
        result.bookmarks!!.forEach { bookmark ->
            assertTrue(bookmark.archived == true, "All returned bookmarks should be archived")
        }
    }

    @Test
    fun testFetchBookmarks_filterByFavourited() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val result = remoteDataSource.fetchBookmarks(testServer, favourited = true, limit = 50)

        assertNotNull(result.bookmarks)
        result.bookmarks!!.forEach { bookmark ->
            assertTrue(bookmark.favourited == true, "All returned bookmarks should be favourited")
        }
    }

    @Test
    fun testFetchBookmark_byId_returnsBookmark() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://fetch-single.example.com/${System.currentTimeMillis()}"
        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, url)

        val bookmark = remoteDataSource.fetchBookmark(testServer, remoteId)

        assertNotNull(bookmark)
        assertEquals(remoteId, bookmark.id)
        assertEquals(url, bookmark.content?.url)
    }

    // -------------------------------------------------------------------------
    // Bookmarks – create & update
    // -------------------------------------------------------------------------

    @Test
    fun testCreateBookmark_returnsCreatedBookmark() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val url = "https://create-test.example.com/${System.currentTimeMillis()}"

        val bookmark = remoteDataSource.createBookmark(testServer, url)

        assertNotNull(bookmark)
        assertNotNull(bookmark.id)
        assertEquals(url, bookmark.content?.url)
        println("Created bookmark: id=${bookmark.id}, url=${bookmark.content?.url}")
    }

    @Test
    fun testUpdateBookmark_archive_updatesServerState() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://archive-update.example.com/${System.currentTimeMillis()}")

        val updated = remoteDataSource.updateBookmark(
            testServer,
            remoteId,
            com.karakept.api.model.BookmarksBookmarkIdPatchRequest(archived = true)
        )

        assertEquals(true, updated.archived, "Bookmark should be archived after update")
    }

    @Test
    fun testUpdateBookmark_favourite_updatesServerState() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://fav-update.example.com/${System.currentTimeMillis()}")

        val updated = remoteDataSource.updateBookmark(
            testServer,
            remoteId,
            com.karakept.api.model.BookmarksBookmarkIdPatchRequest(favourited = true)
        )

        assertEquals(true, updated.favourited, "Bookmark should be favourited after update")
    }

    @Test
    fun testDeleteBookmark_removesFromServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://delete-test.example.com/${System.currentTimeMillis()}")

        // Confirm it exists
        val before = remoteDataSource.fetchBookmark(testServer, remoteId)
        assertEquals(remoteId, before.id)

        // Delete it
        remoteDataSource.deleteBookmark(testServer, remoteId)

        // Confirm it's gone
        try {
            remoteDataSource.fetchBookmark(testServer, remoteId)
            throw AssertionError("fetchBookmark should have thrown after deletion")
        } catch (e: Exception) {
            assertTrue(
                e.message?.contains("404") == true || e.message?.contains("not found", ignoreCase = true) == true || e.message?.contains("Error") == true,
                "Expected a 404-style error after deletion, got: ${e.message}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Tags
    // -------------------------------------------------------------------------

    @Test
    fun testAttachTags_addsTagsToBookmark() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://tag-attach.example.com/${System.currentTimeMillis()}")
        val tagsToAdd = listOf("integration-test", "automated")

        val response = remoteDataSource.attachTags(testServer, remoteId, tagsToAdd)

        // response.attached contains tag IDs (not names); verify the count matches
        assertNotNull(response.attached)
        assertEquals(tagsToAdd.size, response.attached!!.size, "Should have attached ${tagsToAdd.size} tags")

        // Verify on server
        val bookmark = remoteDataSource.fetchBookmark(testServer, remoteId)
        val serverTagNames = bookmark.tags?.mapNotNull { it.name } ?: emptyList()
        assertTrue(tagsToAdd.all { it in serverTagNames }, "Tags should be present on server: $serverTagNames")
    }

    @Test
    fun testDetachTags_removesTagsFromBookmark() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val remoteId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://tag-detach.example.com/${System.currentTimeMillis()}")
        val tagName = "to-be-removed-${System.currentTimeMillis()}"

        // Attach first
        remoteDataSource.attachTags(testServer, remoteId, listOf(tagName))

        // Get the tag ID for detachment
        val bookmarkWithTag = remoteDataSource.fetchBookmark(testServer, remoteId)
        val tagId = bookmarkWithTag.tags?.find { it.name == tagName }?.id
        assertNotNull(tagId, "Tag should have been attached with an ID")

        // Detach by ID
        val response = remoteDataSource.detachTags(testServer, remoteId, listOf(tagId))

        assertNotNull(response.detached)

        // Verify removal
        val updatedBookmark = remoteDataSource.fetchBookmark(testServer, remoteId)
        val remainingTags = updatedBookmark.tags?.mapNotNull { it.name } ?: emptyList()
        assertTrue(tagName !in remainingTags, "Tag should have been removed: $remainingTags")
    }

    // -------------------------------------------------------------------------
    // Lists
    // -------------------------------------------------------------------------

    @Test
    fun testFetchLists_returnsUserLists() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        // Seed a list so results are non-empty
        seedListViaTrpc(baseUrl, apiKey, "Integration Test List ${System.currentTimeMillis()}")

        val lists = remoteDataSource.fetchLists(testServer)

        assertTrue(lists.isNotEmpty(), "Should have at least one list")
    }

    @Test
    fun testAddAndRemoveBookmarkFromList() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val listId = seedListViaTrpc(baseUrl, apiKey, "Add-Remove List ${System.currentTimeMillis()}")
        val bookmarkId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://list-member.example.com/${System.currentTimeMillis()}")

        // Add to list
        remoteDataSource.addBookmarkToList(testServer, listId, bookmarkId)

        // Verify bookmark is in the list
        val bookmarksInList = remoteDataSource.fetchBookmarksForList(testServer, listId)
        assertTrue(bookmarksInList.any { it.id == bookmarkId }, "Bookmark should be in the list")

        // Remove from list
        remoteDataSource.removeBookmarkFromList(testServer, listId, bookmarkId)

        // Verify bookmark is no longer in the list
        val bookmarksAfterRemoval = remoteDataSource.fetchBookmarksForList(testServer, listId)
        assertTrue(bookmarksAfterRemoval.none { it.id == bookmarkId }, "Bookmark should have been removed from the list")
    }

    @Test
    fun testFetchBookmarksForList_returnsOnlyListMembers() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val listId = seedListViaTrpc(baseUrl, apiKey, "Members-Only List ${System.currentTimeMillis()}")
        val bookmark1Id = seedBookmarkViaTrpc(baseUrl, apiKey, "https://member1.example.com/${System.currentTimeMillis()}")
        val bookmark2Id = seedBookmarkViaTrpc(baseUrl, apiKey, "https://member2.example.com/${System.currentTimeMillis()}")

        remoteDataSource.addBookmarkToList(testServer, listId, bookmark1Id)
        // bookmark2 is NOT added to the list

        val listBookmarks = remoteDataSource.fetchBookmarksForList(testServer, listId)

        assertTrue(listBookmarks.any { it.id == bookmark1Id }, "bookmark1 should be in the list")
        assertTrue(listBookmarks.none { it.id == bookmark2Id }, "bookmark2 should not be in the list")
    }

    // -------------------------------------------------------------------------
    // Highlights
    // -------------------------------------------------------------------------

    @Test
    fun testCreateHighlight_persistsOnServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val bookmarkId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://highlight-create.example.com/${System.currentTimeMillis()}")
        val highlightText = "This is a test highlight"

        val highlight = remoteDataSource.createHighlight(
            server = testServer,
            bookmarkId = bookmarkId,
            text = highlightText,
            startOffset = 0,
            endOffset = highlightText.length,
            color = "yellow"
        )

        assertNotNull(highlight.id)
        assertEquals(highlightText, highlight.text)
        assertEquals(bookmarkId, highlight.bookmarkId)
        println("Created highlight: id=${highlight.id}, text=${highlight.text}")
    }

    @Test
    fun testFetchHighlightsForBookmark_returnsCreatedHighlights() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val bookmarkId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://highlight-fetch.example.com/${System.currentTimeMillis()}")
        val text1 = "First highlight"
        val text2 = "Second highlight"

        remoteDataSource.createHighlight(testServer, bookmarkId, text1, 0, text1.length)
        remoteDataSource.createHighlight(testServer, bookmarkId, text2, 20, 20 + text2.length)

        val highlights = remoteDataSource.fetchHighlightsForBookmark(testServer, bookmarkId)

        assertTrue(highlights.size >= 2, "Should have at least 2 highlights")
        val texts = highlights.map { it.text }
        assertTrue(text1 in texts, "First highlight text should be present")
        assertTrue(text2 in texts, "Second highlight text should be present")
    }

    @Test
    fun testFetchAllHighlights_includesCreatedHighlights() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val bookmarkId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://highlight-all.example.com/${System.currentTimeMillis()}")
        val highlightText = "Global highlight ${System.currentTimeMillis()}"

        val created = remoteDataSource.createHighlight(testServer, bookmarkId, highlightText, 0, highlightText.length)
        assertNotNull(created.id)

        val allHighlights = remoteDataSource.fetchAllHighlights(testServer)

        assertTrue(allHighlights.any { it.id == created.id }, "Created highlight should appear in all highlights")
    }

    @Test
    fun testUpdateHighlight_changesColorAndNote() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val bookmarkId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://highlight-update.example.com/${System.currentTimeMillis()}")
        val text = "Highlight to update"

        val created = remoteDataSource.createHighlight(testServer, bookmarkId, text, 0, text.length, color = "yellow")
        val highlightId = created.id!!

        val updated = remoteDataSource.updateHighlight(
            server = testServer,
            highlightId = highlightId,
            note = "Updated note",
            color = "red"
        )

        assertEquals("Updated note", updated.note)
        assertEquals("red", updated.color?.value?.lowercase())
    }

    @Test
    fun testDeleteHighlight_removesFromServer() = runTest(testDispatcher) {
        assertTrue(isDockerRunning, "Docker should be running")

        val bookmarkId = seedBookmarkViaTrpc(baseUrl, apiKey, "https://highlight-delete.example.com/${System.currentTimeMillis()}")
        val text = "Highlight to delete"

        val created = remoteDataSource.createHighlight(testServer, bookmarkId, text, 0, text.length)
        val highlightId = created.id!!

        // Confirm it exists
        val before = remoteDataSource.fetchHighlightsForBookmark(testServer, bookmarkId)
        assertTrue(before.any { it.id == highlightId }, "Highlight should exist before deletion")

        // Delete it
        remoteDataSource.deleteHighlight(testServer, highlightId)

        // Confirm it's gone
        val after = remoteDataSource.fetchHighlightsForBookmark(testServer, bookmarkId)
        assertTrue(after.none { it.id == highlightId }, "Highlight should be removed after deletion")
    }
}
