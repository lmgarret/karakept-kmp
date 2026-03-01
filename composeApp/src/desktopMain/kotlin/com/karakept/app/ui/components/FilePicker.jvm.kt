package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberJsonFilePicker(onContent: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch(Dispatchers.IO) {
            val chooser = JFileChooser().apply {
                dialogTitle = "Import Backup"
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("JSON backup files (*.json)", "json")
            }
            val result = chooser.showOpenDialog(null)
            val content = if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    chooser.selectedFile.readText()
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            withContext(Dispatchers.Main) {
                onContent(content)
            }
        }
    }
}
