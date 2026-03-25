package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Filters [availableTags] to those containing [searchInput] (case-insensitive)
 * and not already in [currentTags], sorted alphabetically, max 8 results.
 */
internal fun filterTagSuggestions(
    availableTags: List<String>,
    searchInput: String,
    currentTags: List<String>
): List<String> {
    if (searchInput.isBlank()) return emptyList()
    return availableTags
        .filter { it.contains(searchInput, ignoreCase = true) && !currentTags.contains(it) }
        .sortedBy { it.lowercase() }
        .take(8)
}

/**
 * Whether the current [searchInput] can be added as a tag.
 */
internal fun canAddTag(
    searchInput: String,
    currentTags: List<String>,
    availableTags: List<String>,
    canCreateNew: Boolean
): Boolean {
    val trimmed = searchInput.trim()
    if (trimmed.isBlank()) return false
    if (currentTags.contains(trimmed)) return false
    if (canCreateNew) return true
    return availableTags.any { it.equals(trimmed, ignoreCase = true) }
}

/**
 * Finds an exact case-insensitive match for [searchInput] in [availableTags].
 */
internal fun findExactTagMatch(searchInput: String, availableTags: List<String>): String? {
    return availableTags.find { it.equals(searchInput.trim(), ignoreCase = true) }
}

/**
 * MD3 AlertDialog for editing (or filtering by) tags.
 *
 * As the user types, matching tags from [availableTags] are shown as clickable suggestion chips
 * below the input field. Clicking a suggestion adds it immediately.
 *
 * @param currentTags Tags already selected/applied (shown as removable chips).
 * @param availableTags Full list of known tag names used for autocomplete suggestions.
 * @param canCreateNew When true the user may add free-text tags not present in [availableTags].
 *                     Set to false in filter-selection mode where only existing tags make sense.
 * @param title Dialog title shown in the header.
 * @param confirmLabel Label for the confirm button (default "Save").
 * @param onTagsUpdated Called with the final tag list when the user confirms.
 * @param onDismiss Called when the dialog should be closed without saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    currentTags: List<String>,
    availableTags: List<String> = emptyList(),
    canCreateNew: Boolean = true,
    title: String = "Edit Tags",
    confirmLabel: String = "Save",
    onTagsUpdated: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var tags by remember { mutableStateOf(currentTags.toMutableList()) }
    var searchInput by remember { mutableStateOf("") }

    val suggestions = remember(availableTags, searchInput, tags) {
        filterTagSuggestions(availableTags, searchInput, tags)
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotBlank() && !tags.contains(trimmed)) {
            tags = (tags + trimmed).toMutableList()
            searchInput = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // Current tags shown as removable chips
                if (tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            TagChip(
                                tag = tag,
                                onRemove = { tags = tags.filter { it != tag }.toMutableList() }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Search / add input
                val exactMatch = findExactTagMatch(searchInput, availableTags)
                val canAdd = canAddTag(searchInput, tags, availableTags, canCreateNew)

                TextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    placeholder = { Text(if (canCreateNew) "Search or add tag" else "Search tags") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchInput.isNotBlank()) {
                            IconButton(
                                enabled = canAdd,
                                onClick = { addTag(exactMatch ?: searchInput) }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add tag")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (canAdd) addTag(exactMatch ?: searchInput) }
                    ),
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Autocomplete suggestions
                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Suggestions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            TagChip(
                                tag = suggestion,
                                onClick = { addTag(suggestion) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onTagsUpdated(tags.filter { it.isNotBlank() }) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
