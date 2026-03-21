package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import com.karakept.app.utils.AppLogger
import java.awt.Desktop
import java.net.URI

@Composable
actual fun rememberCustomTabOpener(): (String) -> Unit {
    return { url ->
        try {
            Desktop.getDesktop().browse(URI(url))
        } catch (e: Exception) {
            AppLogger.e("CustomTabOpener", "Failed to open URL in browser: ${e.message}", e)
        }
    }
}
