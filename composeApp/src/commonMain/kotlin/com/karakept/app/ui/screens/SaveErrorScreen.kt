package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.utils.ShareUtils

data class SaveErrorScreen(val errors: List<SaveError>) : Screen {
    constructor(url: String, message: String) : this(listOf(SaveError(url, message)))

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SaveErrorScreenModel>()
        val uriHandler = LocalUriHandler.current
        val retryStates by screenModel.retryStates.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (errors.size > 1) "Failed to save bookmarks" else "Failed to save bookmark")
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(errors, key = { it.url }) { error ->
                    SaveErrorCard(
                        error = error,
                        retryState = retryStates[error.url] ?: SaveRetryState.Idle,
                        onRetry = { screenModel.retry(error.url) },
                        onOpen = { uriHandler.openUri(error.url) },
                        onShare = { ShareUtils.shareText(error.url) },
                        onViewSaved = { bookmarkId ->
                            navigator.push(BookmarkViewerScreen(bookmarkId))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveErrorCard(
    error: SaveError,
    retryState: SaveRetryState,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onViewSaved: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isSuccess = retryState is SaveRetryState.Success
                Icon(
                    imageVector = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                )
                Text(
                    text = error.url,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
            }

            when (retryState) {
                is SaveRetryState.Success -> {
                    Text(
                        text = "Saved successfully",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row {
                        TextButton(onClick = { onViewSaved(retryState.bookmarkId) }) {
                            Text("View bookmark")
                        }
                    }
                }

                else -> {
                    val message = when (retryState) {
                        is SaveRetryState.Failed -> retryState.message
                        else -> error.message
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRetry,
                            enabled = retryState !is SaveRetryState.Retrying
                        ) {
                            if (retryState is SaveRetryState.Retrying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("Retry", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        OutlinedButton(onClick = onOpen) {
                            Icon(
                                Icons.Filled.OpenInBrowser,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Open", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(onClick = onShare) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Share", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
