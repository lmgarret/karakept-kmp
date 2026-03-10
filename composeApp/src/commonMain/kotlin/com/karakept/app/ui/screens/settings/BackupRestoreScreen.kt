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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.ui.components.rememberDirectoryPicker
import com.karakept.app.ui.components.rememberJsonFilePicker
import com.karakept.app.utils.FileUtils
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
        val backupExportDirectory by screenModel.backupExportDirectory.collectAsState()
        val backupPinHash by screenModel.backupPinHash.collectAsState()
        val pinIsSet = backupPinHash != null

        var showResultDialog by remember { mutableStateOf(false) }

        // ── File pickers ──────────────────────────────────────────────────────
        val filePicker = rememberJsonFilePicker { content ->
            if (content != null) {
                screenModel.importSettings(content)
                showResultDialog = true
            }
        }

        val directoryPicker = rememberDirectoryPicker { path ->
            if (path != null) {
                screenModel.setBackupExportDirectory(path)
            }
        }

        // ── PIN dialogs state ─────────────────────────────────────────────────
        var showSetPinDialog by remember { mutableStateOf(false) }
        var showTestPinDialog by remember { mutableStateOf(false) }
        var showConfirmExportPinDialog by remember { mutableStateOf(false) }

        // ── Result dialog ─────────────────────────────────────────────────────
        val currentState = state
        if (showResultDialog) {
            when (currentState) {
                is BackupRestoreScreenModel.BackupState.Success -> AlertDialog(
                    onDismissRequest = { showResultDialog = false; screenModel.clearState() },
                    title = { Text("Done") },
                    text = { Text(currentState.message) },
                    confirmButton = {
                        TextButton(onClick = { showResultDialog = false; screenModel.clearState() }) {
                            Text("OK")
                        }
                    }
                )
                is BackupRestoreScreenModel.BackupState.Error -> AlertDialog(
                    onDismissRequest = { showResultDialog = false; screenModel.clearState() },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                    title = { Text("Error") },
                    text = { Text(currentState.message) },
                    confirmButton = {
                        TextButton(onClick = { showResultDialog = false; screenModel.clearState() }) {
                            Text("OK")
                        }
                    }
                )
                is BackupRestoreScreenModel.BackupState.PinRequired -> {
                    // All backups are encrypted — ask for PIN
                    showResultDialog = false
                    PinEntryDialog(
                        title = "Enter Backup PIN",
                        supportingText = "Enter the PIN used when this backup was exported.",
                        onConfirm = { pin ->
                            screenModel.importWithPin(currentState.encryptedContent, pin)
                            showResultDialog = true
                        },
                        onDismiss = { screenModel.clearState() }
                    )
                }
                else -> {}
            }
        }

        // ── Set / Change PIN dialog ───────────────────────────────────────────
        if (showSetPinDialog) {
            SetPinDialog(
                isChange = pinIsSet,
                onConfirm = { pin ->
                    screenModel.setBackupPin(pin)
                    showSetPinDialog = false
                },
                onDismiss = { showSetPinDialog = false }
            )
        }

        // ── Test PIN dialog ───────────────────────────────────────────────────
        if (showTestPinDialog) {
            TestPinDialog(
                onVerify = { pin -> screenModel.verifyPin(pin) },
                onDismiss = { showTestPinDialog = false }
            )
        }

        // ── Confirm PIN before export ─────────────────────────────────────────
        if (showConfirmExportPinDialog) {
            PinEntryDialog(
                title = "Confirm PIN",
                supportingText = "Enter your backup PIN to encrypt and export.",
                onConfirm = { pin ->
                    if (screenModel.verifyPin(pin)) {
                        showConfirmExportPinDialog = false
                        showResultDialog = true
                        screenModel.exportSettings(pin)
                    }
                    // Wrong PIN: dialog stays open, user sees no feedback — keep trying or cancel
                },
                onDismiss = { showConfirmExportPinDialog = false }
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
                // ── Backup PIN ────────────────────────────────────────────────
                Text("Backup PIN", style = MaterialTheme.typography.titleMedium)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (pinIsSet) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            headlineContent = {
                                Text(if (pinIsSet) "PIN set — backups are encrypted" else "No PIN set")
                            },
                            supportingContent = {
                                Text(
                                    if (pinIsSet)
                                        "4–6 digit PIN required to open backup files. Servers are automatically restored on import."
                                    else
                                        "A PIN is required to export or import backups. Set a 4–6 digit PIN below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = { showSetPinDialog = true }) {
                                Text(if (pinIsSet) "Change PIN" else "Set PIN")
                            }
                            if (pinIsSet) {
                                TextButton(onClick = { showTestPinDialog = true }) {
                                    Text("Test PIN")
                                }
                            }
                        }
                    }
                }

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
                                    text = "Save all settings + servers to an AES-256-GCM encrypted JSON file",
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
                                onClick = { showConfirmExportPinDialog = true },
                                enabled = pinIsSet,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Export Now")
                            }
                            if (!pinIsSet) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Set a PIN above to enable exports.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

                // ── Export directory ──────────────────────────────────────────
                Text("Export Directory", style = MaterialTheme.typography.titleMedium)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        val displayName = backupExportDirectory
                            ?.let { FileUtils.getDirectoryDisplayName(it) }
                            ?: "Default (app backup folder)"
                        ListItem(
                            leadingContent = {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            headlineContent = { Text("Export to") },
                            supportingContent = {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { directoryPicker() }) {
                                        Text("Change")
                                    }
                                    if (backupExportDirectory != null) {
                                        TextButton(
                                            onClick = { screenModel.setBackupExportDirectory(null) }
                                        ) {
                                            Text(
                                                "Reset",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        )
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
                                    text = "Restore settings and servers from an encrypted backup file",
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
                                text = "Automatically export encrypted settings in the background" +
                                        if (!pinIsSet) " (requires a PIN to be set)" else "",
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
                        text = "All backup files are AES-256-GCM encrypted with your PIN. " +
                                "Settings and server connections (including API keys) are fully " +
                                "backed up and restored on import.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

// ── Reusable PIN dialogs ──────────────────────────────────────────────────────

/**
 * Dialog that prompts the user to enter (and confirm) a new 4–6 digit PIN.
 */
@Composable
private fun SetPinDialog(
    isChange: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val isValid = pin.length in 4..6 && pin.all { it.isDigit() }
    val matches = pin == confirmPin
    val error = when {
        pin.isNotEmpty() && !isValid -> "PIN must be 4–6 digits"
        confirmPin.isNotEmpty() && !matches -> "PINs do not match"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isChange) "Change Backup PIN" else "Set Backup PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter a 4–6 digit PIN. All backups will be encrypted with this PIN, " +
                    "and server connections will be included.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("New PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = confirmPin.isNotEmpty() && !matches,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid && matches) onConfirm(pin) },
                enabled = isValid && matches
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Dialog that prompts the user to enter a PIN and verifies it against the stored hash.
 */
@Composable
private fun TestPinDialog(
    onVerify: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test Backup PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter your PIN to verify it is correct.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                            pin = it
                            result = null
                        }
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                when (result) {
                    true -> Text(
                        "PIN is correct.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    false -> Text(
                        "PIN is incorrect.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    null -> {}
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { result = onVerify(pin) },
                enabled = pin.length in 4..6
            ) {
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * Generic PIN entry dialog (used for confirming PIN on export and decrypting on import).
 */
@Composable
private fun PinEntryDialog(
    title: String,
    supportingText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(supportingText, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin) },
                enabled = pin.length in 4..6
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
