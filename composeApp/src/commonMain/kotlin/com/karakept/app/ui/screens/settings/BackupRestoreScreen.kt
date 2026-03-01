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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.ui.components.rememberJsonFilePicker
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class BackupRestoreScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<BackupRestoreScreenModel>()

        val state by screenModel.state.collectAsState()
        val autoExportInterval by screenModel.autoExportInterval.collectAsState()
        val lastAutoExportTime by screenModel.lastAutoExportTime.collectAsState()

        var showResultDialog by remember { mutableStateOf(false) }

        val filePicker = rememberJsonFilePicker { content ->
            if (content != null) {
                screenModel.importSettings(content)
                showResultDialog = true
            }
        }

        // Show result dialog when export/import completes
        val currentState = state
        if (showResultDialog && currentState is BackupRestoreScreenModel.BackupState.Success) {
            AlertDialog(
                onDismissRequest = {
                    showResultDialog = false
                    screenModel.clearState()
                },
                title = { Text("Done") },
                text = { Text(currentState.message) },
                confirmButton = {
                    TextButton(onClick = {
                        showResultDialog = false
                        screenModel.clearState()
                    }) {
                        Text("OK")
                    }
                }
            )
        }
        if (showResultDialog && currentState is BackupRestoreScreenModel.BackupState.Error) {
            AlertDialog(
                onDismissRequest = {
                    showResultDialog = false
                    screenModel.clearState()
                },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("Error") },
                text = { Text(currentState.message) },
                confirmButton = {
                    TextButton(onClick = {
                        showResultDialog = false
                        screenModel.clearState()
                    }) {
                        Text("OK")
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Backup & Restore") },
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Export ────────────────────────────────────────────────────
                Text("Export", style = MaterialTheme.typography.titleMedium)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Export settings",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Save all app settings to a JSON file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (state is BackupRestoreScreenModel.BackupState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                        } else {
                            Button(
                                onClick = {
                                    showResultDialog = true
                                    screenModel.exportSettings()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Export Now")
                            }
                        }
                        if (lastAutoExportTime > 0L) {
                            Spacer(Modifier.height(4.dp))
                            val lastExportLocal = Instant.fromEpochMilliseconds(lastAutoExportTime)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                            Text(
                                text = "Last auto-export: ${lastExportLocal.date}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Import ────────────────────────────────────────────────────
                Text("Restore", style = MaterialTheme.typography.titleMedium)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Import settings",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Restore settings from a previously exported JSON file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "This will overwrite your current settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (state is BackupRestoreScreenModel.BackupState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                        } else {
                            OutlinedButton(
                                onClick = { filePicker() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Choose Backup File")
                            }
                        }
                    }
                }

                // ── Scheduled Auto-Export ─────────────────────────────────────
                Text("Scheduled Auto-Export", style = MaterialTheme.typography.titleMedium)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = "Automatically export settings in the background",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AutoExportInterval.entries.forEach { interval ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { screenModel.setAutoExportInterval(interval) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = interval == autoExportInterval,
                                    onClick = { screenModel.setAutoExportInterval(interval) }
                                )
                                Text(
                                    text = when (interval) {
                                        AutoExportInterval.NEVER -> "Never"
                                        AutoExportInterval.DAILY -> "Daily"
                                        AutoExportInterval.WEEKLY -> "Weekly"
                                        AutoExportInterval.MONTHLY -> "Monthly"
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }

                // ── Info ──────────────────────────────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Backup files are stored in the app's backup directory. " +
                                "They contain all your settings but NOT your bookmarks (those live on your Karakeep server). " +
                                "Server API keys are included in the backup – keep your backup files secure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
