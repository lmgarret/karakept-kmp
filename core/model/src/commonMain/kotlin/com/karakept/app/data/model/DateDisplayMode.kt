package com.karakept.app.data.model

enum class DateDisplayMode {
    ELAPSED,  // e.g., "33m ago", "2h ago", "3d ago"
    ABSOLUTE; // e.g., "2024-01-15"

    companion object {
        fun fromString(value: String): DateDisplayMode = try {
            valueOf(value)
        } catch (e: IllegalArgumentException) {
            ELAPSED
        }
    }
}
