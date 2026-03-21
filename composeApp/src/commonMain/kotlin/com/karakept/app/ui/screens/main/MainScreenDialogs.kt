package com.karakept.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.karakept.app.ui.screens.toggleBookmarkArchive
import com.karakept.app.ui.screens.toggleBookmarkFavorite
import com.karakept.app.ui.screens.toggleBookmarkRead
import com.karakept.app.ui.screens.moveBookmarkToList
import com.karakept.app.ui.screens.updateBookmarkTags
import com.karakept.app.ui.screens.deleteBookmark
import com.karakept.app.ui.screens.enterSelectionMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.ui.components.AddBookmarkDialog
import com.karakept.app.ui.components.BookmarkAction
import com.karakept.app.ui.components.BookmarkActionsMenu
import com.karakept.app.ui.components.FilterBottomPanel
import com.karakept.app.ui.components.ListPickerDialog
import com.karakept.app.ui.components.TagEditorDialog
import com.karakept.app.ui.screens.MainScreenModel
import com.karakept.api.model.KarakeepList
import com.karakept.app.domain.action.ActionSnackbarManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Rename list dialog with name and icon (emoji) fields.
 */
@Composable
fun RenameListDialog(
    initialName: String,
    initialIcon: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon (emoji)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), icon.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Filter scrim + bottom panel for compact (mobile) layout.
 * On expanded (desktop) layout, the filter renders in the reader pane column instead.
 */
@Composable
fun MainScreenFilterOverlay(
    showFilterDialog: Boolean,
    onDismissFilter: () -> Unit,
    currentFilter: FilterConfig,
    topTagsWithCounts: List<String>,
    allAvailableTags: List<String>,
    lists: List<KarakeepList>,
    onFilterChange: (FilterConfig) -> Unit,
    onFilterReset: () -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = showFilterDialog,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onDismissFilter()
                }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        FilterBottomPanel(
            visible = showFilterDialog,
            currentFilter = currentFilter,
            availableTags = topTagsWithCounts,
            allTags = allAvailableTags,
            availableLists = lists,
            onDismiss = onDismissFilter,
            onFilterChange = onFilterChange,
            onReset = onFilterReset
        )
    }
}

/**
 * Bookmark actions bottom sheet menu.
 */
@Composable
fun MainScreenBookmarkActionsMenu(
    bookmark: BookmarkEntity?,
    lists: List<KarakeepList>,
    allAvailableTags: List<String>,
    screenModel: MainScreenModel,
    snackbarManager: ActionSnackbarManager,
    scope: CoroutineScope,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onDismiss: () -> Unit
) {
    bookmark?.let { bm ->
        BookmarkActionsMenu(
            bookmark = bm,
            availableLists = lists,
            availableTags = allAvailableTags,
            onAction = { action ->
                when (action) {
                    is BookmarkAction.ToggleArchive -> screenModel.toggleBookmarkArchive(bm)
                    is BookmarkAction.ToggleFavorite -> screenModel.toggleBookmarkFavorite(bm)
                    is BookmarkAction.ToggleRead -> screenModel.toggleBookmarkRead(bm)
                    is BookmarkAction.MoveToList -> screenModel.moveBookmarkToList(bm, action.listId)
                    is BookmarkAction.UpdateTags -> screenModel.updateBookmarkTags(bm, action.tags)
                    is BookmarkAction.Delete -> screenModel.deleteBookmark(bm)
                    is BookmarkAction.Share -> {
                        com.karakept.app.utils.ShareUtils.shareText(bm.url, bm.title)
                    }
                    is BookmarkAction.OpenInBrowser -> {
                        try {
                            uriHandler.openUri(bm.url)
                            scope.launch { snackbarManager.showSnackbar("Opening in browser") }
                        } catch (e: Exception) {
                            scope.launch { snackbarManager.showSnackbar("Could not open link") }
                        }
                    }
                    is BookmarkAction.Select -> screenModel.enterSelectionMode(bm)
                }
            },
            onDismiss = onDismiss
        )
    }
}

/**
 * Add bookmark dialog wrapper.
 */
@Composable
fun MainScreenAddBookmarkDialog(
    onConfirm: (url: String) -> Unit,
    onDismiss: () -> Unit
) {
    AddBookmarkDialog(
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * Batch delete confirmation dialog.
 */
@Composable
fun BatchDeleteConfirmDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $selectedCount bookmark${if (selectedCount > 1) "s" else ""}?") },
        text = { Text("This action cannot be undone. The selected bookmarks will be permanently deleted from the server.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Batch move-to-list picker dialog.
 */
@Composable
fun BatchListPickerDialog(
    lists: List<KarakeepList>,
    onListSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ListPickerDialog(
        lists = lists,
        currentListIds = emptyList(),
        onListSelected = onListSelected,
        onDismiss = onDismiss
    )
}

/**
 * Batch set-tags dialog.
 */
@Composable
fun BatchTagEditorDialog(
    selectedBookmarkIds: Set<Long>,
    bookmarks: List<BookmarkEntity>,
    allAvailableTags: List<String>,
    onTagsUpdated: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // Pre-populate with tags shared by ALL selected bookmarks (intersection)
    val selectedBookmarks = remember(selectedBookmarkIds, bookmarks) {
        bookmarks.filter { it.remoteId in selectedBookmarkIds }
    }
    val commonTags = remember(selectedBookmarks) {
        if (selectedBookmarks.isEmpty()) emptyList()
        else {
            val first = selectedBookmarks.first().tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            selectedBookmarks.drop(1).fold(first) { acc, bm ->
                val bmTags = bm.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                acc intersect bmTags
            }.toList()
        }
    }
    TagEditorDialog(
        currentTags = commonTags,
        availableTags = allAvailableTags,
        title = "Set Tags (${selectedBookmarkIds.size} bookmarks)",
        confirmLabel = "Apply",
        onTagsUpdated = onTagsUpdated,
        onDismiss = onDismiss
    )
}
