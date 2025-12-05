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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.ui.screens.settings.BookmarkListSettingsScreen
import com.karakept.app.ui.screens.settings.BookmarkViewSettingsScreen
import com.karakept.app.ui.screens.settings.ServerSettingsScreen
import com.karakept.app.ui.screens.settings.ThemeSettingsScreen


class SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var storageInfo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.karakept.app.utils.StorageInfo?>(null) }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            storageInfo = com.karakept.app.utils.FileUtils.getStorageInfo()
        }

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
                        title = "Theme",
                        description = "Choose the background color and accent",
                        icon = Icons.Default.Palette,
                        onClick = { navigator.push(ThemeSettingsScreen()) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsNavigationItem(
                        title = "Bookmark List",
                        description = "Change list/card layout and filter settings",
                        icon = Icons.Default.ViewList,
                        onClick = { navigator.push(BookmarkListSettingsScreen()) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsNavigationItem(
                        title = "Bookmark View",
                        description = "Reader and viewer mode settings",
                        icon = Icons.Default.Visibility,
                        onClick = { navigator.push(BookmarkViewSettingsScreen()) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsNavigationItem(
                        title = "Servers",
                        description = "Manage connected servers",
                        icon = Icons.Default.Dns,
                        onClick = { navigator.push(ServerSettingsScreen()) }
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

@Composable
private fun SettingsNavigationItem(
    title: String,
    description: String,
    icon: ImageVector,
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
            Column(modifier = Modifier.weight(1f)) {
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open"
            )
        }
    }
}
