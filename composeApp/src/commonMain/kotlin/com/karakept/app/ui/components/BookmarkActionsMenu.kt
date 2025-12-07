package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.remote.model.ListDto

/**
 * Sheet showing all available bookmark actions.
 * Displayed from long press or overflow menu.
 */
@Composable
fun BookmarkActionsMenu(
    bookmark: BookmarkEntity,
    availableLists: List<ListDto>,
    onAction: (BookmarkAction) -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showListPicker by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            LazyColumn(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                item {
                    Text(
                        text = "Bookmark Actions",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    Divider()
                }

                item {
                    BookmarkActionItem(
                        icon = if (bookmark.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        label = if (bookmark.isStarred) "Remove from Favorites" else "Add to Favorites",
                        onClick = {
                            onAction(BookmarkAction.ToggleFavorite)
                            onDismiss()
                        }
                    )
                }

                item {
                    BookmarkActionItem(
                        icon = if (bookmark.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        label = if (bookmark.isArchived) "Unarchive" else "Archive",
                        onClick = {
                            onAction(BookmarkAction.ToggleArchive)
                            onDismiss()
                        }
                    )
                }

                item {
                    BookmarkActionItem(
                        icon = if (bookmark.isRead) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        label = if (bookmark.isRead) "Mark as Unread" else "Mark as Read",
                        onClick = {
                            onAction(BookmarkAction.ToggleRead)
                            onDismiss()
                        }
                    )
                }

                item {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    BookmarkActionItem(
                        icon = Icons.Default.FolderOpen,
                        label = "Move to List",
                        onClick = { showListPicker = true }
                    )
                }

                item {
                    BookmarkActionItem(
                        icon = Icons.Default.Edit,
                        label = "Edit Tags",
                        onClick = { showTagEditor = true }
                    )
                }

                item {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    BookmarkActionItem(
                        icon = Icons.Default.Share,
                        label = "Share",
                        onClick = {
                            onAction(BookmarkAction.Share)
                            onDismiss()
                        }
                    )
                }

                item {
                    BookmarkActionItem(
                        icon = Icons.Default.OpenInBrowser,
                        label = "Open in Browser",
                        onClick = {
                            onAction(BookmarkAction.OpenInBrowser)
                            onDismiss()
                        }
                    )
                }

                item {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    BookmarkActionItem(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { showDeleteConfirm = true }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
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
            onTagsUpdated = { newTags ->
                onAction(BookmarkAction.UpdateTags(newTags))
                showTagEditor = false
                onDismiss()
            },
            onDismiss = { showTagEditor = false }
        )
    }
}

@Composable
private fun BookmarkActionItem(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
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
}
