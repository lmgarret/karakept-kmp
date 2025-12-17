package com.karakept.app.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.SavedFilterEntity
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.remote.model.ListDto
import com.karakept.app.ui.screens.SavedFilterItem

@Composable
internal fun MainScreenDrawer(
    drawerState: DrawerState,
    lists: List<ListDto>,
    savedFilters: List<SavedFilterEntity>,
    expandedLists: Set<String>,
    currentFilter: FilterConfig,
    onFilterApply: (FilterConfig) -> Unit,
    onSavedFilterApply: (SavedFilterEntity) -> Unit,
    onClearFilter: () -> Unit,
    onToggleListExpanded: (String) -> Unit,
    onNavigateToFilterManagement: () -> Unit,
    onNavigateToSettings: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))

                // Quick Filters Section
                Text("Quick Filters", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)

                NavigationDrawerItem(
                    label = { Text("All Bookmarks") },
                    selected = currentFilter == FilterConfig(),
                    icon = { Icon(Icons.Default.Book, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    onClick = onClearFilter
                )

                NavigationDrawerItem(
                    label = { Text("Favorites") },
                    selected = currentFilter == FilterConfig(status = FilterStatus.FAVORITES),
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    onClick = {
                        onFilterApply(FilterConfig(status = FilterStatus.FAVORITES))
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Not Archived") },
                    selected = currentFilter == FilterConfig(status = FilterStatus.NOT_ARCHIVED),
                    icon = { Icon(Icons.Default.Inbox, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    onClick = {
                        onFilterApply(FilterConfig(status = FilterStatus.NOT_ARCHIVED))
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Archived") },
                    selected = currentFilter == FilterConfig(status = FilterStatus.ARCHIVED),
                    icon = { Icon(Icons.Default.Archive, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    onClick = {
                        onFilterApply(FilterConfig(status = FilterStatus.ARCHIVED))
                    }
                )

                // Lists Section (Hierarchical)
                if (lists.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Lists", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)

                    val hierarchy = buildHierarchy(lists)
                    val visibleHierarchy = filterExpandedHierarchy(hierarchy, expandedLists)

                    visibleHierarchy.forEach { (list, depth) ->
                        key(list.id) {
                            val hasChildLists = hasChildren(list.id, lists)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(Modifier.width((depth * 16).dp)) // Indentation

                                // Expand/collapse icon
                                if (hasChildLists) {
                                    IconButton(
                                        onClick = { onToggleListExpanded(list.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (expandedLists.contains(list.id)) {
                                                Icons.Default.KeyboardArrowDown
                                            } else {
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight
                                            },
                                            contentDescription = if (expandedLists.contains(list.id)) "Collapse" else "Expand",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(24.dp))
                                }

                                NavigationDrawerItem(
                                    label = { Text("${list.icon} ${list.name}") },
                                    selected = currentFilter.lists.contains(list.id),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary
                                    ),
                                    onClick = {
                                        onFilterApply(FilterConfig(lists = listOf(list.id)))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Saved Filters Section (Shortcuts)
                if (savedFilters.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Saved Filters", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = onNavigateToFilterManagement) {
                            Icon(Icons.Default.FilterList, contentDescription = "Manage Filters")
                        }
                    }
                    savedFilters.forEach { savedFilter ->
                        val isSelected = try {
                            kotlinx.serialization.json.Json.decodeFromString<FilterConfig>(savedFilter.configJson) == currentFilter
                        } catch (e: Exception) {
                            false
                        }

                        SavedFilterItem(
                            savedFilter = savedFilter,
                            selected = isSelected,
                            onApply = {
                                onSavedFilterApply(savedFilter)
                            }
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    onClick = onNavigateToSettings
                )
            }
        }
    ) {
        content()
    }
}

// Hierarchy helper functions for list display

/**
 * Builds a hierarchical list structure from flat list.
 * Returns list of (ListDto, depth) pairs in display order.
 * Reused from ListManagementScreen.kt
 */
private fun buildHierarchy(lists: List<ListDto>): List<Pair<ListDto, Int>> {
    val result = mutableListOf<Pair<ListDto, Int>>()
    val grouped = lists.groupBy { it.parentId }
    val visited = mutableSetOf<String>() // Prevent circular refs

    fun recurse(parentId: String?, depth: Int) {
        if (depth > 10) return // Max depth protection
        val children = grouped[parentId] ?: return
        children.forEach { child ->
            if (!visited.contains(child.id)) {
                visited.add(child.id)
                result.add(child to depth)
                recurse(child.id, depth + 1)
            }
        }
    }

    recurse(null, 0)

    // Handle orphans
    val processed = result.map { it.first.id }.toSet()
    lists.filter { it.id !in processed }.forEach { list ->
        result.add(list to 0)
    }

    return result
}

/**
 * Filters hierarchy to only show expanded branches.
 * A list is visible only if ALL its ancestors are expanded.
 */
private fun filterExpandedHierarchy(
    hierarchy: List<Pair<ListDto, Int>>,
    expandedIds: Set<String>
): List<Pair<ListDto, Int>> {
    val result = mutableListOf<Pair<ListDto, Int>>()

    // Build a map of list ID to its hierarchy entry for quick lookup
    val listMap = hierarchy.associateBy { it.first.id }

    hierarchy.forEach { (list, depth) ->
        val shouldShow = if (depth == 0) {
            true // Root items always visible
        } else {
            // Check if ALL ancestors in the parent chain are expanded
            var allAncestorsExpanded = true
            var currentParentId = list.parentId

            while (currentParentId != null && allAncestorsExpanded) {
                if (!expandedIds.contains(currentParentId)) {
                    allAncestorsExpanded = false
                }
                // Move to next ancestor
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
 * Checks if a list has children
 */
private fun hasChildren(listId: String, allLists: List<ListDto>): Boolean {
    return allLists.any { it.parentId == listId }
}
