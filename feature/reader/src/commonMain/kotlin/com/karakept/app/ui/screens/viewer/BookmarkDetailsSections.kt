package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.BookmarkAsset
import com.karakept.app.data.model.ContentSource
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.domain.action.AiAction
import com.karakept.app.domain.action.ServerCrawlAction
import com.karakept.app.ui.components.tags.BookmarkTagsDisplay
import com.karakept.app.ui.icons.AppIcons

@Composable
internal fun DetailsGeneralSection(
    bookmark: Bookmark,
    onOpenLink: () -> Unit,
    onLinkCopied: () -> Unit
) {
    DetailsSectionTitle("General")
    LinkDetailsRow(
        url = bookmark.url,
        onOpen = onOpenLink,
        onCopy = onLinkCopied
    )
    DetailsRow(
        icon = AppIcons.Default.CalendarToday,
        label = "Created",
        value = formatEpochMillis(bookmark.createdAt)
    )
    if (bookmark.readingTimeMinutes > 0) {
        DetailsRow(
            icon = AppIcons.Default.AccessTime,
            label = "Reading time",
            value = "${bookmark.readingTimeMinutes} min"
        )
    }
}

@Composable
internal fun DetailsStatusSection(bookmark: Bookmark) {
    DetailsSectionTitle("Status")
    DetailsRow(
        icon = AppIcons.Default.Star,
        label = "Favourited",
        value = if (bookmark.isStarred) "Yes" else "No",
        valueColor = if (bookmark.isStarred) MaterialTheme.colorScheme.primary else null
    )
    DetailsRow(
        icon = AppIcons.Default.Archive,
        label = "Archived",
        value = if (bookmark.isArchived) "Yes" else "No"
    )
    DetailsRow(
        icon = AppIcons.AutoMirrored.Filled.MenuBook,
        label = "Read",
        value = if (bookmark.isRead) "Yes" else "No"
    )
    if (bookmark.crawlStatus != null || bookmark.crawledAt != null) {
        DetailsRow(
            icon = AppIcons.Default.Sync,
            label = "Crawled",
            value = crawlSummary(bookmark.crawlStatus, bookmark.crawledAt),
            valueColor = if (bookmark.crawlStatus == "failure") {
                MaterialTheme.colorScheme.error
            } else null
        )
    }
}

@Composable
internal fun DetailsTagsSection(tags: String) {
    if (tags.isNotBlank()) {
        Spacer(modifier = Modifier.height(16.dp))
        DetailsSectionTitle("Tags")
        BookmarkTagsDisplay(
            tags = tags,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
internal fun DetailsContentSourcesSection(
    bookmark: Bookmark,
    assets: List<BookmarkAsset>,
    selectedSource: ContentSource,
    serverActionsEnabled: Boolean,
    assetDownloads: Map<String, Float?>,
    onSourceSelected: (ContentSource) -> Unit,
    onDownloadAsset: (BookmarkAsset) -> Unit,
    onDownloadAndUseAsset: (BookmarkAsset) -> Unit,
    onRefreshAsset: (BookmarkAsset) -> Unit,
    onOpenAssetExternally: (BookmarkAsset) -> Unit,
    onDeleteAssetLocal: (BookmarkAsset) -> Unit,
    onRequestServerDelete: (BookmarkAsset) -> Unit
) {
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
        icon = AppIcons.AutoMirrored.Filled.ChromeReaderMode,
        label = "Extracted content",
        statusText = if (hasExtractedContent) "Available" else "Not available",
        isActive = selectedSource == ContentSource.EXTRACTED,
        canActivate = hasExtractedContent && selectedSource != ContentSource.EXTRACTED,
        onSelect = { onSourceSelected(ContentSource.EXTRACTED) }
    )

    if (fullPageArchiveAsset != null) {
        ContentSourceRow(
            icon = AppIcons.Default.Web,
            label = "Full page archive",
            statusText = if (fullPageArchiveAsset.localPath != null) "Downloaded" else "On server",
            isActive = activeArchiveAsset?.id == fullPageArchiveAsset.id,
            canActivate = fullPageArchiveAsset.localPath != null
                && selectedSource != ContentSource.FULL_PAGE_ARCHIVE,
            asset = fullPageArchiveAsset,
            canDeleteOnServer = serverActionsEnabled,
            downloadProgress = downloadProgressFor(assetDownloads, fullPageArchiveAsset),
            onSelect = { onSourceSelected(ContentSource.FULL_PAGE_ARCHIVE) },
            onDownload = { onDownloadAsset(fullPageArchiveAsset) },
            onDownloadAndUse = { onDownloadAndUseAsset(fullPageArchiveAsset) },
            onRefresh = { onRefreshAsset(fullPageArchiveAsset) },
            onOpenExternally = { onOpenAssetExternally(fullPageArchiveAsset) },
            onDelete = { onDeleteAssetLocal(fullPageArchiveAsset) },
            onDeleteOnServer = { onRequestServerDelete(fullPageArchiveAsset) }
        )
    }

    if (precrawledArchiveAsset != null) {
        ContentSourceRow(
            icon = AppIcons.Default.Archive,
            label = "Crawled archive",
            statusText = if (precrawledArchiveAsset.localPath != null) "Downloaded" else "On server",
            isActive = activeArchiveAsset?.id == precrawledArchiveAsset.id,
            canActivate = precrawledArchiveAsset.localPath != null
                && selectedSource != ContentSource.FULL_PAGE_ARCHIVE,
            asset = precrawledArchiveAsset,
            canDeleteOnServer = serverActionsEnabled,
            downloadProgress = downloadProgressFor(assetDownloads, precrawledArchiveAsset),
            onSelect = { onSourceSelected(ContentSource.FULL_PAGE_ARCHIVE) },
            onDownload = { onDownloadAsset(precrawledArchiveAsset) },
            onDownloadAndUse = { onDownloadAndUseAsset(precrawledArchiveAsset) },
            onRefresh = { onRefreshAsset(precrawledArchiveAsset) },
            onOpenExternally = { onOpenAssetExternally(precrawledArchiveAsset) },
            onDelete = { onDeleteAssetLocal(precrawledArchiveAsset) },
            onDeleteOnServer = { onRequestServerDelete(precrawledArchiveAsset) }
        )
    }

    // The app has no PDF renderer, so a downloaded PDF is handed to
    // whatever reader the platform has rather than shown inline.
    assets.find { it.assetType == "pdf" }?.let { pdfAsset ->
        ContentSourceRow(
            icon = AppIcons.Default.PictureAsPdf,
            label = "PDF",
            statusText = if (pdfAsset.localPath != null) "Downloaded" else "On server",
            isActive = false,
            canActivate = false,
            asset = pdfAsset,
            canBeContentSource = false,
            canOpenExternally = true,
            canDeleteOnServer = serverActionsEnabled,
            downloadProgress = downloadProgressFor(assetDownloads, pdfAsset),
            onDownload = { onDownloadAsset(pdfAsset) },
            onDownloadAndUse = { onDownloadAndUseAsset(pdfAsset) },
            onRefresh = { onRefreshAsset(pdfAsset) },
            onOpenExternally = { onOpenAssetExternally(pdfAsset) },
            onDelete = { onDeleteAssetLocal(pdfAsset) },
            onDeleteOnServer = { onRequestServerDelete(pdfAsset) }
        )
    }
}

@Composable
internal fun DetailsMediaSection(
    assets: List<BookmarkAsset>,
    serverActionsEnabled: Boolean,
    assetDownloads: Map<String, Float?>,
    onDownloadAsset: (BookmarkAsset) -> Unit,
    onRefreshAsset: (BookmarkAsset) -> Unit,
    onOpenAssetExternally: (BookmarkAsset) -> Unit,
    onDeleteAssetLocal: (BookmarkAsset) -> Unit,
    onRequestServerDelete: (BookmarkAsset) -> Unit
) {
    val bannerAsset = assets.find { it.assetType == "bannerImage" }
    val screenshotAsset = assets.find { it.assetType == "screenshot" }
    if (bannerAsset != null || screenshotAsset != null) {
        Spacer(modifier = Modifier.height(16.dp))
        DetailsSectionTitle("Media")
        bannerAsset?.let { asset ->
            MediaAssetRow(
                icon = AppIcons.Default.Image,
                label = "Banner image",
                isCached = asset.localPath != null,
                canDeleteOnServer = serverActionsEnabled,
                downloadProgress = downloadProgressFor(assetDownloads, asset),
                onDownload = { onDownloadAsset(asset) },
                onRefresh = { onRefreshAsset(asset) },
                onOpenExternally = { onOpenAssetExternally(asset) },
                onDelete = { onDeleteAssetLocal(asset) },
                onDeleteOnServer = { onRequestServerDelete(asset) }
            )
        }
        screenshotAsset?.let { asset ->
            MediaAssetRow(
                icon = AppIcons.Default.PhotoCamera,
                label = "Screenshot",
                isCached = asset.localPath != null,
                canDeleteOnServer = serverActionsEnabled,
                downloadProgress = downloadProgressFor(assetDownloads, asset),
                onDownload = { onDownloadAsset(asset) },
                onRefresh = { onRefreshAsset(asset) },
                onOpenExternally = { onOpenAssetExternally(asset) },
                onDelete = { onDeleteAssetLocal(asset) },
                onDeleteOnServer = { onRequestServerDelete(asset) }
            )
        }
    }
}

@Composable
internal fun DetailsServerActionsSection(
    serverActionsEnabled: Boolean,
    serverCrawlInFlight: ServerCrawlAction?,
    onRequestServerCrawl: (ServerCrawlAction) -> Unit
) {
    DetailsSectionTitle("Server actions")
    ServerActionRow(
        icon = AppIcons.Default.Refresh,
        label = "Refresh",
        supportingText = "Re-crawl metadata and content",
        enabled = serverActionsEnabled && serverCrawlInFlight == null,
        inFlight = serverCrawlInFlight == ServerCrawlAction.REFRESH,
        onClick = { onRequestServerCrawl(ServerCrawlAction.REFRESH) }
    )
    ServerActionRow(
        icon = AppIcons.Default.Save,
        label = "Preserve offline archive",
        supportingText = "Ask the server to store a full page archive",
        enabled = serverActionsEnabled && serverCrawlInFlight == null,
        inFlight = serverCrawlInFlight == ServerCrawlAction.PRESERVE_ARCHIVE,
        onClick = { onRequestServerCrawl(ServerCrawlAction.PRESERVE_ARCHIVE) }
    )
    ServerActionRow(
        icon = AppIcons.Default.PictureAsPdf,
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

@Composable
internal fun DetailsAiSection(
    aiCapabilities: AiCapabilities,
    serverActionsEnabled: Boolean,
    aiActionInFlight: AiAction?,
    onRunAiAction: (AiAction) -> Unit
) {
    if (aiCapabilities.canSummarize || aiCapabilities.isAdmin) {
        Spacer(modifier = Modifier.height(16.dp))
        DetailsSectionTitle("AI")
        if (aiCapabilities.canSummarize) {
            ServerActionRow(
                icon = AppIcons.Default.AutoAwesome,
                label = AiAction.SUMMARIZE.label,
                supportingText = "Ask the server's model to summarize this article",
                enabled = serverActionsEnabled && aiActionInFlight == null,
                inFlight = aiActionInFlight == AiAction.SUMMARIZE,
                onClick = { onRunAiAction(AiAction.SUMMARIZE) }
            )
        }
        if (aiCapabilities.isAdmin) {
            ServerActionRow(
                icon = AppIcons.Default.NewLabel,
                label = AiAction.RETAG.label,
                supportingText = "Regenerate this bookmark's tags with AI",
                enabled = serverActionsEnabled && aiActionInFlight == null,
                inFlight = aiActionInFlight == AiAction.RETAG,
                onClick = { onRunAiAction(AiAction.RETAG) }
            )
        }
    }
}
