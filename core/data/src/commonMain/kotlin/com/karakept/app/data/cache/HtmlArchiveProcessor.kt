package com.karakept.app.data.cache

import com.fleeksoft.ksoup.Ksoup
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.HtmlSanitizer

/**
 * Processes HTML for Archive (Web) mode rendering, both an inline archive and a cached
 * full-page archive read from disk.
 *
 * Archive mode keeps the page's own stylesheets but removes everything that can run script,
 * navigate on its own or load another document, because the WebView it is shown in has
 * JavaScript on and a native bridge for the highlight script.
 */
object HtmlArchiveProcessor {
    private val URL_ATTRIBUTES = listOf("href", "xlink:href", "src", "action", "formaction", "poster", "background")

    fun processForArchive(html: String?): String {
        if (html.isNullOrBlank()) return ""

        return try {
            val doc = Ksoup.parse(html)
            doc.outputSettings().prettyPrint(false)

            doc.select("script, noscript").remove()
            doc.select("iframe, frame, frameset, object, embed, applet, portal").remove()
            doc.select("form").remove()
            // A refresh or a CSP of the page's own, a document base, and every <link> but a
            // stylesheet (preload, import, manifest, …) act without the user.
            doc.select("meta[http-equiv], base").remove()
            doc.select("link").filterNot { it.attr("rel").lowercase().split(' ').contains("stylesheet") }
                .forEach { it.remove() }
            // SVG animation can rewrite an href into a javascript: URL after sanitising.
            doc.select("animate, set, animateMotion, animateTransform, handler, listener").remove()

            doc.getAllElements().forEach { element ->
                element.attributes()
                    .map { it.key }
                    .filter { name ->
                        val lower = name.lowercase()
                        lower.startsWith("on") || lower == "srcdoc"
                    }
                    .forEach { element.removeAttr(it) }

                URL_ATTRIBUTES.forEach { name ->
                    if (element.hasAttr(name)) {
                        val value = element.attr(name)
                        val keepAnchor = (name == "href" || name == "xlink:href") && value.startsWith("#")
                        if (!keepAnchor && !HtmlSanitizer.isValidUrl(value)) element.removeAttr(name)
                    }
                }
            }

            // Wrap body content in karakept-content for highlighting offsets
            val children = doc.body().childNodes().toList()
            doc.body().empty()
            doc.body().appendElement("div").attr("id", "karakept-content").appendChildren(children)

            doc.html()
        } catch (e: Exception) {
            AppLogger.e("HtmlArchiveProcessor", "Error processing HTML: ${e.message}", e)
            "" // Fail safe
        }
    }
}
