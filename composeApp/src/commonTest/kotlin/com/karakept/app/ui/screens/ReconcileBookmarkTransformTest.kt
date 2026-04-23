package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [applyReconcileBookmarkTransform].
 *
 * This pure function is the core of the scroll-position fix: instead of replacing
 * the whole accumulated list with page 0 after smart-list reconciliation, we apply
 * a targeted in-place update so the user's scroll position is preserved.
 *
 * Regression guard for: adding a bookmark to a list that causes a smart list to
 * exclude it (e.g. Feeds + ReadLater) should remove exactly that bookmark without
 * touching the rest of the accumulated list.
 */
class ReconcileBookmarkTransformTest {

    private fun bookmark(
        remoteId: Long,
        listIds: String = ""
    ) = BookmarkEntity(
        localId = remoteId,
        remoteId = remoteId,
        originalRemoteId = "orig-$remoteId",
        serverId = "server-1",
        url = "https://example.com/$remoteId",
        title = "Bookmark $remoteId",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = remoteId * 1000L,
        isArchived = false,
        isStarred = false,
        listIds = listIds
    )

    // ---- Null updated (server deleted the bookmark) ----

    @Test
    fun nullUpdated_removesBookmarkFromList() {
        val bk1 = bookmark(1L, "feeds")
        val bk2 = bookmark(2L, "feeds")
        val result = applyReconcileBookmarkTransform(
            current = listOf(bk1, bk2),
            remoteId = 1L,
            updated = null,
            currentListContext = "feeds"
        )
        assertEquals(listOf(bk2), result)
    }

    @Test
    fun nullUpdated_noListContext_removesBookmark() {
        val bk1 = bookmark(1L)
        val bk2 = bookmark(2L)
        val result = applyReconcileBookmarkTransform(
            current = listOf(bk1, bk2),
            remoteId = 1L,
            updated = null,
            currentListContext = null
        )
        assertEquals(listOf(bk2), result)
    }

    // ---- Smart list exclusion (the Feeds + ReadLater scenario) ----

    @Test
    fun bookmarkRemovedFromSmartList_removedFromAccumulatedList() {
        // User is viewing Feeds smart list. After adding bookmark to ReadLater,
        // reconciliation strips "feeds" from the bookmark's listIds.
        val bk1 = bookmark(1L, "feeds")
        val bk2 = bookmark(2L, "feeds")
        val bk3 = bookmark(3L, "feeds")
        // updated bookmark no longer has "feeds" in its listIds
        val updatedBk2 = bk2.copy(listIds = "read-later")

        val result = applyReconcileBookmarkTransform(
            current = listOf(bk1, bk2, bk3),
            remoteId = 2L,
            updated = updatedBk2,
            currentListContext = "feeds"
        )

        assertEquals(listOf(bk1, bk3), result)
        assertFalse(result.any { it.remoteId == 2L }, "Moved bookmark must be absent from Feeds")
    }

    @Test
    fun bookmarkRemovedFromSmartList_preservesAllOtherItems() {
        // Simulate a large accumulated list (like page 2–3 worth of items)
        val items = (1L..50L).map { bookmark(it, "feeds") }
        val movedBookmark = items[25] // item at position 25
        val updatedMoved = movedBookmark.copy(listIds = "read-later")

        val result = applyReconcileBookmarkTransform(
            current = items,
            remoteId = movedBookmark.remoteId,
            updated = updatedMoved,
            currentListContext = "feeds"
        )

        assertEquals(49, result.size, "Exactly one item should be removed")
        assertFalse(result.any { it.remoteId == movedBookmark.remoteId })
        // All other items must be unchanged and in the same relative order
        val remaining = items.filter { it.remoteId != movedBookmark.remoteId }
        assertEquals(remaining, result)
    }

    // ---- Bookmark still belongs to the list — update in place ----

    @Test
    fun bookmarkStillInList_updatedInPlace() {
        val bk1 = bookmark(1L, "feeds")
        val bk2 = bookmark(2L, "feeds")
        // Suppose the server returned a refreshed version of bk1 (e.g. updated title)
        val updatedBk1 = bk1.copy(title = "Updated Title", listIds = "feeds")

        val result = applyReconcileBookmarkTransform(
            current = listOf(bk1, bk2),
            remoteId = 1L,
            updated = updatedBk1,
            currentListContext = "feeds"
        )

        assertEquals(listOf(updatedBk1, bk2), result)
    }

    @Test
    fun bookmarkStillInList_multipleListIds_updatedInPlace() {
        val bk = bookmark(1L, "feeds,manual-1")
        val updated = bk.copy(listIds = "feeds,manual-1,manual-2")

        val result = applyReconcileBookmarkTransform(
            current = listOf(bk),
            remoteId = 1L,
            updated = updated,
            currentListContext = "feeds"
        )

        assertEquals(listOf(updated), result)
    }

    // ---- No list context (browsing All Bookmarks) ----

    @Test
    fun noListContext_bookmarkAlwaysUpdatedInPlace() {
        val bk = bookmark(1L, "manual-1")
        // Even if listIds changed completely, with no context filter the bookmark stays
        val updated = bk.copy(listIds = "manual-2")

        val result = applyReconcileBookmarkTransform(
            current = listOf(bk),
            remoteId = 1L,
            updated = updated,
            currentListContext = null
        )

        assertEquals(listOf(updated), result)
    }

    // ---- Bookmark not found in accumulated list ----

    @Test
    fun bookmarkNotInList_listUnchanged() {
        val bk1 = bookmark(1L, "feeds")
        val bk2 = bookmark(2L, "feeds")
        val updatedBk3 = bookmark(3L, "feeds") // not in accumulated list

        val result = applyReconcileBookmarkTransform(
            current = listOf(bk1, bk2),
            remoteId = 3L,
            updated = updatedBk3,
            currentListContext = "feeds"
        )

        assertEquals(listOf(bk1, bk2), result)
    }

    // ---- Empty list edge cases ----

    @Test
    fun emptyList_nullUpdated_returnsEmpty() {
        val result = applyReconcileBookmarkTransform(
            current = emptyList(),
            remoteId = 1L,
            updated = null,
            currentListContext = "feeds"
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun emptyList_nonNullUpdated_returnsEmpty() {
        val updated = bookmark(1L, "feeds")
        val result = applyReconcileBookmarkTransform(
            current = emptyList(),
            remoteId = 1L,
            updated = updated,
            currentListContext = "feeds"
        )
        assertTrue(result.isEmpty())
    }
}
