package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.ui.theme.rememberFontFamily

@Composable
fun ReaderAppearanceBottomPanel(
    visible: Boolean,
    textColor: Color?,
    backgroundColor: Color?,
    fontSize: Int,
    fontFamily: ReaderFontFamily,
    onTextColorChange: (Color?) -> Unit,
    onBackgroundColorChange: (Color?) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    allowDismiss: Boolean = true,
    scrollToTopEnabled: Boolean = true,
    onScrollToTopToggle: (Boolean) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }

    BaseBottomPanel(
        visible = visible,
        onDismiss = onDismiss,
        allowDismiss = allowDismiss
    ) {
        // Tab Row with matching background
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = { Icon(Icons.Default.FormatSize, contentDescription = "Text Size") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = { Icon(Icons.Default.FontDownload, contentDescription = "Font") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                icon = { Icon(Icons.Default.FormatColorText, contentDescription = "Text Color") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                icon = { Icon(Icons.Default.Palette, contentDescription = "Background") }
            )
            Tab(
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 },
                icon = { Icon(Icons.Default.RestartAlt, contentDescription = "Reset") }
            )
        }

        // Content for each tab
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> TextSizeTab(fontSize, onFontSizeChange)
                1 -> FontTab(fontFamily, onFontFamilyChange)
                2 -> TextColorTab(textColor, onTextColorChange)
                3 -> BackgroundColorTab(backgroundColor, onBackgroundColorChange)
                4 -> ResetTab(onReset = { showResetDialog = true })
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Scroll-to-top button",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = scrollToTopEnabled,
                onCheckedChange = onScrollToTopToggle
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Reader Appearance") },
            text = { Text("This will reset all reader appearance settings to default. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetDialog = false
                    onDismiss()
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TextSizeTab(fontSize: Int, onFontSizeChange: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Font Size: ${fontSize}px",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Slider(
            value = fontSize.toFloat(),
            onValueChange = { onFontSizeChange(it.toInt()) },
            valueRange = 12f..24f,
            steps = 11
        )
    }
}

@Composable
private fun FontTab(fontFamily: ReaderFontFamily, onFontFamilyChange: (ReaderFontFamily) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(ReaderFontFamily.entries) { family ->
            FontPreviewCard(
                fontFamily = family,
                isSelected = fontFamily == family,
                onClick = { onFontFamilyChange(family) }
            )
        }
    }
}

@Composable
private fun FontPreviewCard(
    fontFamily: ReaderFontFamily,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(100.dp)
            .height(100.dp)
            .clickable(onClick = onClick),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Abc",
                fontSize = 32.sp,
                fontFamily = fontFamily.rememberFontFamily(),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = fontFamily.displayName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun TextColorTab(currentColor: Color?, onColorSelected: (Color?) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Text Color",
            style = MaterialTheme.typography.titleMedium
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Theme Default option
            item {
                ColorSwatch(
                    color = MaterialTheme.colorScheme.onSurface,
                    label = "Theme",
                    isSelected = currentColor == null,
                    onClick = { onColorSelected(null) }
                )
            }

            // Preset colors
            val presetColors = listOf(
                Color.Black to "Black",
                Color.White to "White",
                Color(0xFF212121) to "Dark Gray",
                Color(0xFFE0E0E0) to "Light Gray",
                Color(0xFF1976D2) to "Blue",
                Color(0xFF388E3C) to "Green"
            )

            items(presetColors.size) { index ->
                val (color, label) = presetColors[index]
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

@Composable
private fun BackgroundColorTab(currentColor: Color?, onColorSelected: (Color?) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Background Color",
            style = MaterialTheme.typography.titleMedium
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // "Default" option (null)
            item {
                ColorSwatch(
                    color = MaterialTheme.colorScheme.surface,
                    label = "Default",
                    isSelected = currentColor == null,
                    onClick = { onColorSelected(null) }
                )
            }

            // Preset background colors
            val presetBackgroundColors = listOf(
                Color.White to "White",
                Color(0xFFFFFBE6) to "Sepia",
                Color(0xFFF5F5DC) to "Beige",
                Color(0xFF1E1E1E) to "Dark Gray",
                Color(0xFF121212) to "Black"
            )

            items(presetBackgroundColors.size) { index ->
                val (color, label) = presetBackgroundColors[index]
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

@Composable
private fun ResetTab(onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Reset all reader appearance settings to default",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        androidx.compose.material3.Button(onClick = onReset) {
            Text("Reset to Defaults")
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

// Extension function for color luminance calculation
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
