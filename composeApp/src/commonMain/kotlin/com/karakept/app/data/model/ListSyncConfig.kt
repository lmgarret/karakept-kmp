package com.karakept.app.data.model

import com.karakept.app.data.remote.model.ListDto

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
    fun getEffectiveSyncLists(allLists: List<ListDto>): Set<String> {
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
    private fun addAllDescendants(parentId: String, allLists: List<ListDto>, effective: MutableSet<String>) {
        val children = allLists.filter { it.parentId == parentId }
        children.forEach { child ->
            effective.add(child.id)
            // Recursively add grandchildren, great-grandchildren, etc.
            addAllDescendants(child.id, allLists, effective)
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
    fun isParentInWithChildrenMode(parentId: String?, allLists: List<ListDto>): Boolean {
        if (parentId == null) return false

        // Check if immediate parent is in "with children" mode
        if (parentId in withChildrenMode) return true

        // Recursively check grandparents
        val parent = allLists.find { it.id == parentId }
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
