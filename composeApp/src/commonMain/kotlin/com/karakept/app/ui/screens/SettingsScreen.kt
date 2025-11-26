package com.karakept.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.ViewerMode


class SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SettingsScreenModel>()
        val currentLayoutType by screenModel.layoutType.collectAsState()
        val currentViewerMode by screenModel.viewerMode.collectAsState()

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
                    .padding(16.dp),
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
                    isSelected = currentLayoutType == LayoutType.CARD,
                    onClick = { screenModel.setLayoutType(LayoutType.CARD) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                LayoutOption(
                    title = "List Layout",
                    description = "Show thumbnails on the left with title and description on the right",
                    isSelected = currentLayoutType == LayoutType.LIST,
                    onClick = { screenModel.setLayoutType(LayoutType.LIST) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Viewer Mode",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LayoutOption(
                    title = "Reader",
                    description = "Sanitized content with safe HTML only",
                    isSelected = currentViewerMode == ViewerMode.READER,
                    onClick = { screenModel.setViewerMode(ViewerMode.READER) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                LayoutOption(
                    title = "Archive",
                    description = "Original HTML with stylesheets (JavaScript disabled)",
                    isSelected = currentViewerMode == ViewerMode.ARCHIVE,
                    onClick = { screenModel.setViewerMode(ViewerMode.ARCHIVE) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Servers",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val servers by screenModel.servers.collectAsState()
                val activeServerId by screenModel.activeServerId.collectAsState()

                servers.forEach { server ->
                    ServerOption(
                        server = server,
                        isActive = server.id == activeServerId,
                        onClick = { screenModel.setActiveServer(server.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { navigator.push(LoginScreen()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Add Server")
                }
            }
        }
    }
}

@Composable
private fun LayoutOption(
    title: String,
    description: String,
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

@Composable
private fun ServerOption(
    server: com.karakept.app.data.model.Server,
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
