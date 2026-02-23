package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.ui.components.UrlRenderer

/**
 * In-app browser screen that loads a URL in a native web view.
 * Used when the user has configured links to open in-app rather than in an external browser.
 */
data class WebViewScreen(val url: String) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var pageTitle by remember { mutableStateOf(url) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = pageTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            UrlRenderer(
                url = url,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onPageTitleChanged = { title -> pageTitle = title }
                // onLinkClick is intentionally not passed: the WebView acts as a real in-app
                // browser and handles navigation internally.  Overriding it to push new
                // Voyager screens would also intercept HTTP redirects, leaving the WebView
                // blank whenever the loaded URL issues a server-side redirect.
            )
        }
    }
}
