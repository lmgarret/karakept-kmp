package com.karakept.app.ui.screens.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.ui.utils.buildListHierarchy
import com.karakept.app.ui.utils.filterExpandedHierarchy
import com.karakept.app.ui.utils.listHasChildren
import com.karakept.api.model.KarakeepList

@Composable
internal fun DrawerContent(
    lists: List<KarakeepList>,
    listCounts: Map<String, Int>,
    expandedLists: Set<String>,
    currentFilter: FilterConfig,
    onFilterApply: (FilterConfig) -> Unit,
    onClearFilter: () -> Unit,
    onToggleListExpanded: (String) -> Unit,
    onMarkAllAsRead: (listId: String) -> Unit,
    onRenameList: (listId: String, listName: String) -> Unit,
    onNavigateToListSettings: (listId: String, listName: String) -> Unit,
    onSetAsDefault: (listId: String) -> Unit,
    onSetAsDefaultType: (DefaultListType) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHighlights: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            // Quick Filters Section
            Text("Quick Filters", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)

            BuiltinDrawerItem(
                label = "All Bookmarks",
                icon = { Icon(Icons.Default.Book, contentDescription = null) },
                selected = currentFilter == FilterConfig(),
                onClick = onClearFilter,
                onSetAsHome = { onSetAsDefaultType(DefaultListType.ALL_BOOKMARKS) }
            )

            BuiltinDrawerItem(
                label = "Favorites",
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                selected = currentFilter == FilterConfig(status = FilterStatus.FAVORITES),
                onClick = { onFilterApply(FilterConfig(status = FilterStatus.FAVORITES)) },
                onSetAsHome = { onSetAsDefaultType(DefaultListType.FAVORITES) }
            )

            BuiltinDrawerItem(
                label = "Archived",
                icon = { Icon(Icons.Default.Archive, contentDescription = null) },
                selected = currentFilter == FilterConfig(status = FilterStatus.ARCHIVED),
                onClick = { onFilterApply(FilterConfig(status = FilterStatus.ARCHIVED)) },
                onSetAsHome = { onSetAsDefaultType(DefaultListType.ARCHIVED) }
            )

            // Lists Section (Hierarchical)
            if (lists.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Lists", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)

                val hierarchy = buildListHierarchy(lists)
                val visibleHierarchy = filterExpandedHierarchy(hierarchy, expandedLists)

                visibleHierarchy.forEach { (list, depth) ->
                    val listId = list.id ?: ""
                    key(listId) {
                        val hasChildLists = listHasChildren(listId, lists)
                        ListDrawerItem(
                            list = list,
                            depth = depth,
                            count = listCounts[listId],
                            isSelected = currentFilter.lists.contains(listId),
                            expandedLists = expandedLists,
                            hasChildLists = hasChildLists,
                            onToggleExpanded = { onToggleListExpanded(listId) },
                            onSelected = {
                                onFilterApply(
                                    FilterConfig(
                                        status = FilterStatus.ALL_INCLUDING_ARCHIVED,
                                        lists = listOf(listId)
                                    )
                                )
                            },
                            onMarkAllAsRead = { onMarkAllAsRead(listId) },
                            onRenameList = { onRenameList(listId, list.name ?: "") },
                            onListSettings = { onNavigateToListSettings(listId, list.name ?: "") },
                            onSetAsDefault = { onSetAsDefault(listId) }
                        )
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
    onMarkAllAsRead: (listId: String) -> Unit,
    onRenameList: (listId: String, listName: String) -> Unit,
    onNavigateToListSettings: (listId: String, listName: String) -> Unit,
    onSetAsDefault: (listId: String) -> Unit,
    onSetAsDefaultType: (DefaultListType) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHighlights: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    lists = lists,
                    listCounts = listCounts,
                    expandedLists = expandedLists,
                    currentFilter = currentFilter,
                    onFilterApply = onFilterApply,
                    onClearFilter = onClearFilter,
                    onToggleListExpanded = onToggleListExpanded,
                    onMarkAllAsRead = onMarkAllAsRead,
                    onRenameList = onRenameList,
                    onNavigateToListSettings = onNavigateToListSettings,
                    onSetAsDefault = onSetAsDefault,
                    onSetAsDefaultType = onSetAsDefaultType,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToHighlights = onNavigateToHighlights
                )
            }
        }
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BuiltinDrawerItem(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    onSetAsHome: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val selectedTextColor = MaterialTheme.colorScheme.primary
    val normalTextColor = MaterialTheme.colorScheme.onSurface

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) selectedContainerColor else Color.Transparent)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides
                    if (selected) selectedTextColor else normalTextColor
            ) {
                icon()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) selectedTextColor else normalTextColor
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = MaterialTheme.shapes.extraSmall
        ) {
            DropdownMenuItem(
                text = { Text("Set as home") },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onSetAsHome()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListDrawerItem(
    list: KarakeepList,
    depth: Int,
    count: Int?,
    isSelected: Boolean,
    expandedLists: Set<String>,
    hasChildLists: Boolean,
    onToggleExpanded: () -> Unit,
    onSelected: () -> Unit,
    onMarkAllAsRead: () -> Unit,
    onRenameList: () -> Unit,
    onListSettings: () -> Unit,
    onSetAsDefault: () -> Unit
) {
    val listId = list.id ?: ""
    var showMenu by remember { mutableStateOf(false) }

    val selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val selectedTextColor = MaterialTheme.colorScheme.primary
    val normalTextColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width((depth * 16).dp))

        // Expand/collapse icon
        if (hasChildLists) {
            IconButton(
                onClick = onToggleExpanded,
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

        // List item
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) selectedContainerColor else Color.Transparent)
                    .combinedClickable(
                        onClick = onSelected,
                        onLongClick = { showMenu = true }
                    )
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${list.icon ?: ""} ${list.name ?: ""}".trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) selectedTextColor else normalTextColor
                )
                count?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = (if (isSelected) selectedTextColor else MaterialTheme.colorScheme.onSurfaceVariant)
                            .copy(alpha = 0.6f)
                    )
                }
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = MaterialTheme.shapes.extraSmall
            ) {
                DropdownMenuItem(
                    text = { Text("Set as home") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onSetAsDefault()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Mark all as read") },
                    leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onMarkAllAsRead()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename / Change icon") },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onRenameList()
                    }
                )
                DropdownMenuItem(
                    text = { Text("List settings") },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onListSettings()
                    }
                )
            }
        }
    }
}
