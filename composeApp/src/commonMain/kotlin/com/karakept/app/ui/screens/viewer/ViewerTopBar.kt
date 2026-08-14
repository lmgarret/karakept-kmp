package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.karakept.app.data.local.entity.BookmarkEntity
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.karakept.app.ui.components.InlineLoadingDots
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.utils.FaviconUtils
import getPlatform

/**
 * Top bar with back button, sticky title, and dropdown menu
 */
/** Reading-progress strip under the toolbar. Part of the chrome that covers the article. */
internal val ReadingProgressBarHeight = 3.dp

@Composable
internal fun ViewerTopBar(
    title: String,
    url: String,
    showStickyTitle: Boolean,
    showMenu: Boolean,
    toolbarHeight: Dp,
    readingProgress: Float,
    isRefreshing: Boolean = false,
    onBackClick: () -> Unit,
    onMenuToggle: (Boolean) -> Unit,
    onAppearanceClick: () -> Unit,
    onViewerModeClick: () -> Unit = {},
    onMoveToListClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    // Desktop: FAB actions moved to top bar
    isDesktop: Boolean = false,
    bookmark: BookmarkEntity? = null,
    onFavoriteClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onReadClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onOpenInBrowserClick: () -> Unit = {},
    // Fullscreen toggle (desktop embedded only)
    isFullscreen: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null,
    onDetailsClick: (() -> Unit)? = null
) {
    // Status bar background - fades in with top bar for parallax effect
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(
                color = MaterialTheme.colorScheme.background.copy(
                    alpha = if (showStickyTitle) 1f else 0f
                )
            )
    )

    // Custom Top Bar (Overlay) + Reading Progress Bar
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
    // Custom Top Bar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(toolbarHeight)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(
                    alpha = if (showStickyTitle) 1f else 0f
                )
            )
    ) {
        // Back Button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (showStickyTitle) MaterialTheme.colorScheme.onSurface else Color.White
            )
        }

        // Sticky Title
        // On desktop, the right side has Fullscreen? + Favorite + Archive + MoreVert
        val endPadding = if (isDesktop) {
            if (onFullscreenToggle != null) 200.dp else 152.dp
        } else 48.dp
        androidx.compose.animation.AnimatedVisibility(
            visible = showStickyTitle,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.Center).padding(start = 48.dp, end = endPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (url.isNotEmpty()) {
                    AsyncImage(
                        model = FaviconUtils.getFaviconUrl(url),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.5f)),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Action buttons and menu
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fullscreen toggle (desktop embedded only)
            if (onFullscreenToggle != null) {
                val iconTint = if (showStickyTitle) MaterialTheme.colorScheme.onSurface else Color.White
                IconButton(onClick = onFullscreenToggle) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                        tint = iconTint
                    )
                }
            }

            // Desktop: inline Favorite and Archive buttons
            if (isDesktop && bookmark != null) {
                val iconTint = if (showStickyTitle) MaterialTheme.colorScheme.onSurface else Color.White
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (bookmark.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (bookmark.isStarred) "Unfavorite" else "Favorite",
                        tint = iconTint
                    )
                }
                IconButton(onClick = onArchiveClick) {
                    Icon(
                        imageVector = if (bookmark.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = if (bookmark.isArchived) "Unarchive" else "Archive",
                        tint = iconTint
                    )
                }
            }

            Box {
            IconButton(onClick = { onMenuToggle(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = if (showStickyTitle) MaterialTheme.colorScheme.onSurface else Color.White
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onMenuToggle(false) },
                shape = MaterialTheme.shapes.extraSmall
            ) {
                // Desktop: additional actions from FAB
                if (isDesktop && bookmark != null) {
                    DropdownMenuItem(
                        text = { Text(if (bookmark.isRead) "Mark Unread" else "Mark Read") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (bookmark.isRead) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onReadClick()
                            onMenuToggle(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy Link") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Link, contentDescription = null) },
                        onClick = {
                            onShareClick()
                            onMenuToggle(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open in Browser") },
                        leadingIcon = { Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null) },
                        onClick = {
                            onOpenInBrowserClick()
                            onMenuToggle(false)
                        }
                    )
                    HorizontalDivider()
                }

                // Details — always present in the overflow menu
                if (onDetailsClick != null) {
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null)
                        },
                        onClick = {
                            onDetailsClick()
                            onMenuToggle(false)
                        }
                    )
                    HorizontalDivider()
                }

                DropdownMenuItem(
                    text = { Text("Find in Article") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onSearchClick()
                        onMenuToggle(false)
                    }
                )

                DropdownMenuItem(
                    text = { Text("Reader Appearance") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onAppearanceClick()
                        onMenuToggle(false)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Viewer Mode") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onViewerModeClick()
                        onMenuToggle(false)
                    }
                )

                // Divider to separate sections
                HorizontalDivider()

                // Move to List
                DropdownMenuItem(
                    text = { Text("Move to List") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onMoveToListClick()
                        onMenuToggle(false)
                    }
                )

                // Edit Tags
                DropdownMenuItem(
                    text = { Text("Edit Tags") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onEditTagsClick()
                        onMenuToggle(false)
                    }
                )

                // Refresh — re-fetch bookmark, tags, lists, assets and inline images
                DropdownMenuItem(
                    text = { Text("Refresh") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onRefreshClick()
                        onMenuToggle(false)
                    }
                )

                // Delete (destructive action)
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        onDeleteClick()
                        onMenuToggle(false)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error
                    )
                )
            }
            } // end Box wrapping IconButton + DropdownMenu
        } // end Row wrapping action buttons
    }

    // Reading progress bar — shows indeterminate while refreshing
    if (isRefreshing && LocalEinkMode.current.animationsDisabled) {
        // The indeterminate bar sweeps forever, so on e-ink it reads as either a smear or a
        // frozen line. The dots step once per repaint and carry a label the bar cannot.
        InlineLoadingDots(
            label = "Refreshing…",
            dotSize = 5.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        )
    } else if (isRefreshing) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(ReadingProgressBarHeight),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
        )
    } else {
        LinearProgressIndicator(
            progress = { readingProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(ReadingProgressBarHeight),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
            drawStopIndicator = {}
        )
    }
    } // end Column
}
