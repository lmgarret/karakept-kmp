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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.karakept.app.ui.icons.AppIcons
import getPlatform
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.ui.components.AnimatedVisibilityOrPlain
import com.karakept.app.ui.components.EinkAwareFab
import com.karakept.app.ui.components.borderStroke
import com.karakept.app.ui.components.floatingSurfaceStyle
import com.karakept.app.ui.theme.LocalEinkMode

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
    val einkMode = LocalEinkMode.current
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action FABs
        AnimatedVisibilityOrPlain(
            visible = expanded,
            animated = !einkMode.animationsDisabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FabMenuItem(
                    icon = if (bookmark.isStarred) AppIcons.Default.Star else AppIcons.Default.StarBorder,
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
                    icon = if (bookmark.isArchived) AppIcons.Default.Unarchive else AppIcons.Default.Archive,
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
                    icon = if (bookmark.isRead) AppIcons.Default.VisibilityOff else AppIcons.Default.Visibility,
                    label = if (bookmark.isRead) "Mark unread" else "Mark read",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onReadClick
                )

                FabMenuItem(
                    icon = if (getPlatform().isDesktop) AppIcons.Default.Link else AppIcons.Default.Share,
                    label = if (getPlatform().isDesktop) "Copy Link" else "Share",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onShareClick
                )

                FabMenuItem(
                    icon = AppIcons.Default.OpenInBrowser,
                    label = "Open",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onOpenInBrowserClick
                )
            }
        }

        // Main FAB
        EinkAwareFab(
            onClick = { onExpandedChange(!expanded) }
        ) {
            // The spring and the crossfade both spend a couple of hundred milliseconds part-way
            // between two icons, which an e-ink panel renders as a smear of the two overlaid.
            // The icon still swaps — it just swaps in one repaint.
            if (einkMode.animationsDisabled) {
                FabIcon(expanded)
                return@EinkAwareFab
            }

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
                    FabIcon(isExpanded)
                }
            }
        }
    }
}

@Composable
private fun FabIcon(expanded: Boolean) {
    Icon(
        imageVector = if (expanded) AppIcons.Default.Close else AppIcons.Default.Bookmark,
        contentDescription = if (expanded) "Close menu" else "Bookmark actions"
    )
}

@Composable
private fun FabMenuItem(
    icon: ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    // The container colours above are all page-coloured under high contrast — the label and the
    // icon carry the state instead, and the outline is what makes each row a button at all.
    val style = floatingSurfaceStyle(3.dp)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        border = style.borderStroke(),
        shadowElevation = style.shadowElevation,
        tonalElevation = style.shadowElevation
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(48.dp)
                .padding(start = 16.dp, end = 20.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
