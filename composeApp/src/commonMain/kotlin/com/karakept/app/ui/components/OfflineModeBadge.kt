package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Persistent indicator badge when Offline mode is active.
 *
 * Shows a colored dot and text to remind users that sync functionality is disabled
 * and actions are queued locally. Tapping navigates to offline settings.
 *
 * @param isAutoOffline True if offline mode was auto-detected (network unavailable)
 * @param onClick Called when the badge is tapped (navigates to settings)
 * @param modifier Modifier for positioning
 */
@Composable
fun OfflineModeBadge(
    isAutoOffline: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Different colors for manual vs auto-detected offline
    val containerColor = if (isAutoOffline) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    val dotColor = if (isAutoOffline) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }

    val textColor = if (isAutoOffline) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    val labelText = if (isAutoOffline) "Auto Offline" else "Offline"

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = containerColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored dot indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = dotColor,
                        shape = CircleShape
                    )
            )
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
