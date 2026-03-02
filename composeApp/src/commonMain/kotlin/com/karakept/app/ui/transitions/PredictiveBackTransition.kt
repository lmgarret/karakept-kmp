package com.karakept.app.ui.transitions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator

@Composable
expect fun PredictiveBackTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier
)
