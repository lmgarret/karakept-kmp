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
 * Pure black-on-white (or white-on-black) scheme for e-ink panels.
 *
 * MD3 separates elements with tonal surface steps, which an e-ink panel renders as a handful of
 * indistinguishable greys. This flattens every surface role to the page colour and pushes all
 * separation onto `outline`, so components draw a visible 1dp border instead of relying on fill or
 * elevation — see the `LocalEinkMode` borders in TagChip and the bookmark layouts.
 *
 * Kept orthogonal to [ThemeMode] so it composes with LIGHT / DARK / SYSTEM rather than replacing
 * them; e-ink devices with an inverted mode still want dark.
 */
fun einkColorScheme(isDark: Boolean): androidx.compose.material3.ColorScheme {
    val page = if (isDark) Color.Black else Color.White
    val ink = if (isDark) Color.White else Color.Black
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink,
        onPrimary = page,
        primaryContainer = page,
        onPrimaryContainer = ink,
        secondary = ink,
        onSecondary = page,
        // The one deliberate mid-tone. Small repeated elements — tag chips above all — need to
        // read as a group without each one drawing a full-strength outline, which is a lot of ink
        // for a handful of words. Chosen several steps off the page so it survives the panel's
        // ~16-level greyscale, unlike MD3's tonal steps.
        secondaryContainer = if (isDark) EinkChipDark else EinkChipLight,
        onSecondaryContainer = ink,
        tertiary = ink,
        onTertiary = page,
        tertiaryContainer = page,
        onTertiaryContainer = ink,
        background = page,
        onBackground = ink,
        surface = page,
        onSurface = ink,
        surfaceVariant = page,
        onSurfaceVariant = ink,
        surfaceTint = ink,
        surfaceContainer = page,
        surfaceContainerLow = page,
        surfaceContainerLowest = page,
        surfaceContainerHigh = page,
        surfaceContainerHighest = page,
        inverseSurface = ink,
        inverseOnSurface = page,
        inversePrimary = page,
        outline = ink,
        outlineVariant = ink,
        // Errors stay distinguishable by staying ink-coloured rather than a mid-grey red.
        error = ink,
        onError = page,
        errorContainer = page,
        onErrorContainer = ink,
        scrim = ink
    )
}

/**
 * Generate a ColorScheme based on theme mode and accent color.
 *
 * [highContrast] overrides the accent palette entirely with [einkColorScheme].
 */
@androidx.compose.runtime.Composable
fun getColorScheme(
    themeMode: ThemeMode,
    accentColor: AccentColor,
    isDarkTheme: Boolean,
    highContrast: Boolean = false
): androidx.compose.material3.ColorScheme {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isDarkTheme
    }

    if (highContrast) return einkColorScheme(isDark)

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

// E-ink chip fill — the only mid-tone in the monochrome scheme. Far enough from the page that a
// 16-level greyscale panel still separates the two, and light enough that ink text stays readable.
private val EinkChipLight = Color(0xFFC9C9C9)
private val EinkChipDark = Color(0xFF3D3D3D)

// Highlight Colors (Used in Reader and Panels)
val HighlightYellow = Color(0xFFFFEB3B)
val HighlightBlue = Color(0xFF2196F3)
val HighlightGreen = Color(0xFF4CAF50)
val HighlightRed = Color(0xFFF44336)
