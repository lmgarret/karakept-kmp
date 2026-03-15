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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import androidx.compose.material3.HorizontalDivider
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.CustomSwipeActionType
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.components.ReadingSpeedDialog
import com.karakept.app.ui.components.getIcon
import com.karakept.app.ui.screens.SettingsScreenModel
import com.karakept.app.ui.utils.buildListHierarchy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState

class BookmarkListSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SettingsScreenModel>()
        val readingSpeedWpm by screenModel.readingSpeedWpm.collectAsState()
        val notificationsEnabled by screenModel.notificationsEnabled.collectAsState()
        val swipeLeftAction by screenModel.swipeLeftAction.collectAsState()
        val swipeRightAction by screenModel.swipeRightAction.collectAsState()
        val customConfigs by screenModel.customSwipeActionConfigs.collectAsState()
        val swipeLeftConfigId by screenModel.swipeLeftConfigId.collectAsState()
        val swipeRightConfigId by screenModel.swipeRightConfigId.collectAsState()
        val defaultListType by screenModel.defaultListType.collectAsState()
        val defaultListId by screenModel.defaultListId.collectAsState()
        val availableLists by screenModel.availableLists.collectAsState()
        var showListPickerDialog by remember { mutableStateOf(false) }
        var showReadingSpeedDialog by remember { mutableStateOf(false) }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            screenModel.fetchAvailableLists()
        }

        if (showListPickerDialog) {
            val listSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showListPickerDialog = false },
                sheetState = listSheetState
            ) {
                Text(
                    text = "Select Default List",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                if (availableLists.isEmpty()) {
                    Text(
                        text = "No lists available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val hierarchy = remember(availableLists) { buildListHierarchy(availableLists) }
                    LazyColumn {
                        items(hierarchy) { (list, depth) ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = "${list.icon ?: ""} ${list.name ?: list.id ?: ""}".trim(),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingContent = {
                                    RadioButton(
                                        selected = list.id == defaultListId,
                                        onClick = {
                                            screenModel.setDefaultListType(DefaultListType.SPECIFIC_LIST)
                                            screenModel.setDefaultListId(list.id)
                                            showListPickerDialog = false
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .padding(start = (depth * 16).dp)
                                    .clickable {
                                        screenModel.setDefaultListType(DefaultListType.SPECIFIC_LIST)
                                        screenModel.setDefaultListId(list.id)
                                        showListPickerDialog = false
                                    }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp).navigationBarsPadding())
            }
        }

        // Show reading speed dialog
        if (showReadingSpeedDialog) {
            ReadingSpeedDialog(
                currentWpm = readingSpeedWpm,
                onDismiss = { showReadingSpeedDialog = false },
                onConfirm = { newWpm ->
                    screenModel.setReadingSpeedWpm(newWpm)
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Bookmark List") },
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
                // Default View Section
                Text(
                    text = "Default View",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "The list shown when the app opens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        DefaultViewOption(
                            title = "All Bookmarks",
                            isSelected = defaultListType == DefaultListType.ALL_BOOKMARKS,
                            onClick = {
                                screenModel.setDefaultListType(DefaultListType.ALL_BOOKMARKS)
                                screenModel.setDefaultListId(null)
                            }
                        )
                        HorizontalDivider()
                        DefaultViewOption(
                            title = "Favorites",
                            isSelected = defaultListType == DefaultListType.FAVORITES,
                            onClick = {
                                screenModel.setDefaultListType(DefaultListType.FAVORITES)
                                screenModel.setDefaultListId(null)
                            }
                        )
                        HorizontalDivider()
                        DefaultViewOption(
                            title = "Archived",
                            isSelected = defaultListType == DefaultListType.ARCHIVED,
                            onClick = {
                                screenModel.setDefaultListType(DefaultListType.ARCHIVED)
                                screenModel.setDefaultListId(null)
                            }
                        )
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showListPickerDialog = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultListType == DefaultListType.SPECIFIC_LIST,
                                onClick = { showListPickerDialog = true }
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(
                                    text = "Specific List",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (defaultListType == DefaultListType.SPECIFIC_LIST) {
                                    val selectedListName = availableLists.find { it.id == defaultListId }?.name
                                        ?: defaultListId
                                        ?: "None selected"
                                    Text(
                                        text = selectedListName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Select list"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Gestures Section
                Text(
                    text = "Gestures",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                SwipeActionSettingItem(
                    title = "Swipe Right (Left to Right)",
                    description = "Action when swiping from left to right",
                    selectedAction = swipeRightAction,
                    selectedConfigId = swipeRightConfigId,
                    customConfigs = customConfigs,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onActionSelected = { screenModel.setSwipeRightAction(it) },
                    onConfigSelected = { screenModel.setSwipeRightConfigId(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SwipeActionSettingItem(
                    title = "Swipe Left (Right to Left)",
                    description = "Action when swiping from right to left",
                    selectedAction = swipeLeftAction,
                    selectedConfigId = swipeLeftConfigId,
                    customConfigs = customConfigs,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onActionSelected = { screenModel.setSwipeLeftAction(it) },
                    onConfigSelected = { screenModel.setSwipeLeftConfigId(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Manage Custom Actions
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigator.push(CustomSwipeActionsScreen()) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Manage Custom Actions",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Create Add Tag and Add to List actions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Reading Speed",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Reading Speed Setting
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showReadingSpeedDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reading Speed",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "$readingSpeedWpm words per minute",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Adjust"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Notifications Section
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
                                text = "Enable Notifications",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Show notifications when a bookmark is saved",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { screenModel.setNotificationsEnabled(it) }
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun DefaultViewOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionSettingItem(
    title: String,
    description: String,
    selectedAction: SwipeAction,
    selectedConfigId: String?,
    customConfigs: List<CustomSwipeActionConfig>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onActionSelected: (SwipeAction) -> Unit,
    onConfigSelected: (String?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    // Step 2: when ADD_TAG/ADD_TO_LIST is picked from step 1, show config picker
    var pendingAction by remember { mutableStateOf<SwipeAction?>(null) }

    val selectedConfig = customConfigs.find { it.id == selectedConfigId }

    // Step 2 sheet: pick a custom config for the pending action type
    if (pendingAction != null) {
        val filteredConfigs = customConfigs.filter { config ->
            when (pendingAction) {
                SwipeAction.ADD_TAG -> config.type == CustomSwipeActionType.ADD_TAG
                SwipeAction.ADD_TO_LIST -> config.type == CustomSwipeActionType.ADD_TO_LIST
                else -> false
            }
        }
        val pendingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { pendingAction = null },
            sheetState = pendingSheetState
        ) {
            Text(
                text = "Select Custom Action",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            if (filteredConfigs.isEmpty()) {
                Text(
                    text = "No custom actions of this type yet.\nGo to Manage Custom Actions to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(filteredConfigs) { config ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = config.getDisplayName(),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = config.id == selectedConfigId,
                                    onClick = {
                                        onActionSelected(pendingAction!!)
                                        onConfigSelected(config.id)
                                        pendingAction = null
                                        showDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                onActionSelected(pendingAction!!)
                                onConfigSelected(config.id)
                                pendingAction = null
                                showDialog = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp).navigationBarsPadding())
        }
    }

    // Step 1 sheet: pick the action type
    if (showDialog) {
        val actionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDialog = false },
            sheetState = actionSheetState
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            LazyColumn {
                items(SwipeAction.entries.toList()) { action ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = action.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = action.getIcon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = if (action == selectedAction &&
                            (action != SwipeAction.ADD_TAG && action != SwipeAction.ADD_TO_LIST ||
                                selectedConfig == null)
                        ) {
                            { RadioButton(selected = true, onClick = null) }
                        } else null,
                        modifier = Modifier.clickable {
                            if (action == SwipeAction.ADD_TAG || action == SwipeAction.ADD_TO_LIST) {
                                pendingAction = action
                            } else {
                                onActionSelected(action)
                                onConfigSelected(null)
                                showDialog = false
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp).navigationBarsPadding())
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = when {
                        (selectedAction == SwipeAction.ADD_TAG || selectedAction == SwipeAction.ADD_TO_LIST) &&
                            selectedConfig != null -> selectedConfig.getDisplayName()
                        else -> selectedAction.displayName
                    },
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
}

