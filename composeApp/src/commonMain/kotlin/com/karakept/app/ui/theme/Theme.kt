package com.karakept.app.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.EinkDisplaySettings
import com.karakept.app.data.model.ThemeMode

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.PURPLE,
    einkSettings: EinkDisplaySettings = EinkDisplaySettings(),
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val colorScheme = getColorScheme(themeMode, accentColor, systemIsDark, einkSettings.highContrast)
    val einkMode = EinkMode(
        animationsDisabled = einkSettings.animationsDisabled,
        highContrast = einkSettings.highContrast,
        instantScroll = einkSettings.instantPageScroll,
        enabled = einkSettings.enabled
    )

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalEinkMode provides einkMode,
            LocalIndication provides if (einkMode.animationsDisabled) NoIndication else LocalIndication.current,
            content = content
        )
    }
}
