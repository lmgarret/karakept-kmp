package com.karakept.app.ui.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.repository.BackupRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.utils.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BackupRestoreScreenModel(
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository
) : ScreenModel {

    sealed class BackupState {
        data object Idle : BackupState()
        data object Loading : BackupState()
        data class Success(val message: String) : BackupState()
        data class Error(val message: String) : BackupState()
    }

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    val autoExportInterval: StateFlow<AutoExportInterval> = settingsRepository.autoExportInterval.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AutoExportInterval.NEVER
    )

    val lastAutoExportTime: StateFlow<Long> = settingsRepository.lastAutoExportTime.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val backupExportDirectory: StateFlow<String?> = settingsRepository.backupExportDirectory.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun exportSettings() {
        screenModelScope.launch {
            _state.value = BackupState.Loading
            try {
                val filePath = backupRepository.exportToFile()
                _state.value = BackupState.Success("Backup saved to:\n$filePath")
                FileUtils.shareBackupFile(filePath)
            } catch (e: Exception) {
                _state.value = BackupState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun importSettings(jsonContent: String) {
        screenModelScope.launch {
            _state.value = BackupState.Loading
            try {
                val summary = backupRepository.importFromJson(jsonContent)
                _state.value = BackupState.Success(summary)
            } catch (e: Exception) {
                _state.value = BackupState.Error("Import failed: ${e.message}")
            }
        }
    }

    fun setAutoExportInterval(interval: AutoExportInterval) {
        screenModelScope.launch {
            settingsRepository.setAutoExportInterval(interval)
        }
    }

    fun setBackupExportDirectory(path: String?) {
        screenModelScope.launch {
            settingsRepository.setBackupExportDirectory(path)
        }
    }

    fun clearState() {
        _state.value = BackupState.Idle
    }
}
