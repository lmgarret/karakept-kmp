package com.karakept.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
