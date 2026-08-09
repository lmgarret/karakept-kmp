package com.karakept.app.ui.components.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlin.math.abs

internal const val MIN_IMAGE_ZOOM = 1f
internal const val MAX_IMAGE_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 3f
private const val SCROLL_ZOOM_SENSITIVITY = 0.2f

/** Same dimming alpha as the reader's highlight-selection overlay, for visual consistency. */
private const val SCRIM_ALPHA = 0.6f

/** Upward drag distance, as a fraction of the container height, that dismisses the viewer. */
private const val DISMISS_DRAG_THRESHOLD_FRACTION = 0.15f

/** Clamps a pinch/scroll zoom factor to the range the full-screen image viewer allows. */
internal fun clampImageZoom(scale: Float): Float = scale.coerceIn(MIN_IMAGE_ZOOM, MAX_IMAGE_ZOOM)

/**
 * Clamps a pan offset component so a zoomed-in image never leaves empty space between
 * its edge and the container's edge along that axis. At [MIN_IMAGE_ZOOM] the image exactly
 * fills the container, so no panning is allowed.
 */
internal fun clampImagePan(offset: Float, scale: Float, containerDimension: Float): Float {
    if (scale <= MIN_IMAGE_ZOOM) return 0f
    val maxOffset = containerDimension * (scale - 1f) / 2f
    return offset.coerceIn(-maxOffset, maxOffset)
}

/**
 * How far along the swipe-to-dismiss drag we are, from 0 (at rest) to 1 (dismiss threshold
 * reached). [dragOffsetY] is negative while dragging up; [thresholdPx] non-positive means no
 * threshold is known yet (e.g. before the first layout pass), so progress is reported as 0.
 */
internal fun dismissDragProgress(dragOffsetY: Float, thresholdPx: Float): Float {
    if (thresholdPx <= 0f || dragOffsetY >= 0f) return 0f
    return (-dragOffsetY / thresholdPx).coerceIn(0f, 1f)
}

/** Whether a completed upward drag traveled far enough to dismiss the viewer. */
internal fun shouldDismissFromDrag(dragOffsetY: Float, thresholdPx: Float): Boolean =
    thresholdPx > 0f && dragOffsetY < -thresholdPx

/**
 * Full-screen dialog that shows [url] on a dimmed scrim — the same dimming used when a
 * highlight is selected, for visual consistency — supporting pinch-to-zoom, double-tap zoom,
 * mouse scroll-wheel zoom (desktop), drag-to-pan once zoomed in, and swipe-up-to-dismiss when
 * not zoomed. Tapping the image at [MIN_IMAGE_ZOOM] also dismisses it. When [caption] is
 * non-blank (the image's `<figcaption>`, if any) it's shown centered below the image.
 */
@Composable
fun ZoomableImageDialog(
    url: String,
    alt: String,
    caption: String? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableStateOf(MIN_IMAGE_ZOOM) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        var dragOffsetY by remember { mutableStateOf(0f) }
        var isDismissDragging by remember { mutableStateOf(false) }

        fun dismissThresholdPx(): Float = containerSize.height.toFloat() * DISMISS_DRAG_THRESHOLD_FRACTION

        // Tracks the finger 1:1 while dragging (snap), then eases back to 0 once released
        // below the dismiss threshold.
        val animatedDragOffsetY by animateFloatAsState(
            targetValue = dragOffsetY,
            animationSpec = if (isDismissDragging) snap() else tween(250)
        )
        val dismissProgress = dismissDragProgress(animatedDragOffsetY, dismissThresholdPx())
        val scrimAlpha = SCRIM_ALPHA * (1f - dismissProgress)
        // Close button and caption fade out together as the dismiss-drag progresses.
        val chromeAlpha = 1f - dismissProgress

        fun applyZoom(newScale: Float) {
            scale = clampImageZoom(newScale)
            offset = Offset(
                clampImagePan(offset.x, scale, containerSize.width.toFloat()),
                clampImagePan(offset.y, scale, containerSize.height.toFloat())
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Only the image area drives zoom/pan/dismiss gestures and their bounds, so
                // the caption below stays a normal, non-interactive block of text.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { containerSize = it }
                        .pointerInput(Unit) {
                            detectImageTransformGestures(
                                onGesture = { pan, zoom ->
                                    val newScale = clampImageZoom(scale * zoom)
                                    if (newScale <= MIN_IMAGE_ZOOM && zoom == 1f) {
                                        // Not zoomed and single-finger: track upward drag only,
                                        // for swipe-to-dismiss. Downward drags are clamped away
                                        // since only swiping up should close the viewer.
                                        isDismissDragging = true
                                        dragOffsetY = (dragOffsetY + pan.y).coerceAtMost(0f)
                                    }
                                    scale = newScale
                                    offset = Offset(
                                        clampImagePan(offset.x + pan.x, scale, containerSize.width.toFloat()),
                                        clampImagePan(offset.y + pan.y, scale, containerSize.height.toFloat())
                                    )
                                },
                                onGestureEnd = {
                                    if (shouldDismissFromDrag(dragOffsetY, dismissThresholdPx())) {
                                        onDismiss()
                                    } else {
                                        isDismissDragging = false
                                        dragOffsetY = 0f
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    applyZoom(if (scale > MIN_IMAGE_ZOOM) MIN_IMAGE_ZOOM else DOUBLE_TAP_ZOOM)
                                },
                                onTap = { if (scale <= MIN_IMAGE_ZOOM) onDismiss() }
                            )
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Scroll) {
                                        val scrollDelta = event.changes.first().scrollDelta.y
                                        applyZoom(scale - scrollDelta * SCROLL_ZOOM_SENSITIVITY)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = alt.ifBlank { null },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y + animatedDragOffsetY
                            )
                    )
                }

                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(chromeAlpha)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .alpha(chromeAlpha)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Like [androidx.compose.foundation.gestures.detectTransformGestures] but also reports when a
 * past-touch-slop gesture ends, so [ZoomableImageDialog] can tell whether a drag crossed its
 * dismiss threshold. Rotation is intentionally unsupported — this viewer never rotates images.
 */
private suspend fun PointerInputScope.detectImageTransformGestures(
    onGesture: (pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: () -> Unit
) {
    awaitEachGesture {
        var pastTouchSlop = false
        var zoomAccumulated = 1f
        var panAccumulated = Offset.Zero
        val touchSlop = viewConfiguration.touchSlop
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!pastTouchSlop) {
                    zoomAccumulated *= zoomChange
                    panAccumulated += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoomAccumulated) * centroidSize
                    val panMotion = panAccumulated.getDistance()
                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }
                if (pastTouchSlop) {
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(panChange, zoomChange)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
        if (pastTouchSlop) {
            onGestureEnd()
        }
    }
}
