package com.karakept.app.ui.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.repository.BackupRepository
import com.karakept.app.data.repository.SettingsRepository
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
) : ScreenModel {

    sealed class BackupState {
        data object Idle : BackupState()
        data object Loading : BackupState()
        data class Success(val message: String) : BackupState()
        data class Error(val message: String) : BackupState()
        /** Import was triggered for an encrypted file but no PIN has been provided yet. */
        data class PinRequired(val encryptedContent: String) : BackupState()
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

    /** PBKDF2 hash of the backup PIN, non-null when encryption is enabled. */
    val backupPinHash: StateFlow<String?> = settingsRepository.backupPinHash.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Exports settings.  [pin] must be provided when a backup PIN is configured.
     */
    fun exportSettings(pin: String? = null) {
        screenModelScope.launch {
            _state.value = BackupState.Loading
            try {
                val filePath = backupRepository.exportToFile(pin)
                val encryptionNote = if (pin != null) " (encrypted)" else ""
                _state.value = BackupState.Success("Backup saved$encryptionNote to:\n$filePath")
                FileUtils.shareBackupFile(filePath)
            } catch (e: Exception) {
                _state.value = BackupState.Error("Export failed: ${e.message}")
            }
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Called when the user picks a backup file.
     * If the file is encrypted the state transitions to [BackupState.PinRequired] so the UI
     * can show a PIN-entry dialog; the caller then invokes [importWithPin].
     */
    fun importSettings(jsonContent: String) {
        if (looksEncrypted(jsonContent)) {
            _state.value = BackupState.PinRequired(jsonContent)
        } else {
            doImport(jsonContent, null)
        }
    }

    /** Called after the user enters the PIN for an encrypted backup. */
    fun importWithPin(encryptedContent: String, pin: String) {
        doImport(encryptedContent, pin)
    }

    private fun doImport(jsonContent: String, pin: String?) {
        screenModelScope.launch {
            _state.value = BackupState.Loading
            try {
                val summary = backupRepository.importFromJson(jsonContent, pin)
                _state.value = BackupState.Success(summary)
            } catch (e: Exception) {
                _state.value = BackupState.Error("Import failed: ${e.message}")
            }
        }
    }

    // ── PIN management ────────────────────────────────────────────────────────

    /** Sets the backup PIN (4–6 digits).  Stores the hash and the raw value for auto-exports. */
    fun setBackupPin(pin: String) {
        screenModelScope.launch {
            settingsRepository.setBackupPin(pin)
        }
    }

    /** Removes the backup PIN, disabling encryption for future exports. */
    fun clearBackupPin() {
        screenModelScope.launch {
            settingsRepository.setBackupPin(null)
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun looksEncrypted(content: String): Boolean =
        content.contains("\"encrypted\"") &&
        (content.contains("\"encrypted\":true") || content.contains("\"encrypted\": true"))
}
