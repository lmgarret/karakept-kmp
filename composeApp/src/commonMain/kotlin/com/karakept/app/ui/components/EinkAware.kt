package com.karakept.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.karakept.app.ui.theme.LocalEinkMode

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
