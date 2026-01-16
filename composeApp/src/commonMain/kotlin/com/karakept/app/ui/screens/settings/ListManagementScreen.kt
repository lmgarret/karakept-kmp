package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.CheckboxState
import com.karakept.app.data.model.ListSyncConfig
import com.karakept.app.data.model.SyncStrategy
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListManagementScreenModel(
    private val listRepository: ListRepository,
    private val settingsRepository: SettingsRepository
) : ScreenModel {
    val lists: StateFlow<List<KarakeepList>> = listRepository.lists

    val syncConfig: StateFlow<ListSyncConfig> = settingsRepository.contentSyncConfig
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ListSyncConfig(emptySet(), emptySet()))

    fun cycleListSyncState(listId: String, currentState: CheckboxState) {
        screenModelScope.launch {
            val newState = currentState.next()
            settingsRepository.updateListSyncState(listId, newState)
        }
    }

    fun hasChildren(listId: String, allLists: List<KarakeepList>): Boolean {
        return allLists.any { it.parentId == listId }
    }
}

class ListManagementScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ListManagementScreenModel>()
        val lists by screenModel.lists.collectAsState()
        val syncConfig by screenModel.syncConfig.collectAsState()

        // Build hierarchy
        val hierarchicalLists = remember(lists) {
            buildHierarchy(lists)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Content Sync Lists") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
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
            ) {
                if (lists.isEmpty()) {
                     item {
                         Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                             Text("No lists found")
                         }
                     }
                } else {
                    items(hierarchicalLists) { (list, depth) ->
                        val hasChildren = remember(lists) { screenModel.hasChildren(list.id ?: "", lists) }
                        val currentState = syncConfig.getCheckboxState(list.id ?: "")

                        ListItemRow(
                            list = list,
                            depth = depth,
                            syncConfig = syncConfig,
                            hasChildren = hasChildren,
                            allLists = lists,
                            onCycleState = { state -> screenModel.cycleListSyncState(list.id ?: "", state) }
                        )
                        Divider()
                    }
                }
            }
        }
    }

    @Composable
    private fun ThreeStateCheckbox(
        state: CheckboxState,
        enabled: Boolean,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                CheckboxState.UNCHECKED -> {
                    Checkbox(
                        checked = false,
                        onCheckedChange = { if (enabled) onClick() },
                        enabled = enabled
                    )
                }
                CheckboxState.CHECKED_PARENT_ONLY -> {
                    Checkbox(
                        checked = true,
                        onCheckedChange = { if (enabled) onClick() },
                        enabled = enabled
                    )
                }
                CheckboxState.CHECKED_WITH_CHILDREN -> {
                    // Checkbox with children indicator
                    Checkbox(
                        checked = true,
                        onCheckedChange = { if (enabled) onClick() },
                        enabled = enabled
                    )
                    // Small badge indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ListItemRow(
        list: KarakeepList,
        depth: Int,
        syncConfig: ListSyncConfig,
        hasChildren: Boolean,
        allLists: List<KarakeepList>,
        onCycleState: (CheckboxState) -> Unit
    ) {
        val checkboxState = syncConfig.getCheckboxState(list.id ?: "")
        val isDisabled = syncConfig.isParentInWithChildrenMode(list.parentId, allLists)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isDisabled) { onCycleState(checkboxState) }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width((depth * 24).dp))

            // Show list icon from ListDto.icon field
            Text(
                text = list.icon ?: "",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = list.name ?: "Untitled",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                    // Show indicator if syncing with children
                    if (checkboxState == CheckboxState.CHECKED_WITH_CHILDREN) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Syncing with children",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                val desc = list.description
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDisabled) 0.6f else 1f)
                    )
                }
                // Show child indicator text if list has children
                if (hasChildren && checkboxState != CheckboxState.UNCHECKED) {
                    Text(
                        text = when (checkboxState) {
                            CheckboxState.CHECKED_PARENT_ONLY -> "Parent list only"
                            CheckboxState.CHECKED_WITH_CHILDREN -> "Including all child lists"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // Show info if disabled by parent
                if (isDisabled) {
                    Text(
                        text = "Auto-synced by parent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Three-state checkbox
            ThreeStateCheckbox(
                state = if (isDisabled) CheckboxState.CHECKED_PARENT_ONLY else checkboxState,
                enabled = !isDisabled,
                onClick = { onCycleState(checkboxState) }
            )
        }
    }

    private fun buildHierarchy(lists: List<KarakeepList>): List<Pair<KarakeepList, Int>> {
        val result = mutableListOf<Pair<KarakeepList, Int>>()
        val grouped = lists.groupBy { it.parentId }
        
        fun recurse(parentId: String?, depth: Int) {
            val children = grouped[parentId] ?: return
            children.forEach { child ->
                result.add(child to depth)
                recurse(child.id, depth + 1)
            }
        }
        
        // Start with root items (parentId is null or empty)
        // Note: Some systems use "0" or empty string for root. 
        // Assuming null based on DTO. 
        // Also handling cases where parentId might technically be present but parent not in list (orphans currently treated as roots?)
        // Better: Find all items whose parent is NOT in the list.
        val allIds = lists.map { it.id }.toSet()
        val roots = lists.filter { it.parentId == null || it.parentId !in allIds }
        
        // Only recurse for legitimate roots if we trust parentId, but for now let's just use the roots we found.
        // Actually, if we just recurse from null, we might miss orphans if parentId is "root" or something.
        // Let's assume null is root.
        
        recurse(null, 0)
        
        // If there are leftovers (lists with weird parentIds), add them at root level?
        val processed = result.map { it.first.id }.toSet()
        val leftovers = lists.filter { it.id !in processed }
        leftovers.forEach { list ->
            result.add(list to 0)
            // And their children? 
            // This is complex. Let's simplify: 
            // Just use recursive generic build. If parent not found, it's a root.
        }
        
        return result
    }
}
