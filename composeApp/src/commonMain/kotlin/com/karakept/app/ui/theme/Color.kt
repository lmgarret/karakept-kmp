package com.karakept.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.ThemeMode

// Purple (Default)
private val PurpleLight = Color(0xFF6750A4)
private val PurpleSecondaryLight = Color(0xFF625B71)
private val PurpleTertiaryLight = Color(0xFF7D5260)

private val PurpleDark = Color(0xFFD0BCFF)
private val PurpleSecondaryDark = Color(0xFFCCC2DC)
private val PurpleTertiaryDark = Color(0xFFEFB8C8)

// Blue
private val BlueLight = Color(0xFF1976D2)
private val BlueSecondaryLight = Color(0xFF455A64)
private val BlueTertiaryLight = Color(0xFF0097A7)

private val BlueDark = Color(0xFF90CAF9)
private val BlueSecondaryDark = Color(0xFFB0BEC5)
private val BlueTertiaryDark = Color(0xFF80DEEA)

// Green
private val GreenLight = Color(0xFF388E3C)
private val GreenSecondaryLight = Color(0xFF558B2F)
private val GreenTertiaryLight = Color(0xFF00796B)

private val GreenDark = Color(0xFF81C784)
private val GreenSecondaryDark = Color(0xFF9CCC65)
private val GreenTertiaryDark = Color(0xFF4DB6AC)

// Orange
private val OrangeLight = Color(0xFFE65100)
private val OrangeSecondaryLight = Color(0xFFF57C00)
private val OrangeTertiaryLight = Color(0xFFEF6C00)

private val OrangeDark = Color(0xFFFFB74D)
private val OrangeSecondaryDark = Color(0xFFFFCC80)
private val OrangeTertiaryDark = Color(0xFFFFAB91)

// Red
private val RedLight = Color(0xFFC62828)
private val RedSecondaryLight = Color(0xFFAD1457)
private val RedTertiaryLight = Color(0xFF6A1B9A)

private val RedDark = Color(0xFFEF5350)
private val RedSecondaryDark = Color(0xFFEC407A)
private val RedTertiaryDark = Color(0xFFAB47BC)

// Pink
private val PinkLight = Color(0xFFC2185B)
private val PinkSecondaryLight = Color(0xFF7B1FA2)
private val PinkTertiaryLight = Color(0xFF512DA8)

private val PinkDark = Color(0xFFF48FB1)
private val PinkSecondaryDark = Color(0xFFCE93D8)
private val PinkTertiaryDark = Color(0xFFB39DDB)

/**
 * Generate a ColorScheme based on theme mode and accent color.
 */
@androidx.compose.runtime.Composable
fun getColorScheme(themeMode: ThemeMode, accentColor: AccentColor, isDarkTheme: Boolean): androidx.compose.material3.ColorScheme {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isDarkTheme
    }

    val isAmoled = themeMode == ThemeMode.AMOLED

    return if (isDark) {
        val dynamicScheme = if (accentColor == AccentColor.DYNAMIC) getDynamicColorScheme(true) else null
        
        val baseScheme = dynamicScheme ?: when (accentColor) {
            AccentColor.PURPLE, AccentColor.DYNAMIC -> darkColorScheme(
                primary = PurpleDark,
                secondary = PurpleSecondaryDark,
                tertiary = PurpleTertiaryDark
            )
            AccentColor.BLUE -> darkColorScheme(
                primary = BlueDark,
                secondary = BlueSecondaryDark,
                tertiary = BlueTertiaryDark
            )
            AccentColor.GREEN -> darkColorScheme(
                primary = GreenDark,
                secondary = GreenSecondaryDark,
                tertiary = GreenTertiaryDark
            )
            AccentColor.ORANGE -> darkColorScheme(
                primary = OrangeDark,
                secondary = OrangeSecondaryDark,
                tertiary = OrangeTertiaryDark
            )
            AccentColor.RED -> darkColorScheme(
                primary = RedDark,
                secondary = RedSecondaryDark,
                tertiary = RedTertiaryDark
            )
            AccentColor.PINK -> darkColorScheme(
                primary = PinkDark,
                secondary = PinkSecondaryDark,
                tertiary = PinkTertiaryDark
            )
        }

        // Apply AMOLED pure black if needed
        if (isAmoled) {
            baseScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color(0xFF1A1A1A),
                surfaceContainer = Color(0xFF0D0D0D),
                surfaceContainerLow = Color(0xFF050505),
                surfaceContainerHigh = Color(0xFF1F1F1F)
            )
        } else {
            baseScheme
        }
    } else {
        val dynamicScheme = if (accentColor == AccentColor.DYNAMIC) getDynamicColorScheme(false) else null
        
        dynamicScheme ?: when (accentColor) {
            AccentColor.PURPLE, AccentColor.DYNAMIC -> lightColorScheme(
                primary = PurpleLight,
                secondary = PurpleSecondaryLight,
                tertiary = PurpleTertiaryLight
            )
            AccentColor.BLUE -> lightColorScheme(
                primary = BlueLight,
                secondary = BlueSecondaryLight,
                tertiary = BlueTertiaryLight
            )
            AccentColor.GREEN -> lightColorScheme(
                primary = GreenLight,
                secondary = GreenSecondaryLight,
                tertiary = GreenTertiaryLight
            )
            AccentColor.ORANGE -> lightColorScheme(
                primary = OrangeLight,
                secondary = OrangeSecondaryLight,
                tertiary = OrangeTertiaryLight
            )
            AccentColor.RED -> lightColorScheme(
                primary = RedLight,
                secondary = RedSecondaryLight,
                tertiary = RedTertiaryLight
            )
            AccentColor.PINK -> lightColorScheme(
                primary = PinkLight,
                secondary = PinkSecondaryLight,
                tertiary = PinkTertiaryLight
            )
        }
    }
}
