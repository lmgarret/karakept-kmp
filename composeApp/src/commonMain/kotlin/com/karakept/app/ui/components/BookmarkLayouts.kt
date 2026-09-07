package com.karakept.app.ui.components

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.ReadIndicatorStyle
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.TitlePosition
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.computeAutoDescriptionLines
import com.karakept.app.ui.utils.extractDomain
import com.karakept.app.utils.FaviconUtils
import com.karakept.app.utils.formatBookmarkDate


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkCardLayout(
    bookmark: BookmarkEntity,
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
    tagsScrollable: Boolean = false,
    isActive: Boolean = false,
    showDescription: Boolean = true,
    showUrl: Boolean = false,
    urlDisplayMode: UrlDisplayMode = UrlDisplayMode.DOMAIN_ONLY,
    urlPosition: UrlPosition = UrlPosition.BELOW_TITLE,
    urlIconMode: UrlIconMode = UrlIconMode.GLOBE_ONLY,
    faviconByLinkSize: Int = 16,
    faviconPainter: Painter? = null,
    thumbnailPainter: Painter? = null,
    modifier: Modifier = Modifier
) {
    val isFullyRead = bookmark.isRead
    val alpha = if (isFullyRead && dimRead) 0.5f else 1f
    // The unselected card is distinguished from the page only by its tonal fill, which the e-ink
    // scheme flattens to the page colour — give it an outline instead. Selected/active already
    // carry a 2dp border, so they stay legible unchanged.
    val highContrast = LocalEinkMode.current.highContrast
    val selectionBorderModifier = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else if (isActive) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp))
    } else if (highContrast) {
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(selectionBorderModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = hapticLongClick
            )
            ,
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        } else if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Box {
            Box(modifier = Modifier.alpha(alpha)) {
                Column {
                    // Determine which image to show: thumbnailPainter (bundled) → bannerImageUrl (server asset) → screenshotUrl → emoji
                    val effectiveImageUrl = if (thumbnailPainter == null) bannerImageUrl ?: screenshotUrl else null

                    if (thumbnailPainter != null) {
                        Image(
                            painter = thumbnailPainter,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    } else if (effectiveImageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                .data(effectiveImageUrl)
                                .size(600) // Request a reasonable size for the card
                                .crossfade(!LocalEinkMode.current.animationsDisabled)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📰",
                                style = MaterialTheme.typography.displayLarge
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = bookmark.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showUrl && !bookmark.url.isNullOrBlank() && urlPosition == UrlPosition.BELOW_TITLE) {
                            UrlDisplay(url = bookmark.url, urlDisplayMode = urlDisplayMode, urlIconMode = urlIconMode, iconSize = faviconByLinkSize, faviconPainter = faviconPainter, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (showTags && bookmark.tags.isNotBlank()) {
                            BookmarkTagsDisplay(
                                tags = bookmark.tags,
                                style = TagsDisplayStyle.COMPACT,
                                scrollable = tagsScrollable,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (showDescription && !bookmark.description.isNullOrBlank()) {
                            Text(
                                text = bookmark.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showDate) {
                                Text(
                                    text = formatBookmarkDate(bookmark.createdAt, dateDisplayMode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            if (showUrl && !bookmark.url.isNullOrBlank() && urlPosition == UrlPosition.METADATA_ROW) {
                                UrlDisplay(url = bookmark.url, urlDisplayMode = urlDisplayMode, urlIconMode = urlIconMode, iconSize = faviconByLinkSize, faviconPainter = faviconPainter, modifier = Modifier.padding(end = 8.dp))
                            }
                            if (showReadingTime) {
                                if (bookmark.readingTimeMinutes > 0) {
                                    ReadingTimeBadge(readingTimeMinutes = bookmark.readingTimeMinutes)
                                } else if (offlineMode) {
                                    NotSyncedBadge()
                                }
                            }
                        }
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkListLayout(
    bookmark: BookmarkEntity,
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
                    if (showDescription && !bookmark.description.isNullOrBlank() && descriptionPosition == DescriptionPosition.ABOVE_METADATA) {
                        Text(
                            text = bookmark.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    if (showTags && bookmark.tags.isNotBlank()) {
                        BookmarkTagsDisplay(
                            tags = bookmark.tags,
                            style = TagsDisplayStyle.COMPACT,
                            scrollable = tagsScrollable,
                            // A second row of chips is the one thing in a row the layout settings
                            // do not bound, so a row held to a tile keeps them on one line.
                            maxLines = if (fixedRowHeight != null) 1 else Int.MAX_VALUE,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showDate) {
                            Text(
                                text = formatBookmarkDate(bookmark.createdAt, dateDisplayMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (showUrl && !bookmark.url.isNullOrBlank() && urlPosition == UrlPosition.METADATA_ROW) {
                            UrlDisplay(url = bookmark.url, urlDisplayMode = urlDisplayMode, urlIconMode = urlIconMode, iconSize = faviconByLinkSize, faviconPainter = faviconPainter, modifier = Modifier.padding(end = 8.dp))
                        }
                        if (showReadingTime) {
                            if (bookmark.readingTimeMinutes > 0) {
                                ReadingTimeBadge(readingTimeMinutes = bookmark.readingTimeMinutes)
                            } else if (offlineMode) {
                                NotSyncedBadge()
                            }
                        }
                    }
                }

                val thumbnailContent: @Composable RowScope.() -> Unit = {
                    Box(
                        modifier = Modifier
                            .size(thumbSizeDp)
                            .clip(RoundedCornerShape(cornerDp))
                    ) {
                        val effectiveImageUrl = if (thumbnailPainter == null) bannerImageUrl ?: screenshotUrl else null

                        if (thumbnailPainter != null) {
                            Image(
                                painter = thumbnailPainter,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        } else if (effectiveImageUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(effectiveImageUrl)
                                    .size(300)
                                    .crossfade(!LocalEinkMode.current.animationsDisabled)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop,
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "📰",
                                    style = if (thumbnailSize >= 64) MaterialTheme.typography.headlineMedium
                                        else MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        if (showFavicon) {
                            val faviconModifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(faviconPaddingDp)
                                .size(faviconSizeDp)
                                .clip(RoundedCornerShape(faviconSizeDp / 4))
                                .background(Color.White.copy(alpha = 0.5f))
                            if (faviconPainter != null) {
                                Image(
                                    painter = faviconPainter,
                                    contentDescription = null,
                                    modifier = faviconModifier,
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                AsyncImage(
                                    model = FaviconUtils.getFaviconUrl(bookmark.url),
                                    contentDescription = null,
                                    modifier = faviconModifier,
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
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
                    if (useReadMarker) {
                            // A leading bullet plus a heavier weight — both survive at full
                            // contrast, unlike the 50%-alpha dimming this replaces.
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = if (isFullyRead) "   " else "•  ",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = bookmark.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isFullyRead) FontWeight.Normal else FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                    } else {
                        Text(
                            text = bookmark.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Everything in the text column except the title, so the title can be hoisted
                // above the thumbnail without dragging the rest of the column with it.
                val bodyContent: @Composable () -> Unit = {
                        if (showUrl && !bookmark.url.isNullOrBlank() && urlPosition == UrlPosition.BELOW_TITLE) {
                            UrlDisplay(url = bookmark.url, urlDisplayMode = urlDisplayMode, urlIconMode = urlIconMode, iconSize = faviconByLinkSize, faviconPainter = faviconPainter, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (showDescription && !bookmark.description.isNullOrBlank() && descriptionPosition == DescriptionPosition.BELOW_TITLE) {
                            Text(
                                text = bookmark.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = effectiveDescriptionLines,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (metadataPosition == MetadataPosition.BESIDE) {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                metadataContent()
                            }
                        }
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

/**
 * Wraps a bookmark row in its container.
 *
 * [ItemContainerStyle.CARD] is the Material default. [ItemContainerStyle.FLAT] drops the container
 * and puts a hairline rule under the row instead — a divider costs one line of ink per bookmark
 * where an outlined card costs a whole rectangle, which is what makes the difference on e-ink.
 * Selected and active rows keep a tonal wash so they remain distinguishable without a container.
 */
@Composable
internal fun BookmarkRowContainer(
    isFlat: Boolean,
    showDivider: Boolean,
    isSelected: Boolean,
    isActive: Boolean,
    /**
     * Height the row is held to when the list tiles its rows. The content keeps its own size and is
     * clipped to the tile rather than stretched, but the divider is pushed to the bottom of it: a
     * rule sitting at each row's own natural height would land at a different place on every row.
     */
    fixedHeight: Dp? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val heightModifier = if (fixedHeight != null) Modifier.height(fixedHeight) else Modifier
    if (isFlat) {
        // An opaque page-colour backing, painted before the selection/active tint. Without it the
        // row is transparent and swiping shows the action colour through the whole line instead of
        // only in the gutter the row has slid away from — SwipeableBookmarkItem's action layer sits
        // directly behind this content.
        val tint = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            isActive -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else -> null
        }
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.background)
                .then(if (tint != null) Modifier.background(tint) else Modifier)
                .then(heightModifier)
        ) {
            if (fixedHeight != null) {
                Box(modifier = Modifier.weight(1f).clipToBounds()) { content() }
            } else {
                content()
            }
            if (showDivider) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    } else {
        Card(
            modifier = modifier.then(heightModifier),
            colors = when {
                isSelected -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
                isActive -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
                else -> CardDefaults.cardColors()
            },
            content = { content() }
        )
    }
}

@Composable
private fun UrlDisplay(
    url: String,
    urlDisplayMode: UrlDisplayMode,
    urlIconMode: UrlIconMode = UrlIconMode.GLOBE_ONLY,
    iconSize: Int = 16,
    faviconPainter: Painter? = null,
    modifier: Modifier = Modifier
) {
    val displayText = when (urlDisplayMode) {
        UrlDisplayMode.DOMAIN_ONLY -> extractDomain(url)
        UrlDisplayMode.FULL_URL -> url
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (urlIconMode != UrlIconMode.NONE) {
            Box(modifier = Modifier.size(iconSize.dp)) {
                if (urlIconMode == UrlIconMode.FAVICON) {
                    if (faviconPainter != null) {
                        Image(
                            painter = faviconPainter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        val faviconUrl = FaviconUtils.getFaviconUrl(url)
                        if (faviconUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(faviconUrl)
                                    .crossfade(!LocalEinkMode.current.animationsDisabled)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Fallback to globe when no favicon URL available
                            Icon(
                                imageVector = AppIcons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // GLOBE_ONLY mode
                    Icon(
                        imageVector = AppIcons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.size(4.dp))
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
fun SelectionIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Box(
            modifier = modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Resolves a description line count against the space the row has for one.
 *
 * [BookmarkLayout.DESCRIPTION_LINES_AUTO] fills whatever the rest of the row leaves over. A fixed
 * count is honoured as a *cap* rather than a promise: under a tile the row may have room for fewer
 * lines than the layout asks for, and shedding one is how it gives back the pixels the tile took —
 * far better than rendering them and having the tile clip the metadata underneath.
 *
 * Measures the title with a [TextMeasurer] rather than assuming a line count — a wrapped title
 * costs a whole line, and guessing wrong either overflows the row or wastes the space this is
 * meant to reclaim. Measuring is synchronous and needs no subcomposition, but it does need the
 * row's width, which only the caller knows.
 *
 * Falls back to the requested count whenever the space cannot be reasoned about: nothing to fill
 * against, no width supplied, or metadata sitting in the text column (tags are a `FlowRow` whose
 * height a text measurer cannot predict).
 */
@Composable
private fun rememberEffectiveDescriptionLines(
    requested: Int,
    rowWidth: Dp?,
    showThumbnail: Boolean,
    thumbnailSize: Dp,
    title: String,
    titleCountsAgainstThumbnail: Boolean,
    showUrlBelowTitle: Boolean,
    /**
     * Height the thumbnail-and-text band is held to when the list tiles its rows. It replaces the
     * thumbnail as the space to fill — and unlike the thumbnail it is there even when the layout
     * shows no image, which is the case e-ink actually uses.
     */
    fillHeight: Dp? = null
): Int {
    val isAuto = requested == BookmarkLayout.DESCRIPTION_LINES_AUTO
    val cap = if (isAuto) {
        BookmarkLayout.DESCRIPTION_LINES_MAX
    } else {
        requested.coerceIn(1, BookmarkLayout.DESCRIPTION_LINES_MAX)
    }
    // A fixed count is only worth measuring against a tile — a row sizing itself already fits the
    // lines it asks for — while automatic needs either a tile or a thumbnail to fill.
    val measurable = fillHeight != null || (isAuto && showThumbnail)
    if (!measurable || rowWidth == null) {
        return if (isAuto) BookmarkLayout.DESCRIPTION_LINES_DEFAULT else cap
    }

    val measurer = rememberTextMeasurer(cacheSize = AUTO_LINES_MEASURE_CACHE)
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.titleMedium
    val bodyStyle = MaterialTheme.typography.bodySmall

    // Row padding (16dp each side), then the thumbnail and the 12dp gap beside it when there is one.
    val thumbnailWidth = if (showThumbnail) thumbnailSize + 12.dp else 0.dp
    val textColumnWidth = rowWidth - 32.dp - thumbnailWidth
    if (textColumnWidth <= 0.dp) return cap

    return with(density) {
        // A title above the thumbnail is not in the text column at all, and the tile has already
        // had that band deducted, so either way it costs the description nothing here.
        val titleHeight = if (titleCountsAgainstThumbnail) {
            measurer.measure(
                text = title,
                style = titleStyle,
                maxLines = 2,
                constraints = Constraints(maxWidth = textColumnWidth.roundToPx())
            ).size.height.toFloat()
        } else {
            0f
        }
        // The url is a single line by construction, and each block above the description carries
        // 4dp of top padding.
        val urlHeight = if (showUrlBelowTitle) bodyStyle.lineHeight.toPx() + 4.dp.toPx() else 0f
        computeAutoDescriptionLines(
            availableHeightPx = (fillHeight ?: thumbnailSize).toPx(),
            consumedHeightPx = titleHeight + urlHeight + 4.dp.toPx(),
            descriptionLineHeightPx = bodyStyle.lineHeight.toPx(),
            maxLines = cap
        )
    }
}

/**
 * Roughly a screenful of rows. The default of 8 evicts constantly while scrolling, so every row
 * would re-measure even though the same titles keep coming back into view.
 */
private const val AUTO_LINES_MEASURE_CACHE = 24
