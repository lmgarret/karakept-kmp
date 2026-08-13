package com.karakept.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.theme.LocalEinkMode
import kotlinx.coroutines.delay

/**
 * Shows [content] with a fade when [animated], and instantly otherwise.
 *
 * E-ink panels cannot render a fade: the intermediate frames either ghost or are dropped
 * entirely, so a fading element reads as a flicker. Snapping is both faster and cleaner there.
 */
@Composable
fun AnimatedVisibilityOrPlain(
    visible: Boolean,
    animated: Boolean,
    modifier: Modifier = Modifier,
    durationMillis: Int = 300,
    content: @Composable () -> Unit
) {
    if (animated) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis)),
            exit = fadeOut(animationSpec = tween(durationMillis)),
            modifier = modifier
        ) {
            content()
        }
    } else if (visible) {
        Box(modifier = modifier) { content() }
    }
}

/**
 * A "working on it" signal: a spinner normally, static text on e-ink.
 *
 * An indeterminate spinner animates forever, which on an e-ink panel means either continuous
 * ghosting or — on devices that throttle refreshes — nothing visible at all. Text says the same
 * thing in one frame.
 */
@Composable
fun BusyIndicator(
    modifier: Modifier = Modifier,
    label: String = "Loading…"
) {
    if (LocalEinkMode.current.animationsDisabled) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
    } else {
        CircularProgressIndicator(modifier = modifier)
    }
}

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
        if (LocalEinkMode.current.animationsDisabled) {
            SteppedDots(dotSize)
        } else {
            WaveDots(dotSize)
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
