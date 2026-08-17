package com.karakept.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.theme.LocalEinkMode
import kotlinx.coroutines.delay

/**
 * Shows [content] with a fade when [animated], and instantly otherwise.
 *
 * E-ink panels cannot render a fade: the intermediate frames either ghost or are dropped
 * entirely, so a fading element reads as a flicker. Snapping is both faster and cleaner there.
 *
 * [enter] / [exit] override the fade for callers that animate something richer off e-ink — the
 * e-ink branch shows or hides the content either way.
 */
@Composable
fun AnimatedVisibilityOrPlain(
    visible: Boolean,
    animated: Boolean,
    modifier: Modifier = Modifier,
    durationMillis: Int = 300,
    enter: EnterTransition = fadeIn(animationSpec = tween(durationMillis)),
    exit: ExitTransition = fadeOut(animationSpec = tween(durationMillis)),
    content: @Composable () -> Unit
) {
    if (animated) {
        AnimatedVisibility(
            visible = visible,
            enter = enter,
            exit = exit,
            modifier = modifier
        ) {
            content()
        }
    } else if (visible) {
        Box(modifier = modifier) { content() }
    }
}

/**
 * How an element that floats above the page separates itself from it.
 *
 * Exactly one of the two carries that job: a drop shadow normally, a 1dp outline under e-ink
 * high contrast — where a shadow is a grey gradient the panel cannot render and every surface
 * role has already collapsed to the page colour, so a shadow-only element has no edge at all.
 */
@Immutable
data class FloatingSurfaceStyle(
    val shadowElevation: Dp,
    val outlined: Boolean
)

/**
 * Resolves [FloatingSurfaceStyle] for the current display, given the [shadowElevation] the
 * element uses off e-ink.
 */
fun floatingSurfaceStyle(highContrast: Boolean, shadowElevation: Dp): FloatingSurfaceStyle =
    if (highContrast) {
        FloatingSurfaceStyle(shadowElevation = 0.dp, outlined = true)
    } else {
        FloatingSurfaceStyle(shadowElevation = shadowElevation, outlined = false)
    }

/** [floatingSurfaceStyle] read off the ambient e-ink mode. */
@Composable
fun floatingSurfaceStyle(shadowElevation: Dp): FloatingSurfaceStyle =
    floatingSurfaceStyle(LocalEinkMode.current.highContrast, shadowElevation)

/** The outline that stands in for the shadow, or null when the shadow is doing the work. */
@Composable
fun FloatingSurfaceStyle.borderStroke(): BorderStroke? =
    if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null

/**
 * A [FloatingActionButton] that stays visible on an e-ink page.
 *
 * The Material FAB leans entirely on elevation to lift itself off the content: a tonal container
 * plus a 6dp shadow. Under [com.karakept.app.ui.theme.EinkMode.highContrast] both are gone — the
 * container is the page colour and the shadow does not render — leaving an icon floating with no
 * button around it. Swapping the shadow for an outline puts the edge back.
 *
 * **Use instead of [FloatingActionButton] everywhere.**
 */
@Composable
fun EinkAwareFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable () -> Unit
) {
    if (floatingSurfaceStyle(FAB_ELEVATION).outlined) {
        OutlinedFab(
            onClick = onClick,
            modifier = modifier,
            shape = FloatingActionButtonDefaults.shape,
            containerColor = containerColor,
            contentColor = contentColor,
            size = FAB_SIZE,
            content = content
        )
    } else {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
            content = content
        )
    }
}

/**
 * [EinkAwareFab] in the small size, for secondary affordances — scroll-to-top above all.
 *
 * **Use instead of [SmallFloatingActionButton] everywhere.**
 */
@Composable
fun EinkAwareSmallFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit
) {
    if (floatingSurfaceStyle(FAB_ELEVATION).outlined) {
        OutlinedFab(
            onClick = onClick,
            modifier = modifier,
            shape = FloatingActionButtonDefaults.smallShape,
            containerColor = containerColor,
            contentColor = contentColor,
            size = SMALL_FAB_SIZE,
            content = content
        )
    } else {
        SmallFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
            content = content
        )
    }
}

/**
 * The e-ink half of [EinkAwareFab] / [EinkAwareSmallFab]: the same button drawn as the outlined,
 * unshaded, untinted surface it reduces to under high contrast.
 *
 * A FAB cannot take the outline from an outer `Modifier.border` instead. `Surface(onClick)` centres
 * its visual bounds inside a 48dp minimum touch target, so on the 40dp small FAB an outer border
 * lands 4dp clear of the fill and rings it with a transparent gap. Handing the border to the same
 * `Surface` that paints the background is the only place the two are guaranteed to agree.
 */
@Composable
private fun OutlinedFab(
    onClick: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Button },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.defaultMinSize(minWidth = size, minHeight = size),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

private val FAB_ELEVATION = 6.dp
private val FAB_SIZE = 56.dp
private val SMALL_FAB_SIZE = 40.dp
private val SNACKBAR_ELEVATION = 6.dp

/**
 * How long a snackbar of this [duration] stays up.
 *
 * Material keeps the same mapping internal to `SnackbarHost`, so the e-ink host — which cannot
 * reuse that host at all (see [EinkAwareSnackbarHost]) — has to carry its own copy of the timing
 * or snackbars would never dismiss themselves.
 */
fun snackbarDurationMillis(duration: SnackbarDuration): Long = when (duration) {
    SnackbarDuration.Indefinite -> Long.MAX_VALUE
    SnackbarDuration.Long -> 10_000L
    SnackbarDuration.Short -> 4_000L
}

/**
 * A snackbar host that neither fades nor floats on an e-ink page.
 *
 * Two separate problems, one per e-ink toggle. Material's `SnackbarHost` fades *and* scales its
 * snackbar in and out with a spec it does not expose, so under `animationsDisabled` the host
 * itself is replaced — hence the dismiss timer here, which is the one thing that host was still
 * doing for us. Under `highContrast` the visual is replaced too: `Snackbar` hardcodes a 6dp
 * shadow, and its `inverseSurface` container is a solid block of ink on the e-ink scheme — a lot
 * of ink to lay down and then ghost for a message that disappears after four seconds.
 *
 * **Use instead of `SnackbarHost` everywhere.**
 */
@Composable
fun EinkAwareSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    if (!LocalEinkMode.current.animationsDisabled) {
        SnackbarHost(hostState, modifier) { EinkAwareSnackbar(it) }
        return
    }

    val data = hostState.currentSnackbarData
    LaunchedEffect(data) {
        if (data != null) {
            delay(snackbarDurationMillis(data.visuals.duration))
            data.dismiss()
        }
    }
    if (data != null) {
        Box(modifier) { EinkAwareSnackbar(data) }
    }
}

@Composable
private fun EinkAwareSnackbar(data: SnackbarData) {
    val style = floatingSurfaceStyle(SNACKBAR_ELEVATION)
    if (!style.outlined) {
        Snackbar(data)
        return
    }
    Surface(
        // Material's own Snackbar insets itself by the same amount; the host adds none.
        modifier = Modifier.padding(12.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = style.borderStroke(),
        shadowElevation = style.shadowElevation
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp)
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (data.visuals.withDismissAction) {
                IconButton(onClick = { data.dismiss() }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
                }
            }
        }
    }
}

/**
 * A "working on it" signal: a spinner normally, stepped dots on e-ink.
 *
 * An indeterminate spinner animates forever, which on an e-ink panel means either continuous
 * ghosting or — on devices that throttle refreshes — nothing visible at all. [LoadingDotsIndicator]
 * says the same thing in one discrete repaint at a time.
 */
@Composable
fun BusyIndicator(
    modifier: Modifier = Modifier,
    label: String = "Loading…"
) {
    if (LocalEinkMode.current.animationsDisabled) {
        LoadingDotsIndicator(modifier = modifier, label = label, dotSize = 8.dp)
    } else {
        CircularProgressIndicator(modifier = modifier)
    }
}

/**
 * Wraps [content] in a pull-to-refresh container, or in a plain `Box` when [enabled] is false or
 * e-ink mode is on.
 *
 * Pull-to-refresh is a gesture that tracks a finger across many frames and drives a spinner that
 * keeps animating until the refresh returns — the two things an e-ink panel handles worst. The
 * gesture is also unfamiliar on readers, whose page-turn-first interaction model has no
 * overscroll. Screens that turn it off must expose an explicit refresh button instead; see
 * [com.karakept.app.ui.screens.main.MainScreenTopBar] and
 * [com.karakept.app.ui.screens.main.HighlightsListContent].
 *
 * Pass `enabled = !isDesktop` where a pointer has nothing to pull with either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    if (shouldUsePullToRefresh(gestureCapable = enabled, einkMode = LocalEinkMode.current.enabled)) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            content = content
        )
    } else {
        Box(modifier = modifier, content = content)
    }
}

/**
 * Whether the pull-to-refresh gesture is live, given a surface that can host it at all
 * ([gestureCapable] — false on desktop, where there is no finger to pull with).
 */
fun shouldUsePullToRefresh(gestureCapable: Boolean, einkMode: Boolean): Boolean =
    gestureCapable && !einkMode

/**
 * Whether a screen must carry an explicit refresh button in its top bar.
 *
 * The inverse of [shouldUsePullToRefresh] for a touch surface: refresh is never unreachable —
 * exactly one of the gesture and the button is available at any time.
 */
fun shouldShowRefreshButton(isDesktop: Boolean, einkMode: Boolean): Boolean =
    !shouldUsePullToRefresh(gestureCapable = !isDesktop, einkMode = einkMode)

/**
 * A "content is coming" signal for a full loading slot (an empty screen, a body area, an image
 * placeholder) — three dots that fill in sequence, replacing a skeleton without lying about the
 * shape of the content that hasn't arrived yet.
 *
 * A shimmering skeleton is a continuous animation, which on e-ink means either permanent ghosting
 * or nothing visible at all (its tonal fill collapses into the page color under high contrast).
 * On e-ink this instead steps a single dot from empty to filled on a plain delay loop — one
 * discrete repaint at a time, no interpolation to smear between frames. Off e-ink the same shape
 * plays as a smooth continuous wave.
 */
@Composable
fun LoadingDotsIndicator(
    modifier: Modifier = Modifier,
    label: String? = null,
    dotSize: Dp = 10.dp
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LoadingDots(dotSize)
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * [LoadingDotsIndicator] laid out on one line, for slots too short for the stacked form — a
 * progress-bar strip, a drawer row's trailing status, a button's leading icon.
 *
 * Same dots and the same e-ink branch; only the label moves beside them instead of below.
 */
@Composable
fun InlineLoadingDots(
    modifier: Modifier = Modifier,
    label: String? = null,
    dotSize: Dp = 6.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingDots(dotSize)
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A centred card carrying a screen-level "busy" state — a sync, a refresh — on e-ink.
 *
 * The thin progress strip these replace sits at the very top edge of the content, which on a
 * high-contrast monochrome panel is easy to overlook entirely: there is no colour to catch the
 * eye and no motion the display can render smoothly. One deliberate block in the middle of the
 * page is the only placement that reliably reads as "something is happening".
 *
 * Draws nothing itself when not busy — callers gate on their own state — and takes no pointer
 * input, so the list underneath stays scrollable while it is up.
 *
 * E-ink only. Off e-ink the strip is fine and stays where it is; see `SyncProgressBar`.
 */
@Composable
fun FloatingBusyCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // Under highContrast every surface role is the page colour, so elevation separates
    // nothing — the outline is what makes this read as a card floating over the content.
    val style = floatingSurfaceStyle(6.dp)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = style.shadowElevation,
        border = style.borderStroke()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun LoadingDots(dotSize: Dp) {
    if (LocalEinkMode.current.animationsDisabled) {
        SteppedDots(dotSize)
    } else {
        WaveDots(dotSize)
    }
}

@Composable
private fun WaveDots(dotSize: Dp) {
    val transition = rememberInfiniteTransition(label = "loadingDotsWave")
    val filledColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Row(horizontalArrangement = Arrangement.spacedBy(dotSize / 2)) {
        repeat(3) { index ->
            val fraction by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    initialStartOffset = StartOffset(index * 200)
                ),
                label = "loadingDot$index"
            )
            val wave = if (fraction <= 0.5f) fraction * 2f else (1f - fraction) * 2f
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(lerp(emptyColor, filledColor, wave))
            )
        }
    }
}

@Composable
private fun SteppedDots(dotSize: Dp) {
    var active by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            active = (active + 1) % 3
        }
    }
    val filledColor = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Row(horizontalArrangement = Arrangement.spacedBy(dotSize / 2)) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .then(
                        if (index == active) Modifier.background(filledColor)
                        else Modifier.border(1.5.dp, outline, CircleShape)
                    )
            )
        }
    }
}

/**
 * Jumps to the top of the list, skipping the scroll animation when [instant].
 */
suspend fun LazyListState.scrollToTop(instant: Boolean) {
    if (instant) scrollToItem(0, 0) else animateScrollToItem(0, 0)
}

/**
 * Opens the navigation drawer, snapping instead of sliding when [instant].
 *
 * `ModalNavigationDrawer` has no way to turn its slide off — the sheet and the scrim are both
 * driven off the drawer offset — so the only lever is to move that offset in one step.
 */
suspend fun DrawerState.openDrawer(instant: Boolean) {
    if (instant) snapTo(DrawerValue.Open) else open()
}

/** Closes the navigation drawer, snapping instead of sliding when [instant]. */
suspend fun DrawerState.closeDrawer(instant: Boolean) {
    if (instant) snapTo(DrawerValue.Closed) else close()
}

/**
 * Border for a modal (`AlertDialog`, `ModalBottomSheet`) in high-contrast e-ink mode.
 *
 * Under `highContrast` a modal's shadow and its tonal surface both collapse into the page
 * colour, so without a drawn edge it no longer stands out from the content underneath — the same
 * problem `BaseBottomPanel` already solves for custom panels. [shape] must match the modal's own
 * `shape` so the border traces its actual corners.
 */
@Composable
fun einkModalBorder(shape: Shape): Modifier {
    return if (LocalEinkMode.current.highContrast) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    } else {
        Modifier
    }
}

/**
 * Outline [BorderStroke] for components with a native `border` parameter (`Surface`, `Card`,
 * `DropdownMenu`) in high-contrast e-ink mode — `null` outside it, same rationale as
 * [einkModalBorder].
 */
@Composable
fun einkOutlineBorder(): BorderStroke? {
    return if (LocalEinkMode.current.highContrast) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    } else {
        null
    }
}

/**
 * Trailing-edge border for a full-height side panel (e.g. the navigation drawer) in
 * high-contrast e-ink mode.
 *
 * Unlike [einkModalBorder], only the edge that actually borders the rest of the content gets a
 * drawn line — the panel's other edges already sit flush against the screen boundary, so a full
 * border there would be redundant.
 */
@Composable
fun einkTrailingEdgeBorder(): Modifier {
    if (!LocalEinkMode.current.highContrast) return Modifier
    val color = MaterialTheme.colorScheme.outline
    return Modifier.drawWithContent {
        drawContent()
        val strokePx = 1.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width - strokePx / 2, 0f),
            end = Offset(size.width - strokePx / 2, size.height),
            strokeWidth = strokePx
        )
    }
}
