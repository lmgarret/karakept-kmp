package com.karakept.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.TitlePosition
import com.karakept.app.ui.theme.LocalEinkMode

@Composable
fun BookmarkPlaceholderItem(
    url: String,
    layoutType: LayoutType = LayoutType.LIST,
    itemContainerStyle: ItemContainerStyle = ItemContainerStyle.CARD,
    showThumbnail: Boolean = true,
    thumbnailSize: Int = 80,
    thumbnailSide: ThumbnailSide = ThumbnailSide.LEFT,
    titlePosition: TitlePosition = TitlePosition.BESIDE_THUMBNAIL,
    showRowDivider: Boolean = true
) {
    // The shimmer is a continuous animation — the worst case on e-ink. Collapse the two
    // shimmering thumbnail/title blocks into a single LoadingDotsIndicator there instead of
    // just changing their brush.
    if (LocalEinkMode.current.animationsDisabled) {
        EinkPlaceholder(url, itemContainerStyle, showRowDivider)
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "placeholder_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f)

    when (layoutType) {
        LayoutType.CARD -> CardPlaceholder(url, shimmerColor)
        LayoutType.LIST, @Suppress("DEPRECATION") LayoutType.COMPACT_LIST -> ListPlaceholder(
            url = url,
            shimmerColor = shimmerColor,
            itemContainerStyle = itemContainerStyle,
            showThumbnail = showThumbnail,
            thumbnailSize = thumbnailSize,
            thumbnailSide = thumbnailSide,
            titlePosition = titlePosition,
            showRowDivider = showRowDivider
        )
    }
}

@Composable
private fun EinkPlaceholder(url: String, itemContainerStyle: ItemContainerStyle, showRowDivider: Boolean) {
    val isFlat = itemContainerStyle == ItemContainerStyle.FLAT
    BookmarkRowContainer(
        isFlat = isFlat,
        showDivider = showRowDivider,
        isSelected = false,
        isActive = false,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isFlat) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isFlat) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LoadingDotsIndicator(dotSize = 8.dp)
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ListPlaceholder(
    url: String,
    shimmerColor: Color,
    itemContainerStyle: ItemContainerStyle,
    showThumbnail: Boolean,
    thumbnailSize: Int,
    thumbnailSide: ThumbnailSide,
    titlePosition: TitlePosition,
    showRowDivider: Boolean
) {
    // Mirrors BookmarkListLayout's container + thumbnail/title arrangement so the placeholder
    // matches whichever built-in layout (Compact, Rows, Cards, Digest) is active instead of
    // always looking like a card.
    val isFlat = itemContainerStyle == ItemContainerStyle.FLAT
    val cornerDp = (thumbnailSize * 8 / 80).coerceIn(4, 12).dp

    BookmarkRowContainer(
        isFlat = isFlat,
        showDivider = showRowDivider,
        isSelected = false,
        isActive = false,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isFlat) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isFlat) 12.dp else 16.dp)
        ) {
            val titleBar: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerColor)
                )
            }
            val thumbnailBox: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .size(thumbnailSize.dp)
                        .clip(RoundedCornerShape(cornerDp))
                        .background(shimmerColor)
                )
            }
            val urlLine: @Composable () -> Unit = {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val titleAboveThumbnail = showThumbnail && titlePosition == TitlePosition.ABOVE_THUMBNAIL
            if (titleAboveThumbnail) {
                titleBar()
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (showThumbnail) 12.dp else 0.dp)
            ) {
                val textColumn: @Composable RowScope.() -> Unit = {
                    Column(modifier = Modifier.weight(1f)) {
                        if (!titleAboveThumbnail) {
                            titleBar()
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        urlLine()
                    }
                }
                if (!showThumbnail) {
                    textColumn()
                } else if (thumbnailSide == ThumbnailSide.LEFT) {
                    thumbnailBox()
                    textColumn()
                } else {
                    textColumn()
                    thumbnailBox()
                }
            }
        }
    }
}

@Composable
private fun CardPlaceholder(url: String, shimmerColor: Color) {
    // Matches BookmarkCardLayout structure
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(shimmerColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerColor)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
