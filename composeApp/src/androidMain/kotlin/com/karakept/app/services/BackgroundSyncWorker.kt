package com.karakept.app.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val orchestrator: BackgroundSyncOrchestrator by inject()

    override suspend fun doWork(): Result = when (orchestrator.runSync()) {
        SyncResult.SUCCESS, SyncResult.SKIPPED -> Result.success()
        SyncResult.ERROR -> Result.retry()
    }

    companion object {
        const val WORK_NAME = "background_bookmark_sync"
    }
}
