package com.karakept.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun SyncWindowTheme() {
    val background = MaterialTheme.colorScheme.background
    // Heuristic: if the background luminance is low, we're in dark mode
    val isAppDark = (background.red * 0.299f + background.green * 0.587f + background.blue * 0.114f) < 0.5f

    LaunchedEffect(background, isAppDark) {
        try {
            val awtColor = java.awt.Color(
                (background.red * 255).toInt(),
                (background.green * 255).toInt(),
                (background.blue * 255).toInt()
            )
            java.awt.Window.getWindows().forEach { window ->
                window.background = awtColor
                if (window is javax.swing.JFrame) {
                    val rootPane = window.rootPane
                    // JetBrains Runtime dark title bar support
                    rootPane?.putClientProperty("jetbrains.awt.windowDarkAppearance", isAppDark)
                    // Standard macOS JDK title bar appearance
                    rootPane?.putClientProperty("apple.awt.windowAppearance",
                        if (isAppDark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"
                    )
                }
            }
            // Also update the system-level property for any new windows
            if (System.getProperty("os.name").lowercase().contains("mac")) {
                System.setProperty("apple.awt.application.appearance",
                    if (isAppDark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"
                )
            }
        } catch (_: Exception) {
            // Best-effort — if AWT access fails, just skip.
        }
    }
}
