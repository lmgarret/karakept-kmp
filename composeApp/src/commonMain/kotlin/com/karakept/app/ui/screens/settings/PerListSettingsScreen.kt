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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.ui.components.getIcon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

class PerListSettingsScreenModel(
    private val listId: String,
    private val settingsRepository: SettingsRepository
) : ScreenModel {

    val listSettings: StateFlow<ListSettings> = settingsRepository.getListSettings(listId)
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ListSettings())

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

    fun setScrollAction(action: SwipeAction) {
        screenModelScope.launch {
            settingsRepository.setListSettings(listId, listSettings.value.copy(scrollAction = action))
        }
    }

    fun setIncludeChildListBookmarks(enabled: Boolean) {
        screenModelScope.launch {
            settingsRepository.setListSettings(listId, listSettings.value.copy(includeChildListBookmarks = enabled))
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
        var showScrollActionDialog by remember { mutableStateOf(false) }

        if (showScrollActionDialog) {
            ScrollActionPickerDialog(
                selectedAction = listSettings.scrollAction,
                onDismiss = { showScrollActionDialog = false },
                onActionSelected = { action ->
                    screenModel.setScrollAction(action)
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
                            Text(
                                text = listSettings.scrollAction.displayName,
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
    onDismiss: () -> Unit,
    onActionSelected: (SwipeAction) -> Unit
) {
    // Only offer actions that make sense as scroll triggers (exclude custom config actions)
    val eligibleActions = listOf(
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
                    modifier = Modifier.padding(horizontal = 24.dp, bottom = 8.dp)
                )

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    eligibleActions.forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onActionSelected(action) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = action == selectedAction,
                                onClick = { onActionSelected(action) }
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
