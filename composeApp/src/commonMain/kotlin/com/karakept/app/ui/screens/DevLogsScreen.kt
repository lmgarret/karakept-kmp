package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.setPlainText
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Dev-only in-app log viewer. Renders the [AppLogger] ring buffer so a failing sync can be
 * diagnosed on-device without Logcat. Reachable from the About screen in dev builds only.
 */
@Serializable
class DevLogsScreen : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        DevLogsContent(onBack = { navigator.pop() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevLogsContent(onBack: () -> Unit) {
    val lines by AppLogger.history.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("") }

    val visible = remember(lines, filter) {
        if (filter.isBlank()) lines
        else lines.filter { it.contains(filter, ignoreCase = true) }
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs (${visible.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { clipboard.setPlainText(visible.joinToString("\n")) }
                    }) {
                        Icon(AppIcons.Default.ContentCopy, contentDescription = "Copy logs")
                    }
                    IconButton(onClick = { AppLogger.clearHistory() }) {
                        Icon(AppIcons.Default.DeleteSweep, contentDescription = "Clear logs")
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
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Filter") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (visible.isEmpty()) {
                Text(
                    text = if (lines.isEmpty()) "No logs captured yet." else "No lines match the filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(visible) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colorForLine(line),
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun colorForLine(line: String): androidx.compose.ui.graphics.Color = when {
    line.contains(" E/") -> MaterialTheme.colorScheme.error
    line.contains(" W/") -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface
}
