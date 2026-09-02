package com.karakept.app.ui.screens

import com.karakept.app.data.local.entity.BookmarkEntity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [countBookmarksAbove], which backs the "N new bookmarks" pill.
 *
 * Regression guard for the pill over-reporting. The count used to accumulate per-refresh
 * window diffs, which counted rows regardless of where they landed, never decremented, and
 * double-counted a row that a prepend evicted off the tail and a later removal pulled back in.
 */
class NewBookmarksAboveCountTest {

    private fun bookmark(remoteId: Long, isRead: Boolean = false) = BookmarkEntity(
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
        isRead = isRead,
        listIds = ""
    )

    private fun listOfIds(vararg ids: Long) = ids.map { bookmark(it) }

    @Test
    fun nothingSeenYet_countsZero() {
        assertEquals(0, countBookmarksAbove(listOfIds(1, 2, 3), seenTopRemoteId = null))
    }

    @Test
    fun seenBookmarkIsStillAtTheTop_countsZero() {
        assertEquals(0, countBookmarksAbove(listOfIds(1, 2, 3), seenTopRemoteId = 1L))
    }

    @Test
    fun prependedBookmarks_areCounted() {
        assertEquals(2, countBookmarksAbove(listOfIds(90, 91, 1, 2, 3), seenTopRemoteId = 1L))
    }

    @Test
    fun bookmarksAddedBelowTheSeenRow_areNotCounted() {
        // A diff counted anything new to the window; only rows above the anchor are "new above".
        assertEquals(0, countBookmarksAbove(listOfIds(1, 2, 3, 90, 91), seenTopRemoteId = 1L))
    }

    @Test
    fun removingARowAboveTheAnchor_bringsTheCountBackDown() {
        // The old counter only ever incremented, so it never recovered from this.
        val afterPrepend = listOfIds(90, 91, 1, 2, 3)
        assertEquals(2, countBookmarksAbove(afterPrepend, seenTopRemoteId = 1L))

        val afterRemoval = listOfIds(91, 1, 2, 3)
        assertEquals(1, countBookmarksAbove(afterRemoval, seenTopRemoteId = 1L))
    }

    @Test
    fun rowEvictedOffTheTailAndLaterRestored_isNotCountedAsNew() {
        // A refresh re-reads a fixed page range, so prepending pushes rows off the end. When a
        // later removal at the top pulls one back into the window, a diff called it new even
        // though the user had already seen it. Position-based counting cannot make that mistake.
        val window = listOfIds(1, 2, 3, 4)
        assertEquals(0, countBookmarksAbove(window, seenTopRemoteId = 1L))

        // Sync prepends two rows; bookmark 4 falls out of the loaded window.
        val afterPrepend = listOfIds(90, 91, 1, 2, 3)
        assertEquals(2, countBookmarksAbove(afterPrepend, seenTopRemoteId = 1L))

        // A removal at the top pulls bookmark 4 back in — still only the prepends are above.
        val afterRestore = listOfIds(91, 1, 2, 3, 4)
        assertEquals(1, countBookmarksAbove(afterRestore, seenTopRemoteId = 1L))
    }

    @Test
    fun repeatedRefreshesOfTheSameWindow_doNotCompound() {
        // One sync refreshes the window many times: per committed page, plus twice more.
        val window = listOfIds(90, 1, 2, 3)
        repeat(5) {
            assertEquals(1, countBookmarksAbove(window, seenTopRemoteId = 1L))
        }
    }

    @Test
    fun seenBookmarkNoLongerInTheList_countsZero() {
        assertEquals(0, countBookmarksAbove(listOfIds(90, 91, 2, 3), seenTopRemoteId = 1L))
    }

    @Test
    fun emptyList_countsZero() {
        assertEquals(0, countBookmarksAbove(emptyList(), seenTopRemoteId = 1L))
    }

    @Test
    fun oldestFirstSort_countsOnlyWhatIsActuallyAbove() {
        // Under a non-NEWEST sort new bookmarks land at the bottom. The old counter reported
        // them as being above the viewport regardless.
        assertEquals(0, countBookmarksAbove(listOfIds(1, 2, 3, 90, 91), seenTopRemoteId = 1L))
    }

    // Read bookmarks — the pill offers a trip to the top, and a faded row is not worth one.

    @Test
    fun excludeRead_leavesReadBookmarksOutOfTheCount() {
        val window = listOf(bookmark(90), bookmark(91, isRead = true), bookmark(1))
        assertEquals(1, countBookmarksAbove(window, seenTopRemoteId = 1L, excludeRead = true))
    }

    @Test
    fun excludeRead_countsEveryBookmarkWhenFadingIsOff() {
        val window = listOf(bookmark(90), bookmark(91, isRead = true), bookmark(1))
        assertEquals(2, countBookmarksAbove(window, seenTopRemoteId = 1L, excludeRead = false))
    }

    @Test
    fun excludeRead_allReadAboveCountsZero() {
        // The pill disappears rather than sending the user to rows they have already read.
        val window = listOf(bookmark(90, isRead = true), bookmark(91, isRead = true), bookmark(1))
        assertEquals(0, countBookmarksAbove(window, seenTopRemoteId = 1L, excludeRead = true))
    }

    @Test
    fun excludeRead_ignoresReadBookmarksBelowTheAnchor() {
        val window = listOf(bookmark(90), bookmark(1), bookmark(2, isRead = true))
        assertEquals(1, countBookmarksAbove(window, seenTopRemoteId = 1L, excludeRead = true))
    }
}
