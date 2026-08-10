package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.extractDomain
import com.karakept.app.utils.formatBookmarkDate

/**
 * Extracts the domain name from a full URL.
 * Removes protocol (http/https), www prefix, and path.
 */
private fun extractDomain(url: String): String {
    return try {
        val urlWithoutProtocol = url
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")

        // Take everything before the first slash
        urlWithoutProtocol.substringBefore("/")
    } catch (e: Exception) {
        url  // Fallback to full URL if parsing fails
    }
}

/**
 * Material You hero banner with image and title overlay.
 *
 * Features:
 * - Full-width banner at 280dp height
 * - AsyncImage with Coil for thumbnail
 * - Gradient scrim overlay for title legibility
 * - Title positioned at bottom with padding
 * - Fallback: Colored surface with emoji
 */
@Composable
fun HeroImageBanner(
    title: String,
    url: String? = null,
    tags: String = "",
    readingTimeMinutes: Int = 0,
    scrollProgress: Float = 0f,
    showTags: Boolean = true,
    createdAt: Long? = null,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.ELAPSED,
    onUrlClick: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    bannerImageUrl: String? = null,
    screenshotUrl: String? = null,
    bannerImageLocalPath: String? = null,
    screenshotLocalPath: String? = null,
    /**
     * When false the banner collapses to a plain text header. The 320dp image is most of a
     * small screen and dithers badly on e-ink, and the title is drawn white-on-scrim — with no
     * image there is no scrim, so the whole header has to switch to theme colours.
     */
    showImage: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!showImage) {
        TextOnlyHeroHeader(
            title = title,
            url = url,
            tags = tags,
            readingTimeMinutes = readingTimeMinutes,
            showTags = showTags,
            createdAt = createdAt,
            dateDisplayMode = dateDisplayMode,
            onUrlClick = onUrlClick,
            onTagClick = onTagClick,
            onInfoClick = onInfoClick,
            modifier = modifier
        )
        return
    }

    // Determine which image to show: local paths first, then remote URLs, then emoji fallback
    val effectiveImageData: Any? = when {
        bannerImageLocalPath != null -> File(bannerImageLocalPath)
        bannerImageUrl != null -> bannerImageUrl
        screenshotLocalPath != null -> File(screenshotLocalPath)
        screenshotUrl != null -> screenshotUrl
        else -> null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp) // Increased height for better parallax effect
    ) {
        if (effectiveImageData != null) {
            // Image background
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(effectiveImageData)
                    .crossfade(!LocalEinkMode.current.animationsDisabled)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Gradient scrim for title legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f) // Darker scrim for better text contrast
                            ),
                            startY = 100f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
        } else {
            // Fallback: colored surface with icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📰",
                    style = MaterialTheme.typography.displayLarge
                )
            }
        }

        // Title and metadata at bottom with scale effect
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
                .graphicsLayer {
                    // Scale from 1.0 to 0.85 as user scrolls
                    val scale = 1f - (scrollProgress * 0.15f)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f) // Scale from bottom-left
                }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White, // Always white on image/scrim
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Tags display - above domain info
            if (showTags && tags.isNotBlank()) {
                BookmarkTagsDisplay(
                    tags = tags,
                    style = TagsDisplayStyle.READER,
                    onTagClick = onTagClick,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (url != null || createdAt != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (url != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .then(
                                        if (onUrlClick != null) {
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable(onClick = onUrlClick)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        } else {
                                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        }
                                    )
                            ) {
                                AsyncImage(
                                    model = com.karakept.app.utils.FaviconUtils.getFaviconUrl(url),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.5f)),
                                    contentScale = ContentScale.Fit
                                )
                                Text(
                                    text = extractDomain(url),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                        if (createdAt != null) {
                            Text(
                                text = formatBookmarkDate(createdAt, dateDisplayMode),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Right side: reading time badge + info button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (readingTimeMinutes > 0) {
                            ReadingTimeBadge(
                                readingTimeMinutes = readingTimeMinutes
                            )
                        }
                        if (onInfoClick != null) {
                            IconButton(
                                onClick = onInfoClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Bookmark details",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The banner without its image: title, tags and metadata as ordinary text on the page.
 *
 * Sizes to its content rather than to a fixed 320dp, so an article starts within the first
 * screenful instead of a page-turn later — the main reason to turn the image off at all.
 */
@Composable
private fun TextOnlyHeroHeader(
    title: String,
    url: String?,
    tags: String,
    readingTimeMinutes: Int,
    showTags: Boolean,
    createdAt: Long?,
    dateDisplayMode: DateDisplayMode,
    onUrlClick: (() -> Unit)?,
    onTagClick: ((String) -> Unit)?,
    onInfoClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (showTags && tags.isNotBlank()) {
            BookmarkTagsDisplay(
                tags = tags,
                style = TagsDisplayStyle.READER,
                onTagClick = onTagClick,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (url != null) {
                    Text(
                        text = extractDomain(url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .then(
                                if (onUrlClick != null) {
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable(onClick = onUrlClick)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                } else Modifier
                            )
                    )
                }
                if (createdAt != null) {
                    Text(
                        text = formatBookmarkDate(createdAt, dateDisplayMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (readingTimeMinutes > 0) {
                    ReadingTimeBadge(readingTimeMinutes = readingTimeMinutes)
                }
                if (onInfoClick != null) {
                    IconButton(onClick = onInfoClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Bookmark details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}
