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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.BookmarkRowMetrics

/**
 * A bookmark row that has not arrived yet, drawn at the shape the row will be.
 *
 * Every block comes from [BookmarkRowMetrics] — the same declaration the list tiles its rows
 * from, and the one `BookmarkRowTilingTest` already pins against the real rendered row. So the
 * skeleton has a thumbnail exactly where the row will put one, as many title and description
 * lines as the layout allows, a url line if the layout shows one, and a metadata band on the side
 * it belongs. Describing the row a second time by hand is what would let the two drift apart.
 *
 * The bars are deliberately ragged in width. A column of full-width blocks reads as a loading
 * graphic; lines that stop short of the edge read as text.
 */
@Composable
internal fun BookmarkRowSkeleton(
    metrics: BookmarkRowMetrics,
    itemContainerStyle: ItemContainerStyle,
    thumbnailSide: ThumbnailSide,
    showRowDivider: Boolean,
    modifier: Modifier = Modifier,
    /** The height the list holds rows to, when it tiles them. */
    fixedRowHeight: Dp? = null
) {
    val einkMode = LocalEinkMode.current
    // A screenful of skeletons is a screenful of animations, which on e-ink is the worst case
    // there is — so the bars simply sit still. They are the same bars either way: what a panel
    // that ghosts cannot have is the shimmer moving across them.
    val barColor = if (einkMode.animationsDisabled) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    } else {
        val transition = rememberInfiniteTransition(label = "row_skeleton")
        val alpha by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "row_skeleton_alpha"
        )
        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f)
    }

    val density = LocalDensity.current
    // Named apart from Density.toDp so the call below resolves to that one and not to this.
    fun Float.asDp(): Dp = with(density) { toDp() }

    val isFlat = itemContainerStyle == ItemContainerStyle.FLAT
    val verticalPadding = metrics.verticalPaddingPx.asDp()
    val blockSpacing = metrics.blockSpacingPx.asDp()
    val sectionSpacing = metrics.sectionSpacingPx.asDp()

    BookmarkRowContainer(
        isFlat = isFlat,
        showDivider = showRowDivider,
        isSelected = false,
        isActive = false,
        fixedHeight = fixedRowHeight,
        // No outer margin: a card row gets its margin from the list's own wrapper, exactly as the
        // real row does, so adding one here would make every skeleton taller than what it stands for.
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = verticalPadding)
        ) {
            if (metrics.titleAboveThumbnail) {
                SkeletonLines(
                    count = metrics.titleLines,
                    lineHeight = metrics.titleLinePx.asDp(),
                    widths = TITLE_WIDTHS,
                    color = barColor
                )
                Spacer(Modifier.height(sectionSpacing))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val thumbnail: @Composable () -> Unit = {
                    if (metrics.thumbnailPx > 0f) {
                        Box(
                            Modifier
                                .size(metrics.thumbnailPx.asDp())
                                .clip(RoundedCornerShape(8.dp))
                                .background(barColor)
                        )
                    }
                }
                if (thumbnailSide == ThumbnailSide.LEFT) thumbnail()
                Column(Modifier.weight(1f)) {
                    if (!metrics.titleAboveThumbnail) {
                        SkeletonLines(
                            count = metrics.titleLines,
                            lineHeight = metrics.titleLinePx.asDp(),
                                    widths = TITLE_WIDTHS,
                            color = barColor
                        )
                    }
                    if (metrics.urlLinePx > 0f) {
                        Spacer(Modifier.height(blockSpacing))
                        SkeletonBar(metrics.urlLinePx.asDp(), URL_WIDTH, barColor)
                    }
                    if (metrics.descriptionLines > 0) {
                        Spacer(Modifier.height(blockSpacing))
                        SkeletonLines(
                            count = metrics.descriptionLines,
                            lineHeight = metrics.descriptionLinePx.asDp(),
                                    widths = BODY_WIDTHS,
                            color = barColor
                        )
                    }
                    if (metrics.metadataInTextColumn && metrics.metadataPx > 0f) {
                        Spacer(Modifier.height(blockSpacing))
                        SkeletonBar(metrics.metadataPx.asDp(), METADATA_WIDTH, barColor)
                    }
                }
                if (thumbnailSide == ThumbnailSide.RIGHT) thumbnail()
            }

            if (!metrics.metadataInTextColumn && metrics.metadataPx > 0f) {
                Spacer(Modifier.height(sectionSpacing))
                SkeletonBar(metrics.metadataPx.asDp(), METADATA_WIDTH, barColor)
            }
        }
    }
}

/**
 * A run of [count] text lines, each stopping at its own width.
 *
 * The lines sit flush against one another: [lineHeight] is a line height, and the lines of one
 * paragraph are separated by that rather than by the spacing *between* blocks. Putting a gap
 * between them made every skeleton taller than the row it stands for.
 */
@Composable
private fun SkeletonLines(
    count: Int,
    lineHeight: Dp,
    widths: List<Float>,
    color: Color
) {
    repeat(count) { index ->
        SkeletonBar(lineHeight, widths[index % widths.size], color)
    }
}

@Composable
private fun SkeletonBar(height: Dp, widthFraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
    )
}

// A title runs the width and its second line trails off; body text is fuller; a url and a
// metadata band are short.
private val TITLE_WIDTHS = listOf(1f, 0.62f)
private val BODY_WIDTHS = listOf(1f, 0.94f, 0.71f)
private const val URL_WIDTH = 0.45f
private const val METADATA_WIDTH = 0.55f
