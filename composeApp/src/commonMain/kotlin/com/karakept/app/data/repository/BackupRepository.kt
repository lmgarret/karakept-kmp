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
     * If the user has set a backup PIN, the file is AES-256-GCM encrypted and wrapped in an
     * [EncryptedBackupEnvelope].  If [pin] is null the stored PIN from [SettingsRepository] is
     * used; pass an explicit value to override (e.g. when the user has just changed the PIN).
     */
    suspend fun exportToFile(pin: String? = null): String {
        val backup = buildBackup()
        val effectivePin = pin ?: settingsRepository.backupPin.first()

        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val datePart = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}"
        val fileName = "karakept_backup_$datePart.json"

        val fileBytes: ByteArray = if (effectivePin != null) {
            val plaintext = json.encodeToString(backup).encodeToByteArray()
            val encryptedData = BackupCrypto.encrypt(plaintext, effectivePin)
            val envelope = EncryptedBackupEnvelope(data = encryptedData)
            json.encodeToString(envelope).encodeToByteArray()
        } else {
            json.encodeToString(backup).encodeToByteArray()
        }

        val customDir = settingsRepository.backupExportDirectory.first()
        val dir = customDir ?: FileUtils.getBackupDirectory()
        val filePath = FileUtils.saveFileToDirectory(dir, fileName, fileBytes)
        settingsRepository.setLastAutoExportTime(Clock.System.now().toEpochMilliseconds())
        return filePath
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    /**
     * Parses [jsonContent], decrypting with [pin] when the file is encrypted, and atomically
     * restores all settings it contains.  When an encrypted backup is successfully decrypted,
     * servers are also restored (since the PIN proves explicit user intent).
     *
     * For unencrypted backups, servers are NOT automatically restored (legacy behaviour).
     *
     * Unknown fields are silently ignored to preserve forward-compatibility.
     * Returns a human-readable summary of what was restored.
     *
     * @throws Exception if the JSON is invalid, the PIN is wrong/missing, or the version is
     *                   unsupported.
     */
    suspend fun importFromJson(jsonContent: String, pin: String? = null): String {
        val (backup, wasEncrypted) = if (looksEncrypted(jsonContent)) {
            if (pin == null) error("This backup is encrypted. Please provide a PIN to decrypt it.")
            val envelope = json.decodeFromString<EncryptedBackupEnvelope>(jsonContent)
            val decryptedBytes = BackupCrypto.decrypt(envelope.data, pin)
            json.decodeFromString<AppBackup>(decryptedBytes.decodeToString()) to true
        } else {
            json.decodeFromString<AppBackup>(jsonContent) to false
        }

        if (backup.version > CURRENT_BACKUP_VERSION) {
            error(
                "Backup was created with a newer version of the app (version ${backup.version}). " +
                "Please update the app to restore this backup."
            )
        }

        settingsRepository.restoreSettings(backup.settings)

        // Restore servers only when the backup was encrypted — the PIN acts as an explicit
        // trust signal that the user intended to restore everything, including API keys.
        if (wasEncrypted && backup.servers.isNotEmpty()) {
            serverRepository.deleteAllServers()
            backup.servers.forEach { s ->
                serverRepository.addServer(s.url, s.apiKey, s.label)
            }
        }

        return buildString {
            appendLine("Settings restored from backup (exported ${backup.exportedAt}).")
            when {
                wasEncrypted && backup.servers.isNotEmpty() ->
                    appendLine("${backup.servers.size} server connection(s) have been restored from the encrypted backup.")
                !wasEncrypted && backup.servers.isNotEmpty() ->
                    appendLine(
                        "Note: ${backup.servers.size} server configuration(s) are in this backup " +
                        "but were NOT automatically restored. Re-add your servers in Server settings."
                    )
            }
        }.trim()
    }

    // ── Scheduled Export ───────────────────────────────────────────────────────

    /**
     * Checks whether a scheduled auto-export is due and, if so, runs it.
     * Uses the PIN from [SettingsRepository.backupPin] when encryption is configured.
     * Call this at app startup.
     */
    suspend fun checkAndRunScheduledExport() {
        val interval = settingsRepository.autoExportInterval.first()
        if (interval == AutoExportInterval.NEVER) return

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
                exportToFile()  // uses stored PIN automatically
            } catch (e: Exception) {
                // Scheduled export is best-effort – don't surface errors to user
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Returns true if [jsonContent] looks like an [EncryptedBackupEnvelope].
     * Uses lightweight string detection to avoid a full parse on every import.
     */
    private fun looksEncrypted(jsonContent: String): Boolean =
        jsonContent.contains("\"encrypted\"") &&
        (jsonContent.contains("\"encrypted\":true") || jsonContent.contains("\"encrypted\": true"))

    companion object {
        const val CURRENT_BACKUP_VERSION = 1
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
