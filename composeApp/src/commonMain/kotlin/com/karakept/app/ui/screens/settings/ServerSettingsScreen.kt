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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.ui.screens.LoginScreen
import com.karakept.app.ui.screens.SettingsScreenModel

class ServerSettingsScreen(val highlightOfflineMode: Boolean = false) : Screen {
    @Composable
    fun SyncStrategyOption(
        strategy: com.karakept.app.data.model.SyncStrategy,
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
                        text = when(strategy) {
                            com.karakept.app.data.model.SyncStrategy.NEVER -> "Never (Online Only)"
                            com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK -> "Per Bookmark (When Viewed)"
                            com.karakept.app.data.model.SyncStrategy.PER_LIST -> "Per List (Specific Lists)"
                            com.karakept.app.data.model.SyncStrategy.ALL -> "All Bookmarks"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when(strategy) {
                            com.karakept.app.data.model.SyncStrategy.NEVER -> "Content is fetched only when you open a bookmark. Nothing is stored locally."
                            com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK -> "Content is fetched and stored locally when you open a bookmark."
                            com.karakept.app.data.model.SyncStrategy.PER_LIST -> "Content for selected lists is automatically synced and stored."
                            com.karakept.app.data.model.SyncStrategy.ALL -> "Content for all bookmarks is stored locally. WARNING: this may cause slower sync times and increased storage usage."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SettingsScreenModel>()
        val servers by screenModel.servers.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Server & Sync") },
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
                    text = "Server & Sync",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Offline Mode Toggle
                val offlineMode by screenModel.offlineMode.collectAsState()

                // Blink highlight when arriving from the offline badge
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
                            imageVector = Icons.Default.CloudOff, // Assuming CloudOff exists or similar
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
                        androidx.compose.material3.Switch(
                            checked = offlineMode,
                            onCheckedChange = { screenModel.setOfflineMode(it) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Content Sync Mode (RadioButton Group)
                val syncStrategy by screenModel.contentSyncStrategy.collectAsState()
                val strategies = com.karakept.app.data.model.SyncStrategy.values()

                Text(
                    text = "Content sync mode",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    strategies.forEach { strategy ->
                        SyncStrategyOption(
                            strategy = strategy,
                            isSelected = strategy == syncStrategy,
                            onClick = { screenModel.setContentSyncStrategy(strategy) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Configure List Sync Button (only shown when PER_LIST mode is selected)
                if (syncStrategy == com.karakept.app.data.model.SyncStrategy.PER_LIST) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navigator.push(com.karakept.app.ui.screens.settings.ListManagementScreen()) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Configure List Sync",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Text(
                    text = "Connected Server",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val server = servers.firstOrNull()
                if (server != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
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
                                    text = server.label,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = server.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { navigator.push(LoginScreen(serverUrl = server?.url)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Re-authenticate")
                }
            }
        }
    }
}

