package com.karakept.app.ui.screens.viewer

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import getPlatform
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity

@Composable
internal fun BookmarkFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    bookmark: BookmarkEntity,
    onFavoriteClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onReadClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenInBrowserClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action FABs
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FabMenuItem(
                    icon = if (bookmark.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (bookmark.isStarred) "Unfavorite" else "Favorite",
                    containerColor = if (bookmark.isStarred) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (bookmark.isStarred) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    onClick = onFavoriteClick
                )

                FabMenuItem(
                    icon = if (bookmark.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    label = if (bookmark.isArchived) "Unarchive" else "Archive",
                    containerColor = if (bookmark.isArchived) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (bookmark.isArchived) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    onClick = onArchiveClick
                )

                FabMenuItem(
                    icon = if (bookmark.isRead) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    label = if (bookmark.isRead) "Mark unread" else "Mark read",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onReadClick
                )

                FabMenuItem(
                    icon = if (getPlatform().isDesktop) Icons.Default.Link else Icons.Default.Share,
                    label = if (getPlatform().isDesktop) "Copy Link" else "Share",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onShareClick
                )

                FabMenuItem(
                    icon = Icons.Default.OpenInBrowser,
                    label = "Open",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onOpenInBrowserClick
                )
            }
        }

        // Main FAB with rotation animation
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) }
        ) {
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )

            Box(
                modifier = Modifier.graphicsLayer {
                    rotationZ = rotation
                }
            ) {
                Crossfade(
                    targetState = expanded,
                    animationSpec = tween(200)
                ) { isExpanded ->
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Bookmark,
                        contentDescription = if (isExpanded) "Close menu" else "Bookmark actions"
                    )
                }
            }
        }
    }
}

@Composable
private fun FabMenuItem(
    icon: ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 12.dp)
        )
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label
            )
        }
    }
}
