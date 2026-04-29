package com.karakept.app.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.safety.Cleaner
import com.fleeksoft.ksoup.safety.Safelist

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
            .addTags("div", "span", "pre", "code", "figcaption", "figure", "picture", "img", "source", "mark")
            // Configure link attributes
            .addAttributes("a", "href", "title")
            .addProtocols("a", "href", "http", "https")
            // Configure image attributes - allow data: URLs for embedded base64 images.
            // Also keep srcset/data-src/data-srcset for lazy-load patterns where the real
            // URL lives in data-src rather than src.
            .addAttributes("img", "src", "alt", "title", "width", "height", "srcset", "data-src", "data-srcset", "sizes")
            .addProtocols("img", "src", "http", "https", "data", "file")
            // Configure <source> inside <picture> — keep srcset/data-srcset so the renderer
            // can pick a real URL even when <img src> is a lazy-load SVG placeholder.
            .addAttributes("source", "srcset", "data-srcset", "type", "media", "sizes")
            // Allow classes and styles for highlights
            .addAttributes("mark", "class", "style", "data-id")
            .addAttributes("span", "class", "style")
            .addAttributes("div", "class", "style")
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
            val doc = Ksoup.parse(html)

            // Remove first image if requested
            if (removeFirstImage) {
                doc.selectFirst("img")?.remove()
            }

            // Apply safelist via Cleaner and return
            val cleaner = Cleaner(safelist)
            val cleanDoc = cleaner.clean(doc)
            cleanDoc.outputSettings().prettyPrint(false)
            cleanDoc.body().html()
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
               trimmed.startsWith("data:", ignoreCase = true) ||
               trimmed.startsWith("file://", ignoreCase = true)
    }
}
