package com.karakept.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.ThemeMode

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.PURPLE,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val colorScheme = getColorScheme(themeMode, accentColor, systemIsDark)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
