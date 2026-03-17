package com.karakept.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import com.karakept.app.data.local.entity.BookmarkEntity

/**
 * Desktop context menu for bookmark actions, shown as a DropdownMenu at the right-click position.
 * Uses the same [BookmarkAction] sealed class as [BookmarkActionsMenu].
 */
@Composable
fun BookmarkContextMenu(
    expanded: Boolean,
    bookmark: BookmarkEntity,
    offset: DpOffset = DpOffset.Zero,
    onAction: (BookmarkAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
    ) {
        // Status actions
        DropdownMenuItem(
            text = { Text(if (bookmark.isStarred) "Remove from Favorites" else "Add to Favorites") },
            leadingIcon = {
                Icon(
                    imageVector = if (bookmark.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null
                )
            },
            onClick = {
                onAction(BookmarkAction.ToggleFavorite)
                onDismiss()
            }
        )

        DropdownMenuItem(
            text = { Text(if (bookmark.isArchived) "Unarchive" else "Archive") },
            leadingIcon = {
                Icon(
                    imageVector = if (bookmark.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    contentDescription = null
                )
            },
            onClick = {
                onAction(BookmarkAction.ToggleArchive)
                onDismiss()
            }
        )

        DropdownMenuItem(
            text = { Text(if (bookmark.isRead) "Mark as Unread" else "Mark as Read") },
            leadingIcon = {
                Icon(
                    imageVector = if (bookmark.isRead) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null
                )
            },
            onClick = {
                onAction(BookmarkAction.ToggleRead)
                onDismiss()
            }
        )

        HorizontalDivider()

        // Organization actions
        DropdownMenuItem(
            text = { Text("Move to List") },
            leadingIcon = { Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null) },
            onClick = {
                onAction(BookmarkAction.MoveToList(""))
                onDismiss()
            }
        )

        DropdownMenuItem(
            text = { Text("Edit Tags") },
            leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
            onClick = {
                onAction(BookmarkAction.UpdateTags(emptyList()))
                onDismiss()
            }
        )

        HorizontalDivider()

        // External actions
        DropdownMenuItem(
            text = { Text("Copy Link") },
            leadingIcon = { Icon(imageVector = Icons.Default.Link, contentDescription = null) },
            onClick = {
                onAction(BookmarkAction.Share)
                onDismiss()
            }
        )

        DropdownMenuItem(
            text = { Text("Open in Browser") },
            leadingIcon = { Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null) },
            onClick = {
                onAction(BookmarkAction.OpenInBrowser)
                onDismiss()
            }
        )

        HorizontalDivider()

        // Enter multi-select mode
        DropdownMenuItem(
            text = { Text("Select") },
            leadingIcon = { Icon(imageVector = Icons.Default.CheckBox, contentDescription = null) },
            onClick = {
                onAction(BookmarkAction.Select)
                onDismiss()
            }
        )

        HorizontalDivider()

        // Destructive action
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onAction(BookmarkAction.Delete)
                onDismiss()
            },
            colors = MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.error
            )
        )
    }
}
