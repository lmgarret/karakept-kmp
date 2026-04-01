package com.karakept.app.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.karakept.app.R

class AndroidNotificationProvider(
    private val context: Context
) : NotificationProvider {

    override fun canSendNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun sendNotification(title: String, message: String, type: NotificationType) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val (channelId, channelName, importance, notificationId) = when (type) {
            NotificationType.DIGEST -> NotificationConfig(
                DIGEST_CHANNEL_ID, "Background Sync",
                NotificationManager.IMPORTANCE_LOW, DIGEST_NOTIFICATION_ID
            )
            NotificationType.LIST_UPDATE -> NotificationConfig(
                LIST_CHANNEL_ID, "List Updates",
                NotificationManager.IMPORTANCE_DEFAULT, LIST_NOTIFICATION_ID
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = when (type) {
                    NotificationType.DIGEST -> "Periodic background sync notifications"
                    NotificationType.LIST_UPDATE -> "Notifications for lists with new bookmarks"
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val priority = when (type) {
            NotificationType.DIGEST -> NotificationCompat.PRIORITY_LOW
            NotificationType.LIST_UPDATE -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked between check and post
        }
    }

    private data class NotificationConfig(
        val channelId: String,
        val channelName: String,
        val importance: Int,
        val notificationId: Int
    )

    companion object {
        const val DIGEST_CHANNEL_ID = "background_sync_channel"
        const val DIGEST_NOTIFICATION_ID = 2001
        const val LIST_CHANNEL_ID = "list_updates_channel"
        const val LIST_NOTIFICATION_ID = 2002
    }
}
