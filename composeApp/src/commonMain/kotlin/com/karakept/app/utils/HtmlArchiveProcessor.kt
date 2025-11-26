package com.karakept.app.utils

import org.jsoup.Jsoup

/**
 * Processes HTML for Archive mode rendering.
 *
 * Archive mode preserves original stylesheets but removes all JavaScript
 * and event handlers for security.
 *
 * Security measures:
 * - Remove all <script> tags
 * - Remove all inline event handlers (onclick, onload, etc.)
 * - Validate URLs in href/src attributes
 * - Keep <style> and <link rel="stylesheet"> for visual fidelity
 */
object HtmlArchiveProcessor {
    fun processForArchive(html: String?): String {
        if (html.isNullOrBlank()) return ""

        return try {
            val doc = Jsoup.parse(html)

            // Remove ALL script tags
            doc.select("script").remove()

            // Remove inline event handlers (defense in depth)
            val eventHandlers = listOf(
                "onclick", "onload", "onerror", "onmouseover", "onmouseout",
                "onfocus", "onblur", "onchange", "onsubmit", "onkeydown",
                "onkeyup", "onkeypress", "ondblclick", "onmousedown", "onmouseup",
                "onmousemove", "onmouseleave", "onmouseenter", "oncontextmenu",
                "oninput", "onscroll", "onwheel", "oncopy", "oncut", "onpaste"
            )

            doc.select("*").forEach { element ->
                eventHandlers.forEach { handler ->
                    element.removeAttr(handler)
                }
            }

            // Validate href URLs
            doc.select("[href]").forEach { element ->
                val href = element.attr("href")
                if (!HtmlSanitizer.isValidUrl(href) && !href.startsWith("#")) {
                    element.removeAttr("href")
                }
            }

            // Validate src URLs
            doc.select("[src]").forEach { element ->
                val src = element.attr("src")
                if (!HtmlSanitizer.isValidUrl(src)) {
                    element.removeAttr("src")
                }
            }

            // Keep <style> and <link rel="stylesheet"> for original styling
            // They are preserved by not removing them

            doc.html()
        } catch (e: Exception) {
            println("HtmlArchiveProcessor: Error processing HTML: ${e.message}")
            "" // Fail safe
        }
    }
}
