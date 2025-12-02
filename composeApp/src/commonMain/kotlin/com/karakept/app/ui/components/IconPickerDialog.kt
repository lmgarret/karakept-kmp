package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Icon picker dialog for selecting Material Icons from organized categories.
 * Uses MaterialIconHelper for icon data.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerDialog(
    currentIcon: String,
    onDismiss: () -> Unit,
    onIconSelected: (String) -> Unit
) {
    var selectedIcon by remember { mutableStateOf(currentIcon) }
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = MaterialIconHelper.categories.keys.toList()
    val currentCategory = categories.getOrNull(selectedTab) ?: categories.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Icon") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )

                // Category tabs
                if (searchQuery.isEmpty()) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        categories.forEachIndexed { index, category ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(category, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Icon grid
                val iconsToShow = if (searchQuery.isEmpty()) {
                    MaterialIconHelper.categories[currentCategory] ?: emptyList()
                } else {
                    MaterialIconHelper.allIcons.keys.filter { 
                        it.contains(searchQuery, ignoreCase = true) 
                    }.sorted()
                }

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    iconsToShow.forEach { iconName ->
                        IconItem(
                            iconName = iconName,
                            isSelected = iconName == selectedIcon,
                            onClick = { selectedIcon = iconName }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onIconSelected(selectedIcon)
                    onDismiss()
                }
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun IconItem(
    iconName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        FilterIcon(
            iconName = iconName,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(2.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
