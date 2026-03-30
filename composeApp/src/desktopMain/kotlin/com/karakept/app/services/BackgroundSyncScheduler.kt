package com.karakept.app.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object BackgroundSyncScheduler {

    private var syncJob: Job? = null

    fun schedule(
        scope: CoroutineScope,
        orchestrator: BackgroundSyncOrchestrator,
        frequencyMinutes: Int
    ) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(frequencyMinutes * 60_000L)
                orchestrator.runSync()
            }
        }
    }

    fun cancel() {
        syncJob?.cancel()
        syncJob = null
    }
}
