package com.karakept.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.local.entity.SavedFilterEntity
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.ui.components.FilterBottomPanel
import com.karakept.app.ui.components.FilterIcon
import com.karakept.app.ui.components.IconPickerDialog
import kotlinx.serialization.json.Json
import org.burnoutcrew.reorderable.*

import com.karakept.api.model.KarakeepList

/**
 * Screen for managing saved filters.
 * Features:
 * - Quick Filters (read-only, can set as default)
 * - Shortcuts (visible in drawer)
 * - Hidden filters
 * - Drag-and-drop reordering
 * - Editing filters (config, name, icon)
 */
class FilterManagementScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<FilterManagementScreenModel>()

        val visibleFilters by screenModel.visibleFilters.collectAsState()
        val hiddenFilters by screenModel.hiddenFilters.collectAsState()
        val allSavedFilters by screenModel.allSavedFilters.collectAsState()
        val availableTags by screenModel.availableTags.collectAsState()
        val topTagsWithCounts by screenModel.topTagsWithCounts.collectAsState()
        val availableLists by screenModel.availableLists.collectAsState()

        FilterManagementContent(
            visibleFilters = visibleFilters,
            hiddenFilters = hiddenFilters,
            allSavedFilters = allSavedFilters,
            availableTags = availableTags,
            topTagsWithCounts = topTagsWithCounts,
            availableLists = availableLists,
            onBack = { navigator.pop() },
            onUpdateAppearance = screenModel::updateFilterAppearance,
            onUpdateConfig = screenModel::updateFilterConfig,
            onSetDefault = screenModel::setFilterAsDefault,
            onUnsetDefault = screenModel::unsetDefaultFilter,
            onDelete = screenModel::deleteFilter,
            onReorderVisible = screenModel::reorderVisibleFilters,
            onReorderHidden = screenModel::reorderHiddenFilters,
            onMoveToVisible = screenModel::moveFilterToVisible,
            onMoveToHidden = screenModel::moveFilterToHidden,
            onSaveNewFilter = { name, icon, color, config, isDefault, isQuickFilter ->
                screenModel.saveNewFilter(name, icon, color, config, isDefault, isVisibleInDrawer = false, isQuickFilter = isQuickFilter)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FilterManagementContent(
    visibleFilters: List<SavedFilterEntity>,
    hiddenFilters: List<SavedFilterEntity>,
    allSavedFilters: List<SavedFilterEntity>,
    availableTags: List<String>,
    topTagsWithCounts: List<Pair<String, Int>>,
    availableLists: List<KarakeepList>,
    onBack: () -> Unit,
    onUpdateAppearance: (SavedFilterEntity, String, Long?) -> Unit,
    onUpdateConfig: (SavedFilterEntity, FilterConfig) -> Unit,
    onSetDefault: (SavedFilterEntity) -> Unit,
    onUnsetDefault: (SavedFilterEntity) -> Unit,
    onDelete: (SavedFilterEntity) -> Unit,
    onReorderVisible: (List<SavedFilterEntity>) -> Unit,
    onReorderHidden: (List<SavedFilterEntity>) -> Unit,
    onMoveToVisible: (SavedFilterEntity) -> Unit,
    onMoveToHidden: (SavedFilterEntity) -> Unit,
    onSaveNewFilter: (String, String, Long?, FilterConfig, Boolean, Boolean) -> Unit
) {
    var visibleList by remember { mutableStateOf(visibleFilters) }
    var hiddenList by remember { mutableStateOf(hiddenFilters) }
    var deleteConfirmFilter by remember { mutableStateOf<SavedFilterEntity?>(null) }
    
    // Editing state
    var editingFilter by remember { mutableStateOf<SavedFilterEntity?>(null) }
    var editingConfig by remember { mutableStateOf<FilterConfig?>(null) }
    var editingAppearanceFilter by remember { mutableStateOf<SavedFilterEntity?>(null) }

    // Update lists when source changes
    LaunchedEffect(visibleFilters) {
        visibleList = visibleFilters
    }

    LaunchedEffect(hiddenFilters) {
        hiddenList = hiddenFilters
    }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val fromId = from.key as? Long
            val toId = to.key as? Long
            
            if (fromId != null && toId != null) {
                // Check if both items are in the visible list
                val visibleFromIndex = visibleList.indexOfFirst { it.id == fromId }
                val visibleToIndex = visibleList.indexOfFirst { it.id == toId }
                
                if (visibleFromIndex != -1 && visibleToIndex != -1) {
                    visibleList = visibleList.toMutableList().apply {
                        add(visibleToIndex, removeAt(visibleFromIndex))
                    }
                    return@rememberReorderableLazyListState
                }

                // Check if both items are in the hidden list
                val hiddenFromIndex = hiddenList.indexOfFirst { it.id == fromId }
                val hiddenToIndex = hiddenList.indexOfFirst { it.id == toId }
                
                if (hiddenFromIndex != -1 && hiddenToIndex != -1) {
                    hiddenList = hiddenList.toMutableList().apply {
                        add(hiddenToIndex, removeAt(hiddenFromIndex))
                    }
                    return@rememberReorderableLazyListState
                }

                // Cross-section moves
                // Visible -> Hidden
                if (visibleFromIndex != -1 && hiddenToIndex != -1) {
                    val item = visibleList[visibleFromIndex].copy(isVisibleInDrawer = false)
                    visibleList = visibleList.toMutableList().apply { removeAt(visibleFromIndex) }
                    hiddenList = hiddenList.toMutableList().apply { add(hiddenToIndex, item) }
                    return@rememberReorderableLazyListState
                }

                // Hidden -> Visible
                if (hiddenFromIndex != -1 && visibleToIndex != -1) {
                    val item = hiddenList[hiddenFromIndex].copy(isVisibleInDrawer = true)
                    hiddenList = hiddenList.toMutableList().apply { removeAt(hiddenFromIndex) }
                    visibleList = visibleList.toMutableList().apply { add(visibleToIndex, item) }
                    return@rememberReorderableLazyListState
                }
            }
        },
        onDragEnd = { _, _ ->
            onReorderVisible(visibleList)
            onReorderHidden(hiddenList)
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Filters") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .reorderable(reorderState),
            state = reorderState.listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quick Filters Section
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Quick Filters",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            val quickFilters = listOf(
                Triple("All Bookmarks", "Book", FilterConfig()),
                Triple("Favorites", "Star", FilterConfig(status = FilterStatus.FAVORITES)),
                Triple("Archived", "Archive", FilterConfig(status = FilterStatus.ARCHIVED))
            )
            
            items(quickFilters) { (name, icon, config) ->
                // Check if this quick filter is effectively the default
                val isDefault = allSavedFilters.any { 
                    it.isDefault && it.isQuickFilter && try {
                        Json.decodeFromString<FilterConfig>(it.configJson) == config
                    } catch (e: Exception) { false }
                }
                
                QuickFilterItem(
                    name = name,
                    icon = icon,
                    isDefault = isDefault,
                    onSetDefault = {
                        // Logic to set quick filter as default:
                        // 1. Find existing QUICK filter with this config
                        val existing = allSavedFilters.find {
                            it.isQuickFilter && try {
                                Json.decodeFromString<FilterConfig>(it.configJson) == config
                            } catch (e: Exception) { false }
                        }
                        
                        if (existing != null) {
                            onSetDefault(existing)
                        } else {
                            // Create new hidden quick filter and set as default
                            onSaveNewFilter(name, icon, null, config, true, true)
                        }
                    },
                    onUnsetDefault = {
                        val existing = allSavedFilters.find {
                            it.isQuickFilter && try {
                                Json.decodeFromString<FilterConfig>(it.configJson) == config
                            } catch (e: Exception) { false }
                        }
                        if (existing != null) {
                            onUnsetDefault(existing)
                        }
                    }
                )
            }

            // List Homepage Section
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "List as Homepage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = "Set a specific list to open by default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(availableLists) { list ->
                val isDefault = allSavedFilters.any {
                    it.isDefault && it.isQuickFilter && try {
                        val config = Json.decodeFromString<FilterConfig>(it.configJson)
                        config.lists == listOf(list.id)
                    } catch (e: Exception) { false }
                }

                ListAsHomepageItem(
                    list = list,
                    isDefault = isDefault,
                    onSetDefault = {
                        val config = FilterConfig(lists = listOf(list.id ?: ""))
                        onSaveNewFilter("Homepage: ${list.name ?: ""}", list.icon ?: "", null, config, true, true)
                    },
                    onUnsetDefault = {
                        val existing = allSavedFilters.find {
                            it.isDefault && it.isQuickFilter && try {
                                val config = Json.decodeFromString<FilterConfig>(it.configJson)
                                config.lists == listOf(list.id)
                            } catch (e: Exception) { false }
                        }
                        if (existing != null) {
                            onUnsetDefault(existing)
                        }
                    }
                )
            }

            // Shortcuts Section Header
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Shortcuts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = "These filters appear in the navigation drawer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Shortcuts (Visible) Filters
            if (visibleList.isEmpty()) {
                item {
                    EmptyStateCard("No shortcuts yet. Saved filters will appear here.")
                }
            } else {
                itemsIndexed(
                    items = visibleList,
                    key = { _, filter -> filter.id }
                ) { index, filter ->
                    ReorderableItem(reorderState, key = filter.id) { isDragging ->
                        FilterListItem(
                            filter = filter,
                            isDragging = isDragging,
                            onEdit = { 
                                editingFilter = filter
                                try {
                                    editingConfig = Json.decodeFromString(filter.configJson)
                                } catch (e: Exception) {
                                    editingConfig = FilterConfig()
                                }
                            },
                            onEditAppearance = { editingAppearanceFilter = filter },
                            onSetDefault = { onSetDefault(filter) },
                            onUnsetDefault = { onUnsetDefault(filter) },
                            onDelete = { deleteConfirmFilter = filter },
                            onMoveToOtherSection = { onMoveToHidden(filter) },
                            isInShortcuts = true,
                            modifier = Modifier
                                .detectReorderAfterLongPress(reorderState)
                        )
                    }
                }
            }

            // Hidden Section Header
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Hidden",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = "These filters are saved but not shown in the drawer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Hidden Filters
            if (hiddenList.isEmpty()) {
                item {
                    EmptyStateCard("No hidden filters")
                }
            } else {
                itemsIndexed(
                    items = hiddenList,
                    key = { _, filter -> filter.id }
                ) { index, filter ->
                    ReorderableItem(reorderState, key = filter.id) { isDragging ->
                        FilterListItem(
                            filter = filter,
                            isDragging = isDragging,
                            onEdit = { 
                                editingFilter = filter
                                try {
                                    editingConfig = Json.decodeFromString(filter.configJson)
                                } catch (e: Exception) {
                                    editingConfig = FilterConfig()
                                }
                            },
                            onEditAppearance = { editingAppearanceFilter = filter },
                            onSetDefault = { onSetDefault(filter) },
                            onUnsetDefault = { onUnsetDefault(filter) },
                            onDelete = { deleteConfirmFilter = filter },
                            onMoveToOtherSection = { onMoveToVisible(filter) },
                            isInShortcuts = false,
                            modifier = Modifier
                                .detectReorderAfterLongPress(reorderState)
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Edit Filter Panel
    // Edit Filter Panel
    // Back Handler for drawer
    if (editingFilter != null && editingConfig != null) {
        com.karakept.app.ui.components.BackHandler(enabled = true) {
            editingFilter = null
            editingConfig = null
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        FilterBottomPanel(
            visible = editingFilter != null && editingConfig != null,
            currentFilter = editingConfig ?: FilterConfig(),
            availableTags = topTagsWithCounts.map { "${it.first} (${it.second})" },
            allTags = availableTags,
            availableLists = availableLists,
            onDismiss = { 
                editingFilter = null
                editingConfig = null
            },
            onFilterChange = { editingConfig = it },
            onSaveFilter = { _, _, _, _ -> }, // Not used in update mode
            onUpdateFilter = { newConfig ->
                editingFilter?.let { filter ->
                    onUpdateConfig(filter, newConfig)
                }
            },
            onReset = { editingConfig = FilterConfig() }
        )
    }

    // Delete confirmation dialog
    deleteConfirmFilter?.let { filter ->
        AlertDialog(
            onDismissRequest = { deleteConfirmFilter = null },
            title = { Text("Delete Filter?") },
            text = { Text("Are you sure you want to delete \"${filter.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(filter)
                        deleteConfirmFilter = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFilter = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Icon/Color Picker for editing appearance
    editingAppearanceFilter?.let { filter ->
        IconPickerDialog(
            currentIcon = filter.icon,
            currentColor = filter.color,
            onDismiss = { editingAppearanceFilter = null },
            onIconSelected = { newIcon, newColor ->
                onUpdateAppearance(filter, newIcon, newColor)
                editingAppearanceFilter = null
            }
        )
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun QuickFilterItem(
    name: String,
    icon: String,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onUnsetDefault: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                FilterIcon(iconName = icon, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    if (isDefault) {
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (isDefault) {
                        DropdownMenuItem(
                            text = { Text("Unset as default") },
                            onClick = {
                                onUnsetDefault()
                                showMenu = false
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Set as default") },
                            onClick = {
                                onSetDefault()
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterListItem(
    filter: SavedFilterEntity,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onEditAppearance: () -> Unit,
    onSetDefault: () -> Unit,
    onUnsetDefault: () -> Unit,
    onDelete: () -> Unit,
    onMoveToOtherSection: () -> Unit,
    isInShortcuts: Boolean,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Drag handle
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )

                // Icon
                FilterIcon(
                    iconName = filter.icon,
                    modifier = Modifier.size(18.dp),
                    tint = if (filter.color != null) androidx.compose.ui.graphics.Color(filter.color) else LocalContentColor.current
                )

                // Name and default badge
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = filter.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (filter.isDefault) {
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Three-dot menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Filter") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            onEdit()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Appearance") },
                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                        onClick = {
                            onEditAppearance()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isInShortcuts) "Move to Hidden" else "Move to Shortcuts") },
                        leadingIcon = {
                            Icon(
                                if (isInShortcuts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onMoveToOtherSection()
                            showMenu = false
                        }
                    )
                    if (!filter.isDefault) {
                        DropdownMenuItem(
                            text = { Text("Set as Default") },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                            onClick = {
                                onSetDefault()
                                showMenu = false
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Unset Default") },
                            leadingIcon = { Icon(Icons.Default.StarBorder, contentDescription = null) },
                            onClick = {
                                onUnsetDefault()
                                showMenu = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            onDelete()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ListAsHomepageItem(
    list: KarakeepList,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onUnsetDefault: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = list.icon ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                    Text(list.name ?: "", style = MaterialTheme.typography.bodyMedium)
                    if (isDefault) {
                        Text(
                            text = "Current Homepage",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (isDefault) {
                        DropdownMenuItem(
                            text = { Text("Unset as homepage") },
                            onClick = {
                                onUnsetDefault()
                                showMenu = false
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Set as homepage") },
                            onClick = {
                                onSetDefault()
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}
