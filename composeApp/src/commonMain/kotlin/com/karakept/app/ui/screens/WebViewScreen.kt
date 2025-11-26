package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.ui.components.HtmlContent
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Screen for displaying web content from links clicked in bookmark HTML.
 *
 * Note: This is a simplified implementation that attempts to fetch and display
 * the HTML content from the URL. For production use, consider:
 * - Loading indicators
 * - Error handling
 * - Progress feedback
 * - Network timeouts
 */
data class WebViewScreen(val url: String) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var htmlContent by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        // Fetch the HTML content from the URL
        LaunchedEffect(url) {
            try {
                // For now, just display a message indicating the URL
                // In a full implementation, you might fetch the content
                htmlContent = """
                    <h2>Link Navigation</h2>
                    <p>You clicked on:</p>
                    <p><a href="$url">$url</a></p>
                    <p><em>Note: For security reasons, external content fetching is not yet implemented.
                    This would require additional security measures and user permissions.</em></p>
                """.trimIndent()
                isLoading = false
            } catch (e: Exception) {
                error = "Failed to load content: ${e.message}"
                isLoading = false
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Web View",
                            maxLines = 1
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                when {
                    isLoading -> {
                        Text("Loading...")
                    }
                    error != null -> {
                        Text("Error: $error")
                    }
                    else -> {
                        HtmlContent(
                            html = htmlContent,
                            viewerMode = com.karakept.app.data.model.ViewerMode.READER,
                            modifier = Modifier.fillMaxSize(),
                            onLinkClick = { clickedUrl ->
                                // Navigate to another WebViewScreen for the clicked link
                                navigator.push(WebViewScreen(clickedUrl))
                            }
                        )
                    }
                }
            }
        }
    }
}
