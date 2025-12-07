package com.karakept.app.utils

/**
 * Platform-specific share functionality
 */
expect object ShareUtils {
    /**
     * Share text content (like a URL) using the platform's share sheet
     */
    fun shareText(text: String, title: String? = null)
}
