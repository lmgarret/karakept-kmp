package com.karakept.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.karakept.app.data.model.viewerMode

/**
 * Desktop (JVM) implementation of HtmlRenderer using compose-webview-multiplatform.
 *
 * This uses JCEF (Java Chromium Embedded Framework) for proper HTML rendering.
 * Much better than JEditorPane, supports modern HTML/CSS.
 *
 * Security: JavaScript is ALWAYS disabled in both modes.
 */
@Composable
actual fun HtmlRenderer(
    html: String,
    viewerMode: ViewerMode,
    modifier: Modifier,
    onLinkClick: ((String) -> Unit)?,
    onLoaded: (() -> Unit)?
) {
    // Debug output
    println("HtmlRenderer (Desktop): Rendering HTML, length=${html.length}, first 100 chars=${html.take(100)}")

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    // Format colors as CSS hex strings
    val textColorHex = String.format("#%06X", 0xFFFFFF and textColor)
    val backgroundColorHex = String.format("#%06X", 0xFFFFFF and backgroundColor)
    val linkColorHex = String.format("#%06X", 0xFFFFFF and linkColor)

    val styledHtml = remember(html, viewerMode, textColorHex, backgroundColorHex, linkColorHex) {
        when (viewerMode) {
            ViewerMode.READER -> {
                // Reader mode: wrap with base styles
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            font-family: sans-serif;
                            font-size: 14px;
                            line-height: 1.6;
                            color: $textColorHex;
                            background-color: $backgroundColorHex;
                            margin: 16px;
                            padding: 0;
                        }
                        a {
                            color: $linkColorHex;
                            text-decoration: none;
                        }
                        a:hover {
                            text-decoration: underline;
                        }
                        pre {
                            background-color: rgba(0, 0, 0, 0.05);
                            padding: 8px;
                            overflow: auto;
                            border-radius: 4px;
                        }
                        code {
                            font-family: monospace;
                        }
                        blockquote {
                            border-left: 3px solid $linkColorHex;
                            padding-left: 10px;
                            margin-left: 0;
                        }
                        img {
                            max-width: 100%;
                            height: auto;
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
                    <meta charset="UTF-8">
                </head>
                <body>
                $html
                </body>
                </html>
                """.trimIndent()
            }
        }
    }

    // Observe loading state
    val loadingState = webViewState.loadingState
    androidx.compose.runtime.LaunchedEffect(loadingState) {
        if (loadingState is com.multiplatform.webview.web.LoadingState.Finished) {
            onLoaded?.invoke()
        }
    }

}
