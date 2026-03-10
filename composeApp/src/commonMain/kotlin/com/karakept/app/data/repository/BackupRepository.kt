package com.karakept.app.data.repository

import com.karakept.app.data.model.AppBackup
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.model.EncryptedBackupEnvelope
import com.karakept.app.data.model.ServerBackup
import com.karakept.app.utils.BackupCrypto
import com.karakept.app.utils.FileUtils
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupRepository(
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Export ─────────────────────────────────────────────────────────────────

    /**
     * Builds a complete [AppBackup] from the current settings and server list.
     *
     * Because [SettingsRepository] owns the canonical [com.karakept.app.data.model.BackupSettings]
     * snapshot, this function never needs to be updated when new settings are added.
     */
    suspend fun buildBackup(): AppBackup {
        val servers = serverRepository.servers.first().map {
            ServerBackup(it.id, it.url, it.apiKey, it.label)
        }
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val timestamp = "$now".replace("T", " ").take(19) + " UTC"
        return AppBackup(
            exportedAt = timestamp,
            settings = settingsRepository.currentSettings(),
            servers = servers
        )
    }

    /**
     * Saves the backup to the configured (or default) backup directory and returns the file path.
     *
     * All backups are AES-256-GCM encrypted with [pin]. The PIN must not be null — use
     * [settingsRepository.backupPin] to retrieve the stored PIN when no explicit value is given.
     *
     * @throws IllegalArgumentException if [pin] is blank.
     */
    suspend fun exportToFile(pin: String): String {
        require(pin.isNotBlank()) { "A PIN is required to export a backup." }

        val backup = buildBackup()
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val datePart = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}"
        val fileName = "karakept_backup_$datePart.json"

        val plaintext = json.encodeToString(backup).encodeToByteArray()
        val encryptedData = BackupCrypto.encrypt(plaintext, pin)
        val envelope = EncryptedBackupEnvelope(data = encryptedData)
        val fileBytes = json.encodeToString(envelope).encodeToByteArray()

        val customDir = settingsRepository.backupExportDirectory.first()
        val dir = customDir ?: FileUtils.getBackupDirectory()
        val filePath = FileUtils.saveFileToDirectory(dir, fileName, fileBytes)
        settingsRepository.setLastAutoExportTime(Clock.System.now().toEpochMilliseconds())
        return filePath
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    /**
     * Parses [jsonContent] as an [EncryptedBackupEnvelope], decrypts it with [pin], and
     * atomically restores all settings and server connections it contains.
     *
     * Unknown fields are silently ignored to preserve forward-compatibility.
     * Returns a human-readable summary of what was restored.
     *
     * @throws Exception if the JSON is invalid, the PIN is wrong, or the version is unsupported.
     */
    suspend fun importFromJson(jsonContent: String, pin: String): String {
        val envelope = json.decodeFromString<EncryptedBackupEnvelope>(jsonContent)
        val decryptedBytes = BackupCrypto.decrypt(envelope.data, pin)
        val backup = json.decodeFromString<AppBackup>(decryptedBytes.decodeToString())

        if (backup.version > CURRENT_BACKUP_VERSION) {
            error(
                "Backup was created with a newer version of the app (version ${backup.version}). " +
                "Please update the app to restore this backup."
            )
        }

        settingsRepository.restoreSettings(backup.settings)

        // Always restore servers — PIN entry acts as explicit trust signal.
        if (backup.servers.isNotEmpty()) {
            serverRepository.deleteAllServers()
            backup.servers.forEach { s ->
                serverRepository.addServer(s.url, s.apiKey, s.label)
            }
        }

        return buildString {
            appendLine("Settings restored from backup (exported ${backup.exportedAt}).")
            if (backup.servers.isNotEmpty()) {
                appendLine("${backup.servers.size} server connection(s) have been restored.")
            }
        }.trim()
    }

    // ── Scheduled Export ───────────────────────────────────────────────────────

    /**
     * Checks whether a scheduled auto-export is due and, if so, runs it using the stored PIN.
     * If no PIN is configured, the scheduled export is skipped.
     * Call this at app startup.
     */
    suspend fun checkAndRunScheduledExport() {
        val interval = settingsRepository.autoExportInterval.first()
        if (interval == AutoExportInterval.NEVER) return

        val pin = settingsRepository.backupPin.first() ?: return  // PIN required; skip silently

        val lastExport = settingsRepository.lastAutoExportTime.first()
        val now = Clock.System.now().toEpochMilliseconds()
        val elapsedMs = now - lastExport

        val intervalMs = when (interval) {
            AutoExportInterval.DAILY -> DAY_MS
            AutoExportInterval.WEEKLY -> DAY_MS * 7
            AutoExportInterval.MONTHLY -> DAY_MS * 30
            AutoExportInterval.NEVER -> return
        }

        if (elapsedMs >= intervalMs) {
            try {
                exportToFile(pin)
            } catch (e: Exception) {
                // Scheduled export is best-effort – don't surface errors to user
            }
        }
    }

    companion object {
        const val CURRENT_BACKUP_VERSION = 1
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
