package com.karakept.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
actual fun NotificationPermissionButton(
    onPermissionResult: (Boolean) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = onPermissionResult
        )
        Button(onClick = {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) {
            Text("Grant Notification Permission")
        }
    } else {
        // On Android < 13, notifications are on by default — grant on button click
        Button(onClick = { onPermissionResult(true) }) {
            Text("Continue")
        }
    }
}

actual fun platformNeedsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
