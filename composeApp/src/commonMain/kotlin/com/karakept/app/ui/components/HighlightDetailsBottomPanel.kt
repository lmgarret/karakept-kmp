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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import com.karakept.app.ui.theme.HighlightYellow
import com.karakept.app.ui.theme.HighlightBlue
import com.karakept.app.ui.theme.HighlightGreen
import com.karakept.app.ui.theme.HighlightRed

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
                                imageVector = Icons.Default.Delete,
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
    val colors = listOf(
        "yellow" to HighlightYellow,
        "blue" to HighlightBlue,
        "green" to HighlightGreen,
        "red" to HighlightRed
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(colors) { (name, color) ->
            ColorSwatch(
                color = color,
                isSelected = selectedColor == name,
                onClick = { onColorSelected(name) }
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
