package com.karakept.app.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.domain.action.AiAction
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.screens.AiBatchProgress
import com.karakept.app.ui.components.OfflineModeBadge
import com.karakept.app.ui.components.einkOutlineBorder
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
    onBatchDelete: () -> Unit = {},
    // AI batch actions. Withheld for Select All selections and for servers that would refuse them.
    aiCapabilities: AiCapabilities = AiCapabilities(),
    selectedViaSelectAll: Boolean = false,
    aiBatchProgress: AiBatchProgress? = null,
    onBatchAiAction: (AiAction) -> Unit = {},
    onCancelAiBatch: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    if (isSelectionMode) {
        var showBatchMenu by remember { mutableStateOf(false) }

        // While a batch AI run is going, the bar reports progress and its Close button cancels
        // instead of clearing. Plain text rather than a progress bar, so it needs no e-ink branch.
        val running = aiBatchProgress

        TopAppBar(
            title = {
                Text(
                    if (running != null) "${running.action.runningLabel} ${running.done + 1} of ${running.total}"
                    else "$selectedCount selected"
                )
            },
            navigationIcon = {
                IconButton(onClick = if (running != null) onCancelAiBatch else onClearSelection) {
                    Icon(
                        AppIcons.Default.Close,
                        contentDescription = if (running != null) "Cancel" else "Exit selection mode"
                    )
                }
            },
            actions = {
                // Nothing else is actionable mid-run: the selection is already committed to it,
                // and the only control that stays live is Cancel, in the navigation slot.
                if (running == null) {
                    IconButton(onClick = onSelectAll) {
                        Icon(
                            imageVector = if (allSelected) AppIcons.Default.CheckBox else AppIcons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (allSelected) "Deselect all" else "Select all"
                        )
                    }
                    Box {
                        IconButton(onClick = { showBatchMenu = true }) {
                            Icon(AppIcons.Default.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(
                            expanded = showBatchMenu,
                            onDismissRequest = { showBatchMenu = false },
                            border = einkOutlineBorder()
                        ) {
                            // Status
                            DropdownMenuItem(
                                text = { Text("Mark as Read") },
                                leadingIcon = { Icon(AppIcons.Default.Visibility, null) },
                                onClick = { onBatchMarkRead(); showBatchMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Mark as Unread") },
                                leadingIcon = { Icon(AppIcons.Default.VisibilityOff, null) },
                                onClick = { onBatchMarkUnread(); showBatchMenu = false }
                            )
                            HorizontalDivider()
                            // Archive
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = { Icon(AppIcons.Default.Archive, null) },
                                onClick = { onBatchArchive(); showBatchMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Unarchive") },
                                leadingIcon = { Icon(AppIcons.Default.Unarchive, null) },
                                onClick = { onBatchUnarchive(); showBatchMenu = false }
                            )
                            HorizontalDivider()
                            // Favourites
                            DropdownMenuItem(
                                text = { Text("Add to Favourites") },
                                leadingIcon = { Icon(AppIcons.Default.Star, null) },
                                onClick = { onBatchFavourite(); showBatchMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from Favourites") },
                                leadingIcon = { Icon(AppIcons.Default.StarBorder, null) },
                                onClick = { onBatchUnfavourite(); showBatchMenu = false }
                            )
                            HorizontalDivider()
                            // Organisation
                            DropdownMenuItem(
                                text = { Text("Move to List") },
                                leadingIcon = { Icon(AppIcons.Default.FolderOpen, null) },
                                onClick = { onBatchMoveToList(); showBatchMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Set Tags") },
                                leadingIcon = { Icon(AppIcons.AutoMirrored.Filled.Label, null) },
                                onClick = { onBatchSetTags(); showBatchMenu = false }
                            )
                            // AI actions. Hidden for a Select All selection — that can reach the
                            // whole library, and every item is a separate inference call.
                            if (!selectedViaSelectAll && (aiCapabilities.canSummarize || aiCapabilities.isAdmin)) {
                                HorizontalDivider()
                                if (aiCapabilities.canSummarize) {
                                    DropdownMenuItem(
                                        text = { Text(AiAction.SUMMARIZE.label) },
                                        leadingIcon = { Icon(AppIcons.Default.AutoAwesome, null) },
                                        onClick = { onBatchAiAction(AiAction.SUMMARIZE); showBatchMenu = false }
                                    )
                                }
                                if (aiCapabilities.isAdmin) {
                                    DropdownMenuItem(
                                        text = { Text(AiAction.RETAG.label) },
                                        leadingIcon = { Icon(AppIcons.Default.NewLabel, null) },
                                        onClick = { onBatchAiAction(AiAction.RETAG); showBatchMenu = false }
                                    )
                                }
                            }
                            HorizontalDivider()
                            // Destructive
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        AppIcons.Default.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { onBatchDelete(); showBatchMenu = false }
                            )
                        }
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
                    Icon(AppIcons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = if (isExpandedLayout && isDrawerVisible) AppIcons.AutoMirrored.Filled.MenuOpen
                                      else AppIcons.Default.Menu,
                        contentDescription = if (isExpandedLayout) "Toggle drawer" else "Menu"
                    )
                }
            }
        },
        actions = {
            if (isSearchActive) {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(AppIcons.Default.Close, contentDescription = "Clear search")
                    }
                }
            } else {
                IconButton(onClick = onSearchClick) {
                    Icon(AppIcons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onFilterClick) {
                    BadgedBox(badge = { if (hasActiveFilter) Badge() }) {
                        Icon(AppIcons.Default.FilterList, contentDescription = "Filter")
                    }
                }
                // Desktop has no pull-to-refresh gesture, and e-ink mode turns it off — both
                // need the button as their only way to trigger a sync.
                if (shouldShowRefreshButton(isDesktop, LocalEinkMode.current.enabled)) {
                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !offlineMode && !isSyncing
                    ) {
                        Icon(AppIcons.Default.Refresh, contentDescription = "Sync")
                    }
                }
            }
        }
    )
}
