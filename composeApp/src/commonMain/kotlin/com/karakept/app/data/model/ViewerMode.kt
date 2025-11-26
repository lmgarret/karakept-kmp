package com.karakept.app.data.model

enum class ViewerMode {
    READER,  // Sanitized HTML (default)
    ARCHIVE; // Original HTML with stylesheets, no JS

    companion object {
        fun fromString(value: String): ViewerMode {
            return when (value.uppercase()) {
                "READER" -> READER
                "ARCHIVE" -> ARCHIVE
                else -> READER // Default to safe mode
            }
        }
    }
}
