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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
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
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.ui.screens.ReaderAppearanceScreen
import com.karakept.app.ui.screens.SettingsScreenModel

class BookmarkViewSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SettingsScreenModel>()
        val currentViewerMode by screenModel.viewerMode.collectAsState()
        val hideArticleThumbnails by screenModel.hideArticleThumbnails.collectAsState()
        val currentLinkOpenMode by screenModel.linkOpenMode.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Bookmark View") },
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
                    text = "Viewer Mode",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LayoutOption(
                    title = "Reader",
                    description = "Sanitized content with safe HTML only",
                    icon = Icons.AutoMirrored.Filled.ChromeReaderMode,
                    isSelected = currentViewerMode == ViewerMode.READER,
                    onClick = { screenModel.setViewerMode(ViewerMode.READER) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                LayoutOption(
                    title = "Web",
                    description = "Web view with original HTML and stylesheets (JavaScript disabled)",
                    icon = Icons.Default.Public,
                    isSelected = currentViewerMode == ViewerMode.WEB,
                    onClick = { screenModel.setViewerMode(ViewerMode.WEB) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Reader Mode Settings
                Text(
                    text = "Reader Mode Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val trackReadingProgress by screenModel.trackReadingProgress.collectAsState()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            Text(
                                text = "Track Reading Progress",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Save scroll position to resume reading and show progress in bookmark list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = trackReadingProgress,
                            onCheckedChange = { screenModel.setTrackReadingProgress(it) }
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ImageNotSupported,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            Text(
                                text = "Hide Article Thumbnails",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Remove first image from article content to avoid duplicates with hero banner",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hideArticleThumbnails,
                            onCheckedChange = { screenModel.setHideArticleThumbnails(it) }
                        )
                    }
                }

                val showTags by screenModel.showTags.collectAsState()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            Text(
                                text = "Show Tags",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Display bookmark tags in reader mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showTags,
                            onCheckedChange = { screenModel.setShowTags(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Link Opening Settings
                Text(
                    text = "Link Handling",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LayoutOption(
                    title = "Custom Tab",
                    description = "Open links in a custom tab using the default browser session",
                    icon = Icons.Default.OpenInNew,
                    isSelected = currentLinkOpenMode == LinkOpenMode.CUSTOM_TAB,
                    onClick = { screenModel.setLinkOpenMode(LinkOpenMode.CUSTOM_TAB) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                LayoutOption(
                    title = "External Browser",
                    description = "Open links in the system default browser",
                    icon = Icons.Default.OpenInBrowser,
                    isSelected = currentLinkOpenMode == LinkOpenMode.EXTERNAL_BROWSER,
                    onClick = { screenModel.setLinkOpenMode(LinkOpenMode.EXTERNAL_BROWSER) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Reader Appearance
                Text(
                    text = "Reader Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigator.push(ReaderAppearanceScreen()) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Customize Reader Appearance",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Text color, background, font size, and font family",
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
}

@Composable
private fun LayoutOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
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
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
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
