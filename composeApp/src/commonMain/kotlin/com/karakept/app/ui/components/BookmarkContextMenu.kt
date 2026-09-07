package com.karakept.app.ui.components

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
import com.karakept.app.ui.icons.AppIcons

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
                    imageVector = if (bookmark.isStarred) AppIcons.Default.Star else AppIcons.Default.StarBorder,
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
                    imageVector = if (bookmark.isArchived) AppIcons.Default.Unarchive else AppIcons.Default.Archive,
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
                    imageVector = if (bookmark.isRead) AppIcons.Default.VisibilityOff else AppIcons.Default.Visibility,
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
            leadingIcon = { Icon(imageVector = AppIcons.Default.FolderOpen, contentDescription = null) },
            onClick = { showListPicker = true }
        )

        DropdownMenuItem(
            text = { Text("Edit Tags") },
            leadingIcon = { Icon(imageVector = AppIcons.Default.Edit, contentDescription = null) },
            onClick = { showTagEditor = true }
        )

        HorizontalDivider()

        // External actions
        DropdownMenuItem(
            text = { Text("Copy Link") },
            leadingIcon = { Icon(imageVector = AppIcons.Default.Link, contentDescription = null) },
            onClick = {
                onAction(BookmarkAction.Share)
                onDismiss()
            }
        )

        DropdownMenuItem(
            text = { Text("Open in Browser") },
            leadingIcon = { Icon(imageVector = AppIcons.Default.OpenInBrowser, contentDescription = null) },
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
                    leadingIcon = { Icon(imageVector = AppIcons.Default.AutoAwesome, contentDescription = null) },
                    onClick = {
                        onAction(BookmarkAction.Summarize)
                        onDismiss()
                    }
                )
            }

            if (aiCapabilities.isAdmin) {
                DropdownMenuItem(
                    text = { Text(AiAction.RETAG.label) },
                    leadingIcon = { Icon(imageVector = AppIcons.Default.NewLabel, contentDescription = null) },
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
            leadingIcon = { Icon(imageVector = AppIcons.Default.CheckBox, contentDescription = null) },
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
                    imageVector = AppIcons.Default.Delete,
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
