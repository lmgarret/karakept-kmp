package com.karakept.app.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.karakept.app.MainActivity
import com.karakept.app.R
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SaveBookmarkWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val bookmarkRepository: BookmarkRepository by inject()
    private val settingsRepository: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        // Shared across the progress and final notification so the final one replaces
        // the progress one in place instead of stacking alongside it.
        val notificationId = System.currentTimeMillis().toInt()

        return try {
            postNotificationSafely {
                showProgressNotification(notificationId)
            }

            val result = bookmarkRepository.createBookmark(url)

            if (result.isSuccess) {
                val bookmark = result.getOrNull()
                if (bookmark != null) {
                    postNotificationSafely {
                        showSuccessNotification(notificationId, bookmark.localId.toString(), bookmark.title, bookmark.imageUrl)
                    }
                }
                Result.success()
            } else {
                postNotificationSafely {
                    showErrorNotification(notificationId, url, result.exceptionOrNull()?.message ?: "Unknown error")
                }
                Result.failure()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save bookmark: ${e.message}", e)
            postNotificationSafely {
                showErrorNotification(notificationId, url, e.message ?: "Unknown error")
            }
            Result.failure()
        }
    }

    /**
     * Checks whether the app can post notifications:
     * 1. The in-app notification toggle must be enabled.
     * 2. On Android 13+ (API 33), the POST_NOTIFICATIONS runtime permission must be granted.
     *
     * If both conditions are met, executes [block]. Any exception thrown by [block]
     * is caught and logged so that a notification failure never crashes the worker.
     */
    private suspend fun postNotificationSafely(block: suspend () -> Unit) {
        try {
            val notificationsEnabled = settingsRepository.notificationsEnabled.first()
            if (!notificationsEnabled) {
                AppLogger.d(TAG, "Notifications disabled in app settings, skipping")
                return
            }

            if (!hasNotificationPermission()) {
                AppLogger.w(TAG, "POST_NOTIFICATIONS permission not granted, skipping notification")
                return
            }

            block()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to post notification: ${e.message}", e)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-Android 13: notifications are allowed by default
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bookmark Saves",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for saved bookmarks"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(notificationId: Int) {
        ensureNotificationChannel()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Saving bookmark…")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(notificationId, builder.build())
    }

    private suspend fun showSuccessNotification(notificationId: Int, bookmarkId: String, title: String, imageUrl: String?) {
        ensureNotificationChannel()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Fetch image if present
        var largeIcon: Bitmap? = null
        if (!imageUrl.isNullOrBlank()) {
            try {
                val imageLoader = ImageLoader(applicationContext)
                val request = ImageRequest.Builder(applicationContext)
                    .data(imageUrl)
                    .allowHardware(false) // Bitmap for notification must strictly be software
                    .build()

                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    largeIcon = result.image.toBitmap()
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Failed to load notification image: ${e.message}")
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("bookmark_id", bookmarkId)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Bookmark Saved")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
            builder.setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(largeIcon)
                .bigLargeIcon(null as Bitmap?))
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun showErrorNotification(notificationId: Int, url: String, error: String) {
        ensureNotificationChannel()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Tapping the notification opens the dedicated save-error screen.
        val contentIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_SAVE_ERROR_URL, url)
            putExtra(EXTRA_SAVE_ERROR_MESSAGE, error)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Retry action: re-enqueues the save via a broadcast receiver.
        val retryIntent = Intent(applicationContext, SaveBookmarkRetryReceiver::class.java).apply {
            action = SaveBookmarkRetryReceiver.ACTION_RETRY
            putExtra(SaveBookmarkRetryReceiver.EXTRA_URL, url)
            putExtra(SaveBookmarkRetryReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val retryPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            notificationId,
            retryIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Open action: opens the link in the browser.
        val openIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId + 1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Save Failed")
            .setContentText(url)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$url\n\n$error"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Retry", retryPendingIntent)
            .addAction(0, "Open", openPendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    companion object {
        const val KEY_URL = "key_url"
        const val EXTRA_SAVE_ERROR_URL = "save_error_url"
        const val EXTRA_SAVE_ERROR_MESSAGE = "save_error_message"
        private const val TAG = "SaveBookmarkWorker"
        private const val CHANNEL_ID = "bookmark_save_channel"
    }
}
