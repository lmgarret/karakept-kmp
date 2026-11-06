package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.BookmarkAsset
import com.karakept.app.ui.components.InlineLoadingDots
import com.karakept.app.ui.components.einkOutlineBorder
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.theme.LocalEinkMode

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
internal fun AssetOverflowMenu(
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
                AppIcons.Default.MoreVert,
                contentDescription = "Asset actions",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            border = einkOutlineBorder()
        ) {
            if (hasLocalCopy) {
                if (canOpenExternally) {
                    AssetMenuItem(AppIcons.AutoMirrored.Filled.OpenInNew, "Open") {
                        expanded = false
                        onOpenExternally()
                    }
                }
                AssetMenuItem(AppIcons.Default.Refresh, "Re-download") {
                    expanded = false
                    onRefresh()
                }
                AssetMenuItem(AppIcons.Default.Delete, "Delete local copy") {
                    expanded = false
                    onDeleteLocal()
                }
            } else {
                AssetMenuItem(AppIcons.Default.Download, "Download a copy") {
                    expanded = false
                    onDownload()
                }
            }
            if (canDeleteOnServer) {
                HorizontalDivider()
                AssetMenuItem(
                    icon = AppIcons.Default.DeleteForever,
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
internal fun AssetMenuItem(
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
internal fun ContentSourceRow(
    icon: ImageVector,
    label: String,
    statusText: String,
    isActive: Boolean,
    canActivate: Boolean,
    asset: BookmarkAsset? = null,
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
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 1.dp else 0.dp),
        border = einkOutlineBorder()
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
                    imageVector = AppIcons.Default.CheckCircle,
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
internal data class DownloadProgress(val fraction: Float?)

internal fun downloadProgressFor(assetDownloads: Map<String, Float?>, asset: BookmarkAsset): DownloadProgress? =
    if (assetDownloads.containsKey(asset.id)) DownloadProgress(assetDownloads[asset.id]) else null

@Composable
internal fun DownloadProgressBar(
    progress: DownloadProgress?,
    modifier: Modifier = Modifier
) {
    val fraction = progress?.fraction
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = modifier.height(4.dp)
        )
    } else if (LocalEinkMode.current.animationsDisabled) {
        // No Content-Length, so the bar would have to be indeterminate — which never stops
        // sweeping. Dots step one repaint at a time instead.
        InlineLoadingDots(dotSize = 4.dp, modifier = modifier)
    } else {
        // No Content-Length on the response, so the fraction is unknowable.
        LinearProgressIndicator(modifier = modifier.height(4.dp))
    }
}

@Composable
internal fun MediaAssetRow(
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
