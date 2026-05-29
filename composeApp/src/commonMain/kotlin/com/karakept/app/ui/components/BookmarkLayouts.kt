package com.karakept.app.ui.components

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.BookmarkType
import com.karakept.app.data.local.entity.bookmarkType
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.ui.utils.extractDomain
import com.karakept.app.utils.FaviconUtils
import com.karakept.app.utils.formatBookmarkDate


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkCardLayout(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showReadingTime: Boolean = true,
    showReadingProgress: Boolean = true,
    showTags: Boolean = true,
    showDate: Boolean = true,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.ELAPSED,
    dimRead: Boolean = false,
    offlineMode: Boolean = false,
    bannerImageUrl: String? = null,
    screenshotUrl: String? = null,
    isSelected: Boolean = false,
    tagsScrollable: Boolean = false,
    isActive: Boolean = false,
    showDescription: Boolean = true,
    showUrl: Boolean = false,
    urlDisplayMode: UrlDisplayMode = UrlDisplayMode.DOMAIN_ONLY,
    urlPosition: UrlPosition = UrlPosition.BELOW_TITLE,
    urlIconMode: UrlIconMode = UrlIconMode.GLOBE_ONLY,
    faviconByLinkSize: Int = 16,
    faviconPainter: Painter? = null,
    thumbnailPainter: Painter? = null,
    modifier: Modifier = Modifier
) {
    val isFullyRead = bookmark.isRead
    val alpha = if (isFullyRead && dimRead) 0.5f else 1f
    val type = bookmark.bookmarkType
    val effectiveDescription = bookmark.description?.takeIf { it.isNotBlank() }
        ?: if (type == BookmarkType.TEXT) bookmark.content?.takeIf { it.isNotBlank() }?.let { noteSnippet(it) } else null
    val selectionBorderModifier = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else if (isActive) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }
    val hapticFeedback = LocalHapticFeedback.current
    val hapticLongClick = androidx.compose.runtime.remember(onLongClick) {
        onLongClick?.let { callback ->
            { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress); callback() }
        }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(selectionBorderModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = hapticLongClick
            )
            ,
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        } else if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Box {
            Box(modifier = Modifier.alpha(alpha)) {
                Column {
                    // Determine which image to show: thumbnailPainter (bundled) → bannerImageUrl (server asset) → screenshotUrl → emoji
                    val effectiveImageUrl = if (thumbnailPainter == null) bannerImageUrl ?: screenshotUrl else null

                    Box(contentAlignment = Alignment.Center) {
                        if (thumbnailPainter != null) {
                            Image(
                                painter = thumbnailPainter,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        } else if (effectiveImageUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(effectiveImageUrl)
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
                                    text = bookmarkEmoji(type),
                                    style = MaterialTheme.typography.displayLarge
                                )
                            }
                        }
                        if (type == BookmarkType.VIDEO) {
                            VideoPlayBadge(size = 56.dp)
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
                        if (showUrl && !bookmark.url.isNullOrBlank() && urlPosition == UrlPosition.BELOW_TITLE) {
                            UrlDisplay(url = bookmark.url, urlDisplayMode = urlDisplayMode, urlIconMode = urlIconMode, iconSize = faviconByLinkSize, faviconPainter = faviconPainter, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (showTags && bookmark.tags.isNotBlank()) {
                            BookmarkTagsDisplay(
                                tags = bookmark.tags,
                                style = TagsDisplayStyle.COMPACT,
                                scrollable = tagsScrollable,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (showDescription && !effectiveDescription.isNullOrBlank()) {
                            Text(
                                text = effectiveDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showDate) {
                                Text(
                                    text = formatBookmarkDate(bookmark.createdAt, dateDisplayMode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            if (showUrl && !bookmark.url.isNullOrBlank() && urlPosition == UrlPosition.METADATA_ROW) {
                                UrlDisplay(url = bookmark.url, urlDisplayMode = urlDisplayMode, urlIconMode = urlIconMode, iconSize = faviconByLinkSize, faviconPainter = faviconPainter, modifier = Modifier.padding(end = 8.dp))
                            }
                            if (showReadingTime) {
                                if (bookmark.readingTimeMinutes > 0) {
                                    ReadingTimeBadge(readingTimeMinutes = bookmark.readingTimeMinutes)
                                } else if (offlineMode) {
                                    NotSyncedBadge()
                                }
                            }
                        }
                    }
                }
            }

            // Reading progress bar
            if (showReadingProgress && bookmark.readingProgress > 0f) {
                LinearProgressIndicator(
                    progress = { bookmark.readingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Selection indicator (not affected by dim alpha)
            SelectionIndicator(
                isSelected = isSelected,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
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
    showReadingProgress: Boolean = true,
    showTags: Boolean = true,
    showDate: Boolean = true,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.ELAPSED,
    dimRead: Boolean = false,
    offlineMode: Boolean = false,
    bannerImageUrl: String? = null,
    screenshotUrl: String? = null,
    isSelected: Boolean = false,
    thumbnailSide: ThumbnailSide = ThumbnailSide.LEFT,
    showFavicon: Boolean = true,
    thumbnailSize: Int = 80,
    metadataPosition: MetadataPosition = MetadataPosition.BELOW,
    tagsScrollable: Boolean = false,
    isActive: Boolean = false,
    showDescription: Boolean = true,
    descriptionPosition: DescriptionPosition = DescriptionPosition.BELOW_TITLE,
    showUrl: Boolean = false,
    urlDisplayMode: UrlDisplayMode = UrlDisplayMode.DOMAIN_ONLY,
    urlPosition: UrlPosition = UrlPosition.BELOW_TITLE,
    urlIconMode: UrlIconMode = UrlIconMode.GLOBE_ONLY,
    faviconByLinkSize: Int = 16,
    faviconPainter: Painter? = null,
    thumbnailPainter: Painter? = null,
    modifier: Modifier = Modifier
) {
    val isFullyRead = bookmark.isRead
    val alpha = if (isFullyRead && dimRead) 0.5f else 1f
    val type = bookmark.bookmarkType
    val effectiveDescription = bookmark.description?.takeIf { it.isNotBlank() }
        ?: if (type == BookmarkType.TEXT) bookmark.content?.takeIf { it.isNotBlank() }?.let { noteSnippet(it) } else null
    val selectionBorderModifier = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else if (isActive) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }
    val hapticFeedback = LocalHapticFeedback.current
    val hapticLongClick = androidx.compose.runtime.remember(onLongClick) {
        onLongClick?.let { callback ->
            { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress); callback() }
        }
    }
    val thumbSizeDp = thumbnailSize.dp
    val faviconSizeDp = (thumbnailSize * 16 / 80).coerceIn(10, 20).dp
    val faviconPaddingDp = (thumbnailSize * 4 / 80).coerceIn(2, 6).dp
    val cornerDp = (thumbnailSize * 8 / 80).coerceIn(4, 12).dp

    Card(
        modifier = modifier
           .fillMaxWidth()
            .then(selectionBorderModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = hapticLongClick
            )
            ,
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        } else if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Box {
        Box(modifier = Modifier.alpha(alpha)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Metadata composable used in both positions
                val metadataContent: @Composable () -> Unit = {
                    if (showDescription && !effectiveDescription.isNullOrBlank() && descriptionPosition == DescriptionPosition.ABOVE_METADATA) {
                        Text(
                            text = effectiveDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    if (showTags && bookmark.tags.isNotBlank()) {
                        BookmarkTagsDisplay(
                            tags = bookmark.tags,
                            style = TagsDisplayStyle.COMPACT,
                            scrollable = tagsScrollable,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showDate) {
                            Text(
                                text = formatBookmarkDate(bookmark.createdAt, dateDisplayMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (showUrl && !bookmark.url.isNullOrBlank() && urlPosition == UrlPosition.METADATA_ROW) {
                            UrlDisplay(url = bookmark.url, urlDisplayMode = urlDisplayMode, urlIconMode = urlIconMode, iconSize = faviconByLinkSize, faviconPainter = faviconPainter, modifier = Modifier.padding(end = 8.dp))
                        }
                        if (showReadingTime) {
                            if (bookmark.readingTimeMinutes > 0) {
                                ReadingTimeBadge(readingTimeMinutes = bookmark.readingTimeMinutes)
                            } else if (offlineMode) {
                                NotSyncedBadge()
                            }
                        }
                    }
                }

                val thumbnailContent: @Composable RowScope.() -> Unit = {
                    Box(
                        modifier = Modifier
                            .size(thumbSizeDp)
                            .clip(RoundedCornerShape(cornerDp))
                    ) {
                        val effectiveImageUrl = if (thumbnailPainter == null) bannerImageUrl ?: screenshotUrl else null

                        if (thumbnailPainter != null) {
                            Image(
                                painter = thumbnailPainter,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        } else if (effectiveImageUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(effectiveImageUrl)
                                    .size(300)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop,
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = bookmarkEmoji(type),
                                    style = if (thumbnailSize >= 64) MaterialTheme.typography.headlineMedium
                                        else MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        if (type == BookmarkType.VIDEO) {
                            VideoPlayBadge(
                                size = (thumbnailSize * 0.45f).dp.coerceAtLeast(20.dp),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        if (showFavicon && bookmark.url.isNotBlank()) {
                            val faviconModifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(faviconPaddingDp)
                                .size(faviconSizeDp)
                                .clip(RoundedCornerShape(faviconSizeDp / 4))
                                .background(Color.White.copy(alpha = 0.5f))
                            if (faviconPainter != null) {
                                Image(
                                    painter = faviconPainter,
                                    contentDescription = null,
                                    modifier = faviconModifier,
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                AsyncImage(
                                    model = FaviconUtils.getFaviconUrl(bookmark.url),
                                    contentDescription = null,
                                    modifier = faviconModifier,
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
                val textContent: @Composable RowScope.() -> Unit = {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = bookmark.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showUrl && !bookmark.url.isNullOrBlank() && urlPosition == UrlPosition.BELOW_TITLE) {
                            UrlDisplay(url = bookmark.url, urlDisplayMode = urlDisplayMode, urlIconMode = urlIconMode, iconSize = faviconByLinkSize, faviconPainter = faviconPainter, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (showDescription && !effectiveDescription.isNullOrBlank() && descriptionPosition == DescriptionPosition.BELOW_TITLE) {
                            Text(
                                text = effectiveDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (metadataPosition == MetadataPosition.BESIDE) {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                metadataContent()
                            }
                        }
                    }
                }
                if (metadataPosition == MetadataPosition.ABOVE) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        metadataContent()
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (thumbnailSide == ThumbnailSide.LEFT) {
                        thumbnailContent()
                        textContent()
                    } else {
                        textContent()
                        thumbnailContent()
                    }
                }
                if (metadataPosition == MetadataPosition.BELOW) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        metadataContent()
                    }
                }
            }

            // Reading progress bar
            if (showReadingProgress && bookmark.readingProgress > 0f) {
                LinearProgressIndicator(
                    progress = { bookmark.readingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // Selection indicator (not affected by dim alpha)
        SelectionIndicator(
            isSelected = isSelected,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )
        }
    }
}

@Composable
private fun UrlDisplay(
    url: String,
    urlDisplayMode: UrlDisplayMode,
    urlIconMode: UrlIconMode = UrlIconMode.GLOBE_ONLY,
    iconSize: Int = 16,
    faviconPainter: Painter? = null,
    modifier: Modifier = Modifier
) {
    val displayText = when (urlDisplayMode) {
        UrlDisplayMode.DOMAIN_ONLY -> extractDomain(url)
        UrlDisplayMode.FULL_URL -> url
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (urlIconMode != UrlIconMode.NONE) {
            Box(modifier = Modifier.size(iconSize.dp)) {
                if (urlIconMode == UrlIconMode.FAVICON) {
                    if (faviconPainter != null) {
                        Image(
                            painter = faviconPainter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        val faviconUrl = FaviconUtils.getFaviconUrl(url)
                        if (faviconUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(faviconUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Fallback to globe when no favicon URL available
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // GLOBE_ONLY mode
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.size(4.dp))
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


/** Circular play overlay shown on video bookmark thumbnails. */
@Composable
fun VideoPlayBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Video",
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

internal fun bookmarkEmoji(type: BookmarkType): String = when (type) {
    BookmarkType.TEXT -> "📝"
    BookmarkType.VIDEO -> "🎬"
    else -> "📰"
}

internal fun noteSnippet(content: String): String =
    content.replace(Regex("\\s+"), " ").trim().take(200)


@Composable
fun SelectionIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Box(
            modifier = modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
