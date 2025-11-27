package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ViewerMode

/**
 * Segmented button control for Reader/Web viewer modes.
 */
@Composable
fun ViewerModeToggle(
    currentMode: ViewerMode,
    onModeChange: (ViewerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentMode == ViewerMode.READER,
            onClick = { onModeChange(ViewerMode.READER) },
            label = { Text("Reader") }
        )
        FilterChip(
            selected = currentMode == ViewerMode.WEB,
            onClick = { onModeChange(ViewerMode.WEB) },
            label = { Text("Web") }
        )
    }
}
