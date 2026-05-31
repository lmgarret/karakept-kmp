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
import androidx.compose.material.icons.automirrored.filled.List

import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.outlined.MenuBook
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.LayoutType
import com.karakept.app.ui.screens.SettingsScreenModel

@Serializable
class DefaultDisplaySettingsScreen : NavKey {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<SettingsScreenModel>()
        val currentLayoutType by screenModel.layoutType.collectAsState()
        val dimReadBookmarks by screenModel.dimReadBookmarks.collectAsState()
        val showTags by screenModel.showTags.collectAsState()
        val showReadingTimeBadge by screenModel.showReadingTimeBadge.collectAsState()
        val showDateInList by screenModel.showDateInList.collectAsState()
        val dateDisplayMode by screenModel.dateDisplayMode.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Default Display Settings") },
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
                    text = "These settings apply when no display profile is active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Layout section
                Text(
                    text = "Layout",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LayoutOption(
                    title = "Card",
                    description = "Large thumbnails on top with title below",
                    icon = Icons.Default.Window,
                    isSelected = currentLayoutType == LayoutType.CARD,
                    onClick = { screenModel.setLayoutType(LayoutType.CARD) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LayoutOption(
                    title = "List",
                    description = "Thumbnail on the side with title and description",
                    icon = Icons.AutoMirrored.Filled.List,
                    isSelected = currentLayoutType == LayoutType.LIST,
                    onClick = { screenModel.setLayoutType(LayoutType.LIST) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Show section
                Text(
                    text = "Show",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ToggleSetting(
                            icon = Icons.Default.VisibilityOff,
                            title = "Dim Read Bookmarks",
                            description = "Fade out read articles",
                            checked = dimReadBookmarks,
                            onCheckedChange = { screenModel.setDimReadBookmarks(it) }
                        )
                        HorizontalDivider()
                        ToggleSetting(
                            icon = Icons.Default.Label,
                            title = "Tags",
                            description = "Display tags on bookmark cards",
                            checked = showTags,
                            onCheckedChange = { screenModel.setShowTags(it) }
                        )
                        HorizontalDivider()
                        ToggleSetting(
                            icon = Icons.Outlined.MenuBook,
                            title = "Reading Time",
                            description = "Estimated reading time badge",
                            checked = showReadingTimeBadge,
                            onCheckedChange = { screenModel.setShowReadingTimeBadge(it) }
                        )
                        HorizontalDivider()
                        ToggleSetting(
                            icon = Icons.Default.CalendarToday,
                            title = "Date",
                            description = "Display creation date on cards",
                            checked = showDateInList,
                            onCheckedChange = { screenModel.setShowDateInList(it) }
                        )
                    }
                }

                if (showDateInList) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Date Format",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { screenModel.setDateDisplayMode(DateDisplayMode.ELAPSED) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = dateDisplayMode == DateDisplayMode.ELAPSED,
                                    onClick = { screenModel.setDateDisplayMode(DateDisplayMode.ELAPSED) }
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text("Relative time", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "e.g., 33m ago, 2h ago, 3d ago",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { screenModel.setDateDisplayMode(DateDisplayMode.ABSOLUTE) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = dateDisplayMode == DateDisplayMode.ABSOLUTE,
                                    onClick = { screenModel.setDateDisplayMode(DateDisplayMode.ABSOLUTE) }
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text("Absolute date", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "e.g., 2024-01-15",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            RadioButton(selected = isSelected, onClick = onClick)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun ToggleSetting(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
            modifier = Modifier.padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
