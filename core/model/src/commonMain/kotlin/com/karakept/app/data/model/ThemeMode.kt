package com.karakept.app.data.model

/**
 * Theme mode options for the app.
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED,  // Pure black for AMOLED screens
    SYSTEM;  // Follow system theme

    companion object {
        fun fromString(value: String): ThemeMode {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                SYSTEM
            }
        }
    }
}
