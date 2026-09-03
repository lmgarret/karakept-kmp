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
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.domain.action.AiAction
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
import com.karakept.app.ui.components.einkOutlineBorder
import com.karakept.app.ui.icons.AppIcons
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
    onDetailsClick: (() -> Unit)? = null,
    aiCapabilities: AiCapabilities = AiCapabilities(),
    aiActionInFlight: AiAction? = null,
    onRunAiAction: (AiAction) -> Unit = {}
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
                imageVector = AppIcons.AutoMirrored.Filled.ArrowBack,
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
                        imageVector = if (isFullscreen) AppIcons.Default.FullscreenExit else AppIcons.Default.Fullscreen,
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
                        imageVector = if (bookmark.isStarred) AppIcons.Default.Star else AppIcons.Default.StarBorder,
                        contentDescription = if (bookmark.isStarred) "Unfavorite" else "Favorite",
                        tint = iconTint
                    )
                }
                IconButton(onClick = onArchiveClick) {
                    Icon(
                        imageVector = if (bookmark.isArchived) AppIcons.Default.Unarchive else AppIcons.Default.Archive,
                        contentDescription = if (bookmark.isArchived) "Unarchive" else "Archive",
                        tint = iconTint
                    )
                }
            }

            Box {
            IconButton(onClick = { onMenuToggle(true) }) {
                Icon(
                    imageVector = AppIcons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = if (showStickyTitle) MaterialTheme.colorScheme.onSurface else Color.White
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onMenuToggle(false) },
                shape = MaterialTheme.shapes.extraSmall,
                border = einkOutlineBorder()
            ) {
                // Desktop: additional actions from FAB
                if (isDesktop && bookmark != null) {
                    DropdownMenuItem(
                        text = { Text(if (bookmark.isRead) "Mark Unread" else "Mark Read") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (bookmark.isRead) AppIcons.Default.VisibilityOff else AppIcons.Default.Visibility,
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
                        leadingIcon = { Icon(imageVector = AppIcons.Default.Link, contentDescription = null) },
                        onClick = {
                            onShareClick()
                            onMenuToggle(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open in Browser") },
                        leadingIcon = { Icon(imageVector = AppIcons.Default.OpenInBrowser, contentDescription = null) },
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
                            Icon(imageVector = AppIcons.Default.Info, contentDescription = null)
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
                            imageVector = AppIcons.Default.Search,
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
                            imageVector = AppIcons.Default.Palette,
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
                            imageVector = AppIcons.Default.Visibility,
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
                            imageVector = AppIcons.Default.FolderOpen,
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
                            imageVector = AppIcons.Default.Edit,
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
                            imageVector = AppIcons.Default.Refresh,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onRefreshClick()
                        onMenuToggle(false)
                    }
                )

                // AI actions, next to Refresh: all three ask the server to redo work on this
                // bookmark. Each is hidden unless the server accepts it — summarizing needs an
                // inference client, re-tagging an admin key.
                if (aiCapabilities.canSummarize || aiCapabilities.isAdmin) {
                    HorizontalDivider()
                    if (aiCapabilities.canSummarize) {
                        DropdownMenuItem(
                            text = { Text(AiAction.SUMMARIZE.label) },
                            leadingIcon = {
                                Icon(imageVector = AppIcons.Default.AutoAwesome, contentDescription = null)
                            },
                            enabled = aiActionInFlight == null,
                            onClick = {
                                onRunAiAction(AiAction.SUMMARIZE)
                                onMenuToggle(false)
                            }
                        )
                    }
                    if (aiCapabilities.isAdmin) {
                        DropdownMenuItem(
                            text = { Text(AiAction.RETAG.label) },
                            leadingIcon = {
                                Icon(imageVector = AppIcons.Default.NewLabel, contentDescription = null)
                            },
                            enabled = aiActionInFlight == null,
                            onClick = {
                                onRunAiAction(AiAction.RETAG)
                                onMenuToggle(false)
                            }
                        )
                    }
                }

                // Delete (destructive action)
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = {
                        Icon(
                            imageVector = AppIcons.Default.Delete,
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

    // Reading progress bar — shows indeterminate while refreshing.
    //
    // On e-ink the indeterminate bar is skipped entirely: it sweeps forever, and a 3 dp line at
    // the top edge is too easy to miss there anyway. The reader shows a FloatingBusyCard over
    // the article instead, so this slot keeps showing real reading progress throughout.
    if (isRefreshing && !LocalEinkMode.current.animationsDisabled) {
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
