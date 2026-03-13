package com.karakept.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.Highlight
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.karakept.app.ui.theme.HighlightYellow
import com.karakept.app.ui.theme.HighlightBlue
import com.karakept.app.ui.theme.HighlightGreen
import com.karakept.app.ui.theme.HighlightRed

class HighlightsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<HighlightsScreenModel>()
        val highlights by screenModel.highlights.collectAsState()
        val isSyncing by screenModel.isSyncing.collectAsState()
        val scope = rememberCoroutineScope()

        // Sync highlights when screen opens
        LaunchedEffect(Unit) {
            screenModel.syncHighlights()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("All Highlights") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            if (highlights.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator()
                    } else {
                        Text("No highlights yet", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(highlights, key = { it.id }) { highlight ->
                        HighlightCard(
                            highlight = highlight,
                            onClick = {
                                scope.launch {
                                    val bookmarkLocalId = screenModel.getBookmarkLocalIdForHighlight(highlight)
                                    if (bookmarkLocalId != null) {
                                        navigator.push(BookmarkViewerScreen(bookmarkLocalId, highlight.id))
                                    }
                                }
                            },
                            onDelete = { screenModel.deleteHighlight(highlight) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun HighlightCard(
        highlight: Highlight,
        onClick: () -> Unit,
        onDelete: () -> Unit
    ) {
        val highlightColor = getColorForHighlight(highlight.color ?: "yellow")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // Color indicator bar
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(highlightColor)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "\u201C${highlight.text}\u201D",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Highlight", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (!highlight.note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = highlight.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val date = Instant.fromEpochMilliseconds(highlight.createdAt)
                        .toLocalDateTime(TimeZone.currentSystemDefault())

                    Text(
                        text = "${date.dayOfMonth}/${date.monthNumber}/${date.year}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

private fun getColorForHighlight(colorName: String): Color {
    return when (colorName.lowercase()) {
        "yellow" -> HighlightYellow
        "blue" -> HighlightBlue
        "green" -> HighlightGreen
        "red" -> HighlightRed
        else -> HighlightYellow
    }
}
