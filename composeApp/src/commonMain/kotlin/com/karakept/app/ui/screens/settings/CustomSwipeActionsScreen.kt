package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.CustomSwipeActionType
import com.karakept.app.ui.screens.SettingsScreenModel
import com.karakept.api.model.KarakeepList

private val PRESET_COLORS = listOf(
    "#009688" to "Teal",
    "#673AB7" to "Deep Purple",
    "#E91E63" to "Pink",
    "#FF5722" to "Deep Orange",
    "#2196F3" to "Blue",
    "#4CAF50" to "Green",
    "#FF9800" to "Orange",
    "#795548" to "Brown"
)

class CustomSwipeActionsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SettingsScreenModel>()
        val configs by screenModel.customSwipeActionConfigs.collectAsState()
        val availableLists by screenModel.availableLists.collectAsState()

        LaunchedEffect(Unit) {
            screenModel.fetchAvailableLists()
        }

        var showAddDialog by remember { mutableStateOf(false) }
        var editingConfig by remember { mutableStateOf<CustomSwipeActionConfig?>(null) }
        var confirmDeleteConfig by remember { mutableStateOf<CustomSwipeActionConfig?>(null) }

        if (showAddDialog || editingConfig != null) {
            CustomActionDialog(
                existing = editingConfig,
                availableLists = availableLists,
                onDismiss = {
                    showAddDialog = false
                    editingConfig = null
                },
                onSave = { config ->
                    if (editingConfig != null) {
                        screenModel.updateCustomSwipeActionConfig(config)
                    } else {
                        screenModel.addCustomSwipeActionConfig(config)
                    }
                    showAddDialog = false
                    editingConfig = null
                }
            )
        }

        if (confirmDeleteConfig != null) {
            AlertDialog(
                onDismissRequest = { confirmDeleteConfig = null },
                title = { Text("Delete Action") },
                text = { Text("Remove \"${confirmDeleteConfig!!.getDisplayName()}\"?") },
                confirmButton = {
                    TextButton(onClick = {
                        screenModel.removeCustomSwipeActionConfig(confirmDeleteConfig!!.id)
                        confirmDeleteConfig = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteConfig = null }) { Text("Cancel") }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Custom Swipe Actions") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add custom action")
                }
            }
        ) { padding ->
            if (configs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No custom actions yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap + to create one",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        Text(
                            text = "Create multiple custom actions and assign them to swipe directions in Bookmark List settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    items(configs) { config ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (config.type == CustomSwipeActionType.ADD_TAG)
                                        Icons.Default.Label else Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    tint = config.colorHex?.let { parseColor(it) }
                                        ?: if (config.type == CustomSwipeActionType.ADD_TAG)
                                            Color(0xFF009688) else Color(0xFF673AB7),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = config.getDisplayName(),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = when (config.type) {
                                            CustomSwipeActionType.ADD_TAG -> "Adds tag: ${config.tagName ?: "(none)"}"
                                            CustomSwipeActionType.ADD_TO_LIST -> "Adds to: ${config.listName ?: config.listId ?: "(none)"}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editingConfig = config }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { confirmDeleteConfig = config }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CustomActionDialog(
    existing: CustomSwipeActionConfig?,
    availableLists: List<KarakeepList>,
    onDismiss: () -> Unit,
    onSave: (CustomSwipeActionConfig) -> Unit
) {
    var selectedType by remember { mutableStateOf(existing?.type ?: CustomSwipeActionType.ADD_TAG) }
    var tagName by remember { mutableStateOf(existing?.tagName ?: "") }
    var selectedListId by remember { mutableStateOf(existing?.listId) }
    var selectedListName by remember { mutableStateOf(existing?.listName) }
    var customName by remember { mutableStateOf(existing?.customName ?: "") }
    var selectedColorHex by remember { mutableStateOf(existing?.colorHex) }
    var showListPicker by remember { mutableStateOf(false) }

    if (showListPicker) {
        AlertDialog(
            onDismissRequest = { showListPicker = false },
            title = { Text("Pick a List") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    availableLists.forEach { list ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedListId = list.id
                                    selectedListName = list.name
                                    showListPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = list.id == selectedListId,
                                onClick = {
                                    selectedListId = list.id
                                    selectedListName = list.name
                                    showListPicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = list.name ?: list.id ?: "", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showListPicker = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Action" else "New Custom Action") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Action type
                Text(
                    text = "Action type",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedType == CustomSwipeActionType.ADD_TAG,
                        onClick = { selectedType = CustomSwipeActionType.ADD_TAG }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Tag", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = selectedType == CustomSwipeActionType.ADD_TO_LIST,
                        onClick = { selectedType = CustomSwipeActionType.ADD_TO_LIST }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add to List", style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Type-specific input
                if (selectedType == CustomSwipeActionType.ADD_TAG) {
                    OutlinedTextField(
                        value = tagName,
                        onValueChange = { tagName = it },
                        label = { Text("Tag name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showListPicker = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedListName ?: selectedListId ?: "Select a list…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selectedListId != null)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Optional custom name
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Custom name (optional)") },
                    placeholder = {
                        Text(
                            text = if (selectedType == CustomSwipeActionType.ADD_TAG && tagName.isNotBlank())
                                "Add tag '$tagName'"
                            else if (selectedType == CustomSwipeActionType.ADD_TO_LIST && selectedListName != null)
                                "Add to '${selectedListName}'"
                            else "Auto-generated",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Color picker
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // No color option
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (selectedColorHex == null) 3.dp else 1.dp,
                                color = if (selectedColorHex == null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedColorHex = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", style = MaterialTheme.typography.labelSmall)
                    }
                    PRESET_COLORS.forEach { (hex, name) ->
                        val color = parseColor(hex)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (selectedColorHex == hex) 3.dp else 1.dp,
                                    color = if (selectedColorHex == hex)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        Color.Transparent,
                                    shape = CircleShape
                                )
                                .background(color)
                                .clickable { selectedColorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val isValid = when (selectedType) {
                        CustomSwipeActionType.ADD_TAG -> tagName.isNotBlank()
                        CustomSwipeActionType.ADD_TO_LIST -> selectedListId != null
                    }
                    if (isValid) {
                        val config = CustomSwipeActionConfig(
                            id = existing?.id ?: generateId(),
                            type = selectedType,
                            tagName = if (selectedType == CustomSwipeActionType.ADD_TAG) tagName.trim() else null,
                            listId = if (selectedType == CustomSwipeActionType.ADD_TO_LIST) selectedListId else null,
                            listName = if (selectedType == CustomSwipeActionType.ADD_TO_LIST) selectedListName else null,
                            customName = customName.trim().takeIf { it.isNotBlank() },
                            colorHex = selectedColorHex
                        )
                        onSave(config)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parseColor(hex: String): Color {
    return try {
        val cleaned = hex.trimStart('#')
        val colorLong = cleaned.toLong(16)
        val (r, g, b) = Triple(
            ((colorLong shr 16) and 0xFF).toInt(),
            ((colorLong shr 8) and 0xFF).toInt(),
            (colorLong and 0xFF).toInt()
        )
        Color(r, g, b)
    } catch (e: Exception) {
        Color.Gray
    }
}

private fun generateId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return (1..12).map { chars.random() }.joinToString("")
}
