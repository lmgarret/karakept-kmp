package com.karakept.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
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
            bookmarkRepository.syncBookmarks(server)

            val digestEnabled = settingsRepository.backgroundSyncDigestNotification.first()
            val notificationsEnabled = settingsRepository.notificationsEnabled.first()
            if (digestEnabled && notificationsEnabled) {
                showDigestNotification()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showDigestNotification() {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = CHANNEL_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Background Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Periodic background sync notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Bookmarks synced")
            .setContentText("Your bookmarks have been synced in the background.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "background_bookmark_sync"
        const val CHANNEL_ID = "background_sync_channel"
        const val NOTIFICATION_ID = 2001
    }
}
