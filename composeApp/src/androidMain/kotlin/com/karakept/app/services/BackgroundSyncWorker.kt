package com.karakept.app.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.karakept.app.R
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val bookmarkRepository: BookmarkRepository by inject()
    private val serverRepository: ServerRepository by inject()
    private val settingsRepository: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        val backgroundSyncEnabled = settingsRepository.backgroundSyncEnabled.first()
        if (!backgroundSyncEnabled) return Result.success()

        val servers = serverRepository.servers.first()
        if (servers.isEmpty()) return Result.success()

        val activeServerId = settingsRepository.activeServerId.first()
        val server = servers.find { it.id == activeServerId } ?: servers.first()

        return try {
            val newCount = bookmarkRepository.syncBookmarks(server)

            val digestEnabled = settingsRepository.backgroundSyncDigestNotification.first()
            val notificationsEnabled = settingsRepository.notificationsEnabled.first()
            if (digestEnabled && notificationsEnabled && hasNotificationPermission()) {
                showDigestNotification(newCount)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun showDigestNotification(newBookmarksCount: Int) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Periodic background sync notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentText = if (newBookmarksCount > 0) {
            "$newBookmarksCount new bookmark${if (newBookmarksCount > 1) "s" else ""} synced"
        } else {
            "Bookmarks are up to date"
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Bookmarks synced")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between check and post
        }
    }

    companion object {
        const val WORK_NAME = "background_bookmark_sync"
        const val CHANNEL_ID = "background_sync_channel"
        const val NOTIFICATION_ID = 2001
    }
}
