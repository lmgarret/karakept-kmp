package com.karakept.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A vertical divider with a wider hit target that supports horizontal dragging.
 * Renders as a standard [VerticalDivider] but widens the hit area to 8dp for easier grabbing.
 * Shows a subtle highlight on hover.
 *
 * @param onDrag Called with the horizontal drag delta in dp.
 */
@Composable
fun DraggableDivider(
    modifier: Modifier = Modifier,
    onDrag: (deltaDp: Float) -> Unit
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .width(8.dp)
            .fillMaxHeight()
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val deltaDp = with(density) { dragAmount.x.toDp().value }
                    onDrag(deltaDp)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        VerticalDivider(
            modifier = if (isHovered) Modifier.width(3.dp) else Modifier,
            color = if (isHovered)
                MaterialTheme.colorScheme.outlineVariant
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
