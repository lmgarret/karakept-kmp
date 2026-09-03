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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import coil3.compose.AsyncImage
import com.karakept.app.data.model.PageTurnDirection
import com.karakept.app.ui.components.BackHandler
import com.karakept.app.ui.input.PageTurnDispatcher
import com.karakept.app.ui.theme.LocalEinkMode
import java.io.File
import kotlin.math.abs
import org.koin.compose.koinInject

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
 * Full-screen overlay showing [images] in a swipeable [HorizontalPager], starting on
 * [initialIndex]. Each page owns its own independent zoom/pan/drag state (see
 * [ZoomableImagePage]); the scrim dimming and close button are shared chrome, driven by
 * whichever page is currently active. The pager's own swipe-between-images gesture is
 * disabled while the active page is zoomed in above [MIN_IMAGE_ZOOM], so a pinched-in image
 * can be panned without accidentally flipping to the next one.
 *
 * Deliberately a plain composable rather than a platform [androidx.compose.ui.window.Dialog]:
 * a Dialog opens a separate platform window, and while one holds focus hardware page-turn key
 * events never reach [PageTurnDispatcher] (see [GalleryViewerState]'s doc). Staying inline keeps
 * the reader's window focused, so [PageTurnDispatcher.events] below can repurpose those same
 * buttons to flip between images instead — the caller is expected to disable its own
 * `PageTurnScrollEffect` for the duration so a turn doesn't also scroll the article underneath.
 */
@Composable
internal fun ImageGalleryOverlay(
    images: List<GalleryImage>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return
    val pageTurnDispatcher = koinInject<PageTurnDispatcher>()

    BackHandler(onBack = onDismiss)

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex)
    ) { images.size }
    var currentPageZoomed by remember { mutableStateOf(false) }
    var scrimAlpha by remember { mutableStateOf(SCRIM_ALPHA) }
    var chromeAlpha by remember { mutableStateOf(1f) }

    LaunchedEffect(pagerState, images.size) {
        pageTurnDispatcher.events.collect { direction ->
            val target = when (direction) {
                PageTurnDirection.NEXT -> pagerState.currentPage + 1
                PageTurnDirection.PREVIOUS -> pagerState.currentPage - 1
            }
            if (target !in images.indices) return@collect
            if (pageTurnDispatcher.bindings.value.instantPageTurn) {
                pagerState.scrollToPage(target)
            } else {
                pagerState.animateScrollToPage(target)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !currentPageZoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImagePage(
                galleryImage = images[page],
                page = page,
                onZoomChanged = { zoomed ->
                    if (page == pagerState.currentPage) currentPageZoomed = zoomed
                },
                onDismissProgress = { progress ->
                    if (page == pagerState.currentPage) {
                        scrimAlpha = SCRIM_ALPHA * (1f - progress)
                        chromeAlpha = 1f - progress
                    }
                },
                onDismiss = onDismiss
            )
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

/**
 * A single page of [ImageGalleryOverlay]: pinch-to-zoom, double-tap zoom, mouse
 * scroll-wheel zoom (desktop), drag-to-pan once zoomed in, and swipe-up/tap-to-dismiss,
 * all recognized anywhere on the page — not just over the image's own (unzoomed) layout
 * bounds — since a zoomed-in image's visual size outgrows those bounds but its hit-test
 * area doesn't. When [GalleryImage.caption] is non-blank it's shown centered directly
 * below the image and moves together with it while swiping up to dismiss, but fades out
 * while zoomed in so it doesn't compete with the picture for visibility. Reports its own
 * zoom and dismiss-drag state up via [onZoomChanged]/[onDismissProgress] so the dialog's
 * shared scrim/close-button chrome and pager can react to whichever page is active.
 */
@Composable
private fun ZoomableImagePage(
    galleryImage: GalleryImage,
    page: Int,
    onZoomChanged: (Boolean) -> Unit,
    onDismissProgress: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var idx by remember(page) { mutableIntStateOf(0) }
    // An offline copy on disk comes first, then the remote candidates, each tried in turn
    // as the one before it fails to load.
    val sources = remember(galleryImage) {
        galleryImage.localPath?.let { listOf<Any>(File(it)) }.orEmpty() + galleryImage.urls
    }
    var scale by remember(page) { mutableStateOf(MIN_IMAGE_ZOOM) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(page) { mutableStateOf(IntSize.Zero) }
    var dragOffsetY by remember(page) { mutableStateOf(0f) }
    var isDismissDragging by remember(page) { mutableStateOf(false) }
    // Falls back to the loaded image's own intrinsic size when the HTML declares no
    // width/height, so the viewer can still hug the image instead of filling the screen.
    var loadedAspectRatio by remember(page) { mutableStateOf<Float?>(null) }
    val aspectRatio = galleryImage.dimensions?.aspectRatio ?: loadedAspectRatio

    SideEffect { onZoomChanged(scale > MIN_IMAGE_ZOOM) }

    fun dismissThresholdPx(): Float = containerSize.height.toFloat() * DISMISS_DRAG_THRESHOLD_FRACTION

    // Tracks the finger 1:1 while dragging (snap), then eases back to 0 once released
    // below the dismiss threshold. On e-ink the ease-back is a burst of full-panel
    // refreshes for a position the reader can simply be put back at.
    val einkMode = LocalEinkMode.current
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = if (isDismissDragging || einkMode.animationsDisabled) snap() else tween(250)
    )
    val dismissProgress = dismissDragProgress(animatedDragOffsetY, dismissThresholdPx())
    SideEffect { onDismissProgress(dismissProgress) }
    // Zoomed-in image content isn't clipped to its own frame, so at scale > 1 it can paint
    // past the caption's edge and get covered by its opaque background. Fading the caption
    // out while zoomed (like most photo viewers do with their overlays) keeps it from
    // fighting the picture for visibility; it fades back in once zoomed back out.
    val captionZoomAlpha by animateFloatAsState(
        targetValue = if (scale > MIN_IMAGE_ZOOM) 0f else 1f,
        animationSpec = if (einkMode.animationsDisabled) snap() else tween()
    )

    fun applyZoom(newScale: Float) {
        scale = clampImageZoom(newScale)
        offset = Offset(
            clampImagePan(offset.x, scale, containerSize.width.toFloat()),
            clampImagePan(offset.y, scale, containerSize.height.toFloat())
        )
    }

    // Shared by the image's own gesture detector and the whole-page one below, so a
    // swipe up dismisses consistently whether it starts on the image or outside it.
    fun applyDismissDrag(deltaY: Float) {
        isDismissDragging = true
        dragOffsetY = (dragOffsetY + deltaY).coerceAtMost(0f)
    }

    fun resetDismissDrag() {
        isDismissDragging = false
        dragOffsetY = 0f
    }

    fun endDismissDrag() {
        if (shouldDismissFromDrag(dragOffsetY, dismissThresholdPx())) {
            onDismiss()
        } else {
            resetDismissDrag()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Gestures are handled on the whole page, not just the image's own layout
            // bounds — see the class doc above.
            .pointerInput(page) {
                detectImageTransformGestures(
                    isZoomed = { scale > MIN_IMAGE_ZOOM },
                    onGesture = { pan, zoom ->
                        val newScale = clampImageZoom(scale * zoom)
                        if (newScale <= MIN_IMAGE_ZOOM && zoom == 1f) {
                            // Not zoomed and single-finger: track upward drag only, for
                            // swipe-to-dismiss. Downward drags are clamped away since only
                            // swiping up should close the viewer.
                            applyDismissDrag(pan.y)
                        }
                        scale = newScale
                        offset = Offset(
                            clampImagePan(offset.x + pan.x, scale, containerSize.width.toFloat()),
                            clampImagePan(offset.y + pan.y, scale, containerSize.height.toFloat())
                        )
                    },
                    onGestureEnd = { endDismissDrag() }
                )
            }
            .pointerInput(page) {
                detectTapGestures(
                    onDoubleTap = {
                        applyZoom(if (scale > MIN_IMAGE_ZOOM) MIN_IMAGE_ZOOM else DOUBLE_TAP_ZOOM)
                    },
                    onTap = { if (scale <= MIN_IMAGE_ZOOM) onDismiss() }
                )
            }
            .pointerInput(page) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.first()
                            applyZoom(scale - change.scrollDelta.y * SCROLL_ZOOM_SENSITIVITY)
                            // The overlay is no longer a separate platform window (see class
                            // doc), so an unconsumed mouse-wheel scroll would otherwise also
                            // reach the reader's LazyColumn sitting underneath it.
                            change.consume()
                        }
                    }
                }
            }
    ) {
        val maxImageHeight = maxHeight * 0.8f

        // Centered as a group so the image+caption block sits in the middle of the
        // page rather than the image alone being top-anchored above a stray caption.
        // The dismiss-drag offset is applied here (not just on the image) so the caption
        // stays stuck directly under the image as the whole group is swiped up.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .graphicsLayer(translationY = animatedDragOffsetY)
        ) {
            // Sized to the image's aspect ratio (capped so a very tall image still leaves
            // room for the caption) instead of stretching to fill the screen — unless the
            // ratio isn't known yet, in which case it fills the available space like a
            // loading skeleton until [onSuccess] resolves it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxImageHeight)
                    .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
                    .onSizeChanged { containerSize = it },
                contentAlignment = Alignment.Center
            ) {
                val source = sources.getOrNull(idx)
                if (source == null) {
                    // Every candidate URL for this page failed — show a broken-image
                    // placeholder instead of an empty page.
                    Icon(
                        imageVector = Icons.Default.ImageNotSupported,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                } else {
                    AsyncImage(
                        model = source,
                        contentDescription = galleryImage.alt.ifBlank { null },
                        contentScale = ContentScale.Fit,
                        onSuccess = { state ->
                            if (galleryImage.dimensions == null) {
                                val image = state.result.image
                                loadedAspectRatio = loadedImageDimensions(image.width, image.height)
                                    ?.aspectRatio ?: loadedAspectRatio
                            }
                        },
                        onError = { idx++ },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
            }

            if (!galleryImage.caption.isNullOrBlank()) {
                // Stays fully visible (no fade) while swiping to dismiss — it's carried
                // along with the image via the Column's graphicsLayer above, not left
                // behind, and only disappears once the dialog actually closes. It does
                // fade out while zoomed in, though — see captionZoomAlpha above.
                Text(
                    text = galleryImage.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(captionZoomAlpha)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * Like [androidx.compose.foundation.gestures.detectTransformGestures] but also reports when a
 * past-touch-slop gesture ends, so [ZoomableImagePage] can tell whether a drag crossed its
 * dismiss threshold. Rotation is intentionally unsupported — this viewer never rotates images.
 *
 * A single-finger drag that turns out to be predominantly horizontal while [isZoomed] is
 * false is left unclaimed (never consumed): that's the enclosing [HorizontalPager] wanting to
 * flip to the next/previous image, not this page wanting to pan or dismiss. Pinches (multi-touch
 * or significant zoom motion), predominantly vertical single-finger drags (swipe-to-dismiss),
 * and any single-finger drag while already zoomed in (panning the zoomed image) are always
 * claimed here.
 */
private suspend fun PointerInputScope.detectImageTransformGestures(
    isZoomed: () -> Boolean,
    onGesture: (pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: () -> Unit
) {
    awaitEachGesture {
        var pastTouchSlop = false
        var claimed = false
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
                        val isPinch = zoomMotion > touchSlop || event.changes.size > 1
                        val isVerticalDrag = abs(panAccumulated.y) >= abs(panAccumulated.x)
                        claimed = isPinch || isVerticalDrag || isZoomed()
                    }
                }
                if (pastTouchSlop && claimed) {
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(panChange, zoomChange)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
        if (pastTouchSlop && claimed) {
            onGestureEnd()
        }
    }
}
