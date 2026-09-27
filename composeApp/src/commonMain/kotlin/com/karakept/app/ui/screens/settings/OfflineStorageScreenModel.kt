package com.karakept.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.repository.OfflineCacheCleanupResult
import com.karakept.app.data.repository.OfflineCacheRepository
import com.karakept.app.data.repository.OfflineStorageUsage
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.formatFileSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OfflineStorageScreenModel(
    private val offlineCacheRepository: OfflineCacheRepository
) : ViewModel() {

    private val _usage = MutableStateFlow<OfflineStorageUsage?>(null)
    val usage: StateFlow<OfflineStorageUsage?> = _usage.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    /** What the last action did, shown under the buttons until the next one. */
    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

    init {
        refresh()
    }

    // runExclusive re-reads the usage after every action, so there is nothing else to do.
    fun refresh() = runExclusive {}

    fun cleanUpNow() = runExclusive {
        _lastResult.value = describe(offlineCacheRepository.cleanUp(), verb = "Cleaned up")
    }

    fun clearAll() = runExclusive {
        _lastResult.value = describe(offlineCacheRepository.clearAll(), verb = "Cleared")
    }

    private fun runExclusive(action: suspend () -> Unit) {
        if (_isWorking.value) return
        _isWorking.value = true
        viewModelScope.launch {
            try {
                action()
                _usage.value = offlineCacheRepository.storageUsage()
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

    private fun describe(result: OfflineCacheCleanupResult, verb: String): String =
        if (result.evictedBookmarks == 0 && result.deletedFiles == 0) {
            "Nothing to clean up"
        } else {
            val copies = if (result.evictedBookmarks == 1) "1 offline copy" else "${result.evictedBookmarks} offline copies"
            "$verb $copies, freed ${formatFileSize(result.freedBytes)} of files"
        }
}
