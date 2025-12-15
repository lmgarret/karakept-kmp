package com.karakept.app.ui.components

import androidx.compose.ui.draw.alpha

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.karakept.app.data.local.entity.BookmarkEntity


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkCardLayout(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showReadingTime: Boolean = true,
    dimRead: Boolean = false,
    modifier: Modifier = Modifier
) {
    val alpha = if (bookmark.isRead && dimRead) 0.5f else 1f
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.alpha(alpha)) {
            Column {
                if (bookmark.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(bookmark.imageUrl)
                            .size(600) // Request a reasonable size for the card
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📰",
                            style = MaterialTheme.typography.displayLarge
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = bookmark.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Reading time badge overlay - bottom right
            if (showReadingTime && bookmark.readingTimeMinutes > 0) {
                ReadingTimeBadge(
                    readingTimeMinutes = bookmark.readingTimeMinutes,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkListLayout(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showReadingTime: Boolean = true,
    dimRead: Boolean = false,
    modifier: Modifier = Modifier
) {
    val alpha = if (bookmark.isRead && dimRead) 0.5f else 1f
    Card(
        modifier = modifier
           .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.alpha(alpha)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    if (bookmark.imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                .data(bookmark.imageUrl)
                                .size(300) // Request a smaller size for the list item
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📰",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }

                    // Favicon overlay
                    AsyncImage(
                        model = com.karakept.app.utils.FaviconUtils.getFaviconUrl(bookmark.url),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentScale = ContentScale.Fit
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = bookmark.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (bookmark.description != null) {
                        Text(
                            text = bookmark.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Reading time badge overlay - bottom right
            if (showReadingTime && bookmark.readingTimeMinutes > 0) {
                ReadingTimeBadge(
                    readingTimeMinutes = bookmark.readingTimeMinutes,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}
