package com.karakept.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
 * @param onRemove Optional remove callback — shows an ✕ button when provided (e.g. in TagEditorDialog)
 * @param onClick Optional click callback — makes the chip clickable (e.g. for filtering by tag)
 * @param modifier Optional modifier
 */
@Composable
fun TagChip(
    tag: String,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 2.dp,
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
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove tag",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
