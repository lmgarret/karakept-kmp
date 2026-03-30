package com.karakept.app.services

import com.karakept.app.utils.AppLogger
import io.github.kdroidfilter.knotify.builder.ExperimentalNotificationsApi
import io.github.kdroidfilter.knotify.builder.notification

class DesktopNotificationProvider : NotificationProvider {

    override fun canSendNotifications(): Boolean = isMacAppBundleAvailable()

    @OptIn(ExperimentalNotificationsApi::class)
    override fun sendNotification(title: String, message: String, type: NotificationType) {
        AppLogger.d("BackgroundSync", "Sending notification: $title")
        try {
            notification(title = title, message = message).send()
        } catch (_: Exception) {
            // Notifications not available on this platform
        }
    }

    /**
     * Returns false on macOS when running outside a proper .app bundle (e.g. via `gradlew run`).
     * UNUserNotificationCenter crashes with NSInternalInconsistencyException in that case.
     * Always returns true on non-macOS platforms (Linux).
     */
    private fun isMacAppBundleAvailable(): Boolean {
        if (!System.getProperty("os.name", "").lowercase().contains("mac")) return true
        val command = ProcessHandle.current().info().command().orElse("")
        return command.contains(".app/Contents/MacOS/")
    }
}
