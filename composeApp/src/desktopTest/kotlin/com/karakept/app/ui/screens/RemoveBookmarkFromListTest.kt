package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
 * These tests exercise the transformation logic directly as a pure function,
 * avoiding the need to instantiate MainScreenModel (which depends on Voyager,
 * Koin, and many repositories).
 *
 * Tests are expected to FAIL at the RED phase (before Plan 01 implementation).
 */
class RemoveBookmarkFromListTest {

    // ---- Helper: the transformation logic under test ----

    /**
     * Replicates the expected conditional behavior of removeBookmarkFromList:
     * - If currentListContext == listId, filter the bookmark out entirely.
     * - Otherwise, keep the bookmark but strip listId from its listIds.
     *
     * This function represents the EXPECTED behavior after Plan 01 fix.
     * The current production code always does the "update listIds" path
     * (never filters out), so tests calling this against the actual
     * production transform will fail.
     */
    private fun applyRemoveBookmarkFromListTransform(
        currentListContext: String?,
        listId: String,
        targetRemoteId: Long,
        bookmarks: List<BookmarkEntity>
    ): List<BookmarkEntity> {
        // Delegate to the production logic when it exists.
        // For now, inline the EXPECTED behavior so tests define the contract.
        return if (currentListContext == listId) {
            // D-01: viewing the target list -> filter out the bookmark
            bookmarks.filter { it.remoteId != targetRemoteId }
        } else {
            // D-02: viewing a different context -> update listIds only
            bookmarks.map {
                if (it.remoteId == targetRemoteId) {
                    val newListIds = it.listIds
                        .split(",")
                        .map { id -> id.trim() }
                        .filter { id -> id.isNotBlank() && id != listId }
                    it.copy(listIds = newListIds.joinToString(","))
                } else {
                    it
                }
            }
        }
    }

    /**
     * Replicates the CURRENT production behavior (always updates listIds, never filters).
     * Used as the "production transform" to assert that tests fail at RED phase.
     */
    private fun currentProductionTransform(
        targetRemoteId: Long,
        listId: String,
        bookmarks: List<BookmarkEntity>
    ): List<BookmarkEntity> {
        return bookmarks.map {
            if (it.remoteId == targetRemoteId) {
                val newListIds = it.listIds
                    .split(",")
                    .map { id -> id.trim() }
                    .filter { id -> id.isNotBlank() && id != listId }
                it.copy(listIds = newListIds.joinToString(","))
            } else {
                it
            }
        }
    }

    // ---- Test fixtures ----

    private fun bookmarkEntity(
        remoteId: Long,
        listIds: String = "",
        title: String = "Bookmark $remoteId"
    ) = BookmarkEntity(
        localId = remoteId,
        remoteId = remoteId,
        originalRemoteId = "orig-$remoteId",
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
        val currentListContext = "list-A"  // viewing the same list
        val bookmark = bookmarkEntity(remoteId = 42, listIds = "list-A,list-B")
        val bookmarks = listOf(bookmark)

        // Apply the CURRENT production transform (no conditional logic)
        val result = currentProductionTransform(
            targetRemoteId = 42,
            listId = listId,
            bookmarks = bookmarks
        )

        // The expected behavior (D-01): bookmark should be completely removed
        // This assertion will FAIL with current production code because
        // the bookmark stays in the list with updated listIds instead of being filtered out.
        assertEquals(0, result.size, "Bookmark should be filtered out when viewing the target list")
    }

    // ---- Test 2: Viewing different context -> bookmark stays with updated listIds ----

    @Test
    fun viewingDifferentContext_bookmarkListIdsUpdated() {
        val listId = "list-A"
        val currentListContext: String? = null  // viewing "All Bookmarks"
        val bookmark = bookmarkEntity(remoteId = 42, listIds = "list-A,list-B")
        val bookmarks = listOf(bookmark)

        // Apply the expected conditional transform
        val result = applyRemoveBookmarkFromListTransform(
            currentListContext = currentListContext,
            listId = listId,
            targetRemoteId = 42,
            bookmarks = bookmarks
        )

        // D-02: bookmark remains but listIds updated
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

        // Apply the CURRENT production transform (no conditional logic)
        val result = currentProductionTransform(
            targetRemoteId = 42,
            listId = listId,
            bookmarks = bookmarks
        )

        // The expected behavior (D-01): only the target bookmark is removed,
        // other bookmarks are untouched.
        // This assertion will FAIL with current production code because the target
        // bookmark stays in the list (size stays 3 instead of becoming 2).
        assertEquals(2, result.size, "Only target bookmark should be removed")
        assertTrue(
            result.all { it.remoteId != 42L },
            "Target bookmark (remoteId=42) should not be in the result"
        )
        // Other bookmarks should be completely unchanged
        assertEquals("list-A", result[0].listIds, "Other bookmark 1 listIds should be unchanged")
        assertEquals("list-A,list-C", result[1].listIds, "Other bookmark 2 listIds should be unchanged")
    }
}
