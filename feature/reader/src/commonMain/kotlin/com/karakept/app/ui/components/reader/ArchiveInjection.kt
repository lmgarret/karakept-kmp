package com.karakept.app.ui.components.reader

private val HEAD_OPEN = Regex("<head(\\s[^>]*)?>", RegexOption.IGNORE_CASE)

/**
 * Injects the CSP/viewport meta tags and highlight styles/script into an archive document
 * that `HtmlArchiveProcessor` has already sanitised (so it has a
 * `<head>` and no script of its own).
 *
 * The CSP goes first in `<head>` so it governs everything after it, and `script-src` admits
 * only the app's own script by nonce: inline handlers and any script the sanitiser missed stay
 * inert.
 */
internal fun injectArchiveScripts(
    html: String,
    highlightStyles: String,
    highlightScripts: String,
    nonce: String
): String {
    val headTags = """<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https: data: file:; style-src 'unsafe-inline' http: https:; script-src 'nonce-$nonce';"><meta name="viewport" content="width=device-width, initial-scale=1.0"><style>$highlightStyles</style>"""
    val withHead = HEAD_OPEN.find(html)?.let { match ->
        html.substring(0, match.range.last + 1) + headTags + html.substring(match.range.last + 1)
    } ?: "<head>$headTags</head>$html"
    val script = "<script nonce=\"$nonce\">$highlightScripts</script>"
    val bodyClose = withHead.lastIndexOf("</body>", ignoreCase = true)
    return if (bodyClose >= 0) {
        withHead.substring(0, bodyClose) + script + withHead.substring(bodyClose)
    } else {
        withHead + script
    }
}
