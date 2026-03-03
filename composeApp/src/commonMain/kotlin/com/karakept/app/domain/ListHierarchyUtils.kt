package com.karakept.app.domain

import com.karakept.api.model.KarakeepList

/**
 * Pure, stateless utility functions for traversing the bookmark-list hierarchy.
 *
 * All functions are free of side-effects and require no dependencies,
 * making them straightforward to unit-test.
 */
object ListHierarchyUtils {

    /**
     * Returns all descendant list IDs of [parentId] via depth-first search.
     *
     * The [visited] parameter guards against cycles in malformed data:
     * if a list ID is encountered a second time the recursion terminates
     * for that branch.
     */
    fun getAllDescendantIds(
        parentId: String,
        allLists: List<KarakeepList>,
        visited: Set<String> = emptySet()
    ): List<String> {
        if (parentId in visited) return emptyList()
        val directChildren = allLists.filter { it.parentId == parentId }.mapNotNull { it.id }
        val allDescendants = directChildren.toMutableList()
        val newVisited = visited + parentId
        for (childId in directChildren) {
            allDescendants.addAll(getAllDescendantIds(childId, allLists, newVisited))
        }
        return allDescendants
    }

    /**
     * Returns the IDs of all ancestors of [listId] (i.e. its parent chain up to
     * the root). Used by the drawer to auto-expand parent nodes when a child
     * list is selected.
     */
    fun getAncestorIds(listId: String, allLists: List<KarakeepList>): Set<String> {
        val ancestors = mutableSetOf<String>()
        var currentId: String? = listId
        while (currentId != null) {
            val list = allLists.find { it.id == currentId }
            val pid = list?.parentId
            if (pid != null) {
                ancestors.add(pid)
                currentId = pid
            } else {
                break
            }
        }
        return ancestors
    }
}
