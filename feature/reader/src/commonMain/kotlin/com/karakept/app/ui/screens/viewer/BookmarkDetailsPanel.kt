package com.karakept.app.ui.screens.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.BookmarkAsset
import com.karakept.app.data.model.ContentSource
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.domain.action.AiAction
import com.karakept.app.domain.action.ServerCrawlAction
import com.karakept.app.ui.components.AnimatedVisibilityOrPlain
import com.karakept.app.ui.components.einkModalBorder
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.theme.LocalEinkMode

/**
 * Details panel that slides in from the right, showing all bookmark metadata,
 * content source selection, and asset management.
 */
@Composable
fun BookmarkDetailsPanel(
    visible: Boolean,
    bookmark: Bookmark?,
    assets: List<BookmarkAsset> = emptyList(),
    selectedSource: ContentSource = ContentSource.EXTRACTED,
    serverCrawlInFlight: ServerCrawlAction? = null,
    aiActionInFlight: AiAction? = null,
    aiCapabilities: AiCapabilities = AiCapabilities(),
    serverActionsEnabled: Boolean = true,
    // Asset id -> download fraction (null fraction = size unknown). Absent = not downloading.
    assetDownloads: Map<String, Float?> = emptyMap(),
    onSourceSelected: (ContentSource) -> Unit = {},
    onDownloadAsset: (BookmarkAsset) -> Unit = {},
    // Tap on the row: download and then use it (select as source / open externally).
    onDownloadAndUseAsset: (BookmarkAsset) -> Unit = {},
    onRefreshAsset: (BookmarkAsset) -> Unit = {},
    onOpenAssetExternally: (BookmarkAsset) -> Unit = {},
    onDeleteAssetLocal: (BookmarkAsset) -> Unit = {},
    onDeleteAssetOnServer: (BookmarkAsset) -> Unit = {},
    onRequestServerCrawl: (ServerCrawlAction) -> Unit = {},
    onRunAiAction: (AiAction) -> Unit = {},
    onLinkCopied: () -> Unit = {},
    onOpenLink: () -> Unit = {},
    onDismiss: () -> Unit
) {
    // Confirmation gate for the irreversible server-side delete
    var assetPendingServerDelete by remember { mutableStateOf<BookmarkAsset?>(null) }
    val einkMode = LocalEinkMode.current
    val requestServerDelete: (BookmarkAsset) -> Unit = { assetPendingServerDelete = it }

    // Scrim
    DetailsPanelScrim(
        visible = visible,
        animated = !einkMode.animationsDisabled,
        onDismiss = onDismiss
    )

    // Panel sliding from the right
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = if (einkMode.animationsDisabled) EnterTransition.None
                    else slideInHorizontally(initialOffsetX = { it }),
            exit = if (einkMode.animationsDisabled) ExitTransition.None
                   else slideOutHorizontally(targetOffsetX = { it })
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                // The shadow is the panel's only edge against the article behind it, and it does
                // not render on e-ink.
                shadowElevation = if (einkMode.highContrast) 0.dp else 8.dp,
                border = if (einkMode.highContrast) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                } else null,
                color = MaterialTheme.colorScheme.surface
            ) {
                if (bookmark != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        DetailsPanelHeader(onDismiss = onDismiss)

                        HorizontalDivider()

                        // Scrollable content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            DetailsGeneralSection(
                                bookmark = bookmark,
                                onOpenLink = onOpenLink,
                                onLinkCopied = onLinkCopied
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            DetailsStatusSection(bookmark)

                            DetailsTagsSection(bookmark.tags)

                            Spacer(modifier = Modifier.height(16.dp))

                            DetailsContentSourcesSection(
                                bookmark = bookmark,
                                assets = assets,
                                selectedSource = selectedSource,
                                serverActionsEnabled = serverActionsEnabled,
                                assetDownloads = assetDownloads,
                                onSourceSelected = onSourceSelected,
                                onDownloadAsset = onDownloadAsset,
                                onDownloadAndUseAsset = onDownloadAndUseAsset,
                                onRefreshAsset = onRefreshAsset,
                                onOpenAssetExternally = onOpenAssetExternally,
                                onDeleteAssetLocal = onDeleteAssetLocal,
                                onRequestServerDelete = requestServerDelete
                            )

                            DetailsMediaSection(
                                assets = assets,
                                serverActionsEnabled = serverActionsEnabled,
                                assetDownloads = assetDownloads,
                                onDownloadAsset = onDownloadAsset,
                                onRefreshAsset = onRefreshAsset,
                                onOpenAssetExternally = onOpenAssetExternally,
                                onDeleteAssetLocal = onDeleteAssetLocal,
                                onRequestServerDelete = requestServerDelete
                            )

                            // Server actions — sits after the "what exists" sections so the
                            // user reads the current state before asking for more.
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailsServerActionsSection(
                                serverActionsEnabled = serverActionsEnabled,
                                serverCrawlInFlight = serverCrawlInFlight,
                                onRequestServerCrawl = onRequestServerCrawl
                            )

                            // AI actions. Each is hidden unless this server accepts it —
                            // summarizing needs an inference client, re-tagging an admin key.
                            DetailsAiSection(
                                aiCapabilities = aiCapabilities,
                                serverActionsEnabled = serverActionsEnabled,
                                aiActionInFlight = aiActionInFlight,
                                onRunAiAction = onRunAiAction
                            )
                        }
                    }
                }
            }
        }
    }

    assetPendingServerDelete?.let { asset ->
        ServerDeleteConfirmDialog(
            asset = asset,
            onConfirm = {
                onDeleteAssetOnServer(asset)
                assetPendingServerDelete = null
            },
            onDismiss = { assetPendingServerDelete = null }
        )
    }
}

@Composable
private fun DetailsPanelScrim(
    visible: Boolean,
    animated: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibilityOrPlain(
        visible = visible,
        animated = animated
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
}

@Composable
private fun DetailsPanelHeader(onDismiss: () -> Unit) {
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
            Icon(AppIcons.Default.Close, contentDescription = "Close")
        }
    }
}

@Composable
private fun ServerDeleteConfirmDialog(
    asset: BookmarkAsset,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = einkModalBorder(AlertDialogDefaults.shape),
        title = { Text("Delete from server?") },
        text = {
            Text(
                "${serverAssetLabel(asset.assetType)} will be permanently deleted from " +
                    "your Karakeep server. This can't be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
