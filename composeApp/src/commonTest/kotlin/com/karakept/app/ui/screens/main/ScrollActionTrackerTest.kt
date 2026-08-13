package com.karakept.app.ui.screens.main

import com.karakept.app.data.local.entity.BookmarkEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [ScrollActionTracker], the pure decision logic behind [MainScreenScrollAction].
 *
 * Regression guard for the sync-on-open bulk mark-as-read: a background sync mutates the list
 * repeatedly (once per committed page, plus twice per `syncBookmarks()`), and each pass can
 * prepend rows. None of that is a scroll, so none of it may fire the list's scroll action.
 */
class ScrollActionTrackerTest {

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

    /**
     * A viewport showing [visibleCount] rows starting at [firstIndex]. The default is a long
     * list so the bottom sweep stays out of the way unless a test opts into it.
     */
    private fun snapshot(
        bookmarks: List<BookmarkEntity>,
        firstIndex: Int = 0,
        firstOffset: Int = 0,
        visibleCount: Int = 5,
        isScrolling: Boolean = false,
        actedOnIds: Set<Long> = emptySet()
    ) = ScrollActionSnapshot(
        firstIndex = firstIndex,
        firstKey = bookmarks.getOrNull(firstIndex)?.remoteId,
        firstOffset = firstOffset,
        lastVisibleIndex = minOf(firstIndex + visibleCount - 1, bookmarks.lastIndex),
        bookmarks = bookmarks,
        isScrolling = isScrolling,
        actedOnIds = actedOnIds
    )

    private fun ids(bookmarks: List<BookmarkEntity>) = bookmarks.map { it.remoteId }

    private val longList = (1L..30L).map { bookmark(it) }

    // --- genuine scrolling still fires -------------------------------------------------

    @Test
    fun scrollingDown_firesOnItemsThatLeftTheTop() {
        val tracker = ScrollActionTracker()
        tracker.onSnapshot(snapshot(longList, firstIndex = 0))

        val fired = tracker.onSnapshot(snapshot(longList, firstIndex = 3, isScrolling = true))

        // Items at indices 0,1,2 scrolled off the top.
        assertEquals(listOf(1L, 2L, 3L), ids(fired))
    }

    @Test
    fun sameBookmarkNeverFiresTwice() {
        val tracker = ScrollActionTracker()
        tracker.onSnapshot(snapshot(longList, firstIndex = 0))
        tracker.onSnapshot(snapshot(longList, firstIndex = 3, isScrolling = true))

        val fired = tracker.onSnapshot(snapshot(longList, firstIndex = 5, isScrolling = true))

        assertEquals(listOf(4L, 5L), ids(fired))
    }

    @Test
    fun scrollingBackUpThenDownAgain_reFiresTheSameBookmarks() {
        // A bookmark the user manually marked unread must be able to fire again.
        val tracker = ScrollActionTracker()
        tracker.onSnapshot(snapshot(longList, firstIndex = 0))
        tracker.onSnapshot(snapshot(longList, firstIndex = 3, isScrolling = true))
        tracker.onSnapshot(snapshot(longList, firstIndex = 0, isScrolling = true))

        val fired = tracker.onSnapshot(snapshot(longList, firstIndex = 3, isScrolling = true))

        assertEquals(listOf(1L, 2L, 3L), ids(fired))
    }

    @Test
    fun bookmarksTheUserActedOn_areNeverFired() {
        val tracker = ScrollActionTracker()
        tracker.onSnapshot(snapshot(longList, firstIndex = 0))

        val fired = tracker.onSnapshot(
            snapshot(longList, firstIndex = 3, isScrolling = true, actedOnIds = setOf(2L))
        )

        assertEquals(listOf(1L, 3L), ids(fired))
    }

    // --- sync mutations must never fire ------------------------------------------------

    @Test
    fun prependWhileParkedAtTop_firesNothing() {
        // The reported bug: open the app at the top of a smart list, sync brings in new
        // bookmarks, and they are marked read without the user ever scrolling.
        val tracker = ScrollActionTracker()
        val before = listOfIds(10, 11, 12, 13, 14)
        tracker.onSnapshot(snapshot(before, firstIndex = 0))

        val after = listOfIds(20, 21, 22, 10, 11, 12, 13, 14)
        // The dataset swap lands first, with layoutInfo still describing the old list.
        assertTrue(tracker.onSnapshot(snapshot(after, firstIndex = 0)).isEmpty())
        // The anchor stays pinned at the top, so the new rows are simply on screen.
        assertTrue(tracker.onSnapshot(snapshot(after, firstIndex = 0)).isEmpty())
    }

    @Test
    fun prependWhileScrolledMidList_firesNothingAndReBaselinesTheAnchor() {
        val tracker = ScrollActionTracker()
        tracker.onSnapshot(snapshot(longList, firstIndex = 0))
        tracker.onSnapshot(snapshot(longList, firstIndex = 10, isScrolling = true))

        // Sync prepends 3 rows: Compose re-indexes, so bookmark 11 is now at index 13.
        val grown = listOfIds(101, 102, 103) + longList
        assertTrue(tracker.onSnapshot(snapshot(grown, firstIndex = 10)).isEmpty())
        assertTrue(tracker.onSnapshot(snapshot(grown, firstIndex = 13)).isEmpty())

        // Scrolling one further row down fires only that row, not the re-indexed backlog.
        val fired = tracker.onSnapshot(snapshot(grown, firstIndex = 14, isScrolling = true))
        assertEquals(listOf(11L), ids(fired))
    }

    @Test
    fun repeatedSyncRefreshes_fireNothing() {
        // syncBookmarks() refreshes the window once per committed page plus twice more.
        val tracker = ScrollActionTracker()
        var current = longList
        tracker.onSnapshot(snapshot(current, firstIndex = 0))

        repeat(5) { round ->
            val added = listOfIds(200L + round)
            current = added + current
            assertTrue(tracker.onSnapshot(snapshot(current, firstIndex = 0)).isEmpty())
            assertTrue(tracker.onSnapshot(snapshot(current, firstIndex = 0)).isEmpty())
        }
    }

    // --- the bottom sweep ---------------------------------------------------------------

    @Test
    fun shortListOnOpen_withoutAnyGesture_firesNothing() {
        // Every row fits on screen, so the list is "at the bottom" from the first frame.
        val tracker = ScrollActionTracker()
        val short = listOfIds(1, 2, 3)

        assertTrue(tracker.onSnapshot(snapshot(short, visibleCount = 3)).isEmpty())
        assertTrue(tracker.onSnapshot(snapshot(short, visibleCount = 3)).isEmpty())
    }

    @Test
    fun shortList_afterARealGesture_sweepsTheVisibleTail() {
        val tracker = ScrollActionTracker()
        val short = listOfIds(1, 2, 3)
        tracker.onSnapshot(snapshot(short, visibleCount = 3))

        // A drag that actually moves the list, without changing the first visible index.
        val fired = tracker.onSnapshot(
            snapshot(short, firstOffset = 40, visibleCount = 3, isScrolling = true)
        )

        assertEquals(listOf(1L, 2L, 3L), ids(fired))
    }

    @Test
    fun overscrollThatDoesNotMoveTheList_doesNotArmTheSweep() {
        // Pull-to-refresh reports a scroll in progress while the offset stays put.
        val tracker = ScrollActionTracker()
        val short = listOfIds(1, 2, 3)
        tracker.onSnapshot(snapshot(short, visibleCount = 3))

        val fired = tracker.onSnapshot(snapshot(short, visibleCount = 3, isScrolling = true))

        assertTrue(fired.isEmpty())
    }

    @Test
    fun sweptToBottomThenSyncPrepends_doesNotFireOnTheNewBookmarks() {
        // The runaway: the old tracker parked its anchor past the end of the list, so the next
        // sync refresh looked like a large scroll up, wiped the double-fire guard, and re-swept
        // the whole list — new bookmarks included.
        val tracker = ScrollActionTracker()
        val short = listOfIds(1, 2, 3)
        tracker.onSnapshot(snapshot(short, visibleCount = 3))
        val swept = tracker.onSnapshot(
            snapshot(short, firstOffset = 40, visibleCount = 3, isScrolling = true)
        )
        assertEquals(listOf(1L, 2L, 3L), ids(swept))

        val grown = listOfIds(90, 91) + short
        assertTrue(tracker.onSnapshot(snapshot(grown, firstOffset = 40, visibleCount = 5)).isEmpty())
        assertTrue(tracker.onSnapshot(snapshot(grown, visibleCount = 5)).isEmpty())
        assertTrue(tracker.onSnapshot(snapshot(grown, visibleCount = 5)).isEmpty())
    }

    @Test
    fun scrollingDownAgainAfterASweep_reArmsTheSweepForNewRows() {
        val tracker = ScrollActionTracker()
        val short = listOfIds(1, 2, 3)
        tracker.onSnapshot(snapshot(short, visibleCount = 3))
        tracker.onSnapshot(snapshot(short, firstOffset = 40, visibleCount = 3, isScrolling = true))

        // New rows arrive above; the user then scrolls down past them deliberately.
        val grown = listOfIds(90, 91) + short
        tracker.onSnapshot(snapshot(grown, firstOffset = 40, visibleCount = 5))
        tracker.onSnapshot(snapshot(grown, visibleCount = 5))

        val fired = tracker.onSnapshot(
            snapshot(grown, firstIndex = 2, visibleCount = 3, isScrolling = true)
        )

        assertEquals(listOf(90L, 91L), ids(fired))
    }

    // --- full reload --------------------------------------------------------------------

    @Test
    fun fullReloadReplacingEveryBookmark_firesNothing() {
        val tracker = ScrollActionTracker()
        tracker.onSnapshot(snapshot(longList, firstIndex = 0))
        tracker.onSnapshot(snapshot(longList, firstIndex = 4, isScrolling = true))

        val other = listOfIds(500, 501, 502, 503, 504, 505)
        assertTrue(tracker.onSnapshot(snapshot(other, firstIndex = 4)).isEmpty())
        assertTrue(tracker.onSnapshot(snapshot(other, firstIndex = 0)).isEmpty())
        assertTrue(tracker.onSnapshot(snapshot(other, firstIndex = 0)).isEmpty())
    }

    @Test
    fun emptyListThenFirstPageArrives_firesNothing() {
        val tracker = ScrollActionTracker()
        assertTrue(tracker.onSnapshot(snapshot(emptyList(), visibleCount = 0)).isEmpty())
        assertTrue(tracker.onSnapshot(snapshot(longList, firstIndex = 0)).isEmpty())
        assertTrue(tracker.onSnapshot(snapshot(longList, firstIndex = 0)).isEmpty())
    }
}
