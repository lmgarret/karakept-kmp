package com.karakept.app.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.karakept.app.data.cache.OfflineHeroImages
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.RowActionMode
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.repository.AiCapabilities
import com.karakept.app.ui.components.bookmark.BookmarkAction
import com.karakept.app.ui.components.bookmark.BookmarkCardLayout
import com.karakept.app.ui.components.bookmark.BookmarkContextMenu
import com.karakept.app.ui.components.bookmark.BookmarkListLayout
import com.karakept.app.ui.components.bookmark.BookmarkPlaceholderItem
import com.karakept.app.ui.components.bookmark.QuickActionBookmarkItem
import com.karakept.app.ui.components.bookmark.SwipeableBookmarkItem
import com.karakept.app.ui.components.bookmark.getEffectiveColor
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.utils.AssetUrlUtils
import com.karakept.app.utils.FileUtils

/** How one side's quick action presents itself on a given bookmark. */
internal data class RowSideAction(
    val action: SwipeAction,
    val config: CustomSwipeActionConfig?,
    /** Overrides the action's default icon; Mark Read flips with the bookmark's state. */
    val icon: ImageVector?,
    /** Whether a tag/list action is already in effect on this bookmark. */
    val isApplied: Boolean,
    /** Short label for custom actions: the tag or list name. */
    val label: String?
)

internal fun rowSideAction(
    action: SwipeAction,
    config: CustomSwipeActionConfig?,
    bookmark: Bookmark
): RowSideAction {
    val icon = if (action == SwipeAction.MARK_READ) {
        if (bookmark.isRead) AppIcons.Filled.VisibilityOff else AppIcons.Filled.Visibility
    } else null
    val isApplied = when (action) {
        SwipeAction.ADD_TAG -> config?.tagName?.let { it in bookmark.tags.splitCsv() } ?: false
        SwipeAction.ADD_TO_LIST -> config?.listId?.let { it in bookmark.listIds.splitCsv() } ?: false
        else -> false
    }
    val label = when (action) {
        SwipeAction.ADD_TAG -> config?.tagName
        SwipeAction.ADD_TO_LIST -> config?.listName
        else -> null
    }
    return RowSideAction(action, config, icon, isApplied, label)
}

private fun String.splitCsv(): List<String> = split(",").map { it.trim() }

/** The settings that shape every row's quick actions, the same for the whole list. */
internal data class RowActionSettings(
    val swipeLeftAction: SwipeAction,
    val swipeRightAction: SwipeAction,
    val swipeLeftConfig: CustomSwipeActionConfig?,
    val swipeRightConfig: CustomSwipeActionConfig?,
    val rowActionMode: RowActionMode,
)

/**
 * One loaded bookmark in the list: its quick-action wrapper, the desktop context menu and the
 * layout-specific row. A row still being created on the server renders as a placeholder.
 */
@Composable
internal fun BookmarkListRow(
    bookmark: Bookmark,
    display: MainScreenDisplayConfig,
    actions: RowActionSettings,
    showReadingProgress: Boolean,
    isPending: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isActive: Boolean,
    isDesktop: Boolean,
    offlineMode: Boolean,
    serverUrl: String?,
    rowWidth: Dp,
    fixedRowHeight: Dp?,
    descriptionFillHeight: Dp?,
    /** Where this row's context menu is open, or null when it is not. */
    contextMenuOffset: DpOffset?,
    contextMenuLists: List<com.karakept.api.model.KarakeepList>,
    contextMenuTags: List<String>,
    contextMenuAiCapabilities: AiCapabilities,
    onContextMenuAction: ((Bookmark, BookmarkAction) -> Unit)?,
    onDismissContextMenu: () -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkLongClick: (Bookmark) -> Unit,
    onBookmarkSelectionToggle: (Bookmark) -> Unit,
    onSwipeAction: (Bookmark, SwipeAction, CustomSwipeActionConfig?) -> Unit,
) {
    // Desktop context menu anchored at the right-click position. It is hosted in a zero-size
    // Box offset to the cursor so the DropdownMenu opens at the click point rather than below
    // the whole item.
    if (contextMenuOffset != null && onContextMenuAction != null) {
        Box(modifier = Modifier.offset(contextMenuOffset.x, contextMenuOffset.y).size(0.dp)) {
            BookmarkContextMenu(
                expanded = true,
                bookmark = bookmark,
                availableLists = contextMenuLists,
                availableTags = contextMenuTags,
                aiCapabilities = contextMenuAiCapabilities,
                onAction = { action -> onContextMenuAction(bookmark, action) },
                onDismiss = onDismissContextMenu
            )
        }
    }

    if (isPending) {
        BookmarkPlaceholderItem(
            url = bookmark.url,
            layoutType = display.layoutType,
            itemContainerStyle = display.itemContainerStyle,
            showThumbnail = display.showThumbnail,
            thumbnailSize = display.thumbnailSize,
            thumbnailSide = display.thumbnailSide,
            titlePosition = display.titlePosition,
            showRowDivider = display.showRowDivider
        )
        return
    }

    val left = rowSideAction(actions.swipeLeftAction, actions.swipeLeftConfig, bookmark)
    val right = rowSideAction(actions.swipeRightAction, actions.swipeRightConfig, bookmark)

    RowActionWrapper(
        bookmark = bookmark,
        left = left,
        right = right,
        // Selection mode owns taps and drags; quick actions would fight it.
        enabled = !isSelectionMode,
        useButtons = isDesktop || actions.rowActionMode == RowActionMode.BUTTONS,
        isDesktop = isDesktop,
        display = display,
        onSwipeAction = onSwipeAction
    ) {
        val bannerImageUrl = rowImageUrl(serverUrl, bookmark.bannerImageAssetId, OfflineHeroImages::bannerFileName)
        val screenshotUrl = rowImageUrl(serverUrl, bookmark.screenshotAssetId, OfflineHeroImages::screenshotFileName)
        val onClick = remember(bookmark.localId, isSelectionMode) {
            {
                if (isSelectionMode) onBookmarkSelectionToggle(bookmark)
                else onBookmarkClick(bookmark)
            }
        }
        // In selection mode, long press is handled by the list's drag-selection gesture.
        val onLongClick: (() -> Unit)? = remember(bookmark.localId, isSelectionMode) {
            if (isSelectionMode) null else { { onBookmarkLongClick(bookmark) } }
        }
        when (display.layoutType) {
            LayoutType.CARD -> BookmarkCardLayout(
                bookmark = bookmark,
                onClick = onClick,
                onLongClick = onLongClick,
                showReadingTime = display.showReadingTimeBadge,
                showReadingProgress = showReadingProgress,
                showTags = display.showTags,
                showDate = display.showDate,
                dateDisplayMode = display.dateDisplayMode,
                dimRead = display.dimReadBookmarks,
                offlineMode = offlineMode,
                bannerImageUrl = bannerImageUrl,
                screenshotUrl = screenshotUrl,
                isSelected = isSelected,
                tagsScrollable = display.tagsScrollable,
                isActive = isActive,
                showDescription = display.showDescription,
                showUrl = display.showUrl,
                urlDisplayMode = display.urlDisplayMode,
                urlPosition = display.urlPosition,
                urlIconMode = display.urlIconMode,
                faviconByLinkSize = display.faviconByLinkSize,
            )
            LayoutType.LIST, @Suppress("DEPRECATION") LayoutType.COMPACT_LIST -> BookmarkListLayout(
                bookmark = bookmark,
                onClick = onClick,
                onLongClick = onLongClick,
                showReadingTime = display.showReadingTimeBadge,
                showReadingProgress = showReadingProgress,
                showTags = display.showTags,
                showDate = display.showDate,
                dateDisplayMode = display.dateDisplayMode,
                dimRead = display.dimReadBookmarks,
                offlineMode = offlineMode,
                bannerImageUrl = bannerImageUrl,
                screenshotUrl = screenshotUrl,
                isSelected = isSelected,
                thumbnailSide = display.thumbnailSide,
                showFavicon = display.showFavicon,
                thumbnailSize = display.thumbnailSize,
                metadataPosition = display.metadataPosition,
                tagsScrollable = display.tagsScrollable,
                isActive = isActive,
                showDescription = display.showDescription,
                descriptionPosition = display.descriptionPosition,
                showUrl = display.showUrl,
                urlDisplayMode = display.urlDisplayMode,
                urlPosition = display.urlPosition,
                urlIconMode = display.urlIconMode,
                faviconByLinkSize = display.faviconByLinkSize,
                showThumbnail = display.showThumbnail,
                itemContainerStyle = display.itemContainerStyle,
                readIndicatorStyle = display.readIndicatorStyle,
                showRowDivider = display.showRowDivider,
                titlePosition = display.titlePosition,
                descriptionMaxLines = display.descriptionMaxLines,
                rowWidth = rowWidth,
                fixedRowHeight = fixedRowHeight,
                descriptionFillHeight = descriptionFillHeight,
            )
        }
    }
}

/**
 * The offline copy's file when there is one, so the row works offline and the image loader
 * does not keep a second copy of it.
 */
private fun rowImageUrl(serverUrl: String?, assetId: String?, fileName: (String) -> String): String? {
    if (serverUrl == null || assetId == null) return null
    return OfflineHeroImages.resolve(
        remoteUrl = AssetUrlUtils.getAssetUrl(serverUrl, assetId),
        directory = FileUtils.getImageCacheDirectory(),
        fileName = fileName(assetId)
    )
}

@Composable
private fun RowActionWrapper(
    bookmark: Bookmark,
    left: RowSideAction,
    right: RowSideAction,
    enabled: Boolean,
    useButtons: Boolean,
    isDesktop: Boolean,
    display: MainScreenDisplayConfig,
    onSwipeAction: (Bookmark, SwipeAction, CustomSwipeActionConfig?) -> Unit,
    content: @Composable () -> Unit
) {
    val leftAction = if (enabled) left.action else SwipeAction.NONE
    val rightAction = if (enabled) right.action else SwipeAction.NONE
    val onActionTriggered: (SwipeAction, Boolean) -> Unit = { action, isRightSwipe ->
        onSwipeAction(bookmark, action, if (isRightSwipe) right.config else left.config)
    }
    val flat = display.itemContainerStyle == ItemContainerStyle.FLAT
    if (useButtons) {
        QuickActionBookmarkItem(
            flat = flat,
            // On touch there is no hover to reveal them.
            alwaysVisible = !isDesktop,
            leftAction = leftAction,
            rightAction = rightAction,
            leftIcon = left.icon,
            rightIcon = right.icon,
            leftIsApplied = left.isApplied,
            rightIsApplied = right.isApplied,
            position = display.quickActionPosition,
            onActionTriggered = onActionTriggered,
            content = content
        )
    } else {
        SwipeableBookmarkItem(
            flat = flat,
            leftSwipeAction = leftAction,
            rightSwipeAction = rightAction,
            leftIcon = left.icon,
            rightIcon = right.icon,
            leftColor = left.config.getEffectiveColor(left.action).takeIf { left.config?.colorHex != null },
            rightColor = right.config.getEffectiveColor(right.action).takeIf { right.config?.colorHex != null },
            leftLabel = left.label,
            rightLabel = right.label,
            leftIsApplied = left.isApplied,
            rightIsApplied = right.isApplied,
            onActionTriggered = onActionTriggered,
            content = content
        )
    }
}
