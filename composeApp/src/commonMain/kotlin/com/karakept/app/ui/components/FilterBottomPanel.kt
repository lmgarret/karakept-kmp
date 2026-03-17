package com.karakept.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.karakept.app.ui.utils.buildListHierarchy
import com.karakept.api.model.KarakeepList as KarakeepList
import kotlinx.coroutines.delay

/**
 * Bottom panel for live filter editing with swipe-to-dismiss.
 * Used on compact/mobile layouts.
 */
@Composable
fun FilterBottomPanel(
    visible: Boolean,
    currentFilter: FilterConfig,
    availableTags: List<String>,
    allTags: List<String> = availableTags,
    availableLists: List<KarakeepList>,
    onDismiss: () -> Unit,
    onFilterChange: (FilterConfig) -> Unit,
    onReset: () -> Unit
) {
    var filter by remember(currentFilter) { mutableStateOf(currentFilter) }
    var showTagsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(filter) {
        delay(300)
        onFilterChange(filter)
    }

    BaseBottomPanel(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .wrapContentHeight(Alignment.Top)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = {
                    filter = FilterConfig()
                    onReset()
                }) {
                    Text("Reset")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                FilterPanelContent(
                    filter = filter,
                    onFilterUpdate = { filter = it },
                    availableTags = availableTags,
                    availableLists = availableLists,
                    onShowTagsDialog = { showTagsDialog = true }
                )
            }
        }
    }

    FilterTagsDialog(
        showTagsDialog = showTagsDialog,
        allTags = allTags,
        filter = filter,
        onFilterUpdate = { filter = it },
        onDismiss = { showTagsDialog = false }
    )
}

/**
 * Side panel for live filter editing on desktop/expanded layouts.
 * Renders in the reader pane column instead of as a bottom sheet overlay.
 */
@Composable
fun FilterSidePanel(
    currentFilter: FilterConfig,
    availableTags: List<String>,
    allTags: List<String> = availableTags,
    availableLists: List<KarakeepList>,
    onDismiss: () -> Unit,
    onFilterChange: (FilterConfig) -> Unit,
    onReset: () -> Unit
) {
    var filter by remember(currentFilter) { mutableStateOf(currentFilter) }
    var showTagsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(filter) {
        delay(300)
        onFilterChange(filter)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header with title, reset, and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = {
                        filter = FilterConfig()
                        onReset()
                    }) {
                        Text("Reset")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close filter")
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Scrollable filter content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                FilterPanelContent(
                    filter = filter,
                    onFilterUpdate = { filter = it },
                    availableTags = availableTags,
                    availableLists = availableLists,
                    onShowTagsDialog = { showTagsDialog = true }
                )
            }
        }
    }

    FilterTagsDialog(
        showTagsDialog = showTagsDialog,
        allTags = allTags,
        filter = filter,
        onFilterUpdate = { filter = it },
        onDismiss = { showTagsDialog = false }
    )
}

// ---- Shared internals ----

/**
 * Shared filter content used by both [FilterBottomPanel] and [FilterSidePanel].
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilterPanelContent(
    filter: FilterConfig,
    onFilterUpdate: (FilterConfig) -> Unit,
    availableTags: List<String>,
    availableLists: List<KarakeepList>,
    onShowTagsDialog: () -> Unit
) {
    // Status Filter
    Text("Status", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        FilterChip(
            selected = filter.status == FilterStatus.ALL,
            onClick = { onFilterUpdate(filter.copy(status = FilterStatus.ALL)) },
            label = { Text("All") }
        )
        FilterChip(
            selected = filter.status == FilterStatus.FAVORITES,
            onClick = { onFilterUpdate(filter.copy(status = FilterStatus.FAVORITES)) },
            label = { Text("Favorites") },
            leadingIcon = { Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp)) }
        )
        FilterChip(
            selected = filter.status == FilterStatus.ARCHIVED,
            onClick = { onFilterUpdate(filter.copy(status = FilterStatus.ARCHIVED)) },
            label = { Text("Archived") },
            leadingIcon = { Icon(Icons.Default.Archive, null, modifier = Modifier.size(18.dp)) }
        )
    }

    HorizontalDivider(Modifier.padding(vertical = 8.dp))

    // Sort
    Text("Sort By", style = MaterialTheme.typography.titleMedium)
    FlowRow(
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
                onFilterUpdate(filter.copy(
                    sort = if (filter.sort == SortOption.NEWEST) SortOption.OLDEST else SortOption.NEWEST
                ))
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
                onFilterUpdate(filter.copy(
                    sort = if (filter.sort == SortOption.TITLE_AZ) SortOption.TITLE_ZA else SortOption.TITLE_AZ
                ))
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
                onFilterUpdate(filter.copy(
                    sort = if (filter.sort == SortOption.READING_TIME_SHORT)
                        SortOption.READING_TIME_LONG
                    else
                        SortOption.READING_TIME_SHORT
                ))
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

    // Lists — shown in sorted hierarchical order matching the navigation drawer
    if (availableLists.isNotEmpty()) {
        Text("Lists", style = MaterialTheme.typography.titleMedium)
        val hierarchy = remember(availableLists) { buildListHierarchy(availableLists) }
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            hierarchy.forEach { (list, depth) ->
                val listId = list.id ?: ""
                val isSelected = filter.lists.contains(listId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newLists = if (isSelected) {
                                filter.lists - listId
                            } else {
                                filter.lists + listId
                            }
                            onFilterUpdate(filter.copy(lists = newLists))
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width((depth * 16).dp))
                    val icon = list.icon ?: ""
                    if (icon.isNotBlank()) {
                        Text(
                            text = icon,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = list.name ?: "Untitled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            sortedTags.forEach { tagWithCount ->
                val tagName = tagWithCount.substringBefore(" (").trim()
                val isSelected = filter.tags.contains(tagName)
                TagChip(
                    tag = tagName,
                    selected = isSelected,
                    onClick = {
                        val newTags = if (isSelected) {
                            filter.tags - tagName
                        } else {
                            filter.tags + tagName
                        }
                        onFilterUpdate(filter.copy(tags = newTags))
                    }
                )
            }
            TextButton(onClick = onShowTagsDialog) {
                Text("More tags...")
            }
        }
    }
}

/**
 * Tag selection dialog shared by both panel variants.
 */
@Composable
private fun FilterTagsDialog(
    showTagsDialog: Boolean,
    allTags: List<String>,
    filter: FilterConfig,
    onFilterUpdate: (FilterConfig) -> Unit,
    onDismiss: () -> Unit
) {
    if (showTagsDialog) {
        val cleanAllTags = remember(allTags) {
            allTags.map { it.substringBefore(" (").trim() }.distinct().sorted()
        }
        TagEditorDialog(
            currentTags = filter.tags,
            availableTags = cleanAllTags,
            canCreateNew = false,
            title = "Filter by Tags",
            confirmLabel = "Apply",
            onTagsUpdated = { selectedTags ->
                onFilterUpdate(filter.copy(tags = selectedTags))
                onDismiss()
            },
            onDismiss = onDismiss
        )
    }
}
