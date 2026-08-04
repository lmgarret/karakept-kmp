package com.karakept.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.SwipeAction

fun SwipeAction.getIcon(): ImageVector {
    return when (this) {
        SwipeAction.ARCHIVE -> Icons.Default.Archive
        SwipeAction.FAVOURITE -> Icons.Default.Star
        SwipeAction.MARK_READ -> Icons.Default.Visibility
        SwipeAction.DELETE -> Icons.Default.Delete
        SwipeAction.SHARE -> Icons.Default.Share
        SwipeAction.OPEN_IN_BROWSER -> Icons.Default.OpenInBrowser
        SwipeAction.ADD_TAG -> Icons.AutoMirrored.Filled.Label
        SwipeAction.ADD_TO_LIST -> Icons.AutoMirrored.Filled.List
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
        SwipeAction.ADD_TAG -> Color(0xFF009688) // Teal
        SwipeAction.ADD_TO_LIST -> Color(0xFF673AB7) // Deep Purple
        SwipeAction.NONE -> Color.Transparent
    }
}

fun CustomSwipeActionConfig?.getEffectiveColor(action: SwipeAction): Color {
    val hex = this?.colorHex
    if (hex != null) {
        return try {
            val colorLong = hex.trimStart('#').toLong(16)
            val alpha = if (hex.length > 7) (colorLong shr 24 and 0xFF) else 0xFF
            Color(
                red = ((colorLong shr 16) and 0xFF).toInt() / 255f,
                green = ((colorLong shr 8) and 0xFF).toInt() / 255f,
                blue = (colorLong and 0xFF).toInt() / 255f,
                alpha = alpha.toInt() / 255f
            )
        } catch (e: Exception) {
            action.getColor()
        }
    }
    return action.getColor()
}
