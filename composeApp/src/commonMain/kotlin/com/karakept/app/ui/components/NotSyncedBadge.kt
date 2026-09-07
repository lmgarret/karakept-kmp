package com.karakept.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.icons.AppIcons

/**
 * Badge indicating that a bookmark's content has not been synced for offline reading.
 *
 * Positioned in the same location as ReadingTimeBadge (bottom-right corner of bookmark items).
 * Shows a cloud-off icon to indicate the article won't be readable offline.
 * Only shown when app is in offline mode.
 *
 * @param modifier Modifier for positioning
 */
@Composable
fun NotSyncedBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 2.dp
    ) {
        Icon(
            imageVector = AppIcons.Outlined.CloudOff,
            contentDescription = "Not synced for offline",
            modifier = Modifier
                .padding(6.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
