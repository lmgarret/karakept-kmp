package com.karakept.app.ui.screens

import androidx.compose.runtime.Composable

/**
 * Platform-specific notification permission request button.
 * On Android, triggers the system permission dialog.
 * On Desktop, this is a no-op.
 */
@Composable
expect fun NotificationPermissionButton(
    onPermissionResult: (Boolean) -> Unit
)

/**
 * Returns whether the platform supports notification permissions
 * (i.e., requires an explicit request from the user).
 */
expect fun platformNeedsNotificationPermission(): Boolean
