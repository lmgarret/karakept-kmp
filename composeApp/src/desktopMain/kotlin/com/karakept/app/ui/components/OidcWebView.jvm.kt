package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel

/**
 * Desktop (JVM) implementation of [OidcWebView] using JavaFX WebView.
 *
 * Loads the Karakeep sign-in page in a JavaFX WebView. After the user
 * authenticates, it detects the redirect back to the main app and then
 * calls `/api/user/apiKey` with the session cookies to retrieve the API key.
 *
 * Note: A [java.net.CookieManager] is installed as the default [CookieHandler]
 * so that JavaFX WebEngine's HTTP requests (which route through the JDK HTTP
 * stack) store their cookies in a location we can read.
 */
@Composable
actual fun OidcWebView(
    serverUrl: String,
    onApiKeyObtained: (apiKey: String) -> Unit,
    onError: (message: String) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val baseUrl = serverUrl.trimEnd('/')
    val signInUrl = "$baseUrl/api/auth/signin"
    val apiKeyUrl = "$baseUrl/api/user/apiKey"

    // Prevent duplicate callbacks
    val callbackCalled = remember { AtomicBoolean(false) }

    // Force software rendering for JavaFX (required in headless/CI environments)
    System.setProperty("prism.order", "sw")

    // Install a CookieManager so cookies from the WebView session are accessible
    val cookieManager = remember {
        val manager = CookieManager()
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(manager)
        }
        CookieHandler.getDefault() as? CookieManager ?: manager
    }

    val jfxPanel = remember { JFXPanel() }

    DisposableEffect(serverUrl) {
        try {
            Platform.startup {}
        } catch (_: IllegalStateException) {
            // JavaFX already running
        }

        Platform.runLater {
            val webView = WebView()
            val webEngine = webView.engine
            webEngine.isJavaScriptEnabled = true

            val locationListener = ChangeListener<String> { _, _, newLocation ->
                if (newLocation == null || callbackCalled.get()) return@ChangeListener

                // Detect navigation back to the main Karakeep app after login
                if (newLocation.startsWith(baseUrl) &&
                    !newLocation.contains("/api/auth/") &&
                    !newLocation.contains("/signin")
                ) {
                    if (callbackCalled.compareAndSet(false, true)) {
                        scope.launch {
                            fetchApiKeyDesktop(
                                apiKeyUrl = apiKeyUrl,
                                baseUrl = baseUrl,
                                cookieManager = cookieManager,
                                onApiKeyObtained = onApiKeyObtained,
                                onError = onError,
                            )
                        }
                    }
                }
            }

            webEngine.locationProperty().addListener(locationListener)
            webEngine.load(signInUrl)

            jfxPanel.scene = Scene(webView)
        }

        onDispose { }
    }

    SwingPanel(
        modifier = modifier,
        factory = {
            JPanel(BorderLayout()).apply {
                add(jfxPanel, BorderLayout.CENTER)
            }
        }
    )
}

/**
 * Fetches the API key using cookies from the Java [CookieManager].
 */
private suspend fun fetchApiKeyDesktop(
    apiKeyUrl: String,
    baseUrl: String,
    cookieManager: CookieManager,
    onApiKeyObtained: (String) -> Unit,
    onError: (String) -> Unit,
) = withContext(Dispatchers.IO) {
    try {
        val cookies = cookieManager.cookieStore.get(URI(baseUrl))
            .joinToString("; ") { "${it.name}=${it.value}" }

        val connection = URL(apiKeyUrl).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            if (cookies.isNotEmpty()) {
                setRequestProperty("Cookie", cookies)
            }
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val body = connection.inputStream.bufferedReader().readText()
            val apiKey = extractApiKeyFromJson(body)
            if (apiKey != null) {
                withContext(Dispatchers.Main) { onApiKeyObtained(apiKey) }
            } else {
                withContext(Dispatchers.Main) {
                    onError("Could not extract API key from server response")
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                onError("Server returned HTTP $responseCode when fetching API key")
            }
        }
        connection.disconnect()
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            onError("Failed to fetch API key: ${e.message}")
        }
    }
}

/**
 * Parses the API key from a JSON response like `{"apiKey":"ak_..."}`.
 */
private fun extractApiKeyFromJson(json: String): String? {
    val pattern = Regex(""""apiKey"\s*:\s*"([^"]+)"""")
    return pattern.find(json)?.groupValues?.get(1)
}
