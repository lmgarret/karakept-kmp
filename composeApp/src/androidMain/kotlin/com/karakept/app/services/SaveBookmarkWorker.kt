package com.karakept.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
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
        
        // Show initial notification if needed, or just rely on the final one.
        // Requirement: "display a toast saying 'Saving bookmark...' then ... display a notification"
        // The toast is done in Activity. Here we just do the work and final notification.

        return try {
            val result = bookmarkRepository.createBookmark(url)
            
            if (result.isSuccess) {
                val bookmark = result.getOrNull()
                val notificationsEnabled = settingsRepository.notificationsEnabled.first()
                
                if (notificationsEnabled && bookmark != null) {
                    showSuccessNotification(bookmark.localId.toString(), bookmark.title, bookmark.imageUrl)
                }
                Result.success()
            } else {
                val notificationsEnabled = settingsRepository.notificationsEnabled.first()
                if (notificationsEnabled) {
                    showErrorNotification(result.exceptionOrNull()?.message ?: "Unknown error")
                }
                Result.failure()
            }
        } catch (e: Exception) {
            val notificationsEnabled = settingsRepository.notificationsEnabled.first()
            if (notificationsEnabled) {
                showErrorNotification(e.message ?: "Unknown error")
            }
            Result.failure()
        }
    }

    private suspend fun showSuccessNotification(bookmarkId: String, title: String, imageUrl: String?) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "bookmark_save_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Bookmark Saves",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for saved bookmarks"
            }
            notificationManager.createNotificationChannel(channel)
        }

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
                // Ignore image load failure
            }
        }

        android.util.Log.d("DebuggingCtx", "👷 Creating notification for bookmarkId=$bookmarkId")
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("bookmark_id", bookmarkId)
        }
        android.util.Log.d("DebuggingCtx", "👷 Notification Intent extras: ${intent.extras}")

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
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

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun showErrorNotification(error: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "bookmark_save_channel"
        
        // Channel creation duplicated for safety, or move to init
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            /* ... same channel init ... */
             val channel = NotificationChannel(
                channelId,
                "Bookmark Saves",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Save Failed")
            .setContentText(error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    companion object {
        const val KEY_URL = "key_url"
    }
}
