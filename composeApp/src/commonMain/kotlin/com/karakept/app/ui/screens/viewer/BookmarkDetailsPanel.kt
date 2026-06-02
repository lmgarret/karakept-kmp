package com.karakept.app.ui.screens.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ContentSource
import com.karakept.app.ui.components.BookmarkTagsDisplay
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Details panel that slides in from the right, showing all bookmark metadata,
 * content source selection, and asset management.
 */
@Composable
internal fun BookmarkDetailsPanel(
    visible: Boolean,
    bookmark: BookmarkEntity?,
    assets: List<AssetEntity> = emptyList(),
    selectedSource: ContentSource = ContentSource.EXTRACTED,
    onSourceSelected: (ContentSource) -> Unit = {},
    onDownloadAsset: (AssetEntity) -> Unit = {},
    onRefreshAsset: (AssetEntity) -> Unit = {},
    onDeleteAssetLocal: (AssetEntity) -> Unit = {},
    onDismiss: () -> Unit
) {
    // Scrim
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    // Panel sliding from the right
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                if (bookmark != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        HorizontalDivider()

                        // Scrollable content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            // General
                            DetailsSectionTitle("General")
                            DetailsRow(
                                icon = Icons.Default.CalendarToday,
                                label = "Created",
                                value = formatEpochMillis(bookmark.createdAt)
                            )
                            if (bookmark.readingTimeMinutes > 0) {
                                DetailsRow(
                                    icon = Icons.Default.AccessTime,
                                    label = "Reading time",
                                    value = "${bookmark.readingTimeMinutes} min"
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Status
                            DetailsSectionTitle("Status")
                            DetailsRow(
                                icon = Icons.Default.Star,
                                label = "Favourited",
                                value = if (bookmark.isStarred) "Yes" else "No",
                                valueColor = if (bookmark.isStarred) MaterialTheme.colorScheme.primary else null
                            )
                            DetailsRow(
                                icon = Icons.Default.Archive,
                                label = "Archived",
                                value = if (bookmark.isArchived) "Yes" else "No"
                            )
                            DetailsRow(
                                icon = Icons.Default.MenuBook,
                                label = "Read",
                                value = if (bookmark.isRead) "Yes" else "No"
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Content Sources
                            DetailsSectionTitle("Content Sources")

                            val hasExtractedContent = assets.any { it.assetType == "linkHtmlContent" }
                                || !bookmark.content.isNullOrBlank()
                            val fullPageArchiveAsset = assets.find { it.assetType == "fullPageArchive" }
                            val precrawledArchiveAsset = assets.find { it.assetType == "precrawledArchive" }
                            val activeArchiveAsset = if (selectedSource == ContentSource.FULL_PAGE_ARCHIVE) {
                                assets.find { it.assetType == "fullPageArchive" && it.localPath != null }
                                    ?: assets.find { it.assetType == "precrawledArchive" && it.localPath != null }
                            } else null

                            ContentSourceRow(
                                icon = Icons.AutoMirrored.Filled.ChromeReaderMode,
                                label = "Extracted content",
                                statusText = if (hasExtractedContent) "Available" else "Not available",
                                isActive = selectedSource == ContentSource.EXTRACTED,
                                canActivate = hasExtractedContent && selectedSource != ContentSource.EXTRACTED,
                                onSelect = { onSourceSelected(ContentSource.EXTRACTED) }
                            )

                            if (fullPageArchiveAsset != null) {
                                ContentSourceRow(
                                    icon = Icons.Default.Web,
                                    label = "Full page archive",
                                    statusText = if (fullPageArchiveAsset.localPath != null) "Downloaded" else "On server",
                                    isActive = activeArchiveAsset?.id == fullPageArchiveAsset.id,
                                    canActivate = fullPageArchiveAsset.localPath != null
                                        && selectedSource != ContentSource.FULL_PAGE_ARCHIVE,
                                    asset = fullPageArchiveAsset,
                                    onSelect = { onSourceSelected(ContentSource.FULL_PAGE_ARCHIVE) },
                                    onDownload = { onDownloadAsset(fullPageArchiveAsset) },
                                    onRefresh = { onRefreshAsset(fullPageArchiveAsset) },
                                    onDelete = { onDeleteAssetLocal(fullPageArchiveAsset) }
                                )
                            }

                            if (precrawledArchiveAsset != null) {
                                ContentSourceRow(
                                    icon = Icons.Default.Archive,
                                    label = "Crawled archive",
                                    statusText = if (precrawledArchiveAsset.localPath != null) "Downloaded" else "On server",
                                    isActive = activeArchiveAsset?.id == precrawledArchiveAsset.id,
                                    canActivate = precrawledArchiveAsset.localPath != null
                                        && selectedSource != ContentSource.FULL_PAGE_ARCHIVE,
                                    asset = precrawledArchiveAsset,
                                    onSelect = { onSourceSelected(ContentSource.FULL_PAGE_ARCHIVE) },
                                    onDownload = { onDownloadAsset(precrawledArchiveAsset) },
                                    onRefresh = { onRefreshAsset(precrawledArchiveAsset) },
                                    onDelete = { onDeleteAssetLocal(precrawledArchiveAsset) }
                                )
                            }

                            // Media assets
                            val bannerAsset = assets.find { it.assetType == "bannerImage" }
                            val screenshotAsset = assets.find { it.assetType == "screenshot" }
                            if (bannerAsset != null || screenshotAsset != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                DetailsSectionTitle("Media")
                                bannerAsset?.let { asset ->
                                    MediaAssetRow(
                                        icon = Icons.Default.Image,
                                        label = "Banner image",
                                        isCached = asset.localPath != null,
                                        onDownload = { onDownloadAsset(asset) },
                                        onRefresh = { onRefreshAsset(asset) },
                                        onDelete = { onDeleteAssetLocal(asset) }
                                    )
                                }
                                screenshotAsset?.let { asset ->
                                    MediaAssetRow(
                                        icon = Icons.Default.PhotoCamera,
                                        label = "Screenshot",
                                        isCached = asset.localPath != null,
                                        onDownload = { onDownloadAsset(asset) },
                                        onRefresh = { onRefreshAsset(asset) },
                                        onDelete = { onDeleteAssetLocal(asset) }
                                    )
                                }
                            }

                            // Tags
                            if (bookmark.tags.isNotBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                DetailsSectionTitle("Tags")
                                BookmarkTagsDisplay(
                                    tags = bookmark.tags,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentSourceRow(
    icon: ImageVector,
    label: String,
    statusText: String,
    isActive: Boolean,
    canActivate: Boolean,
    asset: AssetEntity? = null,
    onSelect: () -> Unit = {},
    onDownload: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val containerColor = if (isActive)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .then(if (canActivate) Modifier.clickable(onClick = onSelect) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 1.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = when {
                        asset?.localPath != null -> MaterialTheme.colorScheme.primary
                        statusText == "Available" -> MaterialTheme.colorScheme.primary
                        statusText == "On server" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // File management actions
            if (asset != null) {
                if (asset.localPath != null) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, "Re-download",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, "Delete local copy",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Download, "Download",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (isActive) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Active source",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MediaAssetRow(
    icon: ImageVector,
    label: String,
    isCached: Boolean,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (isCached) "Downloaded" else "Not downloaded",
            style = MaterialTheme.typography.labelSmall,
            color = if (isCached) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        if (isCached) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Refresh, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        } else {
            IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Download, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DetailsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun DetailsRow(
    icon: ImageVector,
    label: String,
    value: String?,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatEpochMillis(epochMillis: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${localDate.year}-${localDate.monthNumber.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        "Unknown"
    }
}
