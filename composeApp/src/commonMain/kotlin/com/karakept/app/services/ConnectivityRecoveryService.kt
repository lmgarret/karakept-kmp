package com.karakept.app.services

import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.processPendingActions
import com.karakept.app.data.repository.setAutoOfflineDetected
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Detects real network outages and recovers from them.
 *
 * When a request fails with a connectivity-type error, [noteConnectivityChange]
 * flips the auto-offline flag so [SettingsRepository.effectiveOfflineMode] blocks
 * further requests (a dead network stops being hammered on every queued action).
 * While auto-offline is active — and the user hasn't chosen offline mode manually —
 * the service probes the server periodically and, when connectivity returns, clears
 * the flag and flushes the pending-action queue for every server.
 */
class ConnectivityRecoveryService(
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val probeIntervalMs: Long = 60_000L
) {
    private val autoOffline = MutableStateFlow(false)
    private var started = false

    private var remoteDataSource: RemoteDataSource? = null
    private var actionsRepository: BookmarkActionsRepository? = null

    fun start(remoteDataSource: RemoteDataSource, actionsRepository: BookmarkActionsRepository) {
        if (started) return
        started = true
        this.remoteDataSource = remoteDataSource
        this.actionsRepository = actionsRepository

        scope.launch {
            settingsRepository.autoOfflineDetected.collect { autoOffline.value = it }
        }
        scope.launch {
            combine(settingsRepository.offlineMode, settingsRepository.autoOfflineDetected) { manual, auto ->
                !manual && auto
            }
                .distinctUntilChanged()
                .collectLatest { shouldProbe ->
                    while (shouldProbe && isActive) {
                        delay(probeIntervalMs)
                        val server = serverRepository.servers.first().firstOrNull() ?: continue
                        AppLogger.d("ConnectivityRecovery", "Probing ${server.url} while auto-offline")
                        if (remoteDataSource.probeConnectivity(server)) {
                            onConnectivityRestored()
                            break
                        }
                    }
                }
        }
    }

    /**
     * Callback for RemoteDataSource: invoked with false on connectivity-type request
     * failures and true on successful requests. Only state transitions touch storage.
     */
    suspend fun noteConnectivityChange(isConnected: Boolean) {
        if (!isConnected && !autoOffline.value) {
            autoOffline.value = true
            settingsRepository.setAutoOfflineDetected(true)
            AppLogger.w("ConnectivityRecovery", "Connectivity lost — auto-offline enabled")
        } else if (isConnected && autoOffline.value) {
            onConnectivityRestored()
        }
    }

    private suspend fun onConnectivityRestored() {
        autoOffline.value = false
        settingsRepository.setAutoOfflineDetected(false)
        AppLogger.d("ConnectivityRecovery", "Connectivity restored — flushing pending actions")
        val repository = actionsRepository ?: return
        serverRepository.servers.first().forEach { server ->
            try {
                repository.processPendingActions(server)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("ConnectivityRecovery", "Post-recovery flush failed for ${server.id}: ${e.message}")
            }
        }
    }
}
