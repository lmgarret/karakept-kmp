package com.karakept.app.ui.transitions

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition

/**
 * Material 3 Shared Axis Z transition for Voyager.
 *
 * This implements the Material Design 3 shared axis pattern along the Z-axis.
 * - Forward navigation (push): Scales in from small (0.8x) with fade
 * - Backward navigation (pop): Scales out to small (0.8x) with fade
 *
 * Duration and easing curves follow Material 3 motion guidelines:
 * - Incoming elements: 300ms with LinearOutSlowInEasing (emphasized decelerate)
 * - Outgoing elements: 150ms with FastOutLinearInEasing (emphasized accelerate)
 *
 * References:
 * - https://m3.material.io/styles/motion/transitions/transition-patterns
 * - https://developer.android.com/reference/com/google/android/material/transition/MaterialSharedAxis
 */
@Composable
fun SharedAxisZTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier
) {
    ScreenTransition(
        navigator = navigator,
        modifier = modifier,
        enterTransition = {
            sharedAxisZEnterTransition(navigator.lastEvent == StackEvent.Push)
        },
        exitTransition = {
            sharedAxisZExitTransition(navigator.lastEvent == StackEvent.Push)
        }
    )
}

private fun sharedAxisZEnterTransition(isPush: Boolean): ContentTransform {
    val initialScale = if (isPush) 0.8f else 1.1f

    return (scaleIn(
        initialScale = initialScale,
        animationSpec = tween(
            durationMillis = 300,
            easing = LinearOutSlowInEasing
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = 150,
            delayMillis = 75,
            easing = LinearOutSlowInEasing
        )
    )).togetherWith(
        scaleOut(
            targetScale = 1.0f, // Placeholder, will be overridden by exitTransition
            animationSpec = tween(
                durationMillis = 1,
                easing = FastOutLinearInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = 1,
                easing = FastOutLinearInEasing
            )
        )
    )
}

private fun sharedAxisZExitTransition(isPush: Boolean): ContentTransform {
    val targetScale = if (isPush) 1.1f else 0.8f

    return (scaleIn(
        initialScale = 1.0f, // Placeholder, will be overridden by enterTransition
        animationSpec = tween(
            durationMillis = 1,
            easing = LinearOutSlowInEasing
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = 1,
            easing = LinearOutSlowInEasing
        )
    )).togetherWith(
        scaleOut(
            targetScale = targetScale,
            animationSpec = tween(
                durationMillis = 150,
                easing = FastOutLinearInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = 75,
                easing = FastOutLinearInEasing
            )
        )
    )
}
