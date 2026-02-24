package com.karakept.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.SortOption
import com.karakept.api.model.KarakeepList as KarakeepList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom panel for live filter editing with swipe-to-dismiss.
 * Replaces the modal FilterDialog with a drawer UX similar to ReaderAppearanceBottomPanel.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomPanel(
    visible: Boolean,
    currentFilter: FilterConfig,
    availableTags: List<String>, // Top 10 most used tags for quick selection
    allTags: List<String> = availableTags, // All tags for the dialog
    availableLists: List<KarakeepList>,
    onDismiss: () -> Unit,
    onFilterChange: (FilterConfig) -> Unit,
    onSaveFilter: (String, String, Long?, Boolean) -> Unit, // name, icon, color, isDefault
    onUpdateFilter: ((FilterConfig) -> Unit)? = null, // New callback for updating existing filter
    onReset: () -> Unit
) {
    var filter by remember(currentFilter) { mutableStateOf(currentFilter) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Live filtering with 300ms debounce
    LaunchedEffect(filter) {
        scope.launch {
            delay(300)
            onFilterChange(filter)
        }
    }

    BaseBottomPanel(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp) // Set a reasonable max height
                .wrapContentHeight(Alignment.Top) // Align top to prevent jumping
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (onUpdateFilter != null) "Edit Filter" else "Filter",
                    style = MaterialTheme.typography.titleLarge
                )
                Row {
                    TextButton(onClick = {
                        filter = FilterConfig() // Reset local state
                        onReset()
                    }) {
                        Text("Reset")
                    }
                    if (onUpdateFilter != null) {
                        Button(onClick = {
                            onUpdateFilter(filter) // Use the local 'filter' state
                            onDismiss()
                        }) {
                            Text("Save")
                        }
                    } else {
                        Button(onClick = { showSaveDialog = true }) {
                            Text("Save As")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false) // Allow scrolling but don't force fill
                    .verticalScroll(rememberScrollState())
            ) {
                // Status Filter
                Text("Status", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    FilterChip(
                        selected = filter.status == FilterStatus.ALL,
                        onClick = { filter = filter.copy(status = FilterStatus.ALL) },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = filter.status == FilterStatus.FAVORITES,
                        onClick = { filter = filter.copy(status = FilterStatus.FAVORITES) },
                        label = { Text("Favorites") },
                        leadingIcon = { Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = filter.status == FilterStatus.ARCHIVED,
                        onClick = { filter = filter.copy(status = FilterStatus.ARCHIVED) },
                        label = { Text("Archived") },
                        leadingIcon = { Icon(Icons.Default.Archive, null, modifier = Modifier.size(18.dp)) }
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // Sort
                Text("Sort By", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    // Added Date (Newest/Oldest)
                    val isAddedSelected = filter.sort == SortOption.NEWEST || filter.sort == SortOption.OLDEST
                    val addedRotation by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (filter.sort == SortOption.OLDEST) 180f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                    FilterChip(
                        selected = isAddedSelected,
                        onClick = {
                            filter = filter.copy(
                                sort = if (filter.sort == SortOption.NEWEST) SortOption.OLDEST else SortOption.NEWEST
                            )
                        },
                        label = { Text("Added") },
                        leadingIcon = {
                            if (isAddedSelected) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer { rotationZ = addedRotation }
                                )
                            } else {
                                Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    // Title (A-Z/Z-A)
                    val isTitleSelected = filter.sort == SortOption.TITLE_AZ || filter.sort == SortOption.TITLE_ZA
                    val titleRotation by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (filter.sort == SortOption.TITLE_ZA) 180f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                    FilterChip(
                        selected = isTitleSelected,
                        onClick = {
                            filter = filter.copy(
                                sort = if (filter.sort == SortOption.TITLE_AZ) SortOption.TITLE_ZA else SortOption.TITLE_AZ
                            )
                        },
                        label = { Text("Title") },
                        leadingIcon = {
                            if (isTitleSelected) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer { rotationZ = titleRotation }
                                )
                            } else {
                                Icon(Icons.Default.SortByAlpha, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )

                    // Reading Time (Short/Long)
                    val isReadingTimeSelected = filter.sort == SortOption.READING_TIME_SHORT || filter.sort == SortOption.READING_TIME_LONG
                    val readingTimeRotation by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (filter.sort == SortOption.READING_TIME_LONG) 180f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                    FilterChip(
                        selected = isReadingTimeSelected,
                        onClick = {
                            filter = filter.copy(
                                sort = if (filter.sort == SortOption.READING_TIME_SHORT)
                                    SortOption.READING_TIME_LONG
                                else
                                    SortOption.READING_TIME_SHORT
                            )
                        },
                        label = { Text("Reading Time") },
                        leadingIcon = {
                            if (isReadingTimeSelected) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer { rotationZ = readingTimeRotation }
                                )
                            } else {
                                Icon(Icons.Outlined.MenuBook, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // Lists
                if (availableLists.isNotEmpty()) {
                    Text("Lists", style = MaterialTheme.typography.titleMedium)
                    val sortedLists = remember(availableLists, filter.lists) {
                        val sel = availableLists.filter { filter.lists.contains(it.id ?: "") }
                        val unsel = availableLists.filter { !filter.lists.contains(it.id ?: "") }
                        sel + unsel
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        sortedLists.forEach { list ->
                            val listId = list.id ?: ""
                            val isSelected = filter.lists.contains(listId)
                            FilterChip(
                                selected = isSelected,
                                border = if (isSelected)
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                else
                                    FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
                                onClick = {
                                    val newLists = if (isSelected) {
                                        filter.lists - listId
                                    } else {
                                        filter.lists + listId
                                    }
                                    filter = filter.copy(lists = newLists)
                                },
                                label = { Text(list.name ?: "Untitled") },
                                leadingIcon = { Text(list.icon ?: "") }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                // Tags
                if (availableTags.isNotEmpty()) {
                    Text("Tags", style = MaterialTheme.typography.titleMedium)
                    val sortedTags = remember(availableTags, filter.tags) {
                        val sel = availableTags.filter { tag ->
                            filter.tags.contains(tag.substringBefore(" (").trim())
                        }
                        val unsel = availableTags.filter { tag ->
                            !filter.tags.contains(tag.substringBefore(" (").trim())
                        }
                        sel + unsel
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        sortedTags.forEach { tagWithCount ->
                            val tagName = tagWithCount.substringBefore(" (").trim()
                            val isSelected = filter.tags.contains(tagName)
                            FilterChip(
                                selected = isSelected,
                                border = if (isSelected)
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                else
                                    FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
                                onClick = {
                                    val newTags = if (isSelected) {
                                        filter.tags - tagName
                                    } else {
                                        filter.tags + tagName
                                    }
                                    filter = filter.copy(tags = newTags)
                                },
                                label = { Text(tagWithCount) }
                            )
                        }
                        TextButton(onClick = { showTagsDialog = true }) {
                            Text("More tags...")
                        }
                    }
                }
            }
        }
    }

    // Tag selection dialog
    if (showTagsDialog) {
        TagSelectionDialog(
            availableTags = allTags, // Use all tags for the dialog
            selectedTags = filter.tags,
            onDismiss = { showTagsDialog = false },
            onConfirm = { selectedTags ->
                filter = filter.copy(tags = selectedTags)
                showTagsDialog = false
            }
        )
    }

    // Save filter dialog with icon picker (only if NOT updating existing)
    if (onUpdateFilter == null && showSaveDialog) {
        SaveFilterDialogWithIcon(
            onDismiss = { showSaveDialog = false },
            onSave = { name, icon, color, isDefault ->
                onSaveFilter(name, icon, color, isDefault)
                showSaveDialog = false
                onDismiss() // Close the drawer after saving
            }
        )
    }
}

@Composable
private fun TagSelectionDialog(
    availableTags: List<String>,
    selectedTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var tempSelectedTags by remember { mutableStateOf(selectedTags) }

    val filteredTags = remember(availableTags, searchQuery) {
        availableTags.filter { it.contains(searchQuery, ignoreCase = true) }
            .sortedBy { it.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Tags") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search tags") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${tempSelectedTags.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(filteredTags) { tag ->
                        FilterCheckbox(
                            label = tag,
                            checked = tempSelectedTags.contains(tag),
                            onCheckedChange = { checked ->
                                tempSelectedTags = if (checked) {
                                    tempSelectedTags + tag
                                } else {
                                    tempSelectedTags - tag
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(tempSelectedTags) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FilterCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveFilterDialogWithIcon(
    onDismiss: () -> Unit,
    onSave: (String, String, Long?, Boolean) -> Unit // name, icon, color, isDefault
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("Bookmark") }
    var color by remember { mutableStateOf<Long?>(null) }
    var isDefault by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Filter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Name field with Icon picker trigger
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Filter Name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .clickable { showIconPicker = true }
                                .padding(8.dp)
                        ) {
                            FilterIcon(
                                iconName = icon,
                                modifier = Modifier.size(24.dp),
                                tint = if (color != null) androidx.compose.ui.graphics.Color(color!!) else LocalContentColor.current
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Icon",
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Set as default")
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, icon, color, isDefault) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showIconPicker) {
        IconPickerDialog(
            currentIcon = icon,
            currentColor = color,
            onDismiss = { showIconPicker = false },
            onIconSelected = { selectedIcon, selectedColor ->
                icon = selectedIcon
                color = selectedColor
                showIconPicker = false
            }
        )
    }
}
