package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** What the user chose to create in [AddBookmarkDialog]. */
sealed interface AddBookmarkInput {
    data class Link(val url: String) : AddBookmarkInput
    data class Note(val text: String) : AddBookmarkInput
}

private enum class AddBookmarkMode { LINK, NOTE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookmarkDialog(
    onConfirm: (AddBookmarkInput) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(AddBookmarkMode.LINK) }
    var url by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val canConfirm = when (mode) {
        AddBookmarkMode.LINK -> url.isNotBlank()
        AddBookmarkMode.NOTE -> noteText.isNotBlank()
    }

    fun confirm() {
        when (mode) {
            AddBookmarkMode.LINK -> if (url.isNotBlank()) onConfirm(AddBookmarkInput.Link(url.trim()))
            AddBookmarkMode.NOTE -> if (noteText.isNotBlank()) onConfirm(AddBookmarkInput.Note(noteText.trim()))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Add Bookmark",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == AddBookmarkMode.LINK,
                        onClick = { mode = AddBookmarkMode.LINK },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Link") }
                    SegmentedButton(
                        selected = mode == AddBookmarkMode.NOTE,
                        onClick = { mode = AddBookmarkMode.NOTE },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Note") }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (mode) {
                    AddBookmarkMode.LINK -> OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        placeholder = { Text("https://...") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { confirm() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    AddBookmarkMode.NOTE -> OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note") },
                        placeholder = { Text("Write a note...") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Default
                        ),
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = { confirm() },
                        enabled = canConfirm
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}
