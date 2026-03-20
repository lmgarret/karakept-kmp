package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Uses the system-native file picker via nativefiledialog-extended when available:
 *   - Linux: GTK file chooser (follows the desktop theme, works inside Flatpak)
 *   - macOS: NSOpenPanel
 *   - Windows: IFileOpenDialog
 *
 * Falls back to Swing [JFileChooser] when nativefiledialog is not on the classpath
 * (e.g. during `./gradlew run` in a devcontainer, where the nativefiledialog JAR
 * is excluded to avoid a kotlin-stdlib class-shadowing conflict with Skiko).
 */
@Composable
actual fun rememberJsonFilePicker(onContent: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch(Dispatchers.IO) {
            val content = pickJsonFile()
            withContext(Dispatchers.Main) { onContent(content) }
        }
    }
}

@Composable
actual fun rememberDirectoryPicker(onDirectorySelected: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch(Dispatchers.IO) {
            val path = pickDirectory()
            withContext(Dispatchers.Main) { onDirectorySelected(path) }
        }
    }
}

private fun pickJsonFile(): String? {
    return try {
        NfdFilePicker.pickJsonFile()
    } catch (_: Throwable) {
        swingPickJsonFile()
    }
}

private fun pickDirectory(): String? {
    return try {
        NfdFilePicker.pickDirectory()
    } catch (_: Throwable) {
        swingPickDirectory()
    }
}

private fun swingPickJsonFile(): String? {
    var content: String? = null
    java.awt.EventQueue.invokeAndWait {
        val chooser = JFileChooser()
        chooser.fileFilter = FileNameExtensionFilter("JSON backup files", "json")
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            content = chooser.selectedFile.readText()
        }
    }
    return content
}

private fun swingPickDirectory(): String? {
    var path: String? = null
    java.awt.EventQueue.invokeAndWait {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            path = chooser.selectedFile.absolutePath
        }
    }
    return path
}
