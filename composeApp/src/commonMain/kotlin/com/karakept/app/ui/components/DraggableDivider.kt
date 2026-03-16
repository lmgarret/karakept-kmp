package com.karakept.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A vertical divider with a wider invisible hit target that supports horizontal dragging.
 * Renders as a standard 1dp [VerticalDivider] in a 12dp transparent hit area.
 *
 * @param lineAlignment Where the 1dp line sits inside the hit area. Default is [Alignment.Center].
 *   Use [Alignment.CenterStart] to place the line flush against the left neighbor.
 * @param onDrag Called with the horizontal drag delta in dp on each drag event.
 */
@Composable
fun DraggableDivider(
    modifier: Modifier = Modifier,
    lineAlignment: Alignment = Alignment.Center,
    onDrag: (deltaDp: Float) -> Unit
) {
    val density = LocalDensity.current
    val currentOnDrag by rememberUpdatedState(onDrag)

    Box(
        modifier = modifier
            .width(12.dp)
            .fillMaxHeight()
            .horizontalResizeCursor()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val deltaDp = with(density) { dragAmount.x.toDp().value }
                    currentOnDrag(deltaDp)
                }
            },
        contentAlignment = lineAlignment
    ) {
        VerticalDivider()
    }
}
