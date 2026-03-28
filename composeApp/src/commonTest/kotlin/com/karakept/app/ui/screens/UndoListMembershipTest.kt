package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the undo-add-to-list transform logic.
 *
 * [applyRestoreAndRemoveFromListTransform] handles two cases:
 * 1. Bookmark still in list → strips the target listId
 * 2. Bookmark removed by smart list reconciliation → re-inserts at original position
 *
 * Also covers [applyRemoveBookmarkTransform] for completeness.
 */
class UndoListMembershipTest {

    // ------------------------------------------------------------------
    // applyRestoreAndRemoveFromListTransform — bookmark still present
    // ------------------------------------------------------------------

    @Test
    fun restoreTransform_stripsListIdWhenBookmarkPresent() {
        val bookmark = makeBookmark(remoteId = 1, listIds = "listA,listB")
        val others = listOf(makeBookmark(remoteId = 2))
        val current = listOf(bookmark, others.first())

        val result = applyRestoreAndRemoveFromListTransform(current, bookmark, "listA")

        assertEquals(2, result.size)
        assertEquals("listB", result.first { it.remoteId == 1L }.listIds)
        // Other bookmarks untouched
        assertEquals("", result.first { it.remoteId == 2L }.listIds)
    }

    @Test
    fun restoreTransform_stripsOnlyTargetListId() {
        val bookmark = makeBookmark(remoteId = 1, listIds = "listA,listB,listC")
        val result = applyRestoreAndRemoveFromListTransform(listOf(bookmark), bookmark, "listB")

        assertEquals("listA,listC", result.first().listIds)
    }

    @Test
    fun restoreTransform_handlesLastListIdRemoval() {
        val bookmark = makeBookmark(remoteId = 1, listIds = "listA")
        val result = applyRestoreAndRemoveFromListTransform(listOf(bookmark), bookmark, "listA")

        assertEquals("", result.first().listIds)
    }

    // ------------------------------------------------------------------
    // applyRestoreAndRemoveFromListTransform — bookmark missing (smart list reconciliation)
    // ------------------------------------------------------------------

    @Test
    fun restoreTransform_reinsertsAtOriginalPositionWhenMissing() {
        val bookmark = makeBookmark(remoteId = 99, listIds = "listA")
        val current = listOf(
            makeBookmark(remoteId = 1),
            makeBookmark(remoteId = 2),
            makeBookmark(remoteId = 3)
        )

        val result = applyRestoreAndRemoveFromListTransform(current, bookmark, "listA", originalPosition = 1)

        assertEquals(4, result.size)
        assertEquals(99L, result[1].remoteId)
    }

    @Test
    fun restoreTransform_reinsertsAtStartWhenPositionIsZero() {
        val bookmark = makeBookmark(remoteId = 99)
        val current = listOf(makeBookmark(remoteId = 1), makeBookmark(remoteId = 2))

        val result = applyRestoreAndRemoveFromListTransform(current, bookmark, "listA", originalPosition = 0)

        assertEquals(3, result.size)
        assertEquals(99L, result[0].remoteId)
    }

    @Test
    fun restoreTransform_reinsertsAtEndWhenPositionEqualsSize() {
        val bookmark = makeBookmark(remoteId = 99)
        val current = listOf(makeBookmark(remoteId = 1), makeBookmark(remoteId = 2))

        val result = applyRestoreAndRemoveFromListTransform(current, bookmark, "listA", originalPosition = 2)

        assertEquals(3, result.size)
        assertEquals(99L, result[2].remoteId)
    }

    @Test
    fun restoreTransform_defaultsToTopWhenPositionIsNegative() {
        val bookmark = makeBookmark(remoteId = 99)
        val current = listOf(makeBookmark(remoteId = 1), makeBookmark(remoteId = 2))

        val result = applyRestoreAndRemoveFromListTransform(current, bookmark, "listA", originalPosition = -1)

        assertEquals(3, result.size)
        assertEquals(99L, result[0].remoteId, "Should insert at index 0 when original position is -1")
    }

    @Test
    fun restoreTransform_defaultsToTopWhenPositionExceedsSize() {
        val bookmark = makeBookmark(remoteId = 99)
        val current = listOf(makeBookmark(remoteId = 1))

        val result = applyRestoreAndRemoveFromListTransform(current, bookmark, "listA", originalPosition = 100)

        assertEquals(2, result.size)
        assertEquals(99L, result[0].remoteId, "Should insert at index 0 when position exceeds list size")
    }

    @Test
    fun restoreTransform_reinsertsIntoEmptyList() {
        val bookmark = makeBookmark(remoteId = 99)
        val result = applyRestoreAndRemoveFromListTransform(emptyList(), bookmark, "listA", originalPosition = 0)

        assertEquals(1, result.size)
        assertEquals(99L, result[0].remoteId)
    }

    // ------------------------------------------------------------------
    // applyRemoveBookmarkTransform — existing function
    // ------------------------------------------------------------------

    @Test
    fun removeTransform_filtersOutBookmarkWhenViewingTargetList() {
        val bookmark = makeBookmark(remoteId = 1, listIds = "listA,listB")
        val other = makeBookmark(remoteId = 2, listIds = "listA")
        val current = listOf(bookmark, other)

        val result = applyRemoveBookmarkTransform(
            currentListContext = "listA",
            listId = "listA",
            bookmarks = current,
            bookmark = bookmark
        )

        assertEquals(1, result.size)
        assertEquals(2L, result[0].remoteId)
    }

    @Test
    fun removeTransform_stripsListIdWhenViewingDifferentContext() {
        val bookmark = makeBookmark(remoteId = 1, listIds = "listA,listB")
        val current = listOf(bookmark)

        val result = applyRemoveBookmarkTransform(
            currentListContext = "listB",
            listId = "listA",
            bookmarks = current,
            bookmark = bookmark
        )

        assertEquals(1, result.size)
        assertEquals("listB", result[0].listIds)
    }

    @Test
    fun removeTransform_stripsListIdWhenNoListContext() {
        val bookmark = makeBookmark(remoteId = 1, listIds = "listA,listB")
        val current = listOf(bookmark)

        val result = applyRemoveBookmarkTransform(
            currentListContext = null,
            listId = "listA",
            bookmarks = current,
            bookmark = bookmark
        )

        assertEquals(1, result.size)
        assertEquals("listB", result[0].listIds)
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private fun makeBookmark(
        remoteId: Long,
        listIds: String = "",
        serverId: String = "server-1"
    ) = BookmarkEntity(
        localId = remoteId,
        remoteId = remoteId,
        originalRemoteId = "remote-$remoteId",
        serverId = serverId,
        title = "Test Bookmark $remoteId",
        url = "https://example.com/$remoteId",
        description = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        tags = "",
        listIds = listIds,
        isStarred = false,
        isArchived = false,
        isRead = false,
        createdAt = 0L,
        readingTimeMinutes = 0,
        content = null
    )
}
