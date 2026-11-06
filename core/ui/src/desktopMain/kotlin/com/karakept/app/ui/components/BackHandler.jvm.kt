package com.karakept.app.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op for desktop as there is no hardware back button
    // Users typically use Escape or UI buttons
}
