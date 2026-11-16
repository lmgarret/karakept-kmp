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
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.karakept.api.model.KarakeepList
import com.karakept.app.domain.ListHierarchyUtils
import com.karakept.app.domain.ListMembership
import com.karakept.app.domain.ListMembershipChange
import com.karakept.app.domain.ListMembershipUtils
import com.karakept.app.ui.theme.LocalEinkMode

/**
 * Bottom sheet for editing which lists one or more bookmarks are in: a checkbox per manual list,
 * applied together on "Done". A bookmark can be in any number of lists, so nothing here is a move.
 *
 * [bookmarksListIds] holds the list ids of each bookmark being edited. With several, a list only
 * some of them are in shows as indeterminate and is left alone unless tapped. While [isLoading],
 * either of the two is still on its way and the sheet shows a busy state with "Done" disabled.
 *
 * Smart lists are shown too, read-only: the server decides who is in them. Lists are in the
 * navigation drawer's order, so a manual list nested under a smart one stays under it.
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
    // The drawer's order: the hierarchy of every list, smart and manual together.
    val hierarchy = remember(lists) {
        ListHierarchyUtils.buildListHierarchy(lists).filter { (list, _) -> list.id != null }
    }
    val memberships = remember(hierarchy, bookmarksListIds) {
        hierarchy.associate { (list, _) ->
            val id = list.id ?: ""
            id to ListMembershipUtils.membershipOf(id, bookmarksListIds)
        }
    }
    val initial = remember(hierarchy, memberships) {
        memberships.filterKeys { id -> hierarchy.any { (list, _) -> list.id == id && list.isEditable } }
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
        } else {
            if (hierarchy.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(hierarchy, key = { (list, _) -> list.id ?: "" }) { (list, depth) ->
                        val listId = list.id ?: ""
                        if (list.isEditable) {
                            val state = edited[listId] ?: ListMembership.NONE
                            ListMembershipRow(
                                list = list,
                                depth = depth,
                                state = state,
                                onToggle = {
                                    val before = initial[listId] ?: ListMembership.NONE
                                    tapped = tapped + (listId to ListMembershipUtils.toggled(state, before))
                                }
                            )
                        } else {
                            ListMembershipRow(
                                list = list,
                                depth = depth,
                                state = memberships[listId] ?: ListMembership.NONE,
                                onToggle = null
                            )
                        }
                    }
                }
            }
            if (initial.isEmpty()) {
                Text(
                    text = "No manual lists yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp).navigationBarsPadding())
    }
}

private val KarakeepList.isEditable: Boolean get() = type != KarakeepList.Type.SMART

/**
 * One list in the sheet. [onToggle] is null for a smart list, which shows whether the bookmarks
 * are in it but cannot be changed.
 *
 * The row's own container is transparent and the indent sits inside it, so the whole width —
 * indent included — is the sheet's colour and the tap target.
 */
@Composable
private fun ListMembershipRow(
    list: KarakeepList,
    depth: Int,
    state: ListMembership,
    onToggle: (() -> Unit)?
) {
    val toggleableState = state.toToggleableState()
    val readOnly = onToggle == null
    val icon = list.icon ?: ""
    ListItem(
        headlineContent = {
            Text(text = list.name ?: "Untitled", style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = if (readOnly) {
            { Text(text = "Smart list", style = MaterialTheme.typography.bodySmall) }
        } else null,
        leadingContent = if (icon.isNotBlank()) {
            { Text(text = icon, style = MaterialTheme.typography.titleMedium) }
        } else null,
        // The row is the toggle; a second one on the checkbox would be read out twice.
        trailingContent = {
            TriStateCheckbox(
                state = toggleableState,
                onClick = null,
                enabled = !readOnly,
                colors = readOnlyCheckboxColors()
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .triStateToggleable(
                state = toggleableState,
                enabled = !readOnly,
                role = Role.Checkbox,
                onClick = { onToggle?.invoke() }
            )
            .padding(start = (depth * INDENT_PER_LEVEL_DP).dp)
    )
}

/**
 * Under high contrast a disabled checkbox's 38% tint is a light grey the panel may barely show,
 * and its tick is the only thing saying the bookmark is in the list — so on e-ink a read-only box
 * keeps full contrast, and "Smart list" says why it does not respond.
 */
@Composable
private fun readOnlyCheckboxColors(): CheckboxColors {
    val defaults = CheckboxDefaults.colors()
    if (!LocalEinkMode.current.highContrast) return defaults
    return defaults.copy(
        disabledCheckedBoxColor = defaults.checkedBoxColor,
        disabledBorderColor = defaults.checkedBorderColor,
        disabledUncheckedBorderColor = defaults.uncheckedBorderColor,
        disabledIndeterminateBoxColor = defaults.checkedBoxColor,
        disabledIndeterminateBorderColor = defaults.checkedBorderColor
    )
}

private const val INDENT_PER_LEVEL_DP = 16

private fun ListMembership.toToggleableState(): ToggleableState = when (this) {
    ListMembership.ALL -> ToggleableState.On
    ListMembership.SOME -> ToggleableState.Indeterminate
    ListMembership.NONE -> ToggleableState.Off
}
