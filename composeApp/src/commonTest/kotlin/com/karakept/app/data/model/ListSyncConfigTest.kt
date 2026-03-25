package com.karakept.app.data.model

import com.karakept.api.model.KarakeepList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [ListSyncConfig] and [CheckboxState].
 */
class ListSyncConfigTest {

    private fun makeList(id: String, parentId: String? = null): KarakeepList =
        KarakeepList(id = id, name = "List $id", parentId = parentId)

    // -- CheckboxState tests --

    @Test
    fun checkboxState_next_uncheckedToParentOnly() {
        assertEquals(CheckboxState.CHECKED_PARENT_ONLY, CheckboxState.UNCHECKED.next())
    }

    @Test
    fun checkboxState_next_parentOnlyToWithChildren() {
        assertEquals(CheckboxState.CHECKED_WITH_CHILDREN, CheckboxState.CHECKED_PARENT_ONLY.next())
    }

    @Test
    fun checkboxState_next_withChildrenToUnchecked() {
        assertEquals(CheckboxState.UNCHECKED, CheckboxState.CHECKED_WITH_CHILDREN.next())
    }

    // -- ListSyncConfig.getCheckboxState tests --

    @Test
    fun getCheckboxState_selectedWithChildren_returnsCheckedWithChildren() {
        val config = ListSyncConfig(
            selectedLists = setOf("A"),
            withChildrenMode = setOf("A")
        )
        assertEquals(CheckboxState.CHECKED_WITH_CHILDREN, config.getCheckboxState("A"))
    }

    @Test
    fun getCheckboxState_selectedOnly_returnsCheckedParentOnly() {
        val config = ListSyncConfig(
            selectedLists = setOf("A"),
            withChildrenMode = emptySet()
        )
        assertEquals(CheckboxState.CHECKED_PARENT_ONLY, config.getCheckboxState("A"))
    }

    @Test
    fun getCheckboxState_notSelected_returnsUnchecked() {
        val config = ListSyncConfig(
            selectedLists = emptySet(),
            withChildrenMode = emptySet()
        )
        assertEquals(CheckboxState.UNCHECKED, config.getCheckboxState("unknown"))
    }

    // -- ListSyncConfig.getEffectiveSyncLists tests --

    @Test
    fun getEffectiveSyncLists_noChildren_returnsSameAsSelected() {
        val config = ListSyncConfig(
            selectedLists = setOf("A"),
            withChildrenMode = emptySet()
        )
        val allLists = listOf(makeList("A"))
        val effective = config.getEffectiveSyncLists(allLists)
        assertEquals(setOf("A"), effective)
    }

    @Test
    fun getEffectiveSyncLists_withChildrenMode_includesDescendants() {
        val config = ListSyncConfig(
            selectedLists = setOf("parent"),
            withChildrenMode = setOf("parent")
        )
        val allLists = listOf(
            makeList("parent"),
            makeList("child1", parentId = "parent"),
            makeList("child2", parentId = "parent"),
            makeList("grandchild", parentId = "child1")
        )
        val effective = config.getEffectiveSyncLists(allLists)
        assertTrue(effective.containsAll(setOf("parent", "child1", "child2", "grandchild")),
            "Should include parent and all descendants, got: $effective")
    }

    // -- ListSyncConfig.isParentInWithChildrenMode tests --

    @Test
    fun isParentInWithChildrenMode_parentIsInMode_returnsTrue() {
        val config = ListSyncConfig(
            selectedLists = setOf("parent"),
            withChildrenMode = setOf("parent")
        )
        val allLists = listOf(
            makeList("parent"),
            makeList("child", parentId = "parent")
        )
        assertTrue(config.isParentInWithChildrenMode("parent", allLists))
    }

    @Test
    fun isParentInWithChildrenMode_grandparentIsInMode_returnsTrue() {
        val config = ListSyncConfig(
            selectedLists = setOf("grandparent"),
            withChildrenMode = setOf("grandparent")
        )
        val allLists = listOf(
            makeList("grandparent"),
            makeList("parent", parentId = "grandparent"),
            makeList("child", parentId = "parent")
        )
        // Check from "parent" perspective -- its ancestor "grandparent" is in withChildrenMode
        assertTrue(config.isParentInWithChildrenMode("parent", allLists))
    }

    @Test
    fun isParentInWithChildrenMode_noAncestorInMode_returnsFalse() {
        val config = ListSyncConfig(
            selectedLists = setOf("A"),
            withChildrenMode = emptySet()
        )
        val allLists = listOf(makeList("A"))
        assertFalse(config.isParentInWithChildrenMode("A", allLists))
    }

    // -- ListSyncConfig.getVisibleLists tests --

    @Test
    fun getVisibleLists_rootListSelected_isVisible() {
        val config = ListSyncConfig(
            selectedLists = setOf("A"),
            withChildrenMode = emptySet()
        )
        val allLists = listOf(makeList("A"))
        val visible = config.getVisibleLists(allLists)
        assertEquals(1, visible.size, "Selected root list should be visible")
        assertEquals("A", visible.first().id)
    }

    @Test
    fun getVisibleLists_childSelectedButParentNot_isNotVisible() {
        val config = ListSyncConfig(
            selectedLists = setOf("child"),
            withChildrenMode = emptySet()
        )
        val allLists = listOf(
            makeList("parent"),
            makeList("child", parentId = "parent")
        )
        val visible = config.getVisibleLists(allLists)
        assertTrue(visible.isEmpty(),
            "Child should not be visible when parent is not selected, got: ${visible.map { it.id }}")
    }
}
