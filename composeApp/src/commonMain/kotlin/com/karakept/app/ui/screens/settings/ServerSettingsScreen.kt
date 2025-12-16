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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.Server
import com.karakept.app.ui.screens.LoginScreen
import com.karakept.app.ui.screens.SettingsScreenModel

class ServerSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SettingsScreenModel>()
        val servers by screenModel.servers.collectAsState()
        val activeServerId by screenModel.activeServerId.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Servers & Sync") },
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
                    text = "Servers & Sync",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Offline Mode Toggle
                val offlineMode by screenModel.offlineMode.collectAsState()
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                // Content Sync Strategy
                val syncStrategy by screenModel.contentSyncStrategy.collectAsState()
                val strategies = com.karakept.app.data.model.SyncStrategy.values()
                
                Text(
                    text = "Sync Strategy",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        strategies.forEach { strategy ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { screenModel.setContentSyncStrategy(strategy) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = syncStrategy == strategy,
                                    onClick = { screenModel.setContentSyncStrategy(strategy) }
                                )
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(
                                        text = when(strategy) {
                                            com.karakept.app.data.model.SyncStrategy.NEVER -> "Never (Online Only)"
                                            com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK -> "Per Bookmark (When Viewed)"
                                            com.karakept.app.data.model.SyncStrategy.PER_LIST -> "Per List (Specific Lists)"
                                            com.karakept.app.data.model.SyncStrategy.ALL -> "All Bookmarks (Offline Access)"
                                        },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = when(strategy) {
                                            com.karakept.app.data.model.SyncStrategy.NEVER -> "Content is fetched only when you open a bookmark. Nothing is stored locally."
                                            com.karakept.app.data.model.SyncStrategy.PER_BOOKMARK -> "Content is fetched and stored locally when you open a bookmark."
                                            com.karakept.app.data.model.SyncStrategy.PER_LIST -> "Content for selected lists is automatically synced and stored."
                                            com.karakept.app.data.model.SyncStrategy.ALL -> "Content for ALL bookmarks is verified and stored locally."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Show List Picker if PER_LIST is selected
                if (syncStrategy == com.karakept.app.data.model.SyncStrategy.PER_LIST) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        screenModel.fetchAvailableLists()
                    }
                    
                    val availableLists by screenModel.availableLists.collectAsState()
                    val targetLists by screenModel.contentSyncTargetLists.collectAsState()
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Select Lists to Sync",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            if (availableLists.isEmpty()) {
                                Text("No lists found or failed to load.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                availableLists.forEach { list ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { screenModel.toggleContentSyncTargetList(list.id) }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.Checkbox(
                                            checked = targetLists.contains(list.id),
                                            onCheckedChange = { screenModel.toggleContentSyncTargetList(list.id) }
                                        )
                                        Text(
                                            text = list.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Connected Servers",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                servers.forEach { server ->
                    ServerOption(
                        server = server,
                        icon = Icons.Default.Dns,
                        isActive = server.id == activeServerId,
                        onClick = { screenModel.setActiveServer(server.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { navigator.push(LoginScreen()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Add Server")
                }
            }
        }
    }
}

@Composable
private fun ServerOption(
    server: Server,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    isActive: Boolean,
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
                selected = isActive,
                onClick = onClick
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
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
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
