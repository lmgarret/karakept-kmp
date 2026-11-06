package com.karakept.app.ui.components.bookmark

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.ui.components.tags.BookmarkTagsDisplay
import com.karakept.app.ui.components.tags.TagsDisplayStyle
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.utils.FaviconUtils
import com.karakept.app.utils.formatBookmarkDate

/**
 * The description (when it sits above the metadata), tags and date/url/reading-time row of a
 * [BookmarkListLayout] row. Emits its children straight into the caller's column, which is what
 * places it above, beside or below the thumbnail band.
 */
@Composable
internal fun BookmarkListMetadata(
    bookmark: Bookmark,
    showDescription: Boolean,
    descriptionPosition: DescriptionPosition,
    showTags: Boolean,
    tagsScrollable: Boolean,
    singleLineTags: Boolean,
    showDate: Boolean,
    dateDisplayMode: DateDisplayMode,
    showUrl: Boolean,
    urlPosition: UrlPosition,
    urlDisplayMode: UrlDisplayMode,
    urlIconMode: UrlIconMode,
    faviconByLinkSize: Int,
    faviconPainter: Painter?,
    showReadingTime: Boolean,
    offlineMode: Boolean
) {
    if (showDescription && !bookmark.description.isNullOrBlank() && descriptionPosition == DescriptionPosition.ABOVE_METADATA) {
        Text(
            text = bookmark.description.orEmpty(),
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
            maxLines = if (singleLineTags) 1 else Int.MAX_VALUE,
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

/** The square thumbnail of a [BookmarkListLayout] row, with the favicon badge in its corner. */
@Composable
internal fun BookmarkListThumbnail(
    bookmark: Bookmark,
    thumbnailPainter: Painter?,
    bannerImageUrl: String?,
    screenshotUrl: String?,
    thumbnailSize: Int,
    thumbSizeDp: Dp,
    cornerDp: Dp,
    showFavicon: Boolean,
    faviconPainter: Painter?,
    faviconSizeDp: Dp,
    faviconPaddingDp: Dp
) {
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

/** The title of a [BookmarkListLayout] row, with the read bullet when [useReadMarker] is set. */
@Composable
internal fun BookmarkListTitle(
    title: String,
    useReadMarker: Boolean,
    isFullyRead: Boolean
) {
    if (useReadMarker) {
        // A leading bullet plus a heavier weight — both survive at full
        // contrast, unlike the 50%-alpha dimming this replaces.
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = if (isFullyRead) "   " else "•  ",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isFullyRead) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Everything in the text column except the title, so the title can be hoisted above the
 * thumbnail without dragging the rest of the column with it. Emits into the caller's column.
 */
@Composable
internal fun BookmarkListBody(
    bookmark: Bookmark,
    showUrl: Boolean,
    urlPosition: UrlPosition,
    urlDisplayMode: UrlDisplayMode,
    urlIconMode: UrlIconMode,
    faviconByLinkSize: Int,
    faviconPainter: Painter?,
    showDescription: Boolean,
    descriptionPosition: DescriptionPosition,
    descriptionLines: Int,
    metadataPosition: MetadataPosition,
    metadata: @Composable () -> Unit
) {
    if (showUrl && !bookmark.url.isNullOrBlank() && urlPosition == UrlPosition.BELOW_TITLE) {
        UrlDisplay(url = bookmark.url, urlDisplayMode = urlDisplayMode, urlIconMode = urlIconMode, iconSize = faviconByLinkSize, faviconPainter = faviconPainter, modifier = Modifier.padding(top = 4.dp))
    }
    if (showDescription && !bookmark.description.isNullOrBlank() && descriptionPosition == DescriptionPosition.BELOW_TITLE) {
        Text(
            text = bookmark.description.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = descriptionLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    if (metadataPosition == MetadataPosition.BESIDE) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            metadata()
        }
    }
}
