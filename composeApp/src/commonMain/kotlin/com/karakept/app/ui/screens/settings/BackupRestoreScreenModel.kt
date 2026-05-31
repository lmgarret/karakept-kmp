package com.karakept.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.repository.BackupRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.setBackupPin
import com.karakept.app.data.repository.setAutoExportInterval
import com.karakept.app.data.repository.setBackupExportDirectory
import com.karakept.app.utils.BackupCrypto
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
) : ViewModel() {

    sealed class BackupState {
        data object Idle : BackupState()
        data object Loading : BackupState()
        data class Success(val message: String) : BackupState()
        data class Error(val message: String) : BackupState()
        /** Import was triggered — PIN is always required to decrypt. */
        data class PinRequired(val encryptedContent: String) : BackupState()
    }

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    val autoExportInterval: StateFlow<AutoExportInterval> = settingsRepository.autoExportInterval.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AutoExportInterval.NEVER
    )

    val lastAutoExportTime: StateFlow<Long> = settingsRepository.lastAutoExportTime.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val backupExportDirectory: StateFlow<String?> = settingsRepository.backupExportDirectory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /** PBKDF2 hash of the backup PIN, non-null when a PIN has been set. */
    val backupPinHash: StateFlow<String?> = settingsRepository.backupPinHash.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Exports and encrypts settings with the given [pin].
     * The caller must verify the PIN matches the stored hash before calling this.
     */
    fun exportSettings(pin: String) {
        viewModelScope.launch {
            _state.value = BackupState.Loading
            try {
                val filePath = backupRepository.exportToFile(pin)
                _state.value = BackupState.Success("Backup saved (encrypted) to:\n$filePath")
                FileUtils.shareBackupFile(filePath)
            } catch (e: Exception) {
                _state.value = BackupState.Error("Export failed: ${e.message}")
            }
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Called when the user picks a backup file. All backups are encrypted, so the state always
     * transitions to [BackupState.PinRequired] for the UI to show a PIN-entry dialog.
     */
    fun importSettings(jsonContent: String) {
        _state.value = BackupState.PinRequired(jsonContent)
    }

    /** Called after the user enters the PIN for the encrypted backup. */
    fun importWithPin(encryptedContent: String, pin: String) {
        viewModelScope.launch {
            _state.value = BackupState.Loading
            try {
                val summary = backupRepository.importFromJson(encryptedContent, pin)
                _state.value = BackupState.Success(summary)
            } catch (e: Exception) {
                _state.value = BackupState.Error("Import failed: ${e.message}")
            }
        }
    }

    // ── PIN management ────────────────────────────────────────────────────────

    /** Sets the backup PIN (4–6 digits). Stores the hash and the raw value for auto-exports. */
    fun setBackupPin(pin: String) {
        viewModelScope.launch {
            settingsRepository.setBackupPin(pin)
        }
    }

    /**
     * Returns `true` if [pin] matches the stored PIN hash.
     * Safe to call on the main thread (hash comparison is fast).
     */
    fun verifyPin(pin: String): Boolean {
        val hash = backupPinHash.value ?: return false
        return BackupCrypto.verifyPin(pin, hash)
    }

    // ── Other setters ─────────────────────────────────────────────────────────

    fun setAutoExportInterval(interval: AutoExportInterval) {
        viewModelScope.launch {
            settingsRepository.setAutoExportInterval(interval)
        }
    }

    fun setBackupExportDirectory(path: String?) {
        viewModelScope.launch {
            settingsRepository.setBackupExportDirectory(path)
        }
    }

    fun clearState() {
        _state.value = BackupState.Idle
    }
}
