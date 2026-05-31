package com.karakept.app.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

/**
 * Material 3 Shared Axis Z transitions for Nav3's [androidx.navigation3.ui.NavDisplay].
 *
 * Forward (push): incoming scales in from 0.8x, outgoing scales out to 1.1x — both with fade.
 * Backward (pop / predictive back): incoming scales in from 1.1x, outgoing scales out to 0.8x.
 *
 * Durations/easing follow M3 motion: incoming 300ms emphasized-decelerate, outgoing 150ms
 * emphasized-accelerate. Ported from the previous Voyager `SharedAxisZTransition`.
 *
 * https://m3.material.io/styles/motion/transitions/transition-patterns
 */
fun sharedAxisZForward(): ContentTransform =
    (scaleIn(
        initialScale = 0.8f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
    ) + fadeIn(
        animationSpec = tween(durationMillis = 150, delayMillis = 75, easing = LinearOutSlowInEasing),
    )).togetherWith(
        scaleOut(
            targetScale = 1.1f,
            animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
        ) + fadeOut(
            animationSpec = tween(durationMillis = 75, easing = FastOutLinearInEasing),
        ),
    )

fun sharedAxisZBackward(): ContentTransform =
    (scaleIn(
        initialScale = 1.1f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
    ) + fadeIn(
        animationSpec = tween(durationMillis = 150, delayMillis = 75, easing = LinearOutSlowInEasing),
    )).togetherWith(
        scaleOut(
            targetScale = 0.8f,
            animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
        ) + fadeOut(
            animationSpec = tween(durationMillis = 75, easing = FastOutLinearInEasing),
        ),
    )
