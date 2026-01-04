package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.ui.components.ReaderAppearanceBottomPanel
import com.karakept.app.ui.components.HighlightDetailsBottomPanel
import com.karakept.app.data.model.Highlight

/**
 * Viewer mode selection dialog
 */
@Composable
internal fun ViewerModeDialog(
    visible: Boolean,
    viewerMode: ViewerMode,
    onModeSelected: (ViewerMode) -> Unit,
    onDismiss: () -> Unit
) {
    if (visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Viewer Mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Choose how to display bookmark content:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    ViewerModeOptionCard(
                        title = "Reader",
                        description = "Sanitized content with safe HTML only",
                        icon = Icons.AutoMirrored.Filled.ChromeReaderMode,
                        isSelected = viewerMode == ViewerMode.READER,
                        onClick = {
                            onModeSelected(ViewerMode.READER)
                        }
                    )

                    ViewerModeOptionCard(
                        title = "Web",
                        description = "Web view with original HTML and stylesheets (JavaScript disabled)",
                        icon = Icons.Default.Public,
                        isSelected = viewerMode == ViewerMode.WEB,
                        onClick = {
                            onModeSelected(ViewerMode.WEB)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Delete confirmation dialog
 */
@Composable
internal fun DeleteConfirmationDialog(
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete Bookmark?") },
            text = { Text("This action cannot be undone. The bookmark will be permanently deleted from the server.") },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Reader appearance panel with scrim
 */
@Composable
internal fun ReaderAppearancePanel(
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
    onDismiss: () -> Unit
) {
    // Scrim
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    onDismiss()
                }
        )
    }

    // Panel (always in composition, visibility controlled by prop for animation)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        ReaderAppearanceBottomPanel(
            visible = visible,
            textColor = textColor,
            backgroundColor = backgroundColor,
            fontSize = fontSize,
            fontFamily = fontFamily,
            onTextColorChange = onTextColorChange,
            onBackgroundColorChange = onBackgroundColorChange,
            onFontSizeChange = onFontSizeChange,
            onFontFamilyChange = onFontFamilyChange,
            onReset = onReset,
            onDismiss = onDismiss
        )
    }
}

/**
 * Viewer mode option card (private helper)
 */
@Composable
private fun ViewerModeOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
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

/**
 * Highlight details panel with scrim and floating text card
 */
@Composable
internal fun HighlightDetailsPanel(
    visible: Boolean,
    highlight: Highlight?,
    fontFamily: com.karakept.app.data.model.ReaderFontFamily = com.karakept.app.data.model.ReaderFontFamily.SYSTEM,
    onUpdateHighlight: (String, String?, String?) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        onDismiss()
                    }
            )
        }

        // Floating card showing highlighted text (centered in available space above panel)
        if (highlight != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.karakept.app.ui.components.HighlightFloatingCard(
                    highlight = highlight,
                    visible = visible,
                    fontFamily = fontFamily
                )
            }
        }

        // Panel at bottom
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            HighlightDetailsBottomPanel(
                visible = visible,
                highlight = highlight,
                onUpdateHighlight = onUpdateHighlight,
                onDeleteHighlight = onDeleteHighlight,
                onDismiss = onDismiss
            )
        }
    }
}
