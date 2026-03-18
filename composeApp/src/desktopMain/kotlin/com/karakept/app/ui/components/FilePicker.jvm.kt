package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.wunderbox.nfd.FileDialog
import tv.wunderbox.nfd.FileDialogResult
import tv.wunderbox.nfd.nfd.NfdFileDialog

/**
 * Uses the system-native file picker via nativefiledialog-extended:
 *   - Linux: GTK file chooser (follows the desktop theme, works inside Flatpak)
 *   - macOS: NSOpenPanel
 *   - Windows: IFileOpenDialog
 *
 * No AWT/Swing dialogs are shown; no GTK L&F is required.
 */
@Composable
actual fun rememberJsonFilePicker(onContent: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch(Dispatchers.IO) {
            val content = openNativeJsonFilePicker()
            withContext(Dispatchers.Main) { onContent(content) }
        }
    }
}

@Composable
actual fun rememberDirectoryPicker(onDirectorySelected: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch(Dispatchers.IO) {
            val path = openNativeDirectoryPicker()
            withContext(Dispatchers.Main) { onDirectorySelected(path) }
        }
    }
}

private fun openNativeJsonFilePicker(): String? {
    val result = NfdFileDialog().pickFile(
        filters = listOf(FileDialog.Filter("JSON backup files", listOf("json"))),
    )
    return when (result) {
        is FileDialogResult.Success<*> -> runCatching { (result.value as java.io.File).readText() }.getOrNull()
        else -> null
    }
}

private fun openNativeDirectoryPicker(): String? {
    val result = NfdFileDialog().pickDirectory()
    return when (result) {
        is FileDialogResult.Success<*> -> (result.value as java.io.File).absolutePath
        else -> null
    }
}
