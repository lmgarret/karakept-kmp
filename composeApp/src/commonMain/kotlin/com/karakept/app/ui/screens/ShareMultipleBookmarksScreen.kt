package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.karakept.app.ui.icons.AppIcons
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.data.repository.BookmarkRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class UrlSaveStatus { PENDING, SAVING, SAVED, ERROR }

private data class UrlSaveState(
    val url: String,
    val status: UrlSaveStatus = UrlSaveStatus.PENDING,
    val error: String? = null,
)

@Serializable
data class ShareMultipleBookmarksScreen(
    val urls: List<String>,
    @Transient val onClose: (() -> Unit)? = null,
) : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<BookmarkRepository>()
        val scope = rememberCoroutineScope()

        val items: SnapshotStateList<UrlSaveState> = remember {
            mutableStateListOf(*urls.map { UrlSaveState(url = it) }.toTypedArray())
        }

        val savedCount = items.count { it.status == UrlSaveStatus.SAVED }
        val errorCount = items.count { it.status == UrlSaveStatus.ERROR }
        val allDone = items.all { it.status == UrlSaveStatus.SAVED || it.status == UrlSaveStatus.ERROR }

        fun saveUrl(index: Int) {
            scope.launch {
                val url = items[index].url
                items[index] = items[index].copy(status = UrlSaveStatus.SAVING, error = null)
                val result = repository.createBookmark(url)
                items[index] = if (result.isSuccess) {
                    items[index].copy(status = UrlSaveStatus.SAVED)
                } else {
                    items[index].copy(
                        status = UrlSaveStatus.ERROR,
                        error = result.exceptionOrNull()?.message ?: "Unknown error",
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            urls.indices.forEach { saveUrl(it) }
        }

        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = when {
                        !allDone -> "Saving ${urls.size} bookmarks..."
                        errorCount == 0 -> "Saved ${urls.size} bookmarks"
                        savedCount == 0 -> "Failed to save bookmarks"
                        else -> "Saved $savedCount of ${urls.size} bookmarks"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (allDone && errorCount > 0 && savedCount == 0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface,
                )

                if (!allDone) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(items) { index, item ->
                        UrlSaveItem(
                            item = item,
                            onRetry = { saveUrl(index) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (allDone && errorCount > 0) {
                        OutlinedButton(
                            onClick = {
                                items.indices
                                    .filter { items[it].status == UrlSaveStatus.ERROR }
                                    .forEach { saveUrl(it) }
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("Retry failed")
                        }
                    }
                    Button(
                        onClick = {
                            if (allDone && savedCount > 0) {
                                navigator.replaceAll(listOf(MainScreen))
                            } else {
                                onClose?.invoke() ?: navigator.pop()
                            }
                        },
                    ) {
                        Text(if (allDone) "Done" else "Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlSaveItem(item: UrlSaveState, onRetry: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = item.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = when (item.status) {
            UrlSaveStatus.ERROR -> {
                {
                    Text(
                        text = item.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            UrlSaveStatus.SAVING -> {
                {
                    Text(
                        text = "Saving...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> null
        },
        leadingContent = {
            when (item.status) {
                UrlSaveStatus.PENDING -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 2.dp,
                )
                UrlSaveStatus.SAVING -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                UrlSaveStatus.SAVED -> Icon(
                    imageVector = AppIcons.Filled.CheckCircle,
                    contentDescription = "Saved",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                UrlSaveStatus.ERROR -> Icon(
                    imageVector = AppIcons.Filled.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        trailingContent = if (item.status == UrlSaveStatus.ERROR) {
            { TextButton(onClick = onRetry) { Text("Retry") } }
        } else null,
    )
    HorizontalDivider()
}
