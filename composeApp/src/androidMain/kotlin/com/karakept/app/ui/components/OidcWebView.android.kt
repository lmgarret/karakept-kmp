package com.karakept.app.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android implementation of [OidcWebView].
 *
 * Uses an Android WebView with cookies enabled to load the Karakeep
 * sign-in page. After the user authenticates, it calls the Karakeep
 * `/api/user/apiKey` endpoint (with the session cookie) to retrieve
 * the user's API key.
 *
 * The API key endpoint returns a JSON object like:
 * `{"apiKey": "ak_..."}`
 */
@SuppressLint("SetJavaScriptEnabled")
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

    // Track whether we already called the callback to avoid duplicate calls
    var callbackCalled by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                }

                // Enable cookies (required for session-based auth)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false

                        // After OIDC callback, Karakeep redirects to its main app
                        // We detect a successful login when we land back on the server
                        // and the URL no longer contains auth-specific paths
                        if (url.startsWith(baseUrl) &&
                            !url.contains("/api/auth/") &&
                            !url.contains("/signin") &&
                            !callbackCalled
                        ) {
                            callbackCalled = true
                            scope.launch {
                                fetchApiKey(
                                    apiKeyUrl = apiKeyUrl,
                                    cookieHeader = CookieManager.getInstance().getCookie(baseUrl) ?: "",
                                    onApiKeyObtained = onApiKeyObtained,
                                    onError = onError
                                )
                            }
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (url == null || callbackCalled) return

                        // Also check after page load in case we navigated to the main app
                        if (url.startsWith(baseUrl) &&
                            !url.contains("/api/auth/") &&
                            !url.contains("/signin")
                        ) {
                            callbackCalled = true
                            scope.launch {
                                fetchApiKey(
                                    apiKeyUrl = apiKeyUrl,
                                    cookieHeader = CookieManager.getInstance().getCookie(baseUrl) ?: "",
                                    onApiKeyObtained = onApiKeyObtained,
                                    onError = onError
                                )
                            }
                        }
                    }
                }

                loadUrl(signInUrl)
            }
        }
    )
}

/**
 * Fetches the API key from the Karakeep server using the active web session cookie.
 *
 * Karakeep exposes a `/api/user/apiKey` endpoint (part of the Next.js web app)
 * that returns the user's API key when called with a valid session cookie.
 * This is not part of the public REST API but is used by the official web client.
 */
private suspend fun fetchApiKey(
    apiKeyUrl: String,
    cookieHeader: String,
    onApiKeyObtained: (String) -> Unit,
    onError: (String) -> Unit,
) = withContext(Dispatchers.IO) {
    try {
        val connection = URL(apiKeyUrl).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            setRequestProperty("Cookie", cookieHeader)
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val body = connection.inputStream.bufferedReader().readText()
            // Expected response: {"apiKey":"ak_..."}
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
 * Uses a simple string search to avoid requiring a JSON library dependency.
 */
private fun extractApiKeyFromJson(json: String): String? {
    // Simple JSON field extraction: find "apiKey":"value"
    val pattern = Regex(""""apiKey"\s*:\s*"([^"]+)"""")
    return pattern.find(json)?.groupValues?.get(1)
}
