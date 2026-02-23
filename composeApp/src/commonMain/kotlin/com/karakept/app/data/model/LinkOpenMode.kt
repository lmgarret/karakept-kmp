package com.karakept.app.data.model

enum class LinkOpenMode {
    EXTERNAL_BROWSER, // Open links in the system default browser
    IN_APP_WEBVIEW;   // Open links in an in-app web view

    companion object {
        fun fromString(value: String): LinkOpenMode {
            return when (value.uppercase()) {
                "EXTERNAL_BROWSER" -> EXTERNAL_BROWSER
                "IN_APP_WEBVIEW" -> IN_APP_WEBVIEW
                else -> EXTERNAL_BROWSER // Default to external browser
            }
        }
    }
}
