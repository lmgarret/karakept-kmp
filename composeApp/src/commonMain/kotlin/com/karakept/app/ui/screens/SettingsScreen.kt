package com.karakept.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.model.ViewerMode


class SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SettingsScreenModel>()
        val currentLayoutType by screenModel.layoutType.collectAsState()
        val currentViewerMode by screenModel.viewerMode.collectAsState()
        val hideArticleThumbnails by screenModel.hideArticleThumbnails.collectAsState()
        val currentThemeMode by screenModel.themeMode.collectAsState()
        val currentAccentColor by screenModel.accentColor.collectAsState()
        val currentHtmlTextColor by screenModel.htmlTextColor.collectAsState()

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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top
            ) {
                // Theme Mode Section
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LayoutOption(
                    title = "Light",
                    description = "Light theme",
                    isSelected = currentThemeMode == ThemeMode.LIGHT,
                    onClick = { screenModel.setThemeMode(ThemeMode.LIGHT) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                LayoutOption(
                    title = "Dark",
                    description = "Dark theme",
                    isSelected = currentThemeMode == ThemeMode.DARK,
                    onClick = { screenModel.setThemeMode(ThemeMode.DARK) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                LayoutOption(
                    title = "AMOLED",
                    description = "Pure black for AMOLED screens",
                    isSelected = currentThemeMode == ThemeMode.AMOLED,
                    onClick = { screenModel.setThemeMode(ThemeMode.AMOLED) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                LayoutOption(
                    title = "System",
                    description = "Follow system theme",
                    isSelected = currentThemeMode == ThemeMode.SYSTEM,
                    onClick = { screenModel.setThemeMode(ThemeMode.SYSTEM) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Accent Color Section
                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                AccentColorPicker(
                    currentAccentColor = currentAccentColor,
                    onColorSelected = { screenModel.setAccentColor(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

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

                // Reader Mode Settings
                Text(
                    text = "Reader Mode Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { screenModel.setHideArticleThumbnails(!hideArticleThumbnails) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = hideArticleThumbnails,
                            onCheckedChange = { screenModel.setHideArticleThumbnails(it) }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
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
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // HTML Viewer Settings
                Text(
                    text = "HTML Viewer Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HtmlTextColorPicker(
                    currentColor = currentHtmlTextColor,
                    onColorSelected = { screenModel.setHtmlTextColor(it) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentColorPicker(
    currentAccentColor: AccentColor,
    onColorSelected: (AccentColor) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Choose your accent color",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AccentColor.entries.forEach { accentColor ->
                    ColorSwatch(
                        color = getAccentColorPreview(accentColor),
                        label = accentColor.name.lowercase().replaceFirstChar { it.uppercase() },
                        isSelected = currentAccentColor == accentColor,
                        onClick = { onColorSelected(accentColor) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HtmlTextColorPicker(
    currentColor: Color?,
    onColorSelected: (Color?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "HTML Viewer Text Color",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Theme Default option
                ColorSwatch(
                    color = MaterialTheme.colorScheme.onSurface,
                    label = "Theme",
                    isSelected = currentColor == null,
                    onClick = { onColorSelected(null) }
                )

                // Preset colors
                val presetColors = listOf(
                    Color.Black to "Black",
                    Color.White to "White",
                    Color(0xFF212121) to "Dark Gray",
                    Color(0xFFE0E0E0) to "Light Gray",
                    Color(0xFF1976D2) to "Blue",
                    Color(0xFF388E3C) to "Green"
                )

                presetColors.forEach { (color, label) ->
                    ColorSwatch(
                        color = color,
                        label = label,
                        isSelected = currentColor == color,
                        onClick = { onColorSelected(color) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// Helper function to get preview color for accent colors
private fun getAccentColorPreview(accentColor: AccentColor): Color {
    return when (accentColor) {
        AccentColor.PURPLE -> Color(0xFF6750A4)
        AccentColor.BLUE -> Color(0xFF1976D2)
        AccentColor.GREEN -> Color(0xFF388E3C)
        AccentColor.ORANGE -> Color(0xFFE65100)
        AccentColor.RED -> Color(0xFFC62828)
        AccentColor.PINK -> Color(0xFFC2185B)
    }
}

// Extension function for color luminance calculation
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
