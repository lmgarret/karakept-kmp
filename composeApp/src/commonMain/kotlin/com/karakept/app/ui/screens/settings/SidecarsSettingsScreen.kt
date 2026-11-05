package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation3.runtime.NavKey
import com.karakept.app.ui.components.InlineLoadingDots
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.domain.sidecar.SidecarKind
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
class SidecarsSettingsScreen : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<SidecarsSettingsScreenModel>()
        SidecarsSettingsContent(
            screenModel = screenModel,
            onBack = { navigator.pop() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidecarsSettingsContent(
    screenModel: SidecarsSettingsScreenModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val editors by screenModel.editors.collectAsState()
    val isWorking by screenModel.isWorking.collectAsState()
    val server by screenModel.server.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sidecars") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                AppIcons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "Optional self-hosted companions",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A sidecar runs next to your Karakeep server and does extra work " +
                                "on bookmarks. Nothing here is needed to read what one has already " +
                                "produced — that arrives with your bookmarks like any other " +
                                "attachment. Configure a service only to ask it for work on demand.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (server == null) {
                Text(
                    text = "Connect a Karakeep server first — sidecars are configured per server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SidecarKind.ALL.forEach { kind ->
                    val editor = editors[kind] ?: return@forEach
                    SidecarCard(
                        kind = kind,
                        baseUrl = editor.baseUrl,
                        token = editor.token,
                        tokenStored = editor.tokenStored,
                        enabled = editor.enabled,
                        status = editor.status,
                        isWorking = isWorking,
                        onBaseUrlChange = { screenModel.setBaseUrl(kind, it) },
                        onTokenChange = { screenModel.setToken(kind, it) },
                        onEnabledChange = { screenModel.setEnabled(kind, it) },
                        onSave = { screenModel.save(kind) },
                        onTest = { screenModel.testConnection(kind) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidecarCard(
    kind: String,
    baseUrl: String,
    token: String?,
    tokenStored: Boolean,
    enabled: Boolean,
    status: String?,
    isWorking: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.Default.Dns,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = SidecarKind.label(kind),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = SidecarKind.description(kind),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Service address") },
                placeholder = { Text("http://karhtmlscrub:8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = token ?: "",
                onValueChange = onTokenChange,
                label = { Text(if (tokenStored) "Replace token" else "Token (optional)") },
                supportingText = {
                    Text(
                        if (tokenStored) "A token is saved. Leave blank to keep it."
                        else "Only if the service is configured to require one."
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onSave, enabled = !isWorking) {
                    Text("Save")
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(onClick = onTest, enabled = !isWorking) {
                    Text("Test connection")
                }
                if (isWorking) {
                    Spacer(modifier = Modifier.width(12.dp))
                    InlineLoadingDots(label = "Checking")
                }
            }

            if (status != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
