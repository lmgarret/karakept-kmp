package com.karakept.app.utils

import io.ktor.http.Url

object FaviconUtils {
    /**
     * Generates a privacy-preserving favicon URL using DuckDuckGo's service.
     * 
     * @param url The URL of the website to get the favicon for.
     * @return The URL of the favicon.
     */
    fun getFaviconUrl(url: String): String {
        return try {
            val domain = Url(url).host
            "https://icons.duckduckgo.com/ip3/$domain.ico"
        } catch (e: Exception) {
            // Fallback if URL parsing fails, though unlikely with valid URLs
            ""
        }
    }
}
