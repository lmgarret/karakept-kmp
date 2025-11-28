package com.karakept.app.data.model

import androidx.compose.ui.text.font.FontFamily

enum class ReaderFontFamily(
    val displayName: String,
    val cssValue: String,
    val composeFontFamily: FontFamily
) {
    SYSTEM(
        "System",
        "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
        FontFamily.Default
    ),
    SERIF(
        "Serif",
        "Georgia, 'Times New Roman', Times, serif",
        FontFamily.Serif
    ),
    SANS_SERIF(
        "Sans Serif",
        "Arial, Roboto, 'Open Sans', 'Helvetica Neue', sans-serif",
        FontFamily.SansSerif
    ),
    OPEN_DYSLEXIC(
        "OpenDyslexic",
        "'OpenDyslexic', 'Comic Sans MS', sans-serif",
        FontFamily.Default
    ),
    ROBOTO(
        "Roboto",
        "Roboto, 'Helvetica Neue', Arial, sans-serif",
        FontFamily.Default
    ),
    MERRIWEATHER(
        "Merriweather",
        "Merriweather, Georgia, serif",
        FontFamily.Serif
    ),
    LORA(
        "Lora",
        "Lora, Georgia, serif",
        FontFamily.Serif
    ),
    MONOSPACE(
        "Monospace",
        "'Courier New', Courier, Consolas, 'Liberation Mono', monospace",
        FontFamily.Monospace
    );

    companion object {
        fun fromString(value: String): ReaderFontFamily {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                SYSTEM
            }
        }
    }
}
