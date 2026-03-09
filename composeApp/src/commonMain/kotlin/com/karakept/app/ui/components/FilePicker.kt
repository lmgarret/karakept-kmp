package com.karakept.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Returns a lambda that, when invoked, opens a platform-native file picker for JSON files.
 * [onContent] is called with the file's UTF-8 text content, or null if the user cancelled.
 */
@Composable
expect fun rememberJsonFilePicker(onContent: (String?) -> Unit): () -> Unit

/**
 * Returns a lambda that, when invoked, opens a platform-native directory picker.
 * [onDirectorySelected] is called with the selected directory identifier, or null if cancelled.
 *
 * On Android the identifier is a SAF URI string (`content://…`); on Desktop it is a plain
 * file-system path.  Pass the result to [SettingsRepository.setBackupExportDirectory] to
 * persist it, and use [FileUtils.getDirectoryDisplayName] to show a human-readable label.
 */
@Composable
expect fun rememberDirectoryPicker(onDirectorySelected: (String?) -> Unit): () -> Unit
