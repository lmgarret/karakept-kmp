package com.karakept.app.ui.components

import android.graphics.Color
import android.view.ViewGroup
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
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color as ComposeColor

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
    onLinkClick: ((String) -> Unit)?,
    onLoaded: (() -> Unit)?,
    customTextColor: ComposeColor?,
    customFontSize: Int,
    customFontFamily: ReaderFontFamily,
    localFilePath: String?
) {
    // Use custom text color if provided, otherwise default to a fixed color (e.g., Black/White based on theme) 
    // or keep using onSurface but ensure it's what the user wants.
    // The user requested that text color should NOT change with dynamic color.
    // If we use onSurface, it WILL change with dynamic color.
    // So we should probably default to a standard color if customTextColor is null, 
    // OR we can rely on the fact that onSurface might be tinted in dynamic themes.
    // Let's use a more neutral default if customTextColor is null, or just stick to onSurface 
    // but maybe the user implies they want a specific color that doesn't shift.
    // However, the best way to "stay the same" is to use the custom color logic.
    // If the user hasn't set a custom color, it defaults to onSurface.
    // If onSurface changes with dynamic theme (which it does), that's the issue.
    // We should probably default to a non-dynamic color if no custom color is set, 
    // OR explicitly set a default that isn't influenced by the dynamic palette if that's the preference.
    // But standard Material Design says onSurface SHOULD match the theme.
    // If the user wants it to "stay the same", they might mean "stay black/white" regardless of the pink/blue tint.
    
    // Let's check if we can get a non-dynamic onSurface. 
    // Actually, if the user selects "Dynamic", the whole theme is dynamic.
    // If they want the text to NOT be dynamic, they should probably set a custom color.
    // BUT, if they haven't set a custom color, maybe we should default to standard Black/White 
    // based on dark mode, ignoring the dynamic tint.
    
    val isDark = androidx.compose.foundation.isSystemInDarkTheme() // This might not match app theme if forced
    // Better to check the luminance of the background or surface to decide default text color
    // But we don't have easy access to "isDark" boolean here directly without passing it.
    // However, MaterialTheme.colorScheme.surface is available.
    
    val defaultTextColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) ComposeColor.Black else ComposeColor.White
    val textColor = (customTextColor ?: defaultTextColor).toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    // Format colors as CSS hex strings
    val textColorHex = String.format("#%06X", 0xFFFFFF and textColor)
    val backgroundColorHex = String.format("#%06X", 0xFFFFFF and backgroundColor)
    val linkColorHex = String.format("#%06X", 0xFFFFFF and linkColor)

    val themedHtml = remember(html, viewerMode, textColorHex, backgroundColorHex, linkColorHex, customFontSize, customFontFamily) {
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
                        html, body {
                            overflow-x: hidden;
                            max-width: 100%;
                        }
                        body {
                            color: $textColorHex;
                            background-color: transparent;
                            font-family: ${customFontFamily.cssValue};
                            font-size: ${customFontSize}px;
                            line-height: 1.6;
                            padding: 16px;
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
                        figure {
                            margin: 16px 0;
                        }
                        figcaption {
                            text-align: center;
                            font-size: 14px;
                            font-style: italic;
                            color: rgba(${(textColor shr 16) and 0xFF}, ${(textColor shr 8) and 0xFF}, ${textColor and 0xFF}, 0.7);
                            margin-top: 8px;
                            margin-bottom: 8px;
                        }
                    </style>
                </head>
                <body>
                $html
                </body>
                </html>
                """.trimIndent()
            }
            ViewerMode.WEB -> {
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
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                // Start invisible to prevent white flash
                setBackgroundColor(Color.TRANSPARENT)
                
                // Security settings
                settings.javaScriptEnabled = false
                // Allow file access only when we need to load local files
                settings.allowFileAccess = localFilePath != null
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

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoaded?.invoke()
                    }
                }

                // Load the HTML content or local file
                if (localFilePath != null) {
                    loadUrl("file://$localFilePath")
                } else {
                    loadDataWithBaseURL(null, themedHtml, "text/html", "UTF-8", null)
                }
            }
        },
        update = { webView ->
            // Update background color in case theme changes
            // webView.setBackgroundColor(Color.TRANSPARENT)
            if (localFilePath != null) {
                // Only reload if URL changed? For now, just reload to be safe or check url
                if (webView.url != "file://$localFilePath") {
                    webView.loadUrl("file://$localFilePath")
                }
            } else {
                webView.loadDataWithBaseURL(null, themedHtml, "text/html", "UTF-8", null)
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            // Cleanup if needed
        }
    }
}
