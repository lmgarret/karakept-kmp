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
        remoteId = "orig-$remoteId",
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
            window = viewWindow(listOfIds(1, 2, 3))
        )
        assertNull(result)
    }

    @Test
    fun whileScrolling_returnsNull() {
        // Don't fight an active user scroll gesture.
        val result = resolveAnchorScrollTarget(
            anchorKey = "orig-10",
            isScrolling = true,
            versionChanged = false,
            currentFirstIndex = 5,
            window = viewWindow(listOfIds(1, 10, 2))
        )
        assertNull(result)
    }

    @Test
    fun versionChanged_returnsNull() {
        // A full reload (sync / filter change) bumps the list version and wants its
        // own scroll behaviour (e.g. scroll-to-top) — never re-pin in that case.
        val newList = (0L..49L).filter { it != 5L }.map { bookmark(it) }
        val result = resolveAnchorScrollTarget(
            anchorKey = "orig-30",
            isScrolling = false,
            versionChanged = true,
            currentFirstIndex = 30,
            window = viewWindow(newList)
        )
        assertNull(result)
    }

    @Test
    fun anchorRemoved_returnsNull() {
        // The anchor bookmark itself was the removed one — leave position to Compose.
        val result = resolveAnchorScrollTarget(
            anchorKey = "orig-99",
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 3,
            window = viewWindow(listOfIds(1, 2, 3, 4))
        )
        assertNull(result)
    }

    @Test
    fun anchorAlreadyFirstVisible_returnsNull() {
        // Compose already kept the anchor at the top (or nothing shifted) — no-op.
        val newList = listOfIds(10, 20, 30)
        val result = resolveAnchorScrollTarget(
            anchorKey = "orig-10",
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 0,
            window = viewWindow(newList)
        )
        assertNull(result)
    }

    @Test
    fun itemRemovedAboveAnchor_anchorNotYetFollowed_returnsNewIndex() {
        // User was at index 30 (anchor key=30). An item above the viewport was removed,
        // so the anchor now lives at index 29, but firstVisibleItemIndex still reads 30
        // (Compose didn't re-anchor). We must re-pin to 29.
        val newList = (0L..49L).filter { it != 5L }.map { bookmark(it) } // removed id 5
        val anchorNewIndex = newList.indexOfFirst { it.remoteId == "orig-30" } // 29
        val result = resolveAnchorScrollTarget(
            anchorKey = "orig-30",
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 30,
            window = viewWindow(newList)
        )
        assertEquals(anchorNewIndex, result)
        assertEquals(29, result)
    }

    @Test
    fun multipleItemsRemovedAboveAnchor_returnsCorrectNewIndex() {
        // Remove ids 1,2,3 (all above anchor 30). Anchor shifts up by 3 → index 27.
        val newList = (0L..49L).filter { it !in setOf(1L, 2L, 3L) }.map { bookmark(it) }
        val result = resolveAnchorScrollTarget(
            anchorKey = "orig-30",
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 30,
            window = viewWindow(newList)
        )
        assertEquals(27, result)
    }

    @Test
    fun itemAppendedBelow_anchorIndexUnchanged_returnsNull() {
        // load-more appended items at the bottom; the top anchor index is unchanged.
        val newList = listOfIds(10, 20, 30, 40, 50, 60)
        val result = resolveAnchorScrollTarget(
            anchorKey = "orig-10",
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 0,
            window = viewWindow(newList)
        )
        assertNull(result)
    }

    /**
     * The window a view of [bookmarks] produces, with its generation tied to [listVersion].
     *
     * They are the same number in production — the model stamps the list version onto the window
     * it publishes — and the anchor uses the pair to tell a reload from a surgical change.
     */
    private fun snapshot(
        bookmarks: List<BookmarkEntity>,
        firstIndex: Int = 0,
        firstOffset: Int = 0,
        isScrolling: Boolean = false,
        listVersion: Int = 0,
        loadedPages: Set<Int>? = null
    ) = AnchorSnapshot(
        viewWindow(bookmarks, generation = listVersion, loadedPages = loadedPages),
        firstIndex,
        firstOffset,
        isScrolling,
        listVersion
    )

    @Test
    fun reloadAnnouncedBeforeDatasetArrives_doesNotRePinToThePreviousListsAnchor() {
        // The version bump and the dataset swap travel through different flows, so a list
        // switch normally lands as two snapshots. If the bump is forgotten by the time the
        // swap shows up, the swap looks surgical and the viewport gets re-pinned to a
        // bookmark carried over from the list the user just left.
        val listA = listOfIds(1, 2, 3, 4, 5)
        val listB = listOfIds(9, 8, 3, 7, 6)  // id 3 exists in both lists
        val anchor = ListScrollAnchorState(initialVersion = 0)

        // User is parked on bookmark 3 (index 2) in list A.
        anchor.onSnapshot(snapshot(listA, firstIndex = 2, listVersion = 0))
        // The reload is announced first, still showing list A.
        assertNull(anchor.onSnapshot(snapshot(listA, firstIndex = 2, listVersion = 1)))
        // The new dataset arrives in a later snapshot.
        assertNull(anchor.onSnapshot(snapshot(listB, firstIndex = 2, listVersion = 1)))
    }

    @Test
    fun reloadLatchIsConsumedByTheSwap_soLaterSurgicalChangesStillRePin() {
        val listA = listOfIds(1, 2, 3, 4, 5)
        val listB = listOfIds(9, 8, 3, 7, 6)
        val anchor = ListScrollAnchorState(initialVersion = 0)

        // Parked on bookmark 3 (index 2) of list A, then a reload swaps in list B.
        anchor.onSnapshot(snapshot(listA, firstIndex = 2, firstOffset = 12, listVersion = 0))
        anchor.onSnapshot(snapshot(listA, firstIndex = 2, firstOffset = 12, listVersion = 1))
        assertNull(anchor.onSnapshot(snapshot(listB, firstIndex = 0, listVersion = 1)))

        // The user scrolls to bookmark 3 (index 2) of the new list — the anchor re-records.
        anchor.onSnapshot(snapshot(listB, firstIndex = 2, firstOffset = 12, listVersion = 1))

        // An item above the anchor is then removed: a surgical change, still compensated.
        val afterRemoval = listOfIds(9, 3, 7, 6)
        assertEquals(
            AnchorScrollTarget(index = 1, offset = 12),
            anchor.onSnapshot(snapshot(afterRemoval, firstIndex = 2, listVersion = 1))
        )
    }

    @Test
    fun firstSnapshotNeverRePins() {
        val anchor = ListScrollAnchorState(initialVersion = 3)
        assertNull(anchor.onSnapshot(snapshot(listOfIds(1, 2, 3), firstIndex = 1, listVersion = 3)))
    }

    @Test
    fun parkedAtTheTop_prependHoldsTheTopInsteadOfPinningTheOldAnchor() {
        // Someone at the very top has not scrolled, so a sync's new bookmarks belong on
        // screen. Pinning to the old anchor pushes them above the viewport and raises a
        // "N new" pill for a user who is already looking at the top of the list.
        val anchor = ListScrollAnchorState(initialVersion = 0)
        val before = listOfIds(10, 11, 12)
        anchor.onSnapshot(snapshot(before, firstIndex = 0, firstOffset = 0))

        val after = listOfIds(20, 21, 10, 11, 12)
        assertEquals(
            AnchorScrollTarget(index = 0, offset = 0),
            anchor.onSnapshot(snapshot(after, firstIndex = 0))
        )
    }

    @Test
    fun parkedJustBelowTheTop_stillRePinsToTheAnchor() {
        // Only the absolute top is special — a user who scrolled keeps their position.
        val anchor = ListScrollAnchorState(initialVersion = 0)
        val before = listOfIds(10, 11, 12)
        anchor.onSnapshot(snapshot(before, firstIndex = 1, firstOffset = 8))

        val after = listOfIds(20, 21, 10, 11, 12)
        assertEquals(
            AnchorScrollTarget(index = 3, offset = 8),
            anchor.onSnapshot(snapshot(after, firstIndex = 1))
        )
    }

    @Test
    fun atTopButPartiallyScrolledWithinTheFirstItem_stillRePinsToTheAnchor() {
        val anchor = ListScrollAnchorState(initialVersion = 0)
        val before = listOfIds(10, 11, 12)
        anchor.onSnapshot(snapshot(before, firstIndex = 0, firstOffset = 30))

        val after = listOfIds(20, 10, 11, 12)
        assertEquals(
            AnchorScrollTarget(index = 1, offset = 30),
            anchor.onSnapshot(snapshot(after, firstIndex = 0))
        )
    }

    @Test
    fun atTopDuringAFullReload_leavesTheReloadsOwnScrollAlone() {
        val anchor = ListScrollAnchorState(initialVersion = 0)
        val before = listOfIds(10, 11, 12)
        anchor.onSnapshot(snapshot(before, firstIndex = 0, listVersion = 0))
        anchor.onSnapshot(snapshot(before, firstIndex = 0, listVersion = 1))

        val other = listOfIds(50, 51, 52)
        assertNull(anchor.onSnapshot(snapshot(other, firstIndex = 0, listVersion = 1)))
    }

    @Test
    fun deepInASparseView_theAnchorIsTheRowAtThatSlot() {
        // The rows in hand are a few pages of a long view, so slot 260 is somewhere in the
        // middle of them and not their 260th entry — of which there is none. Read out of the
        // rows this returned null, and the viewport went unanchored for the whole list.
        val view = listOfIds(*(1L..400L).toList().toLongArray())
        val anchor = ListScrollAnchorState(initialVersion = 0)
        anchor.onSnapshot(
            snapshot(view, firstIndex = 260, firstOffset = 7, loadedPages = setOf(4, 5, 6))
        )

        // A row above the viewport is removed: everything below it moves up one slot.
        val afterRemoval = view.filterNot { it.remoteId == "orig-3" }
        assertEquals(
            AnchorScrollTarget(index = 259, offset = 7),
            anchor.onSnapshot(
                snapshot(afterRemoval, firstIndex = 260, loadedPages = setOf(4, 5, 6))
            )
        )
    }

    @Test
    fun aRePinTargetIsASlot_notAPositionAmongTheRowsInHand() {
        // The whole failure in one assertion: found among the rows in hand, the anchor's
        // position is bounded by how many are loaded, so re-pinning to it could only ever land
        // the user back near the top of the view.
        val view = listOfIds(*(1L..400L).toList().toLongArray())
        val loaded = setOf(4, 5, 6)
        val target = resolveAnchorScrollTarget(
            anchorKey = "orig-261",
            isScrolling = false,
            versionChanged = false,
            currentFirstIndex = 999,
            window = viewWindow(view, loadedPages = loaded)
        )
        assertEquals(260, target)
    }

    @Test
    fun aPageBeingDroppedIsNotAMutation() {
        // A sync invalidates every page on every write, so the window is rebuilt constantly.
        // None of that moves a row: re-pinning on it fought the user's own scrolling.
        val view = listOfIds(*(1L..400L).toList().toLongArray())
        val anchor = ListScrollAnchorState(initialVersion = 0)
        anchor.onSnapshot(snapshot(view, firstIndex = 260, loadedPages = setOf(4, 5, 6)))

        assertNull(anchor.onSnapshot(snapshot(view, firstIndex = 260, loadedPages = setOf(5))))
        assertNull(
            anchor.onSnapshot(snapshot(view, firstIndex = 260, loadedPages = setOf(4, 5, 6)))
        )
    }
}
