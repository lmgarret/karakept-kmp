package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.repository.BookmarkRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class ShareBookmarkScreen(val url: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<BookmarkRepository>()
        val scope = rememberCoroutineScope()
        var error by remember { mutableStateOf<String?>(null) }
        var status by remember { mutableStateOf("Saving bookmark...") }
        
        LaunchedEffect(Unit) {
            scope.launch {
                val result = repository.createBookmark(url) { newStatus ->
                    status = newStatus
                }
                if (result.isSuccess) {
                    val bookmark = result.getOrThrow()
                    // Open the viewer for the new bookmark
                    navigator.replace(BookmarkViewerScreen(bookmark.localId))
                } else {
                    error = result.exceptionOrNull()?.message ?: "Unknown error"
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (error == null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        text = "Failed to save bookmark",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = { navigator.pop() }
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
