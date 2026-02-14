package com.karakept.app.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
actual fun NotificationPermissionButton(
    onPermissionResult: (Boolean) -> Unit
) {
    // Desktop does not require explicit notification permission
    Button(onClick = { onPermissionResult(true) }) {
        Text("Continue")
    }
}

actual fun platformNeedsNotificationPermission(): Boolean = false
