package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Share
import getPlatform
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.domain.action.AiAction
import com.karakept.api.model.KarakeepList as KarakeepList

/**
 * Bottom sheet showing all available bookmark actions.
 * Displayed from long press or overflow menu.
 * Follows MD3 menu guidelines with ModalBottomSheet and DropdownMenuItem styling.
 *
 * Includes a "Select" option to enter multi-select mode for batch operations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkActionsMenu(
    bookmark: BookmarkEntity,
    availableLists: List<KarakeepList>,
    availableTags: List<String> = emptyList(),
    aiCapabilities: AiCapabilities = AiCapabilities(),
    onAction: (BookmarkAction) -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showListPicker by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = einkModalBorder(BottomSheetDefaults.ExpandedShape),
        sheetState = sheetState
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
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null
                )
            },
            onClick = { showListPicker = true }
        )

        DropdownMenuItem(
            text = { Text("Edit Tags") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null
                )
            },
            onClick = { showTagEditor = true }
        )

        HorizontalDivider()

        // AI actions. Each is hidden unless this server has told us it will run it — summarize
        // needs an inference client, re-tagging needs an admin key.
        if (aiCapabilities.canSummarize || aiCapabilities.isAdmin) {
            if (aiCapabilities.canSummarize) {
                DropdownMenuItem(
                    text = { Text(AiAction.SUMMARIZE.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onAction(BookmarkAction.Summarize)
                        onDismiss()
                    }
                )
            }

            if (aiCapabilities.isAdmin) {
                DropdownMenuItem(
                    text = { Text(AiAction.RETAG.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.NewLabel,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onAction(BookmarkAction.RetagWithAi)
                        onDismiss()
                    }
                )
            }

            HorizontalDivider()
        }

        // External actions
        DropdownMenuItem(
            text = { Text(if (getPlatform().isDesktop) "Copy Link" else "Share") },
            leadingIcon = {
                Icon(
                    imageVector = if (getPlatform().isDesktop) Icons.Default.Link else Icons.Default.Share,
                    contentDescription = null
                )
            },
            onClick = {
                onAction(BookmarkAction.Share)
                onDismiss()
            }
        )

        DropdownMenuItem(
            text = { Text("Open in Browser") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null
                )
            },
            onClick = {
                onAction(BookmarkAction.OpenInBrowser)
                onDismiss()
            }
        )

        HorizontalDivider()

        // Enter multi-select mode
        DropdownMenuItem(
            text = { Text("Select") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CheckBox,
                    contentDescription = null
                )
            },
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
            onClick = { showDeleteConfirm = true },
            colors = MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.error
            )
        )

        Spacer(modifier = Modifier.height(8.dp).navigationBarsPadding())
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            modifier = einkModalBorder(AlertDialogDefaults.shape),
            title = { Text("Delete Bookmark?") },
            text = { Text("This action cannot be undone. The bookmark will be permanently deleted from the server.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(BookmarkAction.Delete)
                        showDeleteConfirm = false
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
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
            onDismiss = { showListPicker = false }
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
            onDismiss = { showTagEditor = false }
        )
    }
}

/**
 * Sealed class representing all possible bookmark actions.
 */
sealed class BookmarkAction {
    data object ToggleFavorite : BookmarkAction()
    data object ToggleArchive : BookmarkAction()
    data object ToggleRead : BookmarkAction()
    data class MoveToList(val listId: String) : BookmarkAction()
    data class UpdateTags(val tags: List<String>) : BookmarkAction()
    data object Share : BookmarkAction()
    data object OpenInBrowser : BookmarkAction()
    data object Delete : BookmarkAction()
    /** Enter multi-select mode for this bookmark. */
    data object Select : BookmarkAction()
    /** Ask the server to generate an AI summary. */
    data object Summarize : BookmarkAction()
    /** Ask the server to re-run AI tagging. Admin-only, so only offered when the probe says so. */
    data object RetagWithAi : BookmarkAction()
}
