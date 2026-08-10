package com.karakept.app.ui.screens.settings

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsSystemDaydream
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.ui.screens.SettingsScreenModel

@Serializable
class AppearanceSettingsScreen : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<SettingsScreenModel>()
        AppearanceSettingsContent(
            screenModel = screenModel,
            onBack = { navigator.pop() },
            onNavigate = { navigator.push(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsContent(
    screenModel: SettingsScreenModel,
    onBack: () -> Unit,
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    showBackButton: Boolean = true
) {
    val currentThemeMode by screenModel.themeMode.collectAsState()
    val currentAccentColor by screenModel.accentColor.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
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
            // Layouts link
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(LayoutsScreen()) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Layouts",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Custom display layouts for your bookmark lists",
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

            // E-ink link
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(EinkSettingsScreen()) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tonality,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "E-ink",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "High contrast, no animations, hardware page-turn buttons",
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

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(bottom = 24.dp))

            // Theme Mode Section
            Text(
                text = "Theme Mode",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ThemeModeOption(
                title = "Light",
                description = "Light theme",
                icon = Icons.Default.LightMode,
                isSelected = currentThemeMode == ThemeMode.LIGHT,
                onClick = { screenModel.setThemeMode(ThemeMode.LIGHT) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ThemeModeOption(
                title = "Dark",
                description = "Dark theme",
                icon = Icons.Default.DarkMode,
                isSelected = currentThemeMode == ThemeMode.DARK,
                onClick = { screenModel.setThemeMode(ThemeMode.DARK) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ThemeModeOption(
                title = "AMOLED",
                description = "Pure black for AMOLED screens",
                icon = Icons.Default.Contrast,
                isSelected = currentThemeMode == ThemeMode.AMOLED,
                onClick = { screenModel.setThemeMode(ThemeMode.AMOLED) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ThemeModeOption(
                title = "System",
                description = "Follow system theme",
                icon = Icons.Default.SettingsSystemDaydream,
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

            AccentColorPickerCard(
                currentAccentColor = currentAccentColor,
                onColorSelected = { screenModel.setAccentColor(it) }
            )

        }
    }
}

@Composable
private fun ThemeModeOption(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentColorPickerCard(
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
                    AppearanceColorSwatch(
                        color = getAccentColorPreviewValue(accentColor),
                        label = accentColor.name.lowercase().replaceFirstChar { it.uppercase() },
                        isSelected = currentAccentColor == accentColor,
                        onClick = { onColorSelected(accentColor) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceColorSwatch(
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
                .then(
                    if (label == "Dynamic") {
                        Modifier.background(
                            androidx.compose.ui.graphics.Brush.sweepGradient(
                                listOf(
                                    Color(0xFF6750A4),
                                    Color(0xFF1976D2),
                                    Color(0xFF388E3C),
                                    Color(0xFFE65100),
                                    Color(0xFFC62828),
                                    Color(0xFFC2185B),
                                    Color(0xFF6750A4)
                                )
                            )
                        )
                    } else {
                        Modifier.background(color)
                    }
                )
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
                    tint = if (label == "Dynamic" || color.luminance() > 0.5f) Color.Black else Color.White,
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

private fun getAccentColorPreviewValue(accentColor: AccentColor): Color {
    return when (accentColor) {
        AccentColor.PURPLE -> Color(0xFF6750A4)
        AccentColor.BLUE -> Color(0xFF1976D2)
        AccentColor.GREEN -> Color(0xFF388E3C)
        AccentColor.ORANGE -> Color(0xFFE65100)
        AccentColor.RED -> Color(0xFFC62828)
        AccentColor.PINK -> Color(0xFFC2185B)
        AccentColor.DYNAMIC -> Color.Transparent
    }
}

private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
