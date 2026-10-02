package com.karakept.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.repository.OfflineCacheCleanupResult
import com.karakept.app.data.repository.OfflineCacheRepository
import com.karakept.app.data.repository.OfflineCleanupEstimate
import com.karakept.app.data.repository.OfflineStorageUsage
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.formatFileSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class OfflineStorageScreenModel(
    private val offlineCacheRepository: OfflineCacheRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _usage = MutableStateFlow<OfflineStorageUsage?>(null)
    val usage: StateFlow<OfflineStorageUsage?> = _usage.asStateFlow()

    private val _estimate = MutableStateFlow<OfflineCleanupEstimate?>(null)
    val estimate: StateFlow<OfflineCleanupEstimate?> = _estimate.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    /** What the last action did, shown under its button until the next one. */
    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

    private var estimateJob: Job? = null

    init {
        refresh()
        // The first emission is what refresh() just measured against; only changes after it
        // need a new estimate.
        combine(
            settingsRepository.activeOfflineRetentionDays,
            settingsRepository.activeOfflineStorageCapMb
        ) { days, cap -> days to cap }
            .distinctUntilChanged()
            .drop(1)
            .onEach { reestimate() }
            .launchIn(viewModelScope)
    }

    // runExclusive re-measures after every action, so there is nothing else to do.
    fun refresh() = runExclusive {}

    // Debounced: the size and days fields write a setting on every keystroke, and each estimate
    // reads every stored article.
    private fun reestimate() {
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            delay(ESTIMATE_DEBOUNCE_MILLIS)
            measureEstimate()
        }
    }

    fun cleanUpNow() = runExclusive {
        _lastResult.value = describe(offlineCacheRepository.cleanUp(), verb = "Cleaned up")
    }

    fun clearAll() = runExclusive {
        _lastResult.value = describe(offlineCacheRepository.clearAll(), verb = "Cleared")
    }

    private fun runExclusive(action: suspend () -> Unit) {
        if (_isWorking.value) return
        _isWorking.value = true
        estimateJob?.cancel()
        viewModelScope.launch {
            try {
                action()
                _usage.value = offlineCacheRepository.storageUsage()
                measureEstimate()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("OfflineStorage", "Offline storage action failed: ${e.message}", e)
                _lastResult.value = "Something went wrong: ${e.message}"
            } finally {
                _isWorking.value = false
            }
        }
    }

    private suspend fun measureEstimate() {
        try {
            _estimate.value = offlineCacheRepository.estimateCleanup()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("OfflineStorage", "Cleanup estimate failed: ${e.message}", e)
        }
    }

    private fun describe(result: OfflineCacheCleanupResult, verb: String): String =
        if (result.evictedBookmarks == 0 && result.freedBytes == 0L) {
            "Nothing to clean up"
        } else {
            val copies = if (result.evictedBookmarks == 1) "1 offline copy" else "${result.evictedBookmarks} offline copies"
            "$verb $copies, freed ${formatFileSize(result.freedBytes)}"
        }

    companion object {
        const val ESTIMATE_DEBOUNCE_MILLIS = 400L
    }
}
