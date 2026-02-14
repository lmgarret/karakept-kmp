package com.karakept.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.karakept.app.data.model.SwipeAction

fun SwipeAction.getIcon(): ImageVector {
    return when (this) {
        SwipeAction.ARCHIVE -> Icons.Default.Archive
        SwipeAction.FAVOURITE -> Icons.Default.Star
        SwipeAction.MARK_READ -> Icons.Default.Visibility
        SwipeAction.DELETE -> Icons.Default.Delete
        SwipeAction.SHARE -> Icons.Default.Share
        SwipeAction.OPEN_IN_BROWSER -> Icons.Default.OpenInBrowser
        SwipeAction.NONE -> Icons.Default.Close
    }
}

fun SwipeAction.getColor(): Color {
    return when (this) {
        SwipeAction.ARCHIVE -> Color(0xFF4CAF50) // Green
        SwipeAction.FAVOURITE -> Color(0xFFFFC107) // Amber
        SwipeAction.MARK_READ -> Color(0xFF2196F3) // Blue
        SwipeAction.DELETE -> Color(0xFFF44336) // Red
        SwipeAction.SHARE -> Color(0xFF9C27B0) // Purple
        SwipeAction.OPEN_IN_BROWSER -> Color(0xFF00BCD4) // Cyan
        SwipeAction.NONE -> Color.Transparent
    }
}

/**
 * A settings item that shows the currently selected [SwipeAction] and opens a dialog to change it.
 * Shared between swipe gesture settings and scroll-end action settings.
 */
@Composable
fun SwipeActionSettingItem(
    title: String,
    description: String,
    selectedAction: SwipeAction,
    icon: ImageVector,
    onActionSelected: (SwipeAction) -> Unit,
    availableActions: List<SwipeAction> = SwipeAction.entries
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        availableActions.forEach { action ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onActionSelected(action)
                                        showDialog = false
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = action == selectedAction,
                                    onClick = {
                                        onActionSelected(action)
                                        showDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = action.getIcon(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = action.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 24.dp, top = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                    ) {
                        androidx.compose.material3.TextButton(onClick = { showDialog = false }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = selectedAction.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Select"
            )
        }
    }
}
