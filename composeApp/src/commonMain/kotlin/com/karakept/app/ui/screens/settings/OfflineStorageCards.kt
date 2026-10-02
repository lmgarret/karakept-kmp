package com.karakept.app.ui.screens.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.repository.OfflineCleanupEstimate
import com.karakept.app.data.repository.OfflineStorageUsage
import com.karakept.app.domain.OfflineRetention
import com.karakept.app.ui.components.LoadingDotsIndicator
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.utils.formatFileSize

/** One coloured part of the storage bar, and its legend row. */
private data class StorageSegment(val label: String, val bytes: Long, val color: Color)

// A null usage still names every part, so the skeleton's legend is the one the card will show.
@Composable
private fun storageSegments(usage: OfflineStorageUsage?): List<StorageSegment> {
    val primary = MaterialTheme.colorScheme.primary
    return listOf(
        StorageSegment("Articles", usage?.articleBytes ?: 0L, primary),
        StorageSegment("Images", usage?.imageBytes ?: 0L, primary.copy(alpha = 0.66f)),
        StorageSegment("Archives & PDFs", usage?.fileBytes ?: 0L, primary.copy(alpha = 0.36f)),
        StorageSegment("Thumbnail cache", usage?.thumbnailCacheBytes ?: 0L, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        StorageSegment("App & other data", usage?.otherAppBytes ?: 0L, MaterialTheme.colorScheme.outline)
    )
}

/**
 * What Karakept takes and what of it is offline copies. The bar is scaled to the app, or to the
 * storage limit when one is set — never to the device, where the app would be a dot against tens
 * of gigabytes. The device's free space is a line of text instead.
 */
@Composable
internal fun StorageOverviewCard(
    usage: OfflineStorageUsage?,
    storageCapMb: Int?,
    isWorking: Boolean,
    onClear: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (usage == null) {
                StorageOverviewSkeleton()
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatFileSize(usage.appBytes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "used by Karakept",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${formatFileSize(usage.freeBytes)} free\non this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }

            val segments = storageSegments(usage)
            val capBytes = storageCapMb?.let { it * OfflineRetention.MEGABYTE }
            StorageBar(
                segments = segments,
                scaleBytes = maxOf(usage.appBytes, capBytes ?: 0L)
            )
            if (storageCapMb != null) {
                Text(
                    text = "Limit ${offlineStorageCapLabel(storageCapMb)} · offline copies ${formatFileSize(usage.offlineBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.forEach { segment -> StorageLegendRow(segment) }
            }

            Text(
                text = offlineBookmarkCount(usage.bookmarkCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onClear,
                enabled = !isWorking && usage.offlineBytes > 0
            ) {
                Text("Clear offline copies")
            }
        }
    }
}

/**
 * The overview while the first measurement runs, laid out as the loaded card is so nothing moves
 * when the numbers land. Only the numbers are placeholders: the legend's labels and colours do not
 * depend on the measurement.
 */
@Composable
private fun StorageOverviewSkeleton() {
    if (LocalEinkMode.current.animationsDisabled) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            LoadingDotsIndicator(label = "Measuring…")
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "storage_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "storage_skeleton_alpha"
    )
    val placeholder = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f)
    val typography = MaterialTheme.typography

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = "Measuring storage" }
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                SkeletonText(typography.headlineMedium, width = 112.dp, color = placeholder)
                Text(
                    text = "used by Karakept",
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                SkeletonText(typography.bodySmall, width = 64.dp, color = placeholder)
                Text(
                    text = "on this device",
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        StorageBar(segments = emptyList(), scaleBytes = 0L)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            storageSegments(null).forEach { segment ->
                StorageLegendRow(segment) {
                    SkeletonText(typography.bodyMedium, width = 48.dp, color = placeholder)
                }
            }
        }

        SkeletonText(typography.bodySmall, width = 160.dp, color = placeholder)
        Button(onClick = {}, enabled = false) {
            Text("Clear offline copies")
        }
    }
}

/** A bar standing in for one line of [style] text: the line's height, with its leading left clear. */
@Composable
private fun SkeletonText(style: TextStyle, width: Dp, color: Color) {
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Box(
        modifier = Modifier.width(width).height(lineHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(lineHeight * 0.6f)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

@Composable
private fun StorageBar(segments: List<StorageSegment>, scaleBytes: Long) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val highContrast = LocalEinkMode.current.highContrast
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(50)
    val description = segments.joinToString { "${it.label} ${formatFileSize(it.bytes)}" }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(shape)
            .background(track)
            .then(if (highContrast) Modifier.border(1.dp, outline, shape) else Modifier)
            .semantics { contentDescription = description }
    ) {
        if (scaleBytes <= 0) return@Canvas
        // A gap keeps neighbouring parts apart where their colours are close — and on e-ink,
        // where they are all the same ink.
        val gap = 2.dp.toPx()
        val minWidth = 3.dp.toPx()
        var x = 0f
        for (segment in segments) {
            if (segment.bytes <= 0) continue
            val width = maxOf(minWidth, size.width * segment.bytes / scaleBytes)
            if (x >= size.width) break
            drawRect(
                color = segment.color,
                topLeft = Offset(x, 0f),
                size = Size(minOf(width, size.width - x), size.height)
            )
            x += width + gap
        }
    }
}

@Composable
private fun StorageLegendRow(
    segment: StorageSegment,
    value: @Composable () -> Unit = {
        Text(
            text = formatFileSize(segment.bytes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(segment.color)
        )
        Spacer(modifier = Modifier.padding(start = 10.dp))
        Text(
            text = segment.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        value()
    }
}

@Composable
internal fun ClearOfflineCopiesDialog(
    usage: OfflineStorageUsage,
    syncStrategy: SyncStrategy,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear all offline copies?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This removes everything Karakept stored for offline reading:")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SizeRow("Articles", usage.articleBytes)
                    SizeRow("Images", usage.imageBytes)
                    SizeRow("Archives & PDFs", usage.fileBytes)
                    SizeRow("Total", usage.offlineBytes, emphasized = true)
                }
                Text(clearOfflineCacheWarning(syncStrategy))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Clear ${formatFileSize(usage.offlineBytes)}") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SizeRow(label: String, bytes: Long, emphasized: Boolean = false) {
    val weight = if (emphasized) FontWeight.SemiBold else null
    Row {
        Text(label, fontWeight = weight, modifier = Modifier.weight(1f))
        Text(formatFileSize(bytes), fontWeight = weight)
    }
}

/**
 * What "Clean up now" would free, part by part, beside the button that frees it. A part a
 * switched-off setting can never produce is left out rather than shown as nothing.
 */
@Composable
internal fun CleanupNowCard(
    estimate: OfflineCleanupEstimate?,
    retentionDays: Int?,
    storageCapMb: Int?,
    isWorking: Boolean,
    lastResult: String?,
    onCleanUp: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Can be freed now",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when {
                        estimate == null -> "…"
                        estimate.totalBytes == 0L -> "Nothing"
                        else -> "≈ ${formatFileSize(estimate.totalBytes)}"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (estimate != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (retentionDays != null) {
                        EstimateRow(
                            "${copies(estimate.retiredCopies, "read")} older than ${offlineRetentionPeriod(retentionDays)}",
                            estimate.retiredBytes
                        )
                    }
                    if (storageCapMb != null) {
                        EstimateRow(
                            "Over the ${offlineStorageCapLabel(storageCapMb)} limit" +
                                if (estimate.overLimitCopies > 0) " · ${copies(estimate.overLimitCopies)}" else "",
                            estimate.overLimitBytes
                        )
                    }
                    EstimateRow("Files no bookmark uses", estimate.unusedFileBytes)
                    EstimateRow("Database space to compact", estimate.reclaimableDatabaseBytes)
                    EstimateRow("Thumbnail cache, downloaded again as needed", estimate.thumbnailCacheBytes)
                }
            }
            Text(
                text = "Cleanup also runs on its own after syncs; unused files are swept at most every 6 hours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onCleanUp, enabled = !isWorking) {
                Text("Clean up now")
            }
            val status = if (isWorking) "Working…" else lastResult
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EstimateRow(label: String, bytes: Long) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (bytes > 0) formatFileSize(bytes) else "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun offlineBookmarkCount(count: Int): String =
    if (count == 1) "1 bookmark available offline" else "$count bookmarks available offline"

private fun copies(count: Int, adjective: String? = null): String {
    val noun = if (count == 1) "copy" else "copies"
    return listOfNotNull(count.toString(), adjective, noun).joinToString(" ")
}

internal fun clearOfflineCacheWarning(strategy: SyncStrategy): String {
    val base = "Bookmarks, reading progress and highlights stay."
    val next = when (strategy) {
        SyncStrategy.ALL -> " Your content sync mode stores every bookmark, so the next sync downloads them all again."
        SyncStrategy.PER_LIST -> " The next sync downloads the lists you sync for offline reading again."
        SyncStrategy.PER_BOOKMARK, SyncStrategy.NEVER -> " Lists set to sync offline are downloaded again on the next sync."
    }
    return base + next
}
