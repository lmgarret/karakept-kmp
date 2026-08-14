package com.karakept.app.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.components.OfflineModeBadge
import com.karakept.app.ui.components.shouldShowRefreshButton
import com.karakept.app.ui.theme.LocalEinkMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreenTopBar(
    offlineMode: Boolean,
    onMenuClick: () -> Unit,
    onFilterClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onOfflineBadgeClick: () -> Unit = {},
    isDesktop: Boolean,
    isSyncing: Boolean = false,
    isExpandedLayout: Boolean = false,
    isDrawerVisible: Boolean = true,
    hasActiveFilter: Boolean = false,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchClick: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedCount: Int = 0,
    allSelected: Boolean = false,
    onClearSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    // Batch action callbacks (only used in selection mode)
    onBatchMarkRead: () -> Unit = {},
    onBatchMarkUnread: () -> Unit = {},
    onBatchArchive: () -> Unit = {},
    onBatchUnarchive: () -> Unit = {},
    onBatchFavourite: () -> Unit = {},
    onBatchUnfavourite: () -> Unit = {},
    onBatchSetTags: () -> Unit = {},
    onBatchMoveToList: () -> Unit = {},
    onBatchDelete: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    if (isSelectionMode) {
        var showBatchMenu by remember { mutableStateOf(false) }

        TopAppBar(
            title = { Text("$selectedCount selected") },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Exit selection mode")
                }
            },
            actions = {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = if (allSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = if (allSelected) "Deselect all" else "Select all"
                    )
                }
                Box {
                    IconButton(onClick = { showBatchMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(
                        expanded = showBatchMenu,
                        onDismissRequest = { showBatchMenu = false }
                    ) {
                        // Status
                        DropdownMenuItem(
                            text = { Text("Mark as Read") },
                            leadingIcon = { Icon(Icons.Default.Visibility, null) },
                            onClick = { onBatchMarkRead(); showBatchMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Mark as Unread") },
                            leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                            onClick = { onBatchMarkUnread(); showBatchMenu = false }
                        )
                        HorizontalDivider()
                        // Archive
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            leadingIcon = { Icon(Icons.Default.Archive, null) },
                            onClick = { onBatchArchive(); showBatchMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Unarchive") },
                            leadingIcon = { Icon(Icons.Default.Unarchive, null) },
                            onClick = { onBatchUnarchive(); showBatchMenu = false }
                        )
                        HorizontalDivider()
                        // Favourites
                        DropdownMenuItem(
                            text = { Text("Add to Favourites") },
                            leadingIcon = { Icon(Icons.Default.Star, null) },
                            onClick = { onBatchFavourite(); showBatchMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from Favourites") },
                            leadingIcon = { Icon(Icons.Default.StarBorder, null) },
                            onClick = { onBatchUnfavourite(); showBatchMenu = false }
                        )
                        HorizontalDivider()
                        // Organisation
                        DropdownMenuItem(
                            text = { Text("Move to List") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                            onClick = { onBatchMoveToList(); showBatchMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Set Tags") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                            onClick = { onBatchSetTags(); showBatchMenu = false }
                        )
                        HorizontalDivider()
                        // Destructive
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { onBatchDelete(); showBatchMenu = false }
                        )
                    }
                }
            }
        )
        return
    }

    TopAppBar(
        title = {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search bookmarks…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {})
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Karakept")
                    if (offlineMode) {
                        Spacer(Modifier.width(8.dp))
                        OfflineModeBadge(onClick = onOfflineBadgeClick)
                    }
                }
            }
        },
        navigationIcon = {
            if (isSearchActive) {
                IconButton(onClick = onSearchClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = if (isExpandedLayout && isDrawerVisible) Icons.AutoMirrored.Filled.MenuOpen
                                      else Icons.Default.Menu,
                        contentDescription = if (isExpandedLayout) "Toggle drawer" else "Menu"
                    )
                }
            }
        },
        actions = {
            if (isSearchActive) {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            } else {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onFilterClick) {
                    BadgedBox(badge = { if (hasActiveFilter) Badge() }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
                // Desktop has no pull-to-refresh gesture, and e-ink mode turns it off — both
                // need the button as their only way to trigger a sync.
                if (shouldShowRefreshButton(isDesktop, LocalEinkMode.current.enabled)) {
                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !offlineMode && !isSyncing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                }
            }
        }
    )
}
