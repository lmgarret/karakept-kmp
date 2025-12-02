package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karakept.app.data.local.entity.SavedFilterEntity
import com.karakept.app.ui.components.FilterIcon

/**
 * Simplified saved filter item showing only icon and name.
 * Filter management (edit/delete/reorder) is now done in FilterManagementScreen.
 */
@Composable
fun SavedFilterItem(
    savedFilter: SavedFilterEntity,
    selected: Boolean = false,
    onApply: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Row {
                FilterIcon(
                    iconName = savedFilter.icon,
                    fontSize = 20.sp
                )
                Spacer(Modifier.width(12.dp))
                Text(savedFilter.name)
            }
        },
        selected = selected,
        badge = if (savedFilter.isDefault) { { Text("Default") } } else null,
        onClick = onApply
    )
}
