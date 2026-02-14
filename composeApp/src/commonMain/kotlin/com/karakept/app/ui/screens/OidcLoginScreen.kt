package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.ui.components.OidcWebView

/**
 * Screen that hosts the in-app WebView for OIDC/SSO authentication.
 *
 * This screen is navigated to during onboarding when the user chooses
 * "Sign in with SSO" instead of entering an API key manually.
 *
 * Flow:
 * 1. Opens the Karakeep sign-in page in a full-screen in-app browser
 * 2. The user authenticates via their configured OIDC provider
 * 3. After successful login, the app retrieves an API key from the
 *    Karakeep web session automatically
 * 4. Saves the server + API key and navigates to [MainScreen]
 *
 * @param serverUrl The base URL of the Karakeep server
 * @param isOnboarding If true, also marks onboarding as complete after connecting
 */
data class OidcLoginScreen(
    val serverUrl: String,
    val isOnboarding: Boolean = false,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<OidcLoginScreenModel>()
        var errorMessage by remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sign in with SSO") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Cancel"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = "Authentication failed: $errorMessage",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                OidcWebView(
                    serverUrl = serverUrl,
                    onApiKeyObtained = { apiKey ->
                        screenModel.addServer(
                            url = serverUrl,
                            apiKey = apiKey,
                            isOnboarding = isOnboarding,
                        ) {
                            navigator.replaceAll(MainScreen)
                        }
                    },
                    onError = { message ->
                        errorMessage = message
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
