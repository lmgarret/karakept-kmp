package com.karakept.app.ui.screens.main

import com.karakept.app.data.local.entity.BookmarkEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [resolveAnchorScrollTarget], the pure decision function behind
 * [PreserveListScrollAnchor].
 *
 * Regression guard for the bookmark-list scroll jump: when an item above the
 * viewport is removed (e.g. by smart-list reconciliation after a quick action),
 * the anchor bookmark must be re-pinned to its new index so the list stays put.
 */
class ListScrollAnchorTest {

    private fun bookmark(remoteId: Long) = BookmarkEntity(
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
        listIds = ""
    )

    private fun listOfIds(vararg ids: Long) = ids.map { bookmark(it) }

    @Test
    fun nullAnchor_returnsNull() {
        val result = resolveAnchorScrollTarget(
            anchorKey = null,
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 5,
            newBookmarks = listOfIds(1, 2, 3)
        )
        assertNull(result)
    }

    @Test
    fun whileScrolling_returnsNull() {
        // Don't fight an active user scroll gesture.
        val result = resolveAnchorScrollTarget(
            anchorKey = 10L,
            isScrolling = true,
            versionChanged = false,
            currentFirstIndex = 5,
            newBookmarks = listOfIds(1, 10, 2)
        )
        assertNull(result)
    }

    @Test
    fun versionChanged_returnsNull() {
        // A full reload (sync / filter change) bumps the list version and wants its
        // own scroll behaviour (e.g. scroll-to-top) — never re-pin in that case.
        val newList = (0L..49L).filter { it != 5L }.map { bookmark(it) }
        val result = resolveAnchorScrollTarget(
            anchorKey = 30L,
            isScrolling = false,
            versionChanged = true,
            currentFirstIndex = 30,
            newBookmarks = newList
        )
        assertNull(result)
    }

    @Test
    fun anchorRemoved_returnsNull() {
        // The anchor bookmark itself was the removed one — leave position to Compose.
        val result = resolveAnchorScrollTarget(
            anchorKey = 99L,
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 3,
            newBookmarks = listOfIds(1, 2, 3, 4)
        )
        assertNull(result)
    }

    @Test
    fun anchorAlreadyFirstVisible_returnsNull() {
        // Compose already kept the anchor at the top (or nothing shifted) — no-op.
        val newList = listOfIds(10, 20, 30)
        val result = resolveAnchorScrollTarget(
            anchorKey = 10L,
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 0,
            newBookmarks = newList
        )
        assertNull(result)
    }

    @Test
    fun itemRemovedAboveAnchor_anchorNotYetFollowed_returnsNewIndex() {
        // User was at index 30 (anchor key=30). An item above the viewport was removed,
        // so the anchor now lives at index 29, but firstVisibleItemIndex still reads 30
        // (Compose didn't re-anchor). We must re-pin to 29.
        val newList = (0L..49L).filter { it != 5L }.map { bookmark(it) } // removed id 5
        val anchorNewIndex = newList.indexOfFirst { it.remoteId == 30L } // 29
        val result = resolveAnchorScrollTarget(
            anchorKey = 30L,
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 30,
            newBookmarks = newList
        )
        assertEquals(anchorNewIndex, result)
        assertEquals(29, result)
    }

    @Test
    fun multipleItemsRemovedAboveAnchor_returnsCorrectNewIndex() {
        // Remove ids 1,2,3 (all above anchor 30). Anchor shifts up by 3 → index 27.
        val newList = (0L..49L).filter { it !in setOf(1L, 2L, 3L) }.map { bookmark(it) }
        val result = resolveAnchorScrollTarget(
            anchorKey = 30L,
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 30,
            newBookmarks = newList
        )
        assertEquals(27, result)
    }

    @Test
    fun itemAppendedBelow_anchorIndexUnchanged_returnsNull() {
        // load-more appended items at the bottom; the top anchor index is unchanged.
        val newList = listOfIds(10, 20, 30, 40, 50, 60)
        val result = resolveAnchorScrollTarget(
            anchorKey = 10L,
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 0,
            newBookmarks = newList
        )
        assertNull(result)
    }
}
