package com.karakept.app.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-enqueues a failed Quick Save from the "Retry" action of the save-error notification,
 * then dismisses that notification.
 */
class SaveBookmarkRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        BookmarkSaveScheduler.enqueue(context.applicationContext, url)

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }

    companion object {
        const val ACTION_RETRY = "com.karakept.app.action.RETRY_SAVE"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
