package com.karakept.app.data.model

enum class ReaderFontFamily(
    val displayName: String,
    val cssValue: String
) {
    SYSTEM(
        "System",
        "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
    ),
    MERRIWEATHER(
        "Merriweather",
        "Merriweather, Georgia, serif"
    ),
    LORA(
        "Lora",
        "Lora, Georgia, serif"
    ),
    NOTO_SANS(
        "Noto Sans",
        "'Noto Sans', 'Segoe UI', Roboto, Arial, sans-serif"
    ),
    JETBRAINS_MONO(
        "JetBrains Mono",
        "'JetBrains Mono', 'Courier New', Consolas, monospace"
    ),
    OPEN_DYSLEXIC(
        "OpenDyslexic",
        "'OpenDyslexic', 'Comic Sans MS', sans-serif"
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
