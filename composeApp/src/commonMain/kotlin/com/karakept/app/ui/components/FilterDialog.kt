package com.karakept.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.SortOption
import com.karakept.app.data.remote.model.ListDto

@OptIn(ExperimentalLayoutApi::class)
@ExperimentalMaterial3Api
@Composable
fun FilterDialog(
    currentFilter: FilterConfig,
    availableTags: List<String>,
    availableLists: List<ListDto>,
    onDismiss: () -> Unit,
    onApply: (FilterConfig) -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var filter by remember { mutableStateOf(currentFilter) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Bookmarks") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status Section
                Text("Status", style = MaterialTheme.typography.titleSmall)
                FilterChipGroup(
                    items = FilterStatus.entries.map { it.name },
                    selectedItem = filter.status.name,
                    onItemSelected = { selected ->
                        filter = filter.copy(status = FilterStatus.valueOf(selected))
                    }
                )

                // Tags Section - Collapsible with modal
                if (availableTags.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tags", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { showTagsDialog = true }) {
                            Text("${filter.tags.size} selected")
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp).padding(start = 4.dp))
                        }
                    }
                    if (filter.tags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            filter.tags.forEach { tag ->
                                FilterChip(
                                    selected = true,
                                    onClick = { filter = filter.copy(tags = filter.tags - tag) },
                                    label = { Text(tag, style = MaterialTheme.typography.bodySmall) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                }

                // Lists Section
                if (availableLists.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Lists", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableLists.take(5).forEach { list ->
                            FilterCheckbox(
                                label = "${list.icon} ${list.name}",
                                checked = filter.lists.contains(list.id),
                                onCheckedChange = { checked ->
                                    filter = if (checked) {
                                        filter.copy(lists = filter.lists + list.id)
                                    } else {
                                        filter.copy(lists = filter.lists - list.id)
                                    }
                                }
                            )
                        }
                        if (availableLists.size > 5) {
                            Text(
                                "...and ${availableLists.size - 5} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Sort Section
                HorizontalDivider()
                Text("Sort By", style = MaterialTheme.typography.titleSmall)
                SortChipGroup(
                    selectedSort = filter.sort,
                    onSortSelected = { filter = filter.copy(sort = it) }
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    filter = FilterConfig()
                }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear filters")
                }
                TextButton(onClick = { showSaveDialog = true }) {
                    Text("Save")
                }
                Button(onClick = {
                    onApply(filter)
                    onDismiss()
                }) {
                    Text("Apply")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showTagsDialog) {
        TagSelectionDialog(
            availableTags = availableTags,
            selectedTags = filter.tags,
            onDismiss = { showTagsDialog = false },
            onConfirm = { selectedTags ->
                filter = filter.copy(tags = selectedTags)
                showTagsDialog = false
            }
        )
    }

    if (showSaveDialog) {
        SaveFilterDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name, isDefault ->
                onApply(filter)
                onSave(name, isDefault)
                showSaveDialog = false
                onDismiss()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipGroup(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selectedItem,
                onClick = { onItemSelected(item) },
                label = { Text(item.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
                leadingIcon = if (item == selectedItem) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortChipGroup(
    selectedSort: SortOption,
    onSortSelected: (SortOption) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SortOption.entries.forEach { sort ->
            val icon = when (sort) {
                SortOption.NEWEST -> Icons.Default.ArrowDownward
                SortOption.OLDEST -> Icons.Default.ArrowUpward
                SortOption.TITLE_AZ -> Icons.Default.SortByAlpha
                SortOption.TITLE_ZA -> Icons.Default.SortByAlpha
            }
            FilterChip(
                selected = sort == selectedSort,
                onClick = { onSortSelected(sort) },
                label = { Text(sort.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
                leadingIcon = {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }
    }
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
private fun SaveFilterDialog(
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Filter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Filter Name") },
                    modifier = Modifier.fillMaxWidth()
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
                onClick = { onSave(name, isDefault) },
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
}
