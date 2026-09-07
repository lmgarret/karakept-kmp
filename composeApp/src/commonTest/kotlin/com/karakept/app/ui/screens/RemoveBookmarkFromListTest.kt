package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.ui.screens.applyRemoveBookmarkTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for removeBookmarkFromList conditional filtering behavior (LIST-01).
 *
 * Validates decisions D-01 and D-02 from the phase plan:
 * - D-01: When viewing a specific list, removing a bookmark from that list should
 *         filter it out of the accumulated bookmarks entirely.
 * - D-02: When viewing "All Bookmarks" (or any other context), removing a bookmark
 *         from a list should keep the bookmark but update its listIds.
 *
 * Tests call the production [applyRemoveBookmarkTransform] function directly,
 * ensuring they serve as a valid regression gate for LIST-01.
 */
class RemoveBookmarkFromListTest {

    // ---- Test fixtures ----

    private fun bookmarkEntity(
        remoteId: Long,
        listIds: String = "",
        title: String = "Bookmark $remoteId"
    ) = BookmarkEntity(
        localId = remoteId,
        remoteId = "orig-$remoteId",
        serverId = "server-1",
        url = "https://example.com/$remoteId",
        title = title,
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 1000L,
        isArchived = false,
        isStarred = false,
        listIds = listIds
    )

    // ---- Test 1: Viewing target list -> bookmark filtered out entirely ----

    @Test
    fun viewingTargetList_bookmarkIsFilteredOut() {
        val listId = "list-A"
        val currentListContext = "list-A"
        val bookmark = bookmarkEntity(remoteId = 42, listIds = "list-A,list-B")
        val bookmarks = listOf(bookmark)

        val result = applyRemoveBookmarkTransform(
            currentListContext = currentListContext,
            listId = listId,
            bookmarks = bookmarks,
            bookmark = bookmark
        )

        assertEquals(0, result.size, "Bookmark should be filtered out when viewing the target list")
    }

    // ---- Test 2: Viewing different context -> bookmark stays with updated listIds ----

    @Test
    fun viewingDifferentContext_bookmarkListIdsUpdated() {
        val listId = "list-A"
        val currentListContext: String? = null
        val bookmark = bookmarkEntity(remoteId = 42, listIds = "list-A,list-B")
        val bookmarks = listOf(bookmark)

        val result = applyRemoveBookmarkTransform(
            currentListContext = currentListContext,
            listId = listId,
            bookmarks = bookmarks,
            bookmark = bookmark
        )

        assertEquals(1, result.size, "Bookmark should remain when not viewing the target list")
        assertEquals("list-B", result[0].listIds, "listIds should have list-A removed")
    }

    // ---- Test 3: Viewing target list -> other bookmarks are NOT affected ----

    @Test
    fun viewingTargetList_otherBookmarksUnaffected() {
        val listId = "list-A"
        val currentListContext = "list-A"
        val targetBookmark = bookmarkEntity(remoteId = 42, listIds = "list-A,list-B")
        val otherBookmark1 = bookmarkEntity(remoteId = 10, listIds = "list-A")
        val otherBookmark2 = bookmarkEntity(remoteId = 20, listIds = "list-A,list-C")
        val bookmarks = listOf(otherBookmark1, targetBookmark, otherBookmark2)

        val result = applyRemoveBookmarkTransform(
            currentListContext = currentListContext,
            listId = listId,
            bookmarks = bookmarks,
            bookmark = targetBookmark
        )

        assertEquals(2, result.size, "Only target bookmark should be removed")
        assertTrue(
            result.all { it.remoteId != "orig-42" },
            "Target bookmark (remoteId=42) should not be in the result"
        )
        assertEquals("list-A", result[0].listIds, "Other bookmark 1 listIds should be unchanged")
        assertEquals("list-A,list-C", result[1].listIds, "Other bookmark 2 listIds should be unchanged")
    }
}
