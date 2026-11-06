package com.karakept.app.ui.utils

import io.ktor.http.Url

/**
 * Extracts the domain (host without www prefix) from a URL string.
 *
 * - Strips scheme (http:// / https://)
 * - Strips "www." prefix
 * - Strips port number
 * - Returns the original string as a fallback for non-URL input
 * - Returns empty string for blank input
 */
fun extractDomain(url: String): String {
    if (url.isBlank()) return url
    // Only attempt URL parsing if the string contains a known scheme
    if (!url.startsWith("http://") && !url.startsWith("https://")) return url
    return try {
        val parsed = Url(url)
        val host = parsed.host
        if (host.isBlank()) return url
        if (host.startsWith("www.")) host.removePrefix("www.") else host
    } catch (_: Exception) {
        url
    }
}
