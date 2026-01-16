package com.karakept.app.data.model

import com.karakept.api.model.KarakeepList

/**
 * Configuration for syncing bookmark content based on lists.
 * Supports three-state sync: unchecked, parent only, or parent with all children.
 */
data class ListSyncConfig(
    val selectedLists: Set<String>,
    val withChildrenMode: Set<String>
) {
    /**
     * Computes the effective set of list IDs that should be synced,
     * including all descendants (children, grandchildren, etc.) of lists marked with "sync with children" mode.
     */
    fun getVisibleLists(allLists: List<KarakeepList>): List<KarakeepList> {
        return allLists.filter { list ->
            // If it's a root list, it's visible if it's selected
            if (list.parentId == null) {
                selectedLists.contains(list.id)
            } else {
                // If it's a child list, it's visible if it's selected AND its parent is visible
                val pid = list.parentId
                pid != null && selectedLists.contains(list.id ?: "") && isParentVisible(pid, allLists)
            }
        }
    }

    private fun isParentVisible(parentId: String, allLists: List<KarakeepList>): Boolean {
        val parent = allLists.find { (it.id ?: "") == parentId } ?: return false
        val pid = parent.parentId
        if (pid == null) {
            return selectedLists.contains(parent.id ?: "")
        }
        return selectedLists.contains(parent.id ?: "") && isParentVisible(pid, allLists)
    }

    /**
     * Computes the effective set of list IDs that should be synced,
     * including all descendants (children, grandchildren, etc.) of lists marked with "sync with children" mode.
     */
    fun getEffectiveSyncLists(allLists: List<KarakeepList>): Set<String> {
        val effective = mutableSetOf<String>()
        effective.addAll(selectedLists)

        // Add all descendants recursively for lists in "with children" mode
        withChildrenMode.forEach { parentId ->
            addAllDescendants(parentId, allLists, effective)
        }

        return effective
    }

    /**
     * Recursively adds all descendants of a parent list to the effective set.
     */
    private fun addAllDescendants(parentId: String, allLists: List<KarakeepList>, effective: MutableSet<String>) {
        val children = allLists.filter { it.parentId == parentId }
        children.forEach { child ->
            val childId = child.id ?: ""
            effective.add(childId)
            // Recursively add grandchildren, great-grandchildren, etc.
            addAllDescendants(childId, allLists, effective)
        }
    }

    /**
     * Gets the checkbox state for a given list ID.
     */
    fun getCheckboxState(listId: String): CheckboxState {
        return when {
            listId !in selectedLists -> CheckboxState.UNCHECKED
            listId in withChildrenMode -> CheckboxState.CHECKED_WITH_CHILDREN
            else -> CheckboxState.CHECKED_PARENT_ONLY
        }
    }

    /**
     * Checks if any ancestor (parent, grandparent, etc.) is in "with children" mode.
     * Used to determine if child checkboxes should be disabled.
     */
    fun isParentInWithChildrenMode(parentId: String?, allLists: List<KarakeepList>): Boolean {
        if (parentId == null) return false

        // Check if immediate parent is in "with children" mode
        if (parentId in withChildrenMode) return true

        // Recursively check grandparents
        val parent = allLists.find { (it.id ?: "") == parentId }
        return parent?.parentId?.let { isParentInWithChildrenMode(it, allLists) } ?: false
    }
}

/**
 * Represents the three possible states for list sync checkboxes.
 */
enum class CheckboxState {
    UNCHECKED,
    CHECKED_PARENT_ONLY,
    CHECKED_WITH_CHILDREN;

    /**
     * Cycles to the next state: Unchecked → Parent Only → With Children → Unchecked
     */
    fun next(): CheckboxState {
        return when (this) {
            UNCHECKED -> CHECKED_PARENT_ONLY
            CHECKED_PARENT_ONLY -> CHECKED_WITH_CHILDREN
            CHECKED_WITH_CHILDREN -> UNCHECKED
        }
    }
}
