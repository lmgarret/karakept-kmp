package com.karakept.app.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.Highlight
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.theme.HighlightPalette
import com.karakept.app.ui.theme.HighlightStyle
import com.karakept.app.ui.theme.LocalEinkMode

@Composable
fun HighlightDetailsBottomPanel(
    visible: Boolean,
    highlight: Highlight?,
    selectedColor: String,
    selectedNote: String,
    onColorChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onUpdateHighlight: (String, String?, String?) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Always render BaseBottomPanel to enable animations
    // The visibility is controlled by the visible parameter
    BaseBottomPanel(
        visible = visible,
        onDismiss = onDismiss
    ) {
        // Only render content if highlight is available
        if (highlight != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Color",
                            style = MaterialTheme.typography.labelLarge
                        )

                        // Delete button on the right
                        IconButton(onClick = { onDeleteHighlight(highlight.id) }) {
                            Icon(
                                imageVector = AppIcons.Default.Delete,
                                contentDescription = "Delete Highlight",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    HighlightColorPicker(
                        selectedColor = selectedColor,
                        onColorSelected = onColorChange
                    )
                }

                OutlinedTextField(
                    value = selectedNote,
                    onValueChange = onNoteChange,
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a note...") },
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HighlightColorPicker(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    val highContrast = LocalEinkMode.current.highContrast

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(HighlightPalette.all) { style ->
            ColorSwatch(
                style = style,
                isSelected = selectedColor == style.name,
                highContrast = highContrast,
                onClick = { onColorSelected(style.name) }
            )
        }
    }
}

/**
 * Four swatches that all render as the same grey on a monochrome panel are not a choice. On e-ink
 * the fill is dropped for the colour's pattern and the name is spelled out underneath.
 */
@Composable
private fun ColorSwatch(
    style: HighlightStyle,
    isSelected: Boolean,
    highContrast: Boolean,
    onClick: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onSurface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(if (highContrast) Modifier else Modifier.background(style.color))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        highContrast -> MaterialTheme.colorScheme.outline
                        else -> Color.LightGray.copy(alpha = 0.5f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (highContrast) {
                Canvas(modifier = Modifier.size(width = 22.dp, height = 14.dp)) {
                    drawHighlightRule(
                        pattern = style.pattern,
                        color = ink,
                        left = 0f,
                        right = size.width,
                        bottom = size.height,
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            } else if (isSelected) {
                Icon(
                    imageVector = AppIcons.Default.Check,
                    contentDescription = null,
                    tint = if (style.color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (highContrast) {
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
