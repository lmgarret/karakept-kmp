package com.karakept.app.ui.components.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.ui.icons.AppIcons

/**
 * The gap a card row sits in.
 *
 * Applied by whatever wraps the row — the swipe wrapper, the quick-action wrapper — rather than
 * by the row itself, so everything that stands in a row's place has to apply it too. A skeleton
 * without it is 32dp wider and 16dp taller than the row it is holding a place for, and a column
 * of them has no gaps at all. [BookmarkRowMetrics.wrapperPaddingPx] counts its vertical half, so
 * the number lives here and the height that follows from it is declared there.
 *
 * Flat rows are full-bleed by definition and take none of it.
 */
fun Modifier.bookmarkRowMargin(isFlat: Boolean): Modifier =
    if (isFlat) this else padding(horizontal = 16.dp, vertical = 8.dp)

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
