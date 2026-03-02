package com.karakept.app.ui.transitions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition

@Composable
actual fun PredictiveBackTransition(
    navigator: Navigator,
    modifier: Modifier
) {
    SlideTransition(navigator, modifier)
}
