package com.karakept.app.domain

import com.karakept.api.model.KarakeepList
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [ListHierarchyUtils].
 */
class ListHierarchyUtilsTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a mock [KarakeepList] with only [id] and [parentId] configured.
     * Using mockk because the generated KarakeepList constructor requires many
     * parameters that are irrelevant for hierarchy traversal.
     */
    private fun makeList(id: String, parentId: String? = null): KarakeepList =
        mockk<KarakeepList>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.parentId } returns parentId
        }

    // -------------------------------------------------------------------------
    // getAllDescendantIds
    // -------------------------------------------------------------------------

    @Test
    fun getAllDescendantIds_leafNode_returnsEmpty() {
        val lists = listOf(makeList("a"))
        assertEquals(emptyList(), ListHierarchyUtils.getAllDescendantIds("a", lists))
    }

    @Test
    fun getAllDescendantIds_singleChild_returnsChild() {
        val lists = listOf(
            makeList("parent"),
            makeList("child", parentId = "parent")
        )
        assertEquals(listOf("child"), ListHierarchyUtils.getAllDescendantIds("parent", lists))
    }

    @Test
    fun getAllDescendantIds_multipleChildren_returnsAllDirectChildren() {
        val lists = listOf(
            makeList("root"),
            makeList("child-1", parentId = "root"),
            makeList("child-2", parentId = "root"),
            makeList("child-3", parentId = "root")
        )
        val result = ListHierarchyUtils.getAllDescendantIds("root", lists).toSet()
        assertEquals(setOf("child-1", "child-2", "child-3"), result)
    }

    @Test
    fun getAllDescendantIds_nestedHierarchy_returnsAllDescendants() {
        val lists = listOf(
            makeList("root"),
            makeList("level1", parentId = "root"),
            makeList("level2", parentId = "level1"),
            makeList("level3", parentId = "level2")
        )
        val result = ListHierarchyUtils.getAllDescendantIds("root", lists).toSet()
        assertEquals(setOf("level1", "level2", "level3"), result)
    }

    @Test
    fun getAllDescendantIds_multipleChildrenWithGrandchildren() {
        val lists = listOf(
            makeList("root"),
            makeList("child-a", parentId = "root"),
            makeList("child-b", parentId = "root"),
            makeList("grandchild-a1", parentId = "child-a"),
            makeList("grandchild-b1", parentId = "child-b"),
            makeList("grandchild-b2", parentId = "child-b")
        )
        val result = ListHierarchyUtils.getAllDescendantIds("root", lists).toSet()
        assertEquals(
            setOf("child-a", "child-b", "grandchild-a1", "grandchild-b1", "grandchild-b2"),
            result
        )
    }

    @Test
    fun getAllDescendantIds_unknownParent_returnsEmpty() {
        val lists = listOf(makeList("a"), makeList("b"))
        assertEquals(emptyList(), ListHierarchyUtils.getAllDescendantIds("unknown", lists))
    }

    @Test
    fun getAllDescendantIds_emptyList_returnsEmpty() {
        assertEquals(emptyList(), ListHierarchyUtils.getAllDescendantIds("root", emptyList()))
    }

    @Test
    fun getAllDescendantIds_cycle_doesNotInfiniteLoop() {
        // a → b → a (cycle)
        val lists = listOf(
            makeList("a", parentId = "b"),
            makeList("b", parentId = "a")
        )
        // Should terminate and return non-infinite result
        val result = ListHierarchyUtils.getAllDescendantIds("a", lists)
        assertTrue(result.size < 100, "Expected finite result, got ${result.size} items")
    }

    // -------------------------------------------------------------------------
    // getAncestorIds
    // -------------------------------------------------------------------------

    @Test
    fun getAncestorIds_rootNode_returnsEmpty() {
        val lists = listOf(makeList("root"))
        assertEquals(emptySet(), ListHierarchyUtils.getAncestorIds("root", lists))
    }

    @Test
    fun getAncestorIds_singleLevel_returnsParent() {
        val lists = listOf(
            makeList("root"),
            makeList("child", parentId = "root")
        )
        assertEquals(setOf("root"), ListHierarchyUtils.getAncestorIds("child", lists))
    }

    @Test
    fun getAncestorIds_multipleAncestors_returnsFullChain() {
        val lists = listOf(
            makeList("root"),
            makeList("level1", parentId = "root"),
            makeList("level2", parentId = "level1"),
            makeList("level3", parentId = "level2")
        )
        assertEquals(
            setOf("root", "level1", "level2"),
            ListHierarchyUtils.getAncestorIds("level3", lists)
        )
    }

    @Test
    fun getAncestorIds_unknownList_returnsEmpty() {
        val lists = listOf(makeList("root"))
        assertEquals(emptySet(), ListHierarchyUtils.getAncestorIds("does-not-exist", lists))
    }

    @Test
    fun getAncestorIds_emptyList_returnsEmpty() {
        assertEquals(emptySet(), ListHierarchyUtils.getAncestorIds("any", emptyList()))
    }

    @Test
    fun getAncestorIds_doesNotIncludeSiblings() {
        val lists = listOf(
            makeList("root"),
            makeList("sibling-1", parentId = "root"),
            makeList("sibling-2", parentId = "root")
        )
        val result = ListHierarchyUtils.getAncestorIds("sibling-1", lists)
        assertEquals(setOf("root"), result)
        assertTrue("sibling-2" !in result)
    }

    // -------------------------------------------------------------------------
    // buildListHierarchy
    // -------------------------------------------------------------------------

    private fun makeNamedList(id: String, name: String, parentId: String? = null): KarakeepList =
        mockk<KarakeepList>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns name
            every { this@mockk.parentId } returns parentId
        }

    @Test
    fun buildListHierarchy_ordersParentsBeforeChildrenAndSortsAlphabetically() {
        val lists = listOf(
            makeNamedList("b", "Beta"),
            makeNamedList("a", "Alpha"),
            makeNamedList("a-child", "Child", parentId = "a")
        )
        val result = ListHierarchyUtils.buildListHierarchy(lists)
        assertEquals(
            listOf("a" to 0, "a-child" to 1, "b" to 0),
            result.map { (it.first.id ?: "") to it.second }
        )
    }

    @Test
    fun buildListHierarchy_appendsOrphansAtRootLevel() {
        val lists = listOf(
            makeNamedList("root", "Root"),
            makeNamedList("orphan", "Orphan", parentId = "missing-parent")
        )
        val result = ListHierarchyUtils.buildListHierarchy(lists)
        assertEquals(setOf("root", "orphan"), result.map { it.first.id }.toSet())
        assertTrue(result.all { it.second == 0 })
    }

    // -------------------------------------------------------------------------
    // filterExpandedHierarchy
    // -------------------------------------------------------------------------

    @Test
    fun filterExpandedHierarchy_hidesChildrenOfCollapsedParents() {
        val lists = listOf(
            makeNamedList("a", "Alpha"),
            makeNamedList("a-child", "Child", parentId = "a")
        )
        val hierarchy = ListHierarchyUtils.buildListHierarchy(lists)

        val collapsed = ListHierarchyUtils.filterExpandedHierarchy(hierarchy, emptySet())
        assertEquals(listOf("a"), collapsed.map { it.first.id })

        val expanded = ListHierarchyUtils.filterExpandedHierarchy(hierarchy, setOf("a"))
        assertEquals(listOf("a", "a-child"), expanded.map { it.first.id })
    }

    // -------------------------------------------------------------------------
    // listHasChildren
    // -------------------------------------------------------------------------

    @Test
    fun listHasChildren_returnsTrueOnlyWhenDirectChildExists() {
        val lists = listOf(
            makeList("parent"),
            makeList("child", parentId = "parent"),
            makeList("leaf")
        )
        assertTrue(ListHierarchyUtils.listHasChildren("parent", lists))
        assertTrue(!ListHierarchyUtils.listHasChildren("leaf", lists))
    }
}
