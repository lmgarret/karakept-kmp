package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.ReadIndicatorStyle
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.TitlePosition
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.ui.components.bookmark.BookmarkCardLayout
import com.karakept.app.ui.components.bookmark.BookmarkListLayout
import karakept.composeapp.generated.resources.Res
import karakept.composeapp.generated.resources.preview_thumbnail
import org.jetbrains.compose.resources.painterResource

/** Mock bookmark used for live preview in the editor. */
private val PREVIEW_BOOKMARK = Bookmark(
    localId = -1L,
    remoteId = "preview",
    serverId = "preview",
    url = "https://karakeep.app/article/bookmarking-best-practices",
    title = "Bookmarking Best Practices",
    content = null,
    imageUrl = null,
    bannerImageAssetId = null,
    screenshotAssetId = null,
    description = "A short preview of the bookmark description text to illustrate the layout.",
    createdAt = System.currentTimeMillis() - 3_600_000L,
    isArchived = false,
    isStarred = false,
    isRead = false,
    tags = "technology,design",
    listIds = "",
    readingTimeMinutes = 5,
    readingProgress = 0f,
    readingScrollIndex = 0,
    readingScrollOffset = 0
)

@Composable
internal fun PreviewBookmarkItem(layout: BookmarkLayout) {
    // The preview pane's own width, so an auto line count previews the way it will render.
    BoxWithConstraints {
    val previewRowWidth = maxWidth
    val layoutType = LayoutType.fromString(layout.layoutType)
    val dateMode = DateDisplayMode.fromString(layout.dateDisplayMode)
    val thumbnailSide = ThumbnailSide.fromString(layout.thumbnailSide)
    val metadataPos = MetadataPosition.fromString(layout.metadataPosition)
    val descriptionPos = DescriptionPosition.fromString(layout.descriptionPosition)
    val urlPos = UrlPosition.fromString(layout.urlPosition)
    val urlMode = UrlDisplayMode.fromString(layout.urlDisplayMode)
    val urlIconMode = UrlIconMode.fromString(layout.urlIconMode)
    val previewThumbnail = painterResource(Res.drawable.preview_thumbnail)
    when (layoutType) {
        LayoutType.CARD -> BookmarkCardLayout(
            bookmark = PREVIEW_BOOKMARK,
            onClick = {},
            showReadingTime = layout.showReadingTime,
            showReadingProgress = false,
            showTags = layout.showTags,
            showDate = layout.showDate,
            dateDisplayMode = dateMode,
            dimRead = false,
            offlineMode = false,
            bannerImageUrl = null,
            thumbnailPainter = previewThumbnail,
            tagsScrollable = layout.tagsScrollable,
            showDescription = layout.showDescription,
            showUrl = layout.showUrl,
            urlDisplayMode = urlMode,
            urlPosition = urlPos,
            urlIconMode = urlIconMode,
            faviconByLinkSize = layout.faviconByLinkSize
        )
        LayoutType.LIST -> BookmarkListLayout(
            bookmark = PREVIEW_BOOKMARK,
            onClick = {},
            showReadingTime = layout.showReadingTime,
            showReadingProgress = false,
            showTags = layout.showTags,
            showDate = layout.showDate,
            dateDisplayMode = dateMode,
            dimRead = false,
            offlineMode = false,
            bannerImageUrl = null,
            thumbnailPainter = previewThumbnail,
            thumbnailSide = thumbnailSide,
            showFavicon = layout.showFavicon,
            thumbnailSize = layout.thumbnailSize,
            metadataPosition = metadataPos,
            tagsScrollable = layout.tagsScrollable,
            showDescription = layout.showDescription,
            descriptionPosition = descriptionPos,
            showUrl = layout.showUrl,
            urlDisplayMode = urlMode,
            urlPosition = urlPos,
            urlIconMode = urlIconMode,
            faviconByLinkSize = layout.faviconByLinkSize,
            showThumbnail = layout.showThumbnail,
            itemContainerStyle = ItemContainerStyle.fromString(layout.itemContainerStyle),
            readIndicatorStyle = ReadIndicatorStyle.fromString(layout.readIndicatorStyle),
            showRowDivider = layout.showRowDivider,
            titlePosition = TitlePosition.fromString(layout.titlePosition),
            descriptionMaxLines = layout.descriptionMaxLines,
            rowWidth = previewRowWidth
        )
        @Suppress("DEPRECATION")
        LayoutType.COMPACT_LIST -> BookmarkListLayout(
            bookmark = PREVIEW_BOOKMARK,
            onClick = {},
            showReadingTime = layout.showReadingTime,
            showReadingProgress = false,
            showTags = layout.showTags,
            showDate = layout.showDate,
            dateDisplayMode = dateMode,
            dimRead = false,
            offlineMode = false,
            bannerImageUrl = null,
            thumbnailPainter = previewThumbnail,
            thumbnailSide = thumbnailSide,
            showFavicon = layout.showFavicon,
            thumbnailSize = layout.thumbnailSize,
            metadataPosition = metadataPos,
            tagsScrollable = layout.tagsScrollable,
            showDescription = layout.showDescription,
            descriptionPosition = descriptionPos,
            showUrl = layout.showUrl,
            urlDisplayMode = urlMode,
            urlPosition = urlPos,
            urlIconMode = urlIconMode,
            faviconByLinkSize = layout.faviconByLinkSize,
            showThumbnail = layout.showThumbnail,
            itemContainerStyle = ItemContainerStyle.fromString(layout.itemContainerStyle),
            readIndicatorStyle = ReadIndicatorStyle.fromString(layout.readIndicatorStyle),
            showRowDivider = layout.showRowDivider,
            titlePosition = TitlePosition.fromString(layout.titlePosition),
            descriptionMaxLines = layout.descriptionMaxLines,
            rowWidth = previewRowWidth
        )
    }
    }
}
