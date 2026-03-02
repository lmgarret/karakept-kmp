package com.karakept.app.ui.transitions

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun PredictiveBackTransition(
    navigator: Navigator,
    modifier: Modifier
) {
    val progress = remember { Animatable(0f) }
    var inPredictiveBack by remember { mutableStateOf(false) }
    var gestureScreens by remember { mutableStateOf<Pair<Screen, Screen>?>(null) }
    var swipeEdge by remember { mutableStateOf(BackEventCompat.EDGE_LEFT) }

    PredictiveBackHandler(enabled = navigator.canPop) { events ->
        val currentScreen = navigator.lastItem
        val previousScreen = navigator.items.getOrNull(navigator.items.lastIndex - 1)
            ?: return@PredictiveBackHandler

        gestureScreens = Pair(previousScreen, currentScreen)
        inPredictiveBack = true

        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                progress.snapTo(event.progress)
            }
            // Gesture committed - animate to completion then pop
            progress.animateTo(1f, tween(80))
            navigator.pop()
        } catch (e: CancellationException) {
            // Gesture cancelled - animate back to start
            progress.animateTo(0f, tween(150))
        } finally {
            inPredictiveBack = false
            gestureScreens = null
            progress.snapTo(0f)
        }
    }

    if (inPredictiveBack && gestureScreens != null) {
        val (previousScreen, currentScreen) = gestureScreens!!
        val direction = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        val p = progress.value

        Box(modifier.fillMaxSize()) {
            // Background: previous screen
            previousScreen.Content()

            // Foreground: current screen with gesture-driven animation
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1f - (0.1f * p)
                        scaleY = 1f - (0.1f * p)
                        translationX = size.width * p * 0.08f * direction
                        shadowElevation = 8f * (1f - p)
                        shape = RoundedCornerShape((p * 16f).dp)
                        clip = true
                    }
            ) {
                currentScreen.Content()
            }
        }
    } else {
        // Regular transition for push and non-gesture pop
        SlideTransition(navigator, modifier)
    }
}
