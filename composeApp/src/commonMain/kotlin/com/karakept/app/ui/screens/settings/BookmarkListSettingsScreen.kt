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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
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
import com.karakept.app.data.model.LayoutType
import com.karakept.app.ui.components.ReadingSpeedDialog
import com.karakept.app.ui.screens.FilterManagementScreen
import com.karakept.app.ui.screens.SettingsScreenModel

class BookmarkListSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SettingsScreenModel>()
        val currentLayoutType by screenModel.layoutType.collectAsState()
        val showReadingTimeBadge by screenModel.showReadingTimeBadge.collectAsState()
        val readingSpeedWpm by screenModel.readingSpeedWpm.collectAsState()
        var showReadingSpeedDialog by remember { mutableStateOf(false) }

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
                Text(
                    text = "Article Display Layout",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LayoutOption(
                    title = "Card Layout",
                    description = "Display articles with large thumbnails on top and title below",
                    icon = Icons.AutoMirrored.Filled.ViewList,
                    isSelected = currentLayoutType == LayoutType.CARD,
                    onClick = { screenModel.setLayoutType(LayoutType.CARD) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                LayoutOption(
                    title = "List Layout",
                    description = "Show thumbnails on the left with title and description on the right",
                    icon = Icons.AutoMirrored.Filled.List,
                    isSelected = currentLayoutType == LayoutType.LIST,
                    onClick = { screenModel.setLayoutType(LayoutType.LIST) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Filter Management
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigator.push(FilterManagementScreen()) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Manage Filters",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Add, edit, reorder, and organize saved filters",
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

                // Reading Time Settings
                Text(
                    text = "Reading Time",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Show Reading Time Badge Toggle
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Reading Time",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Display estimated reading time on bookmark cards",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showReadingTimeBadge,
                            onCheckedChange = { screenModel.setShowReadingTimeBadge(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
            }
        }
    }
}

@Composable
private fun LayoutOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
