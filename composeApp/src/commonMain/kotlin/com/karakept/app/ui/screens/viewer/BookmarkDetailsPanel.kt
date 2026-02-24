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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Details panel that slides in from the right, showing all bookmark metadata:
 * date created, tags, archive status, screenshot availability, etc.
 */
@Composable
internal fun BookmarkDetailsPanel(
    visible: Boolean,
    bookmark: BookmarkEntity?,
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
                    .width(300.dp),
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
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
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
                            // Created date
                            DetailsSectionTitle("General")

                            val createdDate = formatEpochMillis(bookmark.createdAt)
                            DetailsRow(
                                icon = Icons.Default.CalendarToday,
                                label = "Created",
                                value = createdDate
                            )

                            // Reading time
                            if (bookmark.readingTimeMinutes > 0) {
                                DetailsRow(
                                    icon = Icons.Default.AccessTime,
                                    label = "Reading time",
                                    value = "${bookmark.readingTimeMinutes} min"
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Tags
                            val tags = bookmark.tags.split(",").filter { it.isNotBlank() }
                            if (tags.isNotEmpty()) {
                                DetailsSectionTitle("Tags")
                                tags.forEach { tag ->
                                    DetailsRow(
                                        icon = Icons.AutoMirrored.Filled.Label,
                                        label = tag,
                                        value = null
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

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

                            // Assets
                            DetailsSectionTitle("Assets")

                            DetailsRow(
                                icon = Icons.Default.Image,
                                label = "Banner image",
                                value = if (bookmark.bannerImageAssetId != null) "Available" else "None",
                                valueColor = if (bookmark.bannerImageAssetId != null) MaterialTheme.colorScheme.primary else null
                            )

                            DetailsRow(
                                icon = Icons.Default.PhotoCamera,
                                label = "Screenshot",
                                value = if (bookmark.screenshotAssetId != null) "Available" else "None",
                                valueColor = if (bookmark.screenshotAssetId != null) MaterialTheme.colorScheme.primary else null
                            )
                        }
                    }
                }
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
    valueColor: androidx.compose.ui.graphics.Color? = null
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
