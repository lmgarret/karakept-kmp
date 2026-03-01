package com.karakept.app.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
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
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.api.model.KarakeepList

@Composable
internal fun MainScreenDrawer(
    drawerState: DrawerState,
    lists: List<KarakeepList>,
    listCounts: Map<String, Int>,
    expandedLists: Set<String>,
    currentFilter: FilterConfig,
    onFilterApply: (FilterConfig) -> Unit,
    onClearFilter: () -> Unit,
    onToggleListExpanded: (String) -> Unit,
    onNavigateToListSettings: (listId: String, listName: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHighlights: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
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
                                val listId = list.id ?: ""
                                key(listId) {
                                    val hasChildLists = hasChildren(listId, lists)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Spacer(Modifier.width((depth * 16).dp)) // Indentation

                                        // Expand/collapse icon
                                        if (hasChildLists) {
                                            IconButton(
                                                onClick = { onToggleListExpanded(listId) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (expandedLists.contains(listId)) {
                                                        Icons.Default.KeyboardArrowDown
                                                    } else {
                                                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                                                    },
                                                    contentDescription = if (expandedLists.contains(listId)) "Collapse" else "Expand",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(Modifier.width(24.dp))
                                        }

                                        NavigationDrawerItem(
                                            label = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${list.icon} ${list.name}")
                                                    listCounts[listId]?.let { count ->
                                                        Text(
                                                            text = count.toString(),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                }
                                            },
                                            selected = currentFilter.lists.contains(listId),
                                            colors = NavigationDrawerItemDefaults.colors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary
                                            ),
                                            onClick = {
                                                onFilterApply(FilterConfig(
                                                    status = FilterStatus.ALL_INCLUDING_ARCHIVED,
                                                    lists = listOf(list.id ?: "")
                                                ))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(
                                            onClick = {
                                                onNavigateToListSettings(listId, list.name ?: "")
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "List settings",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        NavigationDrawerItem(
                            label = { Text("Highlights") },
                            selected = false,
                            icon = { Icon(Icons.Default.Create, contentDescription = null) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            onClick = onNavigateToHighlights
                        )
                        Spacer(Modifier.height(16.dp))
                    }

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
        }
    ) {
        content()
    }
}

// Hierarchy helper functions for list display

/**
 * Builds a hierarchical list structure from flat list.
 * Returns list of (KarakeepList, depth) pairs in display order.
 * Reused from ListManagementScreen.kt
 */
private fun buildHierarchy(lists: List<KarakeepList>): List<Pair<KarakeepList, Int>> {
    val result = mutableListOf<Pair<KarakeepList, Int>>()
    val grouped = lists.groupBy { it.parentId }
    val visited = mutableSetOf<String>() // Prevent circular refs

    fun recurse(parentId: String?, depth: Int) {
        if (depth > 10) return // Max depth protection
        val children = grouped[parentId] ?: return
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

    // Handle orphans
    val processed = result.map { it.first.id ?: "" }.toSet()
    lists.filter { (it.id ?: "") !in processed }.forEach { list ->
        result.add(list to 0)
    }

    return result
}

/**
 * Filters hierarchy to only show expanded branches.
 * A list is visible only if ALL its ancestors are expanded.
 */
private fun filterExpandedHierarchy(
    hierarchy: List<Pair<KarakeepList, Int>>,
    expandedIds: Set<String>
): List<Pair<KarakeepList, Int>> {
    val result = mutableListOf<Pair<KarakeepList, Int>>()

    // Build a map of list ID to its hierarchy entry for quick lookup
    val listMap = hierarchy.associateBy { it.first.id ?: "" }

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
private fun hasChildren(listId: String, allLists: List<KarakeepList>): Boolean {
    return allLists.any { it.parentId == listId }
}
