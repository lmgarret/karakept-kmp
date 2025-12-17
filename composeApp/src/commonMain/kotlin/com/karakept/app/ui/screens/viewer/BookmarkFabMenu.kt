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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
        // Action FABs (5 actions - within M3 guidelines)
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favorite toggle - Extended FAB with text
                ExtendedFloatingActionButton(
                    onClick = onFavoriteClick,
                    containerColor = if (bookmark.isStarred) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (bookmark.isStarred) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ) {
                    Crossfade(
                        targetState = bookmark.isStarred,
                        animationSpec = tween(200)
                    ) { isStarred ->
                        Icon(
                            imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Crossfade(
                        targetState = bookmark.isStarred,
                        animationSpec = tween(200)
                    ) { isStarred ->
                        Text(if (isStarred) "Unfavorite" else "Favorite")
                    }
                }

                // Archive toggle - Extended FAB with text
                ExtendedFloatingActionButton(
                    onClick = onArchiveClick,
                    containerColor = if (bookmark.isArchived) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (bookmark.isArchived) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ) {
                    Crossfade(
                        targetState = bookmark.isArchived,
                        animationSpec = tween(200)
                    ) { isArchived ->
                        Icon(
                            imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Crossfade(
                        targetState = bookmark.isArchived,
                        animationSpec = tween(200)
                    ) { isArchived ->
                        Text(if (isArchived) "Unarchive" else "Archive")
                    }
                }

                // Read toggle - Extended FAB with text
                ExtendedFloatingActionButton(
                    onClick = onReadClick,
                    containerColor = if (bookmark.isRead) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (bookmark.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ) {
                    Crossfade(
                        targetState = bookmark.isRead,
                        animationSpec = tween(200)
                    ) { isRead ->
                        Icon(
                            imageVector = if (isRead) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Crossfade(
                        targetState = bookmark.isRead,
                        animationSpec = tween(200)
                    ) { isRead ->
                        Text(if (isRead) "Mark unread" else "Mark read")
                    }
                }

                // Share action - Extended FAB with text
                ExtendedFloatingActionButton(
                    onClick = onShareClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }

                // Open in browser action - Extended FAB with text
                ExtendedFloatingActionButton(
                    onClick = onOpenInBrowserClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open")
                }
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
