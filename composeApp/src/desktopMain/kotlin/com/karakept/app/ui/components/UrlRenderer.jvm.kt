package com.karakept.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Desktop (JVM) implementation of UrlRenderer using JavaFX WebView.
 */
@Composable
actual fun UrlRenderer(
    url: String,
    modifier: Modifier,
    onPageTitleChanged: ((String) -> Unit)?,
    onLinkClick: ((String) -> Unit)?
) {
    System.setProperty("prism.order", "sw")

    try {
        Platform.setImplicitExit(false)
    } catch (e: Exception) {
        // Ignore if toolkit not initialized yet
    }

    val jPanel = remember { JPanel() }
    val jfxPanel = remember { JFXPanel() }

    SwingPanel(
        factory = {
            jPanel.layout = BorderLayout()
            jPanel.isFocusable = false

            jfxPanel.apply {
                isFocusable = false
                mouseWheelListeners.forEach { removeMouseWheelListener(it) }

                Platform.runLater {
                    try {
                        val webView = WebView()
                        val webEngine = webView.engine

                        webEngine.isJavaScriptEnabled = true

                        webEngine.titleProperty().addListener { _, _, newTitle: String? ->
                            if (!newTitle.isNullOrBlank()) {
                                javax.swing.SwingUtilities.invokeLater {
                                    onPageTitleChanged?.invoke(newTitle)
                                }
                            }
                        }

                        webEngine.locationProperty().addListener { _, oldLocation: String?, newLocation: String? ->
                            if (oldLocation != newLocation && newLocation != null && newLocation.isNotEmpty()) {
                                onLinkClick?.invoke(newLocation)
                            }
                        }

                        scene = Scene(webView)
                        webEngine.load(url)
                    } catch (e: Exception) {
                        println("UrlRenderer (Desktop): Error initializing WebView: ${e.message}")
                    }
                }
            }

            jPanel.add(jfxPanel, BorderLayout.CENTER)
            jPanel
        },
        modifier = modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose { }
    }
}
