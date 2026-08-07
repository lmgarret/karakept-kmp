package com.karakept.app.ui.screens.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.ContentSource
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.domain.action.ServerCrawlAction
import com.karakept.app.ui.components.BookmarkTagsDisplay
import com.karakept.app.utils.formatBookmarkDate
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
    serverCrawlInFlight: ServerCrawlAction? = null,
    serverActionsEnabled: Boolean = true,
    // Asset id -> download fraction (null fraction = size unknown). Absent = not downloading.
    assetDownloads: Map<String, Float?> = emptyMap(),
    onSourceSelected: (ContentSource) -> Unit = {},
    onDownloadAsset: (AssetEntity) -> Unit = {},
    // Tap on the row: download and then use it (select as source / open externally).
    onDownloadAndUseAsset: (AssetEntity) -> Unit = {},
    onRefreshAsset: (AssetEntity) -> Unit = {},
    onOpenAssetExternally: (AssetEntity) -> Unit = {},
    onDeleteAssetLocal: (AssetEntity) -> Unit = {},
    onDeleteAssetOnServer: (AssetEntity) -> Unit = {},
    onRequestServerCrawl: (ServerCrawlAction) -> Unit = {},
    onLinkCopied: () -> Unit = {},
    onDismiss: () -> Unit
) {
    fun progressFor(asset: AssetEntity): DownloadProgress? =
        if (assetDownloads.containsKey(asset.id)) DownloadProgress(assetDownloads[asset.id]) else null

    // Confirmation gate for the irreversible server-side delete
    var assetPendingServerDelete by remember { mutableStateOf<AssetEntity?>(null) }
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
                            LinkDetailsRow(
                                url = bookmark.url,
                                onCopy = onLinkCopied
                            )
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
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                label = "Read",
                                value = if (bookmark.isRead) "Yes" else "No"
                            )
                            if (bookmark.crawlStatus != null || bookmark.crawledAt != null) {
                                DetailsRow(
                                    icon = Icons.Default.Sync,
                                    label = "Crawled",
                                    value = crawlSummary(bookmark.crawlStatus, bookmark.crawledAt),
                                    valueColor = if (bookmark.crawlStatus == "failure") {
                                        MaterialTheme.colorScheme.error
                                    } else null
                                )
                            }

                            if (bookmark.tags.isNotBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                DetailsSectionTitle("Tags")
                                BookmarkTagsDisplay(
                                    tags = bookmark.tags,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

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
                                    canDeleteOnServer = serverActionsEnabled,
                                    downloadProgress = progressFor(fullPageArchiveAsset),
                                    onSelect = { onSourceSelected(ContentSource.FULL_PAGE_ARCHIVE) },
                                    onDownload = { onDownloadAsset(fullPageArchiveAsset) },
                                    onDownloadAndUse = { onDownloadAndUseAsset(fullPageArchiveAsset) },
                                    onRefresh = { onRefreshAsset(fullPageArchiveAsset) },
                                    onOpenExternally = { onOpenAssetExternally(fullPageArchiveAsset) },
                                    onDelete = { onDeleteAssetLocal(fullPageArchiveAsset) },
                                    onDeleteOnServer = { assetPendingServerDelete = fullPageArchiveAsset }
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
                                    canDeleteOnServer = serverActionsEnabled,
                                    downloadProgress = progressFor(precrawledArchiveAsset),
                                    onSelect = { onSourceSelected(ContentSource.FULL_PAGE_ARCHIVE) },
                                    onDownload = { onDownloadAsset(precrawledArchiveAsset) },
                                    onDownloadAndUse = { onDownloadAndUseAsset(precrawledArchiveAsset) },
                                    onRefresh = { onRefreshAsset(precrawledArchiveAsset) },
                                    onOpenExternally = { onOpenAssetExternally(precrawledArchiveAsset) },
                                    onDelete = { onDeleteAssetLocal(precrawledArchiveAsset) },
                                    onDeleteOnServer = { assetPendingServerDelete = precrawledArchiveAsset }
                                )
                            }

                            // The app has no PDF renderer, so a downloaded PDF is handed to
                            // whatever reader the platform has rather than shown inline.
                            assets.find { it.assetType == "pdf" }?.let { pdfAsset ->
                                ContentSourceRow(
                                    icon = Icons.Default.PictureAsPdf,
                                    label = "PDF",
                                    statusText = if (pdfAsset.localPath != null) "Downloaded" else "On server",
                                    isActive = false,
                                    canActivate = false,
                                    asset = pdfAsset,
                                    canBeContentSource = false,
                                    canOpenExternally = true,
                                    canDeleteOnServer = serverActionsEnabled,
                                    downloadProgress = progressFor(pdfAsset),
                                    onDownload = { onDownloadAsset(pdfAsset) },
                                    onDownloadAndUse = { onDownloadAndUseAsset(pdfAsset) },
                                    onRefresh = { onRefreshAsset(pdfAsset) },
                                    onOpenExternally = { onOpenAssetExternally(pdfAsset) },
                                    onDelete = { onDeleteAssetLocal(pdfAsset) },
                                    onDeleteOnServer = { assetPendingServerDelete = pdfAsset }
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
                                        canDeleteOnServer = serverActionsEnabled,
                                        downloadProgress = progressFor(asset),
                                        onDownload = { onDownloadAsset(asset) },
                                        onRefresh = { onRefreshAsset(asset) },
                                        onOpenExternally = { onOpenAssetExternally(asset) },
                                        onDelete = { onDeleteAssetLocal(asset) },
                                        onDeleteOnServer = { assetPendingServerDelete = asset }
                                    )
                                }
                                screenshotAsset?.let { asset ->
                                    MediaAssetRow(
                                        icon = Icons.Default.PhotoCamera,
                                        label = "Screenshot",
                                        isCached = asset.localPath != null,
                                        canDeleteOnServer = serverActionsEnabled,
                                        downloadProgress = progressFor(asset),
                                        onDownload = { onDownloadAsset(asset) },
                                        onRefresh = { onRefreshAsset(asset) },
                                        onOpenExternally = { onOpenAssetExternally(asset) },
                                        onDelete = { onDeleteAssetLocal(asset) },
                                        onDeleteOnServer = { assetPendingServerDelete = asset }
                                    )
                                }
                            }

                            // Server actions — sits after the "what exists" sections so the
                            // user reads the current state before asking for more.
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailsSectionTitle("Server actions")
                            ServerActionRow(
                                icon = Icons.Default.Refresh,
                                label = "Refresh",
                                supportingText = "Re-crawl metadata and content",
                                enabled = serverActionsEnabled && serverCrawlInFlight == null,
                                inFlight = serverCrawlInFlight == ServerCrawlAction.REFRESH,
                                onClick = { onRequestServerCrawl(ServerCrawlAction.REFRESH) }
                            )
                            ServerActionRow(
                                icon = Icons.Default.Save,
                                label = "Preserve offline archive",
                                supportingText = "Ask the server to store a full page archive",
                                enabled = serverActionsEnabled && serverCrawlInFlight == null,
                                inFlight = serverCrawlInFlight == ServerCrawlAction.PRESERVE_ARCHIVE,
                                onClick = { onRequestServerCrawl(ServerCrawlAction.PRESERVE_ARCHIVE) }
                            )
                            ServerActionRow(
                                icon = Icons.Default.PictureAsPdf,
                                label = "Preserve as PDF",
                                supportingText = "Ask the server to store a PDF",
                                enabled = serverActionsEnabled && serverCrawlInFlight == null,
                                inFlight = serverCrawlInFlight == ServerCrawlAction.PRESERVE_PDF,
                                onClick = { onRequestServerCrawl(ServerCrawlAction.PRESERVE_PDF) }
                            )
                            if (!serverActionsEnabled) {
                                Text(
                                    text = "Unavailable in offline mode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                        }
                    }
                }
            }
        }
    }

    assetPendingServerDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { assetPendingServerDelete = null },
            title = { Text("Delete from server?") },
            text = {
                Text(
                    "${serverAssetLabel(asset.assetType)} will be permanently deleted from " +
                        "your Karakeep server. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAssetOnServer(asset)
                        assetPendingServerDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { assetPendingServerDelete = null }) { Text("Cancel") }
            }
        )
    }
}

/** One-line summary of the server's crawl state, e.g. "Failed · 3d ago". */
private fun crawlSummary(crawlStatus: String?, crawledAt: Long?): String {
    val state = when (crawlStatus) {
        "success" -> "Success"
        "failure" -> "Failed"
        "pending" -> "Pending"
        null -> null
        else -> crawlStatus.replaceFirstChar { it.uppercase() }
    }
    val when_ = crawledAt?.let { formatBookmarkDate(it, DateDisplayMode.ELAPSED) }
    return listOfNotNull(state, when_).joinToString(" · ").ifEmpty { "Unknown" }
}

private fun serverAssetLabel(assetType: String): String = when (assetType) {
    "fullPageArchive" -> "The full page archive"
    "precrawledArchive" -> "The crawled archive"
    "pdf" -> "The PDF"
    "bannerImage" -> "The banner image"
    "screenshot" -> "The screenshot"
    else -> "This asset"
}

/**
 * A request the server will act on asynchronously — distinct from the local download/delete
 * buttons on the asset rows above.
 */
@Composable
private fun ServerActionRow(
    icon: ImageVector,
    label: String,
    supportingText: String,
    enabled: Boolean,
    inFlight: Boolean,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled || inFlight) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
        if (inFlight) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Every action for one asset, behind a single overflow button.
 *
 * The row previously carried up to three bare 15dp glyphs side by side — below the MD3
 * touch-target minimum and hard to tell apart. One menu keeps the row compact and gives
 * each action a text label.
 *
 * [hasLocalCopy] selects between the download and the re-download/delete pair;
 * [canOpenExternally] adds a hand-off to another app for formats with no in-app renderer.
 */
@Composable
private fun AssetOverflowMenu(
    hasLocalCopy: Boolean,
    canOpenExternally: Boolean,
    canDeleteOnServer: Boolean,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onOpenExternally: () -> Unit,
    onDeleteLocal: () -> Unit,
    onDeleteOnServer: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Asset actions",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (hasLocalCopy) {
                if (canOpenExternally) {
                    AssetMenuItem(Icons.AutoMirrored.Filled.OpenInNew, "Open") {
                        expanded = false
                        onOpenExternally()
                    }
                }
                AssetMenuItem(Icons.Default.Refresh, "Re-download") {
                    expanded = false
                    onRefresh()
                }
                AssetMenuItem(Icons.Default.Delete, "Delete local copy") {
                    expanded = false
                    onDeleteLocal()
                }
            } else {
                AssetMenuItem(Icons.Default.Download, "Download a copy") {
                    expanded = false
                    onDownload()
                }
            }
            if (canDeleteOnServer) {
                HorizontalDivider()
                AssetMenuItem(
                    icon = Icons.Default.DeleteForever,
                    label = "Delete from server",
                    tint = MaterialTheme.colorScheme.error
                ) {
                    expanded = false
                    onDeleteOnServer()
                }
            }
        }
    }
}

@Composable
private fun AssetMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        colors = tint?.let { MenuDefaults.itemColors(textColor = it) } ?: MenuDefaults.itemColors(),
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = onClick
    )
}

/**
 * One asset or content source as a compact card.
 *
 * Tapping the card does the obvious next thing: download it if there is no local copy yet,
 * otherwise make it the active content source. [downloadProgress] is non-null while a
 * download is running — 0f..1f when the size is known, null inside the wrapper for an
 * indeterminate bar.
 */
@Composable
private fun ContentSourceRow(
    icon: ImageVector,
    label: String,
    statusText: String,
    isActive: Boolean,
    canActivate: Boolean,
    asset: AssetEntity? = null,
    // False for assets that can't be shown in-app and are only opened elsewhere (PDFs).
    canBeContentSource: Boolean = true,
    canOpenExternally: Boolean = false,
    canDeleteOnServer: Boolean = false,
    downloadProgress: DownloadProgress? = null,
    onSelect: () -> Unit = {},
    onDownload: () -> Unit = {},
    onDownloadAndUse: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenExternally: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDeleteOnServer: (() -> Unit)? = null
) {
    val containerColor = if (isActive)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    val isDownloading = downloadProgress != null
    val hasLocalCopy = asset?.localPath != null
    // Tap = the obvious next step for this row's state.
    val rowAction: (() -> Unit)? = when {
        isDownloading -> null
        asset != null && !hasLocalCopy -> onDownloadAndUse
        canActivate && canBeContentSource -> onSelect
        hasLocalCopy && canOpenExternally -> onOpenExternally
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .then(rowAction?.let { Modifier.clickable(onClick = it) } ?: Modifier),
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
                        isDownloading -> MaterialTheme.colorScheme.tertiary
                        hasLocalCopy -> MaterialTheme.colorScheme.primary
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
                        text = if (isDownloading) "Downloading" else statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // The bar sits beside the status rather than replacing it, so the row
                    // keeps its shape and the label still says what is happening.
                    if (isDownloading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        DownloadProgressBar(downloadProgress, modifier = Modifier.weight(1f))
                    }
                }
            }
            // All file management lives in one menu so the row stays compact.
            if (asset != null && !isDownloading) {
                AssetOverflowMenu(
                    hasLocalCopy = hasLocalCopy,
                    canOpenExternally = canOpenExternally,
                    canDeleteOnServer = canDeleteOnServer && onDeleteOnServer != null,
                    onDownload = onDownload,
                    onRefresh = onRefresh,
                    onOpenExternally = onOpenExternally,
                    onDeleteLocal = onDelete,
                    onDeleteOnServer = { onDeleteOnServer?.invoke() }
                )
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

/**
 * Wrapper so "not downloading" and "downloading, size unknown" stay distinguishable — a bare
 * `Float?` would collapse both onto null.
 */
private data class DownloadProgress(val fraction: Float?)

@Composable
private fun DownloadProgressBar(
    progress: DownloadProgress?,
    modifier: Modifier = Modifier
) {
    val fraction = progress?.fraction
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = modifier.height(4.dp)
        )
    } else {
        // No Content-Length on the response, so the fraction is unknowable.
        LinearProgressIndicator(modifier = modifier.height(4.dp))
    }
}

@Composable
private fun MediaAssetRow(
    icon: ImageVector,
    label: String,
    isCached: Boolean,
    canDeleteOnServer: Boolean = false,
    downloadProgress: DownloadProgress? = null,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onOpenExternally: () -> Unit = {},
    onDelete: () -> Unit,
    onDeleteOnServer: (() -> Unit)? = null
) {
    val isDownloading = downloadProgress != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isDownloading && !isCached) Modifier.clickable(onClick = onDownload) else Modifier)
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
            text = when {
                isDownloading -> "Downloading"
                isCached -> "Downloaded"
                else -> "Not downloaded"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isCached) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isDownloading) {
            Spacer(modifier = Modifier.width(8.dp))
            DownloadProgressBar(downloadProgress, modifier = Modifier.width(64.dp))
        } else {
            Spacer(modifier = Modifier.width(4.dp))
            AssetOverflowMenu(
                hasLocalCopy = isCached,
                canOpenExternally = isCached,
                canDeleteOnServer = canDeleteOnServer && onDeleteOnServer != null,
                onDownload = onDownload,
                onRefresh = onRefresh,
                onOpenExternally = onOpenExternally,
                onDeleteLocal = onDelete,
                onDeleteOnServer = { onDeleteOnServer?.invoke() }
            )
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

/**
 * Shows the bookmark's full URL (never truncated) with a copy-to-clipboard action:
 * an explicit button for discoverability, plus long-press on the row itself.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkDetailsRow(url: String, onCopy: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val copyToClipboard = {
        clipboardManager.setText(AnnotatedString(url))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        onCopy()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = {}, onLongClick = copyToClipboard)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = copyToClipboard,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy link",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
