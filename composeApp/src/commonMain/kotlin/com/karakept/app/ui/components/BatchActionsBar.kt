package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.api.model.KarakeepList

/**
 * Bottom action bar shown when bookmarks are selected.
 * Provides batch operations without snackbars.
 */
@Composable
fun BatchActionsBar(
    selectedCount: Int,
    allSelected: Boolean,
    availableLists: List<KarakeepList>,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onFavourite: () -> Unit,
    onUnfavourite: () -> Unit,
    onDelete: () -> Unit,
    onMoveToList: (String) -> Unit,
    onSelectAll: () -> Unit
) {
    var showListPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BottomAppBar(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Select all toggle
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = if (allSelected) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (allSelected) "Deselect all" else "Select all",
                    tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onMarkRead) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Mark as read"
                )
            }

            IconButton(onClick = onMarkUnread) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Mark as unread"
                )
            }

            IconButton(onClick = onArchive) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = "Archive"
                )
            }

            IconButton(onClick = onUnarchive) {
                Icon(
                    imageVector = Icons.Default.Unarchive,
                    contentDescription = "Unarchive"
                )
            }

            IconButton(onClick = onFavourite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Add to favorites"
                )
            }

            IconButton(onClick = onUnfavourite) {
                Icon(
                    imageVector = Icons.Default.StarBorder,
                    contentDescription = "Remove from favorites"
                )
            }

            if (availableLists.isNotEmpty()) {
                IconButton(onClick = { showListPicker = true }) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Add to list"
                    )
                }
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showListPicker) {
        ListPickerDialog(
            lists = availableLists,
            currentListIds = emptyList(),
            onListSelected = { listId ->
                onMoveToList(listId)
                showListPicker = false
            },
            onDismiss = { showListPicker = false }
        )
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $selectedCount bookmark${if (selectedCount > 1) "s" else ""}?") },
            text = { Text("This action cannot be undone. The selected bookmarks will be permanently deleted from the server.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
