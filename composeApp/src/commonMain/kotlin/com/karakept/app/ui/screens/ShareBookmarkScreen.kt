package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.karakept.app.ui.icons.AppIcons
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.data.repository.BookmarkRepository
import org.koin.compose.koinInject

@Serializable
data class ShareBookmarkScreen(val url: String, @Transient val onClose: (() -> Unit)? = null) : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<BookmarkRepository>()
        var error by remember { mutableStateOf<String?>(null) }
        var status by remember { mutableStateOf("Saving bookmark...") }
        var retryTrigger by remember { mutableIntStateOf(0) }

        LaunchedEffect(retryTrigger) {
            error = null
            status = "Saving bookmark..."
            val result = repository.createBookmark(url) { newStatus ->
                status = newStatus
            }
            if (result.isSuccess) {
                val bookmark = result.getOrThrow()
                navigator.replaceAll(listOf(MainScreen, BookmarkViewerScreen(bookmark.localId)))
            } else {
                error = result.exceptionOrNull()?.message ?: "Unknown error"
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
                        imageVector = AppIcons.Filled.Error,
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

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { retryTrigger++ }) {
                            Text("Retry")
                        }
                        Button(onClick = { onClose?.invoke() ?: navigator.pop() }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}
