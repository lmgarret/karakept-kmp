package com.karakept.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.karakept.app.utils.AppLogger
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.karakept.app.data.repository.BookmarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class BookmarkSaveService : Service() {

    private val bookmarkRepository: BookmarkRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Channel created in App
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        // Show "Saving..." toast here to ensure it only appears once per service start
        Handler(Looper.getMainLooper()).post {
             Toast.makeText(applicationContext, "Saving bookmark...", Toast.LENGTH_SHORT).show()
        }

        saveBookmark(url)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun saveBookmark(url: String) {
        scope.launch {
            try {
                // Ensure we catch EVERYTHING
                val result = runCatching {
                     bookmarkRepository.createBookmark(url)
                }.getOrElse { e ->
                     Result.failure(e)
                }
                
                // Result Notification
                if (result.isSuccess) {
                    val bookmark = result.getOrNull()
                    val title = bookmark?.title ?: "Bookmark Saved"
                    val imageUrl = bookmark?.imageUrl
                    
                    var bitmap: android.graphics.Bitmap? = null
                    if (!imageUrl.isNullOrBlank()) {
                        try {
                            val javaUrl = java.net.URL(imageUrl)
                            bitmap = android.graphics.BitmapFactory.decodeStream(javaUrl.openStream())
                        } catch (e: Exception) {
                            AppLogger.e("BookmarkSaveService", "Failed to save bookmark from share intent: ${e.message}", e)
                        }
                    }

                    // Intent to open the app
                    val openIntent = Intent(applicationContext, com.karakept.app.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        applicationContext, 
                        0, 
                        openIntent, 
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val builder = NotificationCompat.Builder(this@BookmarkSaveService, CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(url)
                        .setSmallIcon(android.R.drawable.ic_input_add)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                    
                    if (bitmap != null) {
                        builder.setLargeIcon(bitmap)
                        builder.setStyle(NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null as android.graphics.Bitmap?))
                    }

                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID + 1, builder.build())
                } else {
                     val errorNotif = NotificationCompat.Builder(this@BookmarkSaveService, CHANNEL_ID)
                        .setContentTitle("Save Failed")
                        .setContentText(result.exceptionOrNull()?.message ?: "Unknown error")
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID + 1, errorNotif)
                }

                // Give the Notification a moment to post before killing the service
                kotlinx.coroutines.delay(1000)

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        // Channel created in KarakeptApp
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Saving bookmark...")
            .setSmallIcon(android.R.drawable.ic_menu_save) // Use a generic system icon for now if app icon is tricky to reference without R class setup
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val CHANNEL_ID = "quick_share_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, url: String) {
            val intent = Intent(context, BookmarkSaveService::class.java).apply {
                putExtra(EXTRA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
