package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific WebView for OIDC authentication.
 *
 * This composable renders a full browser that:
 * - Navigates to the Karakeep sign-in page
 * - Allows the user to authenticate via OIDC/SSO
 * - After successful authentication, retrieves an API key from the
 *   Karakeep web session by calling the `/api/user/apiKey` endpoint
 * - Returns the API key via [onApiKeyObtained]
 * - Reports loading progress via [onLoading] and errors via [onError]
 *
 * @param serverUrl The base URL of the Karakeep server (e.g. "https://karakeep.example.com")
 * @param onApiKeyObtained Called when an API key has been successfully extracted
 * @param onError Called when authentication fails or is cancelled
 * @param modifier Layout modifier
 */
@Composable
expect fun OidcWebView(
    serverUrl: String,
    onApiKeyObtained: (apiKey: String) -> Unit,
    onError: (message: String) -> Unit,
    modifier: Modifier = Modifier,
)
