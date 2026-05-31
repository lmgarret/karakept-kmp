package com.karakept.app.ui.screens.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.ui.screens.SettingsScreenModel
import kotlinx.coroutines.delay

@Serializable
class SyncDataSettingsScreen(val highlightOfflineMode: Boolean = false) : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<SettingsScreenModel>()
        SyncDataSettingsContent(
            screenModel = screenModel,
            highlightOfflineMode = highlightOfflineMode,
            onBack = { navigator.pop() },
            onNavigate = { navigator.push(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDataSettingsContent(
    screenModel: SettingsScreenModel,
    highlightOfflineMode: Boolean = false,
    onBack: () -> Unit,
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    showBackButton: Boolean = true
) {
    val offlineMode by screenModel.offlineMode.collectAsState()
    val syncStrategy by screenModel.contentSyncStrategy.collectAsState()

    val defaultCardColor = CardDefaults.cardColors().containerColor
    var highlightAlpha by remember { mutableStateOf(0f) }
    val animatedAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = highlightAlpha,
        animationSpec = tween(250, easing = LinearEasing),
        label = "offline_blink"
    )
    LaunchedEffect(Unit) {
        if (highlightOfflineMode) {
            repeat(2) {
                highlightAlpha = 1f
                delay(250)
                highlightAlpha = 0f
                delay(250)
            }
        }
    }
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    val cardColor = lerp(defaultCardColor, highlightColor, animatedAlpha)

    val strategies = SyncStrategy.values()
        .filter { it != SyncStrategy.PER_LIST }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync & Data") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
            // Offline Mode Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Offline Mode",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (offlineMode) "Sync disabled. Actions are queued locally." else "Auto-sync enabled (after actions and on startup)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = offlineMode,
                        onCheckedChange = { screenModel.setOfflineMode(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Content Sync Mode
            Text(
                text = "Content Sync Mode",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                strategies.forEach { strategy ->
                    SyncStrategyOptionCard(
                        strategy = strategy,
                        isSelected = strategy == syncStrategy,
                        onClick = { screenModel.setContentSyncStrategy(strategy) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You can also toggle offline sync per list. Long-press any list in the sidebar and open its settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Server Connection link
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(ServerSettingsScreen()) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Server Connection",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Manage connected server and authentication",
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

            Spacer(modifier = Modifier.height(12.dp))

            // Background Sync link
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(BackgroundSyncSettingsScreen()) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Sync",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Automatically sync bookmarks in the background",
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

            Spacer(modifier = Modifier.height(12.dp))

            // Backup & Restore link
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(BackupRestoreScreen()) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Backup & Restore",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Export settings to JSON or restore from a backup",
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
        }
    }
}

@Composable
private fun SyncStrategyOptionCard(
    strategy: SyncStrategy,
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
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )

            Spacer(modifier = Modifier.padding(start = 12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (strategy) {
                        SyncStrategy.NEVER -> "Never (Online Only)"
                        SyncStrategy.PER_BOOKMARK -> "Per Bookmark (When Viewed)"
                        SyncStrategy.PER_LIST -> "Per List (Specific Lists)"
                        SyncStrategy.ALL -> "All Bookmarks"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (strategy) {
                        SyncStrategy.NEVER -> "Content is fetched only when you open a bookmark. Nothing is stored locally."
                        SyncStrategy.PER_BOOKMARK -> "Content is fetched and stored locally when you open a bookmark."
                        SyncStrategy.PER_LIST -> "Content for selected lists is automatically synced and stored."
                        SyncStrategy.ALL -> "Content for all bookmarks is stored locally. WARNING: this may cause slower sync times and increased storage usage."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
