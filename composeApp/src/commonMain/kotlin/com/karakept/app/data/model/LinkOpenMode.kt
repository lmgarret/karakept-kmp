package com.karakept.app.data.model

enum class LinkOpenMode {
    CUSTOM_TAB,        // Open links in a Custom Tab (uses the default browser session)
    EXTERNAL_BROWSER;  // Open links in the system default browser (new process)

    companion object {
        fun fromString(value: String): LinkOpenMode {
            return when (value.uppercase()) {
                "CUSTOM_TAB" -> CUSTOM_TAB
                "IN_APP_WEBVIEW" -> CUSTOM_TAB // migrate legacy value
                "EXTERNAL_BROWSER" -> EXTERNAL_BROWSER
                else -> CUSTOM_TAB // Default to custom tab
            }
        }
    }
}
