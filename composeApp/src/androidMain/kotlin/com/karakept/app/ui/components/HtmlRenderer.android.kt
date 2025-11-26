package com.karakept.app.ui.components

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.karakept.app.data.model.ViewerMode

/**
 * Android implementation of HtmlRenderer using WebView.
 *
 * Security measures:
 * - JavaScript disabled (always, in both modes)
 * - File access disabled
 * - Content access disabled
 * - Mixed content blocked
 * - Link clicks intercepted
 */
@Composable
actual fun HtmlRenderer(
    html: String,
    viewerMode: ViewerMode,
    modifier: Modifier,
    onLinkClick: ((String) -> Unit)?
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    // Format colors as CSS hex strings
    val textColorHex = String.format("#%06X", 0xFFFFFF and textColor)
    val backgroundColorHex = String.format("#%06X", 0xFFFFFF and backgroundColor)
    val linkColorHex = String.format("#%06X", 0xFFFFFF and linkColor)

    val themedHtml = remember(html, viewerMode, textColorHex, backgroundColorHex, linkColorHex) {
        when (viewerMode) {
            ViewerMode.READER -> {
                // Reader mode: wrap with base styles
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https: data:; style-src 'unsafe-inline'; script-src 'none';">
                    <style>
                        * {
                            margin: 0;
                            padding: 0;
                            box-sizing: border-box;
                        }
                        body {
                            color: $textColorHex;
                            background-color: $backgroundColorHex;
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                            font-size: 16px;
                            line-height: 1.6;
                            padding: 0;
                            margin: 0;
                            word-wrap: break-word;
                            overflow-wrap: break-word;
                        }
                        a {
                            color: $linkColorHex;
                            text-decoration: underline;
                        }
                        img {
                            max-width: 100%;
                            height: auto;
                            display: block;
                            margin: 8px 0;
                        }
                        pre {
                            overflow-x: auto;
                            padding: 8px;
                            background-color: rgba(127, 127, 127, 0.1);
                            border-radius: 4px;
                            margin: 8px 0;
                        }
                        code {
                            font-family: "Courier New", Courier, monospace;
                            font-size: 14px;
                        }
                        blockquote {
                            border-left: 4px solid $linkColorHex;
                            padding-left: 12px;
                            margin: 8px 0;
                            font-style: italic;
                        }
                        ul, ol {
                            padding-left: 24px;
                            margin: 8px 0;
                        }
                        p {
                            margin: 8px 0;
                        }
                        h1, h2, h3, h4, h5, h6 {
                            margin: 12px 0 8px 0;
                            font-weight: bold;
                        }
                    </style>
                </head>
                <body>
                $html
                </body>
                </html>
                """.trimIndent()
            }
            ViewerMode.ARCHIVE -> {
                // Archive mode: minimal wrapper, preserve original styles
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https: data:; style-src 'unsafe-inline' http: https:; script-src 'none';">
                </head>
                <body>
                $html
                </body>
                </html>
                """.trimIndent()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // Security settings
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                // Disable mixed content (enforce HTTPS)
                @Suppress("DEPRECATION")
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                // Set up WebViewClient to intercept link clicks
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString()
                        if (url != null && onLinkClick != null) {
                            onLinkClick(url)
                            return true // Prevent WebView from loading the URL
                        }
                        return false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url != null && onLinkClick != null) {
                            onLinkClick(url)
                            return true
                        }
                        return false
                    }
                }

                // Load the HTML content
                loadDataWithBaseURL(null, themedHtml, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, themedHtml, "text/html", "UTF-8", null)
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            // Cleanup if needed
        }
    }
}
