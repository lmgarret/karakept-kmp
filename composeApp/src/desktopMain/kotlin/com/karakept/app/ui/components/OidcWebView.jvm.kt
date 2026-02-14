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
 * The custom URL scheme used as the OIDC callback.
 *
 * After a successful OIDC login, Karakeep's NextAuth redirects to this URL.
 * JavaFX's WebEngine fires a location change to this scheme which we intercept,
 * at which point all session cookies from the auth flow are already set.
 */
private const val MOBILE_CALLBACK_URL = "karakept://auth-callback"

/**
 * Desktop (JVM) implementation of [OidcWebView] using JavaFX WebView.
 *
 * Loads the Karakeep sign-in page with [MOBILE_CALLBACK_URL] as the OAuth
 * callbackUrl. After OIDC login, NextAuth redirects to [MOBILE_CALLBACK_URL],
 * which is detected via a [javafx.beans.value.ChangeListener] on the
 * WebEngine's location property. At that point all session cookies are set,
 * so we call `/api/user/apiKey` to retrieve the API key.
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
    val encodedCallback = java.net.URLEncoder.encode(MOBILE_CALLBACK_URL, "UTF-8")
    val signInUrl = "$baseUrl/api/auth/signin?callbackUrl=$encodedCallback"
    val apiKeyUrl = "$baseUrl/api/user/apiKey"

    val callbackCalled = remember { AtomicBoolean(false) }

    System.setProperty("prism.order", "sw")

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

                // Detect the custom mobile callback URL.
                // At this point all session cookies from the OIDC flow are set.
                if (newLocation.startsWith(MOBILE_CALLBACK_URL)) {
                    if (callbackCalled.compareAndSet(false, true)) {
                        webEngine.load("about:blank")
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
