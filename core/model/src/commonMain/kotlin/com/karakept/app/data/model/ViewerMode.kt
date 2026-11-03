package com.karakept.app.data.model

enum class ViewerMode {
    READER,  // Sanitized HTML (default)
    WEB; // Web view with original HTML and stylesheets, no JS

    companion object {
        fun fromString(value: String): ViewerMode {
            return when (value.uppercase()) {
                "READER" -> READER
                "WEB" -> WEB
                "ARCHIVE" -> WEB // Backward compatibility
                else -> READER // Default to safe mode
            }
        }
    }
}
