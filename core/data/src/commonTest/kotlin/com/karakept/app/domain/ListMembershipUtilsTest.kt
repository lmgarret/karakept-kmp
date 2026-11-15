package com.karakept.app.domain

import com.karakept.app.testing.testBookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListMembershipUtilsTest {

    @Test
    fun membershipOf_reportsAllSomeNone() {
        val selection = listOf(setOf("a", "b"), setOf("a"))

        assertEquals(ListMembership.ALL, ListMembershipUtils.membershipOf("a", selection))
        assertEquals(ListMembership.SOME, ListMembershipUtils.membershipOf("b", selection))
        assertEquals(ListMembership.NONE, ListMembershipUtils.membershipOf("c", selection))
        assertEquals(ListMembership.NONE, ListMembershipUtils.membershipOf("a", emptyList()))
    }

    @Test
    fun toggled_flipsAListEveryBookmarkAgreesOn() {
        assertEquals(ListMembership.NONE, ListMembershipUtils.toggled(ListMembership.ALL, ListMembership.ALL))
        assertEquals(ListMembership.ALL, ListMembershipUtils.toggled(ListMembership.NONE, ListMembership.ALL))
        assertEquals(ListMembership.ALL, ListMembershipUtils.toggled(ListMembership.NONE, ListMembership.NONE))
    }

    @Test
    fun toggled_cyclesAMixedListBackToMixed() {
        val initial = ListMembership.SOME
        val first = ListMembershipUtils.toggled(initial, initial)
        val second = ListMembershipUtils.toggled(first, initial)
        val third = ListMembershipUtils.toggled(second, initial)

        assertEquals(listOf(ListMembership.ALL, ListMembership.NONE, ListMembership.SOME), listOf(first, second, third))
    }

    @Test
    fun changeBetween_reportsOnlyListsThatMoved() {
        val initial = mapOf(
            "kept" to ListMembership.ALL,
            "dropped" to ListMembership.ALL,
            "joined" to ListMembership.NONE,
            "mixedLeftAlone" to ListMembership.SOME,
            "mixedCompleted" to ListMembership.SOME,
            "mixedCleared" to ListMembership.SOME
        )
        val edited = initial + mapOf(
            "dropped" to ListMembership.NONE,
            "joined" to ListMembership.ALL,
            "mixedCompleted" to ListMembership.ALL,
            "mixedCleared" to ListMembership.NONE
        )

        val change = ListMembershipUtils.changeBetween(initial, edited)

        assertEquals(setOf("joined", "mixedCompleted"), change.added)
        assertEquals(setOf("dropped", "mixedCleared"), change.removed)
    }

    @Test
    fun changeBetween_isEmptyWhenNothingMoved() {
        val states = mapOf("a" to ListMembership.ALL, "b" to ListMembership.SOME)

        assertTrue(ListMembershipUtils.changeBetween(states, states).isEmpty)
    }

    @Test
    fun operationsFor_keepsOnlyBookmarksWhoseMembershipChanges() {
        val inBoth = testBookmark(1L, listIds = "a,b")
        val inA = testBookmark(2L, listIds = "a")
        val inNone = testBookmark(3L, listIds = "")

        val operations = ListMembershipUtils.operationsFor(
            listOf(inBoth, inA, inNone),
            ListMembershipChange(added = setOf("b"), removed = setOf("a"))
        )

        assertEquals(
            listOf(
                ListMembershipOperation("b", listOf(inA, inNone), add = true),
                ListMembershipOperation("a", listOf(inBoth, inA), add = false)
            ),
            operations
        )
    }

    @Test
    fun operationsFor_dropsAListNoBookmarkChangesIn() {
        val bookmark = testBookmark(1L, listIds = "a")

        val operations = ListMembershipUtils.operationsFor(
            listOf(bookmark),
            ListMembershipChange(added = setOf("a"), removed = setOf("b"))
        )

        assertTrue(operations.isEmpty())
    }

    @Test
    fun describeChange_namesASingleList() {
        val names = mapOf("a" to "Read Later")

        assertEquals(
            "Added to 'Read Later'",
            ListMembershipUtils.describeChange(ListMembershipChange(added = setOf("a")), 1, names::get)
        )
        assertEquals(
            "Removed from 'Read Later'",
            ListMembershipUtils.describeChange(ListMembershipChange(removed = setOf("a")), 1, names::get)
        )
        assertEquals(
            "Added 3 bookmarks to 'Read Later'",
            ListMembershipUtils.describeChange(ListMembershipChange(added = setOf("a")), 3, names::get)
        )
    }

    @Test
    fun describeChange_fallsBackWhenSeveralListsChange() {
        val change = ListMembershipChange(added = setOf("a"), removed = setOf("b"))

        assertEquals("Lists updated", ListMembershipUtils.describeChange(change, 1) { null })
        assertEquals("Lists updated for 2 bookmarks", ListMembershipUtils.describeChange(change, 2) { null })
    }
}
