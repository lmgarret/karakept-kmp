package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import getPlatform
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.setListSettings
import com.karakept.app.data.repository.setListLayoutId
import com.karakept.app.ui.components.getIcon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

class PerListSettingsScreenModel(
    private val listId: String,
    private val settingsRepository: SettingsRepository
) : ScreenModel {

    val listSettings: StateFlow<ListSettings> = settingsRepository.getListSettings(listId)
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ListSettings())

    val customSwipeActionConfigs: StateFlow<List<CustomSwipeActionConfig>> =
        settingsRepository.customSwipeActionConfigs
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLayouts: StateFlow<List<BookmarkLayout>> = combine(
        settingsRepository.customLayouts,
        settingsRepository.defaultLayoutId
    ) { custom, _ ->
        BookmarkLayout.ALL_BUILTIN + custom
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), BookmarkLayout.ALL_BUILTIN)

    fun setSyncOffline(enabled: Boolean) {
        screenModelScope.launch {
            settingsRepository.setListSettings(listId, listSettings.value.copy(syncOffline = enabled))
        }
    }

    fun setNotifyOnNewBookmarks(enabled: Boolean) {
        screenModelScope.launch {
            settingsRepository.setListSettings(listId, listSettings.value.copy(notifyOnNewBookmarks = enabled))
        }
    }

    fun setScrollAction(action: SwipeAction, configId: String? = null) {
        screenModelScope.launch {
            settingsRepository.setListSettings(
                listId,
                listSettings.value.copy(scrollAction = action, scrollActionConfigId = configId)
            )
        }
    }

    fun setIncludeChildListBookmarks(enabled: Boolean) {
        screenModelScope.launch {
            settingsRepository.setListSettings(listId, listSettings.value.copy(includeChildListBookmarks = enabled))
        }
    }

    fun setCountOnlyUnread(enabled: Boolean) {
        screenModelScope.launch {
            settingsRepository.setListSettings(listId, listSettings.value.copy(countOnlyUnread = enabled))
        }
    }

    fun setLayoutId(layoutId: String?) {
        screenModelScope.launch {
            settingsRepository.setListLayoutId(listId, layoutId)
        }
    }
}

data class PerListSettingsScreen(
    val listId: String,
    val listName: String
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<PerListSettingsScreenModel> { parametersOf(listId) }
        val listSettings by screenModel.listSettings.collectAsState()
        val customConfigs by screenModel.customSwipeActionConfigs.collectAsState()
        val allLayouts by screenModel.allLayouts.collectAsState()
        var showScrollActionDialog by remember { mutableStateOf(false) }
        var showLayoutPickerDialog by remember { mutableStateOf(false) }
        var showLayoutEditorDialog by remember { mutableStateOf(false) }
        val isDesktop = getPlatform().isDesktop

        if (showLayoutEditorDialog && isDesktop) {
            LayoutEditorDialog(
                layoutId = null,
                onDismiss = { showLayoutEditorDialog = false }
            )
        }

        if (showLayoutPickerDialog) {
            AlertDialog(
                onDismissRequest = { showLayoutPickerDialog = false },
                title = { Text("Layout") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        // "Default" option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    screenModel.setLayoutId(null)
                                    showLayoutPickerDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = listSettings.layoutId == null,
                                onClick = {
                                    screenModel.setLayoutId(null)
                                    showLayoutPickerDialog = false
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("Use default layout", style = MaterialTheme.typography.bodyLarge)
                                Text("Follows the app-wide default", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        allLayouts.forEach { layout ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        screenModel.setLayoutId(layout.id)
                                        showLayoutPickerDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = listSettings.layoutId == layout.id,
                                    onClick = {
                                        screenModel.setLayoutId(layout.id)
                                        showLayoutPickerDialog = false
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(layout.name, style = MaterialTheme.typography.bodyLarge)
                                    if (layout.description != null) {
                                        Text(layout.description, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                        TextButton(
                            onClick = {
                                showLayoutPickerDialog = false
                                if (isDesktop) {
                                    showLayoutEditorDialog = true
                                } else {
                                    navigator.push(LayoutEditorScreen(layoutId = null))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Create new layout",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLayoutPickerDialog = false }) { Text("Close") }
                }
            )
        }

        if (showScrollActionDialog) {
            ScrollActionPickerDialog(
                selectedAction = listSettings.scrollAction,
                selectedConfigId = listSettings.scrollActionConfigId,
                customConfigs = customConfigs,
                onDismiss = { showScrollActionDialog = false },
                onActionSelected = { action, configId ->
                    screenModel.setScrollAction(action, configId)
                    showScrollActionDialog = false
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("$listName Settings") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "Sync",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sync offline",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Pre-fetch and cache all bookmark content for offline access",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = listSettings.syncOffline,
                            onCheckedChange = { screenModel.setSyncOffline(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notify on new bookmarks",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Send a notification when a new bookmark is added to this list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = listSettings.notifyOnNewBookmarks,
                            onCheckedChange = { screenModel.setNotifyOnNewBookmarks(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Display",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Per-list layout
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLayoutPickerDialog = true }
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Layout",
                                style = MaterialTheme.typography.titleMedium
                            )
                            val layoutName = listSettings.layoutId?.let { id ->
                                if (BookmarkLayout.isBuiltInId(id)) {
                                    BookmarkLayout.getBuiltIn(id)?.name
                                } else {
                                    allLayouts.find { it.id == id }?.name
                                }
                            } ?: "Use default layout"
                            Text(
                                text = layoutName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Select"
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Count only unread",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Show only the count of unread bookmarks in the sidebar badge",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = listSettings.countOnlyUnread,
                            onCheckedChange = { screenModel.setCountOnlyUnread(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Scroll Behavior",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showScrollActionDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "On scroll action",
                                style = MaterialTheme.typography.titleMedium
                            )
                            val scrollActionLabel = if (listSettings.scrollAction == SwipeAction.NONE) {
                                SwipeAction.NONE.displayName
                            } else {
                                val configId = listSettings.scrollActionConfigId
                                if (configId != null) {
                                    // Custom action label is shown in the dialog,
                                    // display the action type for now
                                    listSettings.scrollAction.displayName
                                } else {
                                    listSettings.scrollAction.displayName
                                }
                            }
                            Text(
                                text = scrollActionLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Select"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Content",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include bookmarks from sub-lists",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Also show bookmarks from all nested child lists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = listSettings.includeChildListBookmarks,
                            onCheckedChange = { screenModel.setIncludeChildListBookmarks(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollActionPickerDialog(
    selectedAction: SwipeAction,
    selectedConfigId: String?,
    customConfigs: List<CustomSwipeActionConfig>,
    onDismiss: () -> Unit,
    onActionSelected: (SwipeAction, String?) -> Unit
) {
    val standardActions = listOf(
        SwipeAction.NONE,
        SwipeAction.MARK_READ,
        SwipeAction.ARCHIVE,
        SwipeAction.FAVOURITE
    )

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = "On scroll action",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Text(
                    text = "Triggered automatically when a bookmark scrolls off screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                )

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // Standard actions
                    standardActions.forEach { action ->
                        val isSelected = selectedConfigId == null && action == selectedAction
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onActionSelected(action, null) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onActionSelected(action, null) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = action.getIcon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = action.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Custom actions (if any)
                    if (customConfigs.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Custom actions",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                        customConfigs.forEach { config ->
                            val swipeAction = when (config.type) {
                                com.karakept.app.data.model.CustomSwipeActionType.ADD_TAG -> SwipeAction.ADD_TAG
                                com.karakept.app.data.model.CustomSwipeActionType.ADD_TO_LIST -> SwipeAction.ADD_TO_LIST
                            }
                            val isSelected = selectedConfigId == config.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onActionSelected(swipeAction, config.id) }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onActionSelected(swipeAction, config.id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = swipeAction.getIcon(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = config.getDisplayName(),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 24.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
