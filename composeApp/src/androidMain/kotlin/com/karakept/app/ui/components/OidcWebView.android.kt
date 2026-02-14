package com.karakept.app.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
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
 * The custom URL scheme used as the OIDC callback.
 *
 * After a successful OIDC login, Karakeep's NextAuth redirects to this URL.
 * Since it's not a real URL, the WebView cannot load it and
 * [WebViewClient.shouldOverrideUrlLoading] intercepts it reliably —
 * at which point all session cookies have already been set by the auth flow.
 */
private const val MOBILE_CALLBACK_URL = "karakept://auth-callback"

/**
 * Android implementation of [OidcWebView].
 *
 * Loads the Karakeep sign-in page with [MOBILE_CALLBACK_URL] as the OAuth
 * callbackUrl. After the user authenticates through OIDC, NextAuth redirects
 * to [MOBILE_CALLBACK_URL], which is intercepted by the WebView client.
 * At this point the session cookie is fully set, so we call the Karakeep
 * `/api/user/apiKey` endpoint to retrieve the user's API key.
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
    val encodedCallback = java.net.URLEncoder.encode(MOBILE_CALLBACK_URL, "UTF-8")
    val signInUrl = "$baseUrl/api/auth/signin?callbackUrl=$encodedCallback"
    val apiKeyUrl = "$baseUrl/api/user/apiKey"

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

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false

                        // Intercept the custom mobile callback URL.
                        // At this point all session cookies from the OIDC flow are set.
                        if (url.startsWith(MOBILE_CALLBACK_URL) && !callbackCalled) {
                            callbackCalled = true
                            // Flush cookies to make sure CookieManager has them all
                            CookieManager.getInstance().flush()
                            val cookieHeader = CookieManager.getInstance().getCookie(baseUrl) ?: ""
                            scope.launch {
                                fetchApiKey(
                                    apiKeyUrl = apiKeyUrl,
                                    cookieHeader = cookieHeader,
                                    onApiKeyObtained = onApiKeyObtained,
                                    onError = onError,
                                )
                            }
                            return true // do not navigate to karakept://
                        }

                        return false
                    }
                }

                loadUrl(signInUrl)
            }
        }
    )
}

/**
 * Fetches the API key from Karakeep using the active web session cookie.
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
