package com.karakept.app.utils

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * HTML sanitizer that removes potentially dangerous content while preserving safe HTML elements.
 *
 * Security measures:
 * - Uses whitelist-based approach (only explicitly allowed tags/attributes)
 * - Removes all JavaScript and event handlers
 * - Validates URLs (http/https for links, http/https/data for images)
 * - Prevents XSS attacks
 */
object HtmlSanitizer {

    private val safelist: Safelist by lazy {
        Safelist.relaxed()
            // Add additional tags beyond relaxed() defaults
            .addTags("div", "span", "pre", "code", "figcaption", "figure", "picture", "img")
            // Configure link attributes
            .addAttributes("a", "href", "title")
            .addProtocols("a", "href", "http", "https")
            // Configure image attributes - allow data: URLs for embedded base64 images
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addProtocols("img", "src", "http", "https", "data")
            // Explicitly remove event handlers (defense in depth)
            .removeAttributes("*", "onclick", "onerror", "onload", "onmouseover",
                "onfocus", "onblur", "onchange", "onsubmit")
    }

    /**
     * Sanitizes HTML content by removing dangerous elements and attributes.
     *
     * @param html Raw HTML content (potentially unsafe)
     * @param removeFirstImage Whether to remove the first image (useful when hero banner shows the article thumbnail)
     * @return Sanitized HTML with only safe elements and attributes
     */
    fun sanitize(html: String?, removeFirstImage: Boolean = false): String {
        if (html.isNullOrBlank()) {
            return ""
        }

        return try {
            // Parse and sanitize
            val doc = Jsoup.parse(html)
            
            // Remove first image if requested
            if (removeFirstImage) {
                doc.selectFirst("img")?.remove()
            }
            
            // Apply safelist and return
            val outputSettings = doc.outputSettings().prettyPrint(false)
            Jsoup.clean(doc.html(), "", safelist, outputSettings)
        } catch (e: Exception) {
            // If sanitization fails, return empty string as a safe fallback
            ""
        }
    }

    /**
     * Validates if a URL is safe to use.
     * Only http, https, and data URLs are considered safe.
     *
     * @param url URL to validate
     * @return true if URL is safe, false otherwise
     */
    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false

        val trimmed = url.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
               trimmed.startsWith("https://", ignoreCase = true) ||
               trimmed.startsWith("data:", ignoreCase = true)
    }
}
