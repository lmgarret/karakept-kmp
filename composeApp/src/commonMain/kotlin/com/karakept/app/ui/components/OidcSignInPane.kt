package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.screens.OidcSignInScreenModel
import com.karakept.app.ui.screens.OidcSignInState
import com.karakept.app.utils.OidcSignInUtils
import io.github.kdroidfilter.webview.web.LoadingState
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.rememberWebViewNavigator
import io.github.kdroidfilter.webview.web.rememberWebViewState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.viewmodel.koinViewModel

/**
 * Karakeep's own sign-in page in an embedded WebView, for SSO/OIDC logins. Once the user is
 * signed in, the web session is traded for an API key, handed to [onApiKey].
 *
 * Embedded rather than a Custom Tab or the system browser: only an embedded WebView lets the
 * app read the session cookie back out (see [OidcSignInUtils]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OidcSignInPane(
    serverUrl: String,
    onApiKey: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenModel = koinViewModel<OidcSignInScreenModel>(key = "oidc-sign-in:$serverUrl")
    val state by screenModel.state.collectAsState()
    val webViewState = rememberWebViewState(OidcSignInUtils.signInUrl(serverUrl))
    val navigator = rememberWebViewNavigator()
    val webBaseUrl = OidcSignInUtils.webBaseUrl(serverUrl)

    LaunchedEffect(webViewState, serverUrl) {
        snapshotFlow { (webViewState.loadingState is LoadingState.Finished) to webViewState.lastLoadedUrl }
            .distinctUntilChanged()
            .filter { (finished, _) -> finished }
            .collect { (_, pageUrl) ->
                val cookies = webViewState.cookieManager.getCookies(webBaseUrl)
                screenModel.onPageLoaded(serverUrl, pageUrl, cookies.map { it.name to it.value })
            }
    }

    LaunchedEffect(state) {
        val done = state as? OidcSignInState.Done ?: return@LaunchedEffect
        // The key is all the app keeps; don't leave a signed-in web session behind.
        webViewState.cookieManager.removeCookies(webBaseUrl)
        onApiKey(done.apiKey)
    }

    BackHandler {
        if (navigator.canGoBack) navigator.navigateBack() else onCancel()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sign in with SSO") },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(AppIcons.Default.Close, contentDescription = "Cancel")
                }
            },
            actions = {
                IconButton(onClick = { navigator.reload() }) {
                    Icon(AppIcons.Default.Refresh, contentDescription = "Reload")
                }
            }
        )
        // Above the page rather than over it: on desktop the WebView is a native panel that
        // Compose cannot draw on top of.
        when (val current = state) {
            OidcSignInState.CreatingKey, is OidcSignInState.Done -> BusyIndicator(
                label = "Creating API key…",
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            is OidcSignInState.Failed -> Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Signed in, but the server did not hand out an API key.\n${current.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Button(onClick = {
                    screenModel.retry()
                    navigator.reload()
                }) {
                    Text("Retry")
                }
            }
            OidcSignInState.WaitingForLogin -> Unit
        }
        WebView(
            state = webViewState,
            navigator = navigator,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}
