package com.karakept.app.ui.screens.main

import com.karakept.app.data.local.entity.BookmarkEntity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [ItemAnimationGate], which decides whether the bookmark LazyColumn should
 * animate a dataset change.
 *
 * Regression guard for the flash seen when switching lists: animating a wholesale dataset
 * swap draws the outgoing list over the incoming one. Surgical changes — a reconciliation
 * removal, a sync update, a load-more append — must keep animating.
 */
class ItemAnimationGateTest {

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

    private fun listOfIds(ids: Iterable<Long>) = ids.map { bookmark(it) }

    @Test
    fun switchingToAnUnrelatedList_disablesAnimations() {
        val listA = listOfIds(1L..20L)
        val listB = listOfIds(100L..119L)
        val gate = ItemAnimationGate(viewWindow(listA))

        assertFalse(gate.update(viewWindow(listB)))
    }

    @Test
    fun sameWindowInstance_keepsThePreviousDecision() {
        val listA = listOfIds(1L..20L)
        val windowB = viewWindow(listOfIds(100L..119L))
        val gate = ItemAnimationGate(viewWindow(listA))

        assertFalse(gate.update(windowB))
        assertFalse(gate.update(windowB))
    }

    @Test
    fun loadMoreAppend_keepsAnimations() {
        val page0 = listOfIds(1L..20L)
        val gate = ItemAnimationGate(viewWindow(page0))

        assertTrue(gate.update(viewWindow(listOfIds(1L..40L))))
    }

    @Test
    fun reconciliationRemoval_keepsAnimations() {
        val loaded = listOfIds(1L..20L)
        val gate = ItemAnimationGate(viewWindow(loaded))

        assertTrue(gate.update(viewWindow(listOfIds((1L..20L).filter { it != 7L }))))
    }

    @Test
    fun syncPrependingNewBookmarks_disablesAnimations() {
        // Prepended rows displace everything below them, and the viewport is held at the top
        // for a user who has not scrolled — animating that springs the whole visible list down
        // from the top edge, which reads as the order shuffling and settling back.
        val loaded = listOfIds(1L..20L)
        val gate = ItemAnimationGate(viewWindow(loaded))

        assertFalse(gate.update(viewWindow(listOfIds(90L..94L) + loaded)))
    }

    @Test
    fun repeatedSyncPrepends_stayDisabled() {
        // A sync commits page by page, so prepends keep landing while the user watches.
        var current = listOfIds(1L..20L)
        val gate = ItemAnimationGate(viewWindow(current))

        repeat(3) { round ->
            current = listOfIds(listOf(90L + round)) + current
            assertFalse(gate.update(viewWindow(current)))
        }
    }

    @Test
    fun gateRecoversAfterAPrepend() {
        val loaded = listOfIds(1L..20L)
        val gate = ItemAnimationGate(viewWindow(loaded))
        assertFalse(gate.update(viewWindow(listOfIds(90L..94L) + loaded)))

        // A later removal within the same list animates again.
        assertTrue(gate.update(viewWindow(listOfIds(90L..94L) + listOfIds((1L..20L).filter { it != 7L }))))
    }

    @Test
    fun removalAtTheHead_keepsAnimations() {
        // The previously-first row is gone rather than displaced — nothing shifts down.
        val loaded = listOfIds(1L..20L)
        val gate = ItemAnimationGate(viewWindow(loaded))

        assertTrue(gate.update(viewWindow(listOfIds(2L..20L))))
    }

    @Test
    fun firstLoadIntoAnEmptyList_keepsAnimations() {
        val gate = ItemAnimationGate(viewWindow(emptyList()))

        assertTrue(gate.update(viewWindow(listOfIds(1L..20L))))
    }

    @Test
    fun switchingToAnEmptyList_keepsAnimations() {
        // Nothing is drawn over anything when the incoming list is empty.
        val gate = ItemAnimationGate(viewWindow(listOfIds(1L..20L)))

        assertTrue(gate.update(viewWindow(emptyList())))
    }

    @Test
    fun gateRecoversAfterASwap() {
        val listA = listOfIds(1L..20L)
        val listB = listOfIds(100L..119L)
        val gate = ItemAnimationGate(viewWindow(listA))

        assertFalse(gate.update(viewWindow(listB)))
        // A surgical change within list B animates again.
        assertTrue(gate.update(viewWindow(listOfIds((100L..119L).filter { it != 105L }))))
    }

    @Test
    fun listsOverlappingByOnlyAFewBookmarks_disablesAnimations() {
        // A bookmark can belong to several lists, so two different views can share a handful
        // of items — that is still a swap, not a surgical change.
        val listA = listOfIds(1L..20L)
        val listB = listOfIds(listOf(3L, 11L) + (100L..117L))
        val gate = ItemAnimationGate(viewWindow(listA))

        assertFalse(gate.update(viewWindow(listB)))
    }

    @Test
    fun scrollingBackUpThroughASparseView_isNotAPrepend() {
        // The window slides as the user scrolls, so the row at the head of the rows in hand is a
        // different row each time. Asked of those rows, scrolling back up put the previous head
        // further down the list and read as an insertion above it — which switched the item
        // animations off for the rest of the session. Asked of slots, nothing moved.
        val view = listOfIds(1L..400L)
        val gate = ItemAnimationGate(viewWindow(view, loadedPages = setOf(2, 3, 4)))

        assertTrue(gate.update(viewWindow(view, loadedPages = setOf(1, 2, 3))))
        assertTrue(gate.update(viewWindow(view, loadedPages = setOf(0, 1, 2))))
    }

    @Test
    fun aPrependIntoASparseView_isStillAPrepend() {
        val view = listOfIds(1L..400L)
        val gate = ItemAnimationGate(viewWindow(view, loadedPages = setOf(0, 1)))

        assertFalse(
            gate.update(viewWindow(listOfIds(900L..904L) + view, loadedPages = setOf(0, 1)))
        )
    }
}
