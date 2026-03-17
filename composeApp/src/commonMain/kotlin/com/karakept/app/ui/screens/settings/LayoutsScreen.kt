package com.karakept.app.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import getPlatform
import org.koin.compose.koinInject

class LayoutsScreenModel(
    private val settingsRepository: SettingsRepository
) : ScreenModel {

    data class LayoutsState(
        val allLayouts: List<BookmarkLayout> = emptyList(),
        val defaultLayoutId: String? = null
    )

    val state: StateFlow<LayoutsState> = combine(
        settingsRepository.customLayouts,
        settingsRepository.defaultLayoutId
    ) { custom, defaultId ->
        LayoutsState(
            allLayouts = BookmarkLayout.ALL_BUILTIN + custom,
            defaultLayoutId = defaultId
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), LayoutsState())

    fun setDefaultLayout(id: String?) {
        screenModelScope.launch {
            settingsRepository.setDefaultLayoutId(id)
        }
    }

    fun deleteLayout(id: String) {
        screenModelScope.launch {
            settingsRepository.deleteLayout(id)
        }
    }
}

class LayoutsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        LayoutsContent(
            onBack = { navigator.pop() },
            onNavigate = { navigator.push(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutsContent(
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    showBackButton: Boolean = true
) {
    val screenModel = koinInject<LayoutsScreenModel>()
    val state by screenModel.state.collectAsState()
    var pendingDeleteLayout by remember { mutableStateOf<BookmarkLayout?>(null) }
    // Desktop: show layout editor as dialog instead of navigating to a 4th screen
    var editingLayoutId by remember { mutableStateOf<String?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    val isDesktop = getPlatform().isDesktop

    if (showEditorDialog && isDesktop) {
        LayoutEditorDialog(
            layoutId = editingLayoutId,
            onDismiss = { showEditorDialog = false }
        )
    }

    if (pendingDeleteLayout != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteLayout = null },
            title = { Text("Delete Layout") },
            text = { Text("Delete \"${pendingDeleteLayout!!.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        screenModel.deleteLayout(pendingDeleteLayout!!.id)
                        pendingDeleteLayout = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteLayout = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Layouts") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (isDesktop) {
                    editingLayoutId = null
                    showEditorDialog = true
                } else {
                    onNavigate(LayoutEditorScreen(layoutId = null))
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Create layout")
            }
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
                text = "Choose a layout to use by default. You can also assign a layout per list.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Default display settings (global fallback)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(DefaultDisplaySettingsScreen()) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Default Display Settings",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Applied when no layout is active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Edit"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

            // "No layout (use global settings)" option
            LayoutCard(
                layout = null,
                isDefault = state.defaultLayoutId == null,
                onSelect = { screenModel.setDefaultLayout(null) },
                onEdit = null,
                onDelete = null
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            state.allLayouts.forEach { layout ->
                LayoutCard(
                    layout = layout,
                    isDefault = state.defaultLayoutId == layout.id,
                    onSelect = { screenModel.setDefaultLayout(layout.id) },
                    onEdit = if (!layout.isBuiltIn) {
                        {
                            if (isDesktop) {
                                editingLayoutId = layout.id
                                showEditorDialog = true
                            } else {
                                onNavigate(LayoutEditorScreen(layoutId = layout.id))
                            }
                        }
                    } else null,
                    onDelete = if (!layout.isBuiltIn) {
                        { pendingDeleteLayout = layout }
                    } else null
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LayoutCard(
    layout: BookmarkLayout?,
    isDefault: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = if (isDefault) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (layout?.icon) {
                    "Window" -> Icons.Default.Window
                    "ViewList" -> Icons.AutoMirrored.Filled.ViewList
                    "List" -> Icons.AutoMirrored.Filled.List
                    else -> Icons.Default.Layers
                },
                contentDescription = null,
                tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 4.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = layout?.name ?: "Global Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (layout != null) {
                    Text(
                        text = layout.description ?: layoutSummary(layout),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Use the default display settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isDefault) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Default",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun layoutSummary(layout: BookmarkLayout): String {
    val parts = mutableListOf(layout.layoutType.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() })
    if (layout.showTags) parts.add("tags")
    if (layout.showDate) parts.add("date")
    if (layout.showReadingTime) parts.add("reading time")
    return parts.joinToString(" · ")
}
