package com.karakept.app.data.repository

import com.karakept.app.data.local.entity.BookmarkEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [computeStaleListRemovals] — the pure reconciliation function
 * that determines which bookmarks need their list membership updated after a ForList sync.
 *
 * Tests LIST-02 gap closure: after a smart list sync, bookmarks locally in the list
 * but absent from the server response must have the listId stripped.
 */
class BookmarkSyncPipelineReconcileTest {

    private fun makeBookmarkEntity(
        localId: Long = 1L,
        remoteId: String = "bk-1",
        serverId: String = "server1",
        listIds: String = ""
    ) = BookmarkEntity(
        localId = localId,
        remoteId = remoteId,
        serverId = serverId,
        title = "Test Bookmark",
        url = "https://example.com",
        description = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        tags = "",
        listIds = listIds,
        isStarred = false,
        isArchived = false,
        isRead = false,
        createdAt = 1000L,
        readingTimeMinutes = 0,
        readingProgress = 0f,
        readingScrollIndex = 0,
        readingScrollOffset = 0,
        content = null
    )

    @Test
    fun `returns empty when all local bookmarks appear in server response`() {
        val local = listOf(
            makeBookmarkEntity(localId = 1, remoteId = "bk-1", listIds = "smart-1"),
            makeBookmarkEntity(localId = 2, remoteId = "bk-2", listIds = "smart-1,manual-1")
        )
        val serverIds = setOf("bk-1", "bk-2")

        val result = computeStaleListRemovals(local, serverIds, "smart-1")

        assertTrue(result.isEmpty(), "No removals expected when all bookmarks are in server response")
    }

    @Test
    fun `strips stale listId but preserves other list memberships`() {
        val local = listOf(
            makeBookmarkEntity(localId = 10, remoteId = "bk-10", listIds = "smart-1,manual-1")
        )
        val serverIds = setOf("bk-99") // bk-10 is NOT in server response

        val result = computeStaleListRemovals(local, serverIds, "smart-1")

        assertEquals(1, result.size)
        assertEquals(10L, result[0].first)
        assertEquals("manual-1", result[0].second, "smart-1 should be stripped, manual-1 preserved")
    }

    @Test
    fun `handles bookmark whose only listId is the reconciled one`() {
        val local = listOf(
            makeBookmarkEntity(localId = 20, remoteId = "bk-20", listIds = "smart-1")
        )
        val serverIds = emptySet<String>() // bk-20 is NOT in server response

        val result = computeStaleListRemovals(local, serverIds, "smart-1")

        assertEquals(1, result.size)
        assertEquals(20L, result[0].first)
        assertEquals("", result[0].second, "listIds should become empty string when sole list is stripped")
    }

    @Test
    fun `returns multiple entries when multiple bookmarks are stale`() {
        val local = listOf(
            makeBookmarkEntity(localId = 1, remoteId = "bk-1", listIds = "smart-1"),
            makeBookmarkEntity(localId = 2, remoteId = "bk-2", listIds = "smart-1,other"),
            makeBookmarkEntity(localId = 3, remoteId = "bk-3", listIds = "smart-1")
        )
        val serverIds = emptySet<String>() // None are in server response

        val result = computeStaleListRemovals(local, serverIds, "smart-1")

        assertEquals(3, result.size)
        val ids = result.map { it.first }.toSet()
        assertEquals(setOf(1L, 2L, 3L), ids)
    }

    @Test
    fun `does not strip listId from bookmarks present in server response`() {
        val local = listOf(
            makeBookmarkEntity(localId = 1, remoteId = "bk-1", listIds = "smart-1"),
            makeBookmarkEntity(localId = 2, remoteId = "bk-2", listIds = "smart-1,manual-1"),
            makeBookmarkEntity(localId = 3, remoteId = "bk-3", listIds = "smart-1")
        )
        // bk-1 and bk-3 are in server response; bk-2 is NOT
        val serverIds = setOf("bk-1", "bk-3")

        val result = computeStaleListRemovals(local, serverIds, "smart-1")

        assertEquals(1, result.size, "Only bk-2 should be flagged as stale")
        assertEquals(2L, result[0].first)
        assertEquals("manual-1", result[0].second)
    }
}
