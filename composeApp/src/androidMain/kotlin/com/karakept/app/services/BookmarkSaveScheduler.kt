package com.karakept.app.services

import android.content.Context
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object BookmarkSaveScheduler {
    fun buildRequest(url: String): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<SaveBookmarkWorker>()
            .setInputData(workDataOf(SaveBookmarkWorker.KEY_URL to url))
            .build()

    fun enqueue(context: Context, url: String) {
        WorkManager.getInstance(context).enqueue(buildRequest(url))
    }
}
