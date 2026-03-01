package com.karakept.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Returns a lambda that, when invoked, opens a platform-native file picker for JSON files.
 * [onContent] is called with the file's UTF-8 text content, or null if the user cancelled.
 */
@Composable
expect fun rememberJsonFilePicker(onContent: (String?) -> Unit): () -> Unit
