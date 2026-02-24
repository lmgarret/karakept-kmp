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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
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
import com.karakept.app.utils.FaviconUtils

/**
 * Top bar with back button, sticky title, and dropdown menu
 */
@Composable
internal fun ViewerTopBar(
    title: String,
    url: String,
    showStickyTitle: Boolean,
    showMenu: Boolean,
    toolbarHeight: Dp,
    readingProgress: Float,
    onBackClick: () -> Unit,
    onMenuToggle: (Boolean) -> Unit,
    onAppearanceClick: () -> Unit,
    onViewerModeClick: () -> Unit,
    onMoveToListClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onDeleteClick: () -> Unit
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
        androidx.compose.animation.AnimatedVisibility(
            visible = showStickyTitle,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp)
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

        // Menu button
        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
            IconButton(onClick = { onMenuToggle(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = if (showStickyTitle) MaterialTheme.colorScheme.onSurface else Color.White
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onMenuToggle(false) }
            ) {
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
        }
    }

    // Reading progress bar - thin accent-colored line below the toolbar
    LinearProgressIndicator(
        progress = { readingProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.Transparent
    )
    } // end Column
}
