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
 * skeleton has a thumbnail exactly where the row will put one, as many title lines as the layout
 * allows, a url line if the layout shows one, and a metadata band on the side it belongs.
 * Describing the row a second time by hand is what would let the two drift apart.
 *
 * Only the title is ruled into lines, and the description is a single block: ruling every line of
 * an excerpt is more detail than a placeholder needs, and one block reads more calmly under a
 * finger that is still moving.
 *
 * The metadata band is a thin rule rather than a block. What sits there is tag chips, a date and a
 * reading time — several small things with air between them — and a block that tall reads as a
 * paragraph. The rule does not line up with where those items will land, which is the price of
 * not pretending to know how many chips a bookmark carries.
 *
 * Each bar is inked to a fraction of the space the row gives that element, the rest standing for
 * the leading a line of text carries. Bars drawn at the full line height leave nothing between
 * them and run together into a slab.
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
    // Named apart from Density.toDp so the call inside resolves to that one and not to this.
    fun Float.asDp(): Dp = with(density) { toDp() }

    val isFlat = itemContainerStyle == ItemContainerStyle.FLAT
    val blockSpacing = metrics.blockSpacingPx.asDp()
    val sectionSpacing = metrics.sectionSpacingPx.asDp()

    BookmarkRowContainer(
        isFlat = isFlat,
        showDivider = showRowDivider,
        isSelected = false,
        isActive = false,
        fixedHeight = fixedRowHeight,
        // The margin the row's wrapper would have given it. A skeleton is rendered straight into
        // the list item — there is no swipe or quick-action wrapper around a row that has not
        // arrived — so without this it is 32dp wider and 16dp taller than the row it stands for,
        // and a screenful of them runs together with no gaps.
        modifier = modifier.bookmarkRowMargin(isFlat).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = metrics.verticalPaddingPx.asDp())
        ) {
            if (metrics.titleAboveThumbnail) {
                SkeletonTitle(metrics.titleLinePx.asDp(), metrics.titleLines, barColor)
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
                        SkeletonTitle(metrics.titleLinePx.asDp(), metrics.titleLines, barColor)
                    }
                    if (metrics.urlLinePx > 0f) {
                        Spacer(Modifier.height(blockSpacing))
                        SkeletonBar(metrics.urlLinePx.asDp(), LINE_INK, URL_WIDTH, barColor)
                    }
                    if (metrics.descriptionLines > 0) {
                        Spacer(Modifier.height(blockSpacing))
                        SkeletonBar(
                            boxHeight =
                                (metrics.descriptionLinePx * metrics.descriptionLines).asDp(),
                            inkFraction = BLOCK_INK,
                            widthFraction = 1f,
                            color = barColor
                        )
                    }
                    if (metrics.metadataInTextColumn && metrics.metadataPx > 0f) {
                        Spacer(Modifier.height(blockSpacing))
                        SkeletonMetadata(metrics, blockSpacing, barColor) { asDp() }
                    }
                }
                if (thumbnailSide == ThumbnailSide.RIGHT) thumbnail()
            }

            if (!metrics.metadataInTextColumn && metrics.metadataPx > 0f) {
                Spacer(Modifier.height(sectionSpacing))
                SkeletonMetadata(metrics, blockSpacing, barColor) { asDp() }
            }
        }
    }
}

/**
 * The metadata band: an excerpt where the layout puts one there, a row of tag chips, and the
 * trailing row — a date at the leading edge, a link and a reading time at the trailing one.
 *
 * Drawn as the three rows it really is. One bar the height of all of them put a thin rule in the
 * middle of a tall box, which reads as an oversized gap under the description and a tag row too
 * small for what lands in it.
 */
@Composable
private fun SkeletonMetadata(
    metrics: BookmarkRowMetrics,
    blockSpacing: Dp,
    color: Color,
    asDp: Float.() -> Dp
) {
    if (metrics.metadataDescriptionPx > 0f) {
        SkeletonBar(metrics.metadataDescriptionPx.asDp(), BLOCK_INK, 1f, color)
        Spacer(Modifier.height(blockSpacing))
    }
    if (metrics.tagsPx > 0f) {
        // A chip is a filled surface, so it covers nearly the whole line it sits on.
        SkeletonBar(metrics.tagsPx.asDp(), CHIP_INK, TAGS_WIDTH, color)
        Spacer(Modifier.height(blockSpacing))
    }
    if (metrics.metadataLinePx > 0f) {
        val lineHeight = metrics.metadataLinePx.asDp()
        Row(
            modifier = Modifier.fillMaxWidth().height(lineHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Inked to what really lands there rather than to a fixed height: the row is as tall
            // as the reading-time badge, and a short mark centred in it leaves as much space
            // above and below as the mark itself.
            SkeletonMark(DATE_WIDTH, lineHeight * LINE_INK, color)
            Spacer(Modifier.weight(1f))
            SkeletonMark(LINK_WIDTH, lineHeight * LINE_INK, color)
            Spacer(Modifier.width(6.dp))
            // A badge, so it fills its line the way a chip does.
            SkeletonMark(TIME_WIDTH, lineHeight * CHIP_INK, color)
        }
    }
}

/** One of the small marks in the trailing row — a date, a link, a reading time. */
@Composable
private fun RowScope.SkeletonMark(widthFraction: Float, height: Dp, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
    )
}

/**
 * The title, as [count] lines of [lineHeight].
 *
 * The lines sit flush against one another, because [lineHeight] is a line height: what separates
 * them is the leading inside each one, not a gap between them.
 */
@Composable
private fun SkeletonTitle(lineHeight: Dp, count: Int, color: Color) {
    repeat(count) { index ->
        SkeletonBar(lineHeight, LINE_INK, TITLE_WIDTHS[index % TITLE_WIDTHS.size], color)
    }
}

/**
 * A bar inked to [inkFraction] of [boxHeight] and centred in it.
 *
 * The box is the space the row's geometry gives this element; the bar is the part of it that
 * would be covered in text.
 */
@Composable
private fun SkeletonBar(boxHeight: Dp, inkFraction: Float, widthFraction: Float, color: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().height(boxHeight),
        // Text starts at the left edge, so a bar standing for a part-width line has to as well.
        // Centred, a short second title line floated in the middle of the row.
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth(widthFraction)
                .height(boxHeight * inkFraction)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

/** A title runs the width and its second line trails off; a url is short. */
private val TITLE_WIDTHS = listOf(1f, 0.62f)
private const val URL_WIDTH = 0.45f

/** Two or three chips' worth of the row, which is what a bookmark usually carries. */
private const val TAGS_WIDTH = 0.45f

/** The trailing row: a date at the leading edge, a link and a reading time at the other. */
private const val DATE_WIDTH = 0.18f
private const val LINK_WIDTH = 0.14f
private const val TIME_WIDTH = 0.1f

/** How much of its box a line of text covers, and how much of one a solid block does. */
private const val LINE_INK = 0.56f
private const val BLOCK_INK = 0.84f

/** A chip is a filled surface, so it covers nearly the whole line it sits on. */
private const val CHIP_INK = 0.8f
