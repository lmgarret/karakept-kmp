package com.karakept.app.data.model

/**
 * Predefined accent color options for theming.
 */
enum class AccentColor {
    PURPLE,
    BLUE,
    GREEN,
    ORANGE,
    RED,
    PINK;

    companion object {
        fun fromString(value: String): AccentColor {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                PURPLE
            }
        }
    }
}
