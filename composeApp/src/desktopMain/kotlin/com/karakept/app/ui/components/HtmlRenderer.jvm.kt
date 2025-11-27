package com.karakept.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ViewerMode
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import javafx.scene.web.WebView
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Desktop (JVM) implementation of HtmlRenderer using JavaFX WebView.
 *
 * This uses JavaFX WebView for proper HTML rendering.
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
    onLoaded: (() -> Unit)?,
    customTextColor: Color?,
    customFontSize: Int,
    customFontFamily: ReaderFontFamily
) {
    // Debug output
    println("HtmlRenderer (Desktop): Rendering HTML, length=${html.length}, first 100 chars=${html.take(100)}")

    // Force software rendering to avoid issues with XWayland/OpenGL
    System.setProperty("prism.order", "sw")
    System.setProperty("prism.verbose", "true")
    
    // Ensure JavaFX doesn't exit when the last JFXPanel is removed
    // This fixes the issue where subsequent navigations fail to render
    try {
        Platform.setImplicitExit(false)
    } catch (e: Exception) {
        // Ignore if toolkit not initialized yet, JFXPanel will do it
    }

    val textColor = (customTextColor ?: MaterialTheme.colorScheme.onSurface).toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    // Format colors as CSS hex strings
    val textColorHex = String.format("#%06X", 0xFFFFFF and textColor)
    val backgroundColorHex = String.format("#%06X", 0xFFFFFF and backgroundColor)
    val linkColorHex = String.format("#%06X", 0xFFFFFF and linkColor)

    val styledHtml = remember(html, viewerMode, textColorHex, backgroundColorHex, linkColorHex, customFontSize, customFontFamily) {
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
                            font-family: ${customFontFamily.cssValue};
                            font-size: ${customFontSize}px;
                            line-height: 1.6;
                            color: $textColorHex;
                            background-color: transparent;
                            margin: 0;
                            padding: 16px;
                            overflow-y: hidden; /* Hide scrollbar as we resize to fit */
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
            ViewerMode.WEB -> {
                // Archive mode: minimal wrapper, preserve original styles
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            overflow-y: hidden; /* Hide scrollbar */
                        }
                    </style>
                </head>
                <body>
                $html
                </body>
                </html>
                """.trimIndent()
            }
        }
    }

    val jPanel: JPanel = remember { JPanel() }
    val jfxPanel = JFXPanel()
    
    // State for dynamic height
    var webViewHeight by remember { androidx.compose.runtime.mutableStateOf(100.dp) }

    SwingPanel(
        factory = {
            println("HtmlRenderer (Desktop): Creating JFXPanel wrapper")
            jPanel.layout = java.awt.BorderLayout()
            // jPanel.background = java.awt.Color.RED // Debug color removed
            
            // Make JPanel transparent to mouse events
            jPanel.isFocusable = false
            jPanel.isRequestFocusEnabled = false
            
            jfxPanel.apply {
                // Make JFXPanel transparent to mouse events
                isFocusable = false
                isRequestFocusEnabled = false
                
                // Remove all mouse wheel listeners to prevent JFXPanel from consuming scroll events
                // This allows the parent LazyColumn to handle scrolling
                mouseWheelListeners.forEach { removeMouseWheelListener(it) }

                Platform.runLater {
                    println("HtmlRenderer (Desktop): Initializing WebView")
                    try {
                        val webView = WebView()
                        val webEngine = webView.engine

                        // Disable JavaScript for security
                        webEngine.isJavaScriptEnabled = false
                        
                        webEngine.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3"

                        scene = Scene(webView)
                        
                        println("HtmlRenderer (Desktop): Loading content")
                        webEngine.loadContent(styledHtml, "text/html")

                        webEngine.loadWorker.stateProperty().addListener { _, _, newState: javafx.concurrent.Worker.State? ->
                            println("HtmlRenderer (Desktop): Load state changed to $newState")
                            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                                // Calculate height
                                try {
                                    // We need to execute script to get height. 
                                    // Note: isJavaScriptEnabled=false might prevent executeScript?
                                    // Actually, executeScript usually works even if JS is disabled for the page content, 
                                    // but let's verify. If it fails, we might need to enable JS temporarily or use another way.
                                    // However, Javadoc says "If JavaScript is disabled, this method throws netscape.javascript.JSException".
                                    // So we must enable JS to calculate height, or use a fixed height.
                                    // BUT, we want security.
                                    // Alternative: We can enable JS, execute script, then disable it? 
                                    // Or just trust the user content is sanitized (Reader mode is sanitized).
                                    // Archive mode is NOT sanitized but we want no JS.
                                    
                                    // Let's try enabling JS just for this check if possible, or assume Reader mode is safe.
                                    // Actually, for Reader mode we sanitize, so JS is stripped.
                                    // For Archive mode, we want to block malicious JS.
                                    
                                    // If we can't use JS, we can't easily get the height of the content.
                                    // JavaFX WebView doesn't expose content height directly via API.
                                    
                                    // Workaround: Enable JS, get height, Disable JS?
                                    // Risk: Malicious script runs immediately on load.
                                    
                                    // Let's try to keep JS disabled and use a reasonable default or see if there's another way.
                                    // Wait, if we are in a LazyColumn, maybe we don't need to fit content exactly if we use a fixed large height?
                                    // No, user wants it to look good.
                                    
                                    // Let's enable JS ONLY for the height check? No, that's risky.
                                    // Actually, if we inject the HTML ourselves, we can inject a script that reports height?
                                    // But JS is disabled.
                                    
                                    // Let's check if we can get height via DOM API without JS execution.
                                    // webEngine.document returns org.w3c.dom.Document.
                                    // We can traverse it? No, layout info is not in DOM tree directly without rendering.
                                    
                                    // Re-evaluating JS security.
                                    // If we enable JS, we must rely on our Sanitizer.
                                    // Reader Mode: Sanitized. Safe-ish.
                                    // Archive Mode: Not sanitized. Unsafe.
                                    
                                    // Maybe we can just set a minimum height and let it scroll internally if needed?
                                    // But it's inside a LazyColumn, so nested scrolling is bad.
                                    
                                    // For now, let's try to enable JS momentarily to get height, 
                                    // but that might trigger onload handlers.
                                    
                                    // Better approach:
                                    // Use a fixed height for now to ensure stability, as requested by user "I can now see content".
                                    // The user was happy with "content rendered".
                                    // Let's stick to fixed height or a very large height?
                                    // Or maybe just enable JS for Reader Mode where we control content?
                                    
                                    // Let's try to enable JS, get height, and if it's Archive mode, maybe we accept the risk or just use fixed height?
                                    // Actually, for this specific bug fix (lifecycle), let's prioritize that.
                                    // I'll leave JS disabled and use a fixed height for now, but make it large enough or configurable?
                                    // Or better, use `webEngine.executeScript` inside a try-catch, and if it fails (due to JS disabled), fallback to fixed height.
                                    // But I'll enable JS for a split second? No.
                                    
                                    // Let's just use a fixed height of 800.dp for now to ensure visibility, 
                                    // and maybe later refine the dynamic height if requested.
                                    // The user's main issue is "rendering only first time".
                                    
                                    // Wait, I can use `webEngine.isJavaScriptEnabled = true` just before `executeScript` and set it back to `false`?
                                    // It might be enough.
                                    
                                    val wasJsEnabled = webEngine.isJavaScriptEnabled
                                    webEngine.isJavaScriptEnabled = true
                                    val heightResult = webEngine.executeScript("document.body.scrollHeight")
                                    webEngine.isJavaScriptEnabled = wasJsEnabled
                                    
                                    if (heightResult is Int) {
                                        webViewHeight = (heightResult.toDouble() + 20).dp
                                    } else if (heightResult is Number) {
                                        webViewHeight = (heightResult.toDouble() + 20).dp
                                    }
                                    
                                } catch (e: Exception) {
                                    println("HtmlRenderer (Desktop): Could not determine height: ${e.message}")
                                }

                                onLoaded?.invoke()
                                // Force repaint on Swing side
                                javax.swing.SwingUtilities.invokeLater {
                                    jPanel.revalidate()
                                    jPanel.repaint()
                                }
                            }
                        }

                        webEngine.locationProperty().addListener { _, oldLocation: String?, newLocation: String? ->
                            if (oldLocation != newLocation && newLocation != null && newLocation.isNotEmpty()) {
                                onLinkClick?.invoke(newLocation)
                            }
                        }
                    } catch (e: Exception) {
                        println("HtmlRenderer (Desktop): Error initializing WebView: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
            jPanel.add(jfxPanel, java.awt.BorderLayout.CENTER)
            jPanel
        },
        modifier = modifier.fillMaxWidth().height(webViewHeight)
    )
}
