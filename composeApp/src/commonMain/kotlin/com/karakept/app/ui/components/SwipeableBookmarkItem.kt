package com.karakept.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.utils.HapticUtils
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Wraps a bookmark item with swipe-to-act functionality.
 * Displays action icons in the background when swiping.
 *
 * @param onActionTriggered Callback with (action, isRightSwipe). isRightSwipe=true means the
 *   right action was triggered (swiped left-to-right).
 */
@Composable
fun SwipeableBookmarkItem(
    leftSwipeAction: SwipeAction = SwipeAction.NONE, // Swipe left reveals this (Trailing)
    rightSwipeAction: SwipeAction = SwipeAction.NONE, // Swipe right reveals this (Leading)
    leftIcon: ImageVector? = null, // Optional override for left action icon
    rightIcon: ImageVector? = null, // Optional override for right action icon
    leftColor: Color? = null, // Optional override for left action background color
    rightColor: Color? = null, // Optional override for right action background color
    leftLabel: String? = null, // Optional label shown under the left action icon
    rightLabel: String? = null, // Optional label shown under the right action icon
    leftIsApplied: Boolean = false, // When true, shows crossed icon + faded color (action already applied)
    rightIsApplied: Boolean = false, // When true, shows crossed icon + faded color (action already applied)
    onActionTriggered: (SwipeAction, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Full-bleed rows with no gutter, for [com.karakept.app.data.model.ItemContainerStyle.FLAT].
     * The row supplies its own text inset and a divider that must reach both edges.
     */
    flat: Boolean = false,
    content: @Composable () -> Unit
) {
    val currentOnActionTriggered by androidx.compose.runtime.rememberUpdatedState(onActionTriggered)
    val currentLeftAction by androidx.compose.runtime.rememberUpdatedState(leftSwipeAction)
    val currentRightAction by androidx.compose.runtime.rememberUpdatedState(rightSwipeAction)

    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffset = remember { Animatable(0f) }
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val maxSwipeDistance = with(LocalDensity.current) { 250.dp.toPx() } // Allow dragging further
    val scope = rememberCoroutineScope()
    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    
    // Sync animated offset with offsetX
    LaunchedEffect(offsetX) {
        animatedOffset.snapTo(offsetX)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // Match bookmark layout padding; flat rows are full-bleed instead.
                if (flat) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
    ) {
        // Background layer showing action icons
        Row(
            modifier = Modifier
                .matchParentSize()  // Match the size of the parent Box (which is sized by content)
                .background(
                    when {
                        offsetX > swipeThreshold -> {
                            val base = rightColor ?: rightSwipeAction.getColor()
                            if (rightIsApplied) base.copy(alpha = 0.55f) else base
                        }
                        offsetX < -swipeThreshold -> {
                            val base = leftColor ?: leftSwipeAction.getColor()
                            if (leftIsApplied) base.copy(alpha = 0.55f) else base
                        }
                        else -> Color.Transparent
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left action (visible when swiping right)
            if (offsetX > 0 && rightSwipeAction != SwipeAction.NONE) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = rightIcon ?: rightSwipeAction.getIcon(),
                                contentDescription = rightSwipeAction.displayName,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            if (rightIsApplied) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, 0f),
                                        strokeWidth = 5f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                        if (rightLabel != null) {
                            Text(
                                text = rightLabel,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Right action (visible when swiping left)
            if (offsetX < 0 && leftSwipeAction != SwipeAction.NONE) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = leftIcon ?: leftSwipeAction.getIcon(),
                                contentDescription = leftSwipeAction.displayName,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            if (leftIsApplied) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, 0f),
                                        strokeWidth = 5f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                        if (leftLabel != null) {
                            Text(
                                text = leftLabel,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Foreground content with gesture handling
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                // Check if threshold was met; capture direction before resetting
                                val isRightSwipe = offsetX > swipeThreshold
                                val actionToTrigger = when {
                                    isRightSwipe && currentRightAction != SwipeAction.NONE -> currentRightAction
                                    offsetX < -swipeThreshold && currentLeftAction != SwipeAction.NONE -> currentLeftAction
                                    else -> null
                                }

                                // Trigger action if threshold met
                                actionToTrigger?.let {
                                    currentOnActionTriggered(it, isRightSwipe)
                                }

                                // Reset position
                                offsetX = 0f
                                hasTriggeredHaptic = false
                                animatedOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX = 0f
                                hasTriggeredHaptic = false
                                animatedOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = (offsetX + dragAmount).coerceIn(-maxSwipeDistance, maxSwipeDistance)
                            offsetX = newOffset
                            
                            // Trigger haptic when crossing threshold
                            if (!hasTriggeredHaptic) {
                                if ((newOffset > swipeThreshold && rightSwipeAction != SwipeAction.NONE) ||
                                    (newOffset < -swipeThreshold && leftSwipeAction != SwipeAction.NONE)) {
                                    HapticUtils.performMedium()
                                    hasTriggeredHaptic = true
                                }
                            } else {
                                // Reset haptic flag if we go back below threshold
                                if (kotlin.math.abs(newOffset) < swipeThreshold) {
                                    hasTriggeredHaptic = false
                                }
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}


