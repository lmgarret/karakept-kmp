package com.karakept.app.ui.utils

import com.karakept.api.model.KarakeepList

/**
 * Builds a hierarchical list structure from a flat list.
 * Returns (KarakeepList, depth) pairs in display order: parents before children,
 * sorted alphabetically at each level.
 *
 * Use this function everywhere lists need to be displayed with hierarchy and sorting —
 * in the navigation drawer, list picker, filter panel, sync settings, etc.
 *
 * @param lists Flat list of all KarakeepList items
 * @return Ordered list of (list, depth) pairs for hierarchical display
 */
fun buildListHierarchy(lists: List<KarakeepList>): List<Pair<KarakeepList, Int>> {
    val result = mutableListOf<Pair<KarakeepList, Int>>()
    val grouped = lists.groupBy { it.parentId }
    val visited = mutableSetOf<String>() // Prevent circular references

    fun recurse(parentId: String?, depth: Int) {
        if (depth > 10) return // Guard against infinite recursion
        val children = (grouped[parentId] ?: return).sortedBy { it.name?.lowercase() ?: "" }
        children.forEach { child ->
            val childId = child.id ?: ""
            if (!visited.contains(childId)) {
                visited.add(childId)
                result.add(child to depth)
                recurse(childId, depth + 1)
            }
        }
    }

    recurse(null, 0)

    // Append orphans (lists whose parent is not present in the list) at root level
    val processed = result.map { it.first.id ?: "" }.toSet()
    lists.filter { (it.id ?: "") !in processed }.forEach { list ->
        result.add(list to 0)
    }

    return result
}

/**
 * Filters a hierarchy to only show visible (expanded) branches.
 * A list is visible only if ALL its ancestors are expanded.
 *
 * Use this in combination with [buildListHierarchy] when showing a collapsible hierarchy
 * (e.g. the navigation drawer).
 *
 * @param hierarchy Result of [buildListHierarchy]
 * @param expandedIds Set of list IDs whose children should be visible
 * @return Filtered list showing only items whose full ancestor chain is expanded
 */
fun filterExpandedHierarchy(
    hierarchy: List<Pair<KarakeepList, Int>>,
    expandedIds: Set<String>
): List<Pair<KarakeepList, Int>> {
    val result = mutableListOf<Pair<KarakeepList, Int>>()
    val listMap = hierarchy.associateBy { it.first.id ?: "" }

    hierarchy.forEach { (list, depth) ->
        val shouldShow = if (depth == 0) {
            true // Root items are always visible
        } else {
            var allAncestorsExpanded = true
            var currentParentId = list.parentId

            while (currentParentId != null && allAncestorsExpanded) {
                if (!expandedIds.contains(currentParentId)) {
                    allAncestorsExpanded = false
                }
                currentParentId = listMap[currentParentId]?.first?.parentId
            }

            allAncestorsExpanded
        }

        if (shouldShow) {
            result.add(list to depth)
        }
    }

    return result
}

/**
 * Returns true if the given list has any direct children in [allLists].
 */
fun listHasChildren(listId: String, allLists: List<KarakeepList>): Boolean {
    return allLists.any { it.parentId == listId }
}
