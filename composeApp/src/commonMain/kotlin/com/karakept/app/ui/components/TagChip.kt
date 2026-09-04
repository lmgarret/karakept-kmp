package com.karakept.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.theme.LocalEinkMode

/**
 * Reusable tag chip matching the BookmarkTagsDisplay surface design.
 *
 * Use this component everywhere a single tag is displayed as a chip — in the tag editor,
 * filter panel, details panel, and bookmark cards. This ensures a consistent tag appearance
 * throughout the app.
 *
 * For displaying a list of tags from a comma-separated string, use [BookmarkTagsDisplay] instead.
 *
 * @param tag The tag text to display
 * @param selected Whether the chip appears selected (e.g. when used as a filter toggle)
 * @param onRemove Optional remove callback — shows an ✕ button when provided (e.g. in TagEditorDialog)
 * @param onClick Optional click callback — makes the chip clickable (e.g. for filtering by tag)
 * @param modifier Optional modifier
 */
@Composable
fun TagChip(
    tag: String,
    selected: Boolean = false,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // On e-ink the 2dp shadow renders as nothing and the tonal container is flattened to the page,
    // so a chip would be invisible. The scheme keeps one grey for exactly this: it separates the
    // chip from the page on its own, without an outline per tag. "Selected" inverts to ink-on-page
    // rather than shifting hue, since hue does not survive a monochrome panel.
    val highContrast = LocalEinkMode.current.highContrast
    val containerColor = when {
        highContrast && selected -> MaterialTheme.colorScheme.onSurface
        highContrast -> MaterialTheme.colorScheme.secondaryContainer
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
    }
    val contentColor = when {
        highContrast && selected -> MaterialTheme.colorScheme.surface
        highContrast -> MaterialTheme.colorScheme.onSecondaryContainer
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val border = when {
        highContrast -> null
        selected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        shadowElevation = if (highContrast) 0.dp else 2.dp,
        border = border,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = 8.dp,
                end = if (onRemove != null) 2.dp else 8.dp,
                top = 4.dp,
                bottom = 4.dp
            )
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Default.Close,
                        contentDescription = "Remove tag",
                        modifier = Modifier.size(12.dp),
                        tint = contentColor
                    )
                }
            }
        }
    }
}
