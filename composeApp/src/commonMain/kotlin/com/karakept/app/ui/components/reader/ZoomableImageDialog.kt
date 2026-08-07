package com.karakept.app.ui.components.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

internal const val MIN_IMAGE_ZOOM = 1f
internal const val MAX_IMAGE_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 3f
private const val SCROLL_ZOOM_SENSITIVITY = 0.2f

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
 * Full-screen dialog that shows [url] on a black scrim, supporting pinch-to-zoom,
 * double-tap zoom, mouse scroll-wheel zoom (desktop), and drag-to-pan once zoomed in.
 * Tapping the image while at [MIN_IMAGE_ZOOM] dismisses the dialog.
 */
@Composable
fun ZoomableImageDialog(
    url: String,
    alt: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableStateOf(MIN_IMAGE_ZOOM) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }

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
                .background(Color.Black)
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = clampImageZoom(scale * zoom)
                        offset = Offset(
                            clampImagePan(offset.x + pan.x, scale, containerSize.width.toFloat()),
                            clampImagePan(offset.y + pan.y, scale, containerSize.height.toFloat())
                        )
                    }
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
                        translationY = offset.y
                    )
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
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
