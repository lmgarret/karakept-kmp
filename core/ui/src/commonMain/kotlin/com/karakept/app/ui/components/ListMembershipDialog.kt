package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.karakept.api.model.KarakeepList
import com.karakept.app.domain.ListHierarchyUtils
import com.karakept.app.domain.ListMembership
import com.karakept.app.domain.ListMembershipChange
import com.karakept.app.domain.ListMembershipUtils

/**
 * Bottom sheet for editing which lists one or more bookmarks are in: a checkbox per manual list,
 * applied together on "Done". A bookmark can be in any number of lists, so nothing here is a move.
 *
 * [bookmarksListIds] holds the list ids of each bookmark being edited. With several, a list only
 * some of them are in shows as indeterminate and is left alone unless tapped. While [isLoading],
 * either of the two is still on its way and the sheet shows a busy state with "Done" disabled.
 *
 * Smart lists are not offered: the server computes who is in them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListMembershipDialog(
    lists: List<KarakeepList>,
    bookmarksListIds: List<Set<String>>,
    onApply: (ListMembershipChange) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hierarchy = remember(lists) {
        ListHierarchyUtils.buildListHierarchy(lists.filter { it.type != KarakeepList.Type.SMART && it.id != null })
    }
    val initial = remember(hierarchy, bookmarksListIds) {
        hierarchy.associate { (list, _) ->
            val id = list.id ?: ""
            id to ListMembershipUtils.membershipOf(id, bookmarksListIds)
        }
    }
    // Only the rows the user tapped, so lists that arrive or change while the sheet is open never
    // throw those taps away.
    var tapped by remember { mutableStateOf(emptyMap<String, ListMembership>()) }
    val edited = initial + tapped.filterKeys { it in initial }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = einkModalBorder(BottomSheetDefaults.ExpandedShape),
        sheetState = sheetState
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lists",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                onClick = { onApply(ListMembershipUtils.changeBetween(initial, edited)) },
                enabled = !isLoading
            ) { Text("Done") }
        }

        HorizontalDivider()

        if (isLoading) {
            BusyIndicator(
                modifier = Modifier.fillMaxWidth().padding(16.dp).wrapContentWidth(Alignment.CenterHorizontally)
            )
        } else if (hierarchy.isEmpty()) {
            Text(
                text = "No manual lists yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(hierarchy, key = { (list, _) -> list.id ?: "" }) { (list, depth) ->
                    val listId = list.id ?: ""
                    val state = edited[listId] ?: ListMembership.NONE
                    val toggleableState = state.toToggleableState()
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
                        // The row is the toggle; a second one on the checkbox would be read out twice.
                        trailingContent = { TriStateCheckbox(state = toggleableState, onClick = null) },
                        modifier = Modifier
                            .triStateToggleable(
                                state = toggleableState,
                                role = Role.Checkbox,
                                onClick = {
                                    val before = initial[listId] ?: ListMembership.NONE
                                    tapped = tapped + (listId to ListMembershipUtils.toggled(state, before))
                                }
                            )
                            .padding(start = (depth * 16).dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp).navigationBarsPadding())
    }
}

private fun ListMembership.toToggleableState(): ToggleableState = when (this) {
    ListMembership.ALL -> ToggleableState.On
    ListMembership.SOME -> ToggleableState.Indeterminate
    ListMembership.NONE -> ToggleableState.Off
}
