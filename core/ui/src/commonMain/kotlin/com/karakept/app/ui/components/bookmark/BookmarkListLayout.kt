package com.karakept.app.ui.components.bookmark

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.ReadIndicatorStyle
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.TitlePosition
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.ui.theme.LocalEinkMode

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkListLayout(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showReadingTime: Boolean = true,
    showReadingProgress: Boolean = true,
    showTags: Boolean = true,
    showDate: Boolean = true,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.ELAPSED,
    dimRead: Boolean = false,
    offlineMode: Boolean = false,
    bannerImageUrl: String? = null,
    screenshotUrl: String? = null,
    isSelected: Boolean = false,
    thumbnailSide: ThumbnailSide = ThumbnailSide.LEFT,
    showFavicon: Boolean = true,
    thumbnailSize: Int = 80,
    metadataPosition: MetadataPosition = MetadataPosition.BELOW,
    tagsScrollable: Boolean = false,
    isActive: Boolean = false,
    showDescription: Boolean = true,
    descriptionPosition: DescriptionPosition = DescriptionPosition.BELOW_TITLE,
    showUrl: Boolean = false,
    urlDisplayMode: UrlDisplayMode = UrlDisplayMode.DOMAIN_ONLY,
    urlPosition: UrlPosition = UrlPosition.BELOW_TITLE,
    urlIconMode: UrlIconMode = UrlIconMode.GLOBE_ONLY,
    faviconByLinkSize: Int = 16,
    showThumbnail: Boolean = true,
    itemContainerStyle: ItemContainerStyle = ItemContainerStyle.CARD,
    readIndicatorStyle: ReadIndicatorStyle = ReadIndicatorStyle.DIM,
    showRowDivider: Boolean = true,
    titlePosition: TitlePosition = TitlePosition.BESIDE_THUMBNAIL,
    descriptionMaxLines: Int = BookmarkLayout.DESCRIPTION_LINES_DEFAULT,
    /**
     * Width the whole row will get, needed only to resolve
     * [BookmarkLayout.DESCRIPTION_LINES_AUTO]. Measured once by the caller for the whole list
     * rather than per row — a row cannot ask for its own width without a subcomposition.
     */
    rowWidth: Dp? = null,
    /**
     * Height this row is held to so an exact number of rows fills the page, or null when rows size
     * themselves. Only a paged e-ink list imposes one — see `rememberTiledRows`.
     */
    fixedRowHeight: Dp? = null,
    /**
     * The share of [fixedRowHeight] the thumbnail-and-text band gets, and so the space an automatic
     * description count fills. Null whenever [fixedRowHeight] is.
     */
    descriptionFillHeight: Dp? = null,
    faviconPainter: Painter? = null,
    thumbnailPainter: Painter? = null,
    modifier: Modifier = Modifier
) {
    val isFullyRead = bookmark.isRead
    val isFlat = itemContainerStyle == ItemContainerStyle.FLAT
    // MARKER keeps read rows at full contrast and moves the signal to a bullet beside the title:
    // a 50%-alpha row is mid-grey, which on e-ink is both hard to read and hard to tell apart.
    val useReadMarker = readIndicatorStyle == ReadIndicatorStyle.MARKER
    val alpha = if (isFullyRead && dimRead && !useReadMarker) 0.5f else 1f
    // The unselected card is distinguished from the page only by its tonal fill, which the e-ink
    // scheme flattens to the page colour — give it an outline instead. Selected/active already
    // carry a 2dp border, so they stay legible unchanged. Flat rows never take the outline: their
    // separation comes from the divider below them.
    val highContrast = LocalEinkMode.current.highContrast
    val selectionBorderModifier = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else if (isActive) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp))
    } else if (highContrast && !isFlat) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }
    val hapticFeedback = LocalHapticFeedback.current
    val hapticLongClick = androidx.compose.runtime.remember(onLongClick) {
        onLongClick?.let { callback ->
            { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress); callback() }
        }
    }
    // Under a tile the thumbnail is the last few percent the row has to give: the tile is chosen so
    // that shedding a description line and trimming the image covers the difference, which is what
    // lets a page hold the number of rows nearest its natural one instead of one fewer.
    val thumbSizeDp = if (descriptionFillHeight != null) {
        minOf(thumbnailSize.dp, descriptionFillHeight)
    } else {
        thumbnailSize.dp
    }
    val faviconSizeDp = (thumbnailSize * 16 / 80).coerceIn(10, 20).dp
    val faviconPaddingDp = (thumbnailSize * 4 / 80).coerceIn(2, 6).dp
    val cornerDp = (thumbnailSize * 8 / 80).coerceIn(4, 12).dp

    BookmarkRowContainer(
        isFlat = isFlat,
        showDivider = showRowDivider,
        isSelected = isSelected,
        isActive = isActive,
        fixedHeight = fixedRowHeight,
        modifier = modifier
            .fillMaxWidth()
            .then(selectionBorderModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = hapticLongClick
            )
    ) {
        Box {
        Box(modifier = Modifier.alpha(alpha)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (isFlat) 12.dp else 16.dp)
            ) {
                // Metadata composable used in both positions
                val metadataContent: @Composable () -> Unit = {
                    BookmarkListMetadata(
                        bookmark = bookmark,
                        showDescription = showDescription,
                        descriptionPosition = descriptionPosition,
                        showTags = showTags,
                        tagsScrollable = tagsScrollable,
                        singleLineTags = fixedRowHeight != null,
                        showDate = showDate,
                        dateDisplayMode = dateDisplayMode,
                        showUrl = showUrl,
                        urlPosition = urlPosition,
                        urlDisplayMode = urlDisplayMode,
                        urlIconMode = urlIconMode,
                        faviconByLinkSize = faviconByLinkSize,
                        faviconPainter = faviconPainter,
                        showReadingTime = showReadingTime,
                        offlineMode = offlineMode
                    )
                }

                val thumbnailContent: @Composable RowScope.() -> Unit = {
                    BookmarkListThumbnail(
                        bookmark = bookmark,
                        thumbnailPainter = thumbnailPainter,
                        bannerImageUrl = bannerImageUrl,
                        screenshotUrl = screenshotUrl,
                        thumbnailSize = thumbnailSize,
                        thumbSizeDp = thumbSizeDp,
                        cornerDp = cornerDp,
                        showFavicon = showFavicon,
                        faviconPainter = faviconPainter,
                        faviconSizeDp = faviconSizeDp,
                        faviconPaddingDp = faviconPaddingDp
                    )
                }
                val effectiveDescriptionLines = rememberEffectiveDescriptionLines(
                    requested = descriptionMaxLines,
                    rowWidth = rowWidth,
                    showThumbnail = showThumbnail,
                    thumbnailSize = thumbSizeDp,
                    title = bookmark.title,
                    titleCountsAgainstThumbnail = titlePosition == TitlePosition.BESIDE_THUMBNAIL,
                    showUrlBelowTitle = showUrl && !bookmark.url.isNullOrBlank() &&
                        urlPosition == UrlPosition.BELOW_TITLE,
                    fillHeight = descriptionFillHeight
                )

                val titleContent: @Composable () -> Unit = {
                    BookmarkListTitle(
                        title = bookmark.title,
                        useReadMarker = useReadMarker,
                        isFullyRead = isFullyRead
                    )
                }

                val bodyContent: @Composable () -> Unit = {
                    BookmarkListBody(
                        bookmark = bookmark,
                        showUrl = showUrl,
                        urlPosition = urlPosition,
                        urlDisplayMode = urlDisplayMode,
                        urlIconMode = urlIconMode,
                        faviconByLinkSize = faviconByLinkSize,
                        faviconPainter = faviconPainter,
                        showDescription = showDescription,
                        descriptionPosition = descriptionPosition,
                        descriptionLines = effectiveDescriptionLines,
                        metadataPosition = metadataPosition,
                        metadata = metadataContent
                    )
                }

                val textContent: @Composable RowScope.() -> Unit = {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        titleContent()
                        bodyContent()
                    }
                }
                if (metadataPosition == MetadataPosition.ABOVE) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        metadataContent()
                    }
                }
                // With no thumbnail the two title positions render identically, so the title
                // always spans the row and there is nothing to lay out beside it.
                val titleAboveThumbnail =
                    showThumbnail && titlePosition == TitlePosition.ABOVE_THUMBNAIL
                if (titleAboveThumbnail) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        titleContent()
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (showThumbnail) 12.dp else 0.dp)
                ) {
                    val rowText: @Composable RowScope.() -> Unit = if (titleAboveThumbnail) {
                        {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                bodyContent()
                            }
                        }
                    } else {
                        textContent
                    }
                    if (!showThumbnail) {
                        rowText()
                    } else if (thumbnailSide == ThumbnailSide.LEFT) {
                        thumbnailContent()
                        rowText()
                    } else {
                        rowText()
                        thumbnailContent()
                    }
                }
                if (metadataPosition == MetadataPosition.BELOW) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        metadataContent()
                    }
                }
            }

            // Reading progress bar
            if (showReadingProgress && bookmark.readingProgress > 0f) {
                LinearProgressIndicator(
                    progress = { bookmark.readingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // Selection indicator (not affected by dim alpha)
        SelectionIndicator(
            isSelected = isSelected,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )
        }
    }
}
