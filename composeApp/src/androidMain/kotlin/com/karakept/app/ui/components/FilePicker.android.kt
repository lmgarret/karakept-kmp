package com.karakept.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberJsonFilePicker(onContent: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            onContent(null)
            return@rememberLauncherForActivityResult
        }
        val content = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        } catch (e: Exception) {
            null
        }
        onContent(content)
    }
    return { launcher.launch("application/json") }
}
