package com.karakept.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NewLabel
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.domain.action.AiAction

/**
 * Desktop context menu for bookmark actions, shown as a DropdownMenu at the right-click position.
 * Uses the same [BookmarkAction] sealed class as [BookmarkActionsMenu].
 *
 * "Move to List" and "Edit Tags" open the shared [ListPickerDialog] / [TagEditorDialog] and emit
 * the chosen value, mirroring [BookmarkActionsMenu].
 */
@Composable
fun BookmarkContextMenu(
    expanded: Boolean,
    bookmark: BookmarkEntity,
    availableLists: List<KarakeepList> = emptyList(),
    availableTags: List<String> = emptyList(),
    aiCapabilities: AiCapabilities = AiCapabilities(),
    offset: DpOffset = DpOffset.Zero,
    onAction: (BookmarkAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showListPicker by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded && !showListPicker && !showTagEditor,
        onDismissRequest = onDismiss,
        offset = offset,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
        border = einkOutlineBorder()
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
            onClick = { showListPicker = true }
        )

        DropdownMenuItem(
            text = { Text("Edit Tags") },
            leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
            onClick = { showTagEditor = true }
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

        // AI actions, gated the same way as in BookmarkActionsMenu.
        if (aiCapabilities.canSummarize || aiCapabilities.isAdmin) {
            if (aiCapabilities.canSummarize) {
                DropdownMenuItem(
                    text = { Text(AiAction.SUMMARIZE.label) },
                    leadingIcon = { Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null) },
                    onClick = {
                        onAction(BookmarkAction.Summarize)
                        onDismiss()
                    }
                )
            }

            if (aiCapabilities.isAdmin) {
                DropdownMenuItem(
                    text = { Text(AiAction.RETAG.label) },
                    leadingIcon = { Icon(imageVector = Icons.Default.NewLabel, contentDescription = null) },
                    onClick = {
                        onAction(BookmarkAction.RetagWithAi)
                        onDismiss()
                    }
                )
            }

            HorizontalDivider()
        }

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

    // List picker dialog
    if (showListPicker && availableLists.isNotEmpty()) {
        ListPickerDialog(
            lists = availableLists,
            currentListIds = bookmark.listIds.split(",").filter { it.isNotBlank() },
            onListSelected = { listId ->
                onAction(BookmarkAction.MoveToList(listId))
                showListPicker = false
                onDismiss()
            },
            onDismiss = {
                showListPicker = false
                onDismiss()
            }
        )
    }

    // Tag editor dialog
    if (showTagEditor) {
        TagEditorDialog(
            currentTags = bookmark.tags.split(",").filter { it.isNotBlank() },
            availableTags = availableTags,
            onTagsUpdated = { newTags ->
                onAction(BookmarkAction.UpdateTags(newTags))
                showTagEditor = false
                onDismiss()
            },
            onDismiss = {
                showTagEditor = false
                onDismiss()
            }
        )
    }
}
