package com.karakept.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.utils.isExpandedWidth
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.ui.screens.settings.AppearanceSettingsContent
import com.karakept.app.ui.screens.settings.AppearanceSettingsScreen
import com.karakept.app.ui.screens.settings.BackgroundSyncSettingsContent
import com.karakept.app.ui.screens.settings.BackgroundSyncSettingsScreen
import com.karakept.app.ui.screens.settings.BackupRestoreContent
import com.karakept.app.ui.screens.settings.BackupRestoreScreen
import com.karakept.app.ui.screens.settings.BookmarkListSettingsContent
import com.karakept.app.ui.screens.settings.BookmarkListSettingsScreen
import com.karakept.app.ui.screens.settings.BookmarkViewSettingsContent
import com.karakept.app.ui.screens.settings.BookmarkViewSettingsScreen
import com.karakept.app.ui.screens.settings.CustomSwipeActionsContent
import com.karakept.app.ui.screens.settings.CustomSwipeActionsScreen
import com.karakept.app.ui.screens.settings.EinkSettingsContent
import com.karakept.app.ui.screens.settings.EinkSettingsScreen
import com.karakept.app.ui.screens.settings.LayoutsContent
import com.karakept.app.ui.screens.settings.LayoutsScreen
import com.karakept.app.ui.screens.settings.ServerSettingsContent
import com.karakept.app.ui.screens.settings.ServerSettingsScreen
import com.karakept.app.ui.screens.settings.SyncDataSettingsContent
import com.karakept.app.ui.screens.settings.SyncDataSettingsScreen
import com.karakept.app.ui.screens.settings.BackupRestoreScreenModel
import org.koin.compose.koinInject

private enum class SettingsSection(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    APPEARANCE("Appearance", "Theme, accent color, layouts", Icons.Default.Palette),
    BEHAVIOR("Behavior", "Layout, gestures, reading speed, notifications", Icons.AutoMirrored.Filled.ViewList),
    READER("Reader", "Viewer mode, progress, tags, link handling", Icons.Default.Visibility),
    EINK("E-ink", "Contrast, motion, page-turn buttons", Icons.Default.Tonality),
    SYNC_DATA("Sync & Data", "Offline mode, content sync, server, backup", Icons.Default.Sync),
    ABOUT("About", "App version and open source licenses", Icons.Default.Info)
}

@Serializable
class SettingsScreen : NavKey {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<SettingsScreenModel>()
        var storageInfo by remember { mutableStateOf<com.karakept.app.utils.StorageInfo?>(null) }
        var selectedSection by remember { mutableStateOf(SettingsSection.APPEARANCE) }
        var selectedSubScreen by remember { mutableStateOf<NavKey?>(null) }

        // Clear sub-screen when section changes
        LaunchedEffect(selectedSection) {
            selectedSubScreen = null
        }

        LaunchedEffect(Unit) {
            storageInfo = com.karakept.app.utils.FileUtils.getStorageInfo()
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedLayout = isExpandedWidth(maxWidth)

            if (isExpandedLayout) {
                // Expanded: settings list + section content + sub-screen content
                Row(modifier = Modifier.fillMaxSize()) {
                    // Pane 1: settings navigation list
                    Surface(
                        modifier = Modifier.width(280.dp).fillMaxHeight()
                    ) {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Settings") },
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
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    SettingsSection.entries.forEach { section ->
                                        SettingsNavigationItem(
                                            title = section.title,
                                            description = section.description,
                                            icon = section.icon,
                                            isSelected = section == selectedSection,
                                            onClick = { selectedSection = section }
                                        )
                                        if (section != SettingsSection.entries.last()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }

                                storageInfo?.let {
                                    com.karakept.app.ui.components.StorageUsageBar(
                                        storageInfo = it,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    VerticalDivider()

                    // Pane 2: selected section content
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (selectedSection) {
                            SettingsSection.APPEARANCE -> AppearanceSettingsContent(
                                screenModel = screenModel,
                                onBack = { navigator.pop() },
                                onNavigate = { selectedSubScreen = it },
                                showBackButton = false
                            )
                            SettingsSection.BEHAVIOR -> BookmarkListSettingsContent(
                                screenModel = screenModel,
                                onBack = { navigator.pop() },
                                onNavigate = { selectedSubScreen = it },
                                showBackButton = false
                            )
                            SettingsSection.READER -> BookmarkViewSettingsContent(
                                screenModel = screenModel,
                                onBack = { navigator.pop() },
                                onNavigate = { selectedSubScreen = it },
                                showBackButton = false
                            )
                            SettingsSection.EINK -> EinkSettingsContent(
                                screenModel = koinViewModel(),
                                onBack = { navigator.pop() },
                                showBackButton = false
                            )
                            SettingsSection.SYNC_DATA -> SyncDataSettingsContent(
                                screenModel = screenModel,
                                onBack = { navigator.pop() },
                                onNavigate = { selectedSubScreen = it },
                                showBackButton = false
                            )
                            SettingsSection.ABOUT -> AboutContent(
                                onBack = { navigator.pop() },
                                showBackButton = false
                            )
                        }
                    }

                    // Pane 3: sub-screen content (only when a sub-screen is selected)
                    val currentSubScreen = selectedSubScreen
                    if (currentSubScreen != null) {
                        VerticalDivider()
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (currentSubScreen) {
                                is LayoutsScreen -> LayoutsContent(
                                    onBack = { selectedSubScreen = null },
                                    onNavigate = { navigator.push(it) }
                                )
                                is CustomSwipeActionsScreen -> CustomSwipeActionsContent(
                                    onBack = { selectedSubScreen = null }
                                )
                                is ReaderAppearanceScreen -> ReaderAppearanceContent(
                                    onBack = { selectedSubScreen = null }
                                )
                                is ServerSettingsScreen -> ServerSettingsContent(
                                    screenModel = screenModel,
                                    onBack = { selectedSubScreen = null },
                                    onNavigate = { navigator.push(it) }
                                )
                                is BackgroundSyncSettingsScreen -> BackgroundSyncSettingsContent(
                                    screenModel = screenModel,
                                    onBack = { selectedSubScreen = null }
                                )
                                is BackupRestoreScreen -> {
                                    val backupScreenModel = koinInject<BackupRestoreScreenModel>()
                                    BackupRestoreContent(
                                        screenModel = backupScreenModel,
                                        onBack = { selectedSubScreen = null }
                                    )
                                }
                                else -> {
                                    // Fallback: push as full screen
                                    LaunchedEffect(currentSubScreen) {
                                        navigator.push(currentSubScreen)
                                        selectedSubScreen = null
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Compact: current behavior — push sub-screens
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
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
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Top
                        ) {
                            SettingsNavigationItem(
                                title = "Appearance",
                                description = "Theme, accent color, layouts",
                                icon = Icons.Default.Palette,
                                onClick = { navigator.push(AppearanceSettingsScreen()) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsNavigationItem(
                                title = "Behavior",
                                description = "Layout, gestures, reading speed, notifications",
                                icon = Icons.AutoMirrored.Filled.ViewList,
                                onClick = { navigator.push(BookmarkListSettingsScreen()) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsNavigationItem(
                                title = "Reader",
                                description = "Viewer mode, progress, tags, link handling",
                                icon = Icons.Default.Visibility,
                                onClick = { navigator.push(BookmarkViewSettingsScreen()) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsNavigationItem(
                                title = "E-ink",
                                description = "Contrast, motion, page-turn buttons",
                                icon = Icons.Default.Tonality,
                                onClick = { navigator.push(EinkSettingsScreen()) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsNavigationItem(
                                title = "Sync & Data",
                                description = "Offline mode, content sync, server, backup",
                                icon = Icons.Default.Sync,
                                onClick = { navigator.push(SyncDataSettingsScreen()) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsNavigationItem(
                                title = "About",
                                description = "App version and open source licenses",
                                icon = Icons.Default.Info,
                                onClick = { navigator.push(AboutScreen()) }
                            )
                        }

                        storageInfo?.let {
                            com.karakept.app.ui.components.StorageUsageBar(
                                storageInfo = it,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isSelected: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
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
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (!isSelected) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open"
                )
            }
        }
    }
}
