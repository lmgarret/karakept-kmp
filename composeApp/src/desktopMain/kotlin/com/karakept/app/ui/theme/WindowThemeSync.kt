package com.karakept.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.WindowScope

@Composable
actual fun SyncWindowTheme() {
    val background = MaterialTheme.colorScheme.background
    // Set AWT window background so the native title-bar / decoration follows the theme
    LaunchedEffect(background) {
        try {
            // Access the AWT Window through the Compose window hierarchy.
            // java.awt.Window.getWindows() returns all open AWT windows.
            val awtColor = java.awt.Color(
                (background.red * 255).toInt(),
                (background.green * 255).toInt(),
                (background.blue * 255).toInt()
            )
            java.awt.Window.getWindows().forEach { window ->
                window.background = awtColor
            }
        } catch (_: Exception) {
            // Best-effort — if AWT access fails, just skip.
        }
    }
}
