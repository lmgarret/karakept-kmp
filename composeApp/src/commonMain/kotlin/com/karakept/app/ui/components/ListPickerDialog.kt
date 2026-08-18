package com.karakept.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.api.model.KarakeepList
import com.karakept.app.domain.ListHierarchyUtils

/**
 * Bottom sheet for selecting a list to move a bookmark to.
 * Follows MD3 menu guidelines with ModalBottomSheet and ListItem components.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListPickerDialog(
    lists: List<KarakeepList>,
    currentListIds: List<String>,
    onListSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = einkModalBorder(BottomSheetDefaults.ExpandedShape),
        sheetState = sheetState
    ) {
        Text(
            text = "Move to List",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        HorizontalDivider()

        // Show lists in sorted hierarchical order (parents before children, alphabetical at each level)
        val hierarchy = remember(lists) { ListHierarchyUtils.buildListHierarchy(lists) }

        LazyColumn {
            items(hierarchy) { (list, depth) ->
                val listId = list.id ?: ""
                val isInList = currentListIds.contains(listId)
                val icon = list.icon ?: ""

                ListItem(
                    headlineContent = {
                        Text(
                            text = list.name ?: "Untitled",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingContent = if (icon.isNotBlank()) {
                        { Text(text = icon, style = MaterialTheme.typography.titleMedium) }
                    } else null,
                    trailingContent = if (isInList) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Currently in list",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    colors = if (isInList) {
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    // Indent child lists to show hierarchy
                    modifier = Modifier
                        .padding(start = (depth * 16).dp)
                        .clickable { onListSelected(listId) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp).navigationBarsPadding())
    }
}
