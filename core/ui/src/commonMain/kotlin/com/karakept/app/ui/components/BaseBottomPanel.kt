package com.karakept.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.theme.LocalEinkMode
import kotlin.math.roundToInt

/**
 * Reusable bottom panel component that provides:
 * - Swipe-to-dismiss gesture handling
 * - Slide up/down animation
 * - Drag handle UI
 * - Customizable content via lambda
 *
 * Used by ReaderAppearanceBottomPanel and FilterBottomPanel to maintain consistent UX.
 */
@Composable
fun BaseBottomPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    allowDismiss: Boolean = true,
    dismissThreshold: Float = 100f,
    content: @Composable () -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    val einkMode = LocalEinkMode.current

    AnimatedVisibility(
        visible = visible,
        enter = if (einkMode.animationsDisabled) EnterTransition.None else slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            initialOffsetY = { it }
        ),
        exit = if (einkMode.animationsDisabled) ExitTransition.None else slideOutVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            targetOffsetY = { it }
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .pointerInput(allowDismiss) {
                    if (allowDismiss) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY > dismissThreshold) {
                                    onDismiss()
                                } else {
                                    offsetY = 0f
                                }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                val newOffset = offsetY + dragAmount
                                offsetY = if (newOffset > 0) newOffset else 0f
                            }
                        )
                    }
                },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            // The 8dp shadow is what separates the panel from the page; on e-ink it renders as
            // nothing, so the panel needs a drawn edge instead.
            shadowElevation = if (einkMode.highContrast) 0.dp else 8.dp,
            border = if (einkMode.highContrast) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            } else null,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column {
                // Drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                // Custom content
                content()
            }
        }
    }
}
