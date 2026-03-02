package com.karakept.app.data.repository

import com.karakept.app.data.model.AppBackup
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.model.ServerBackup
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
     * Because [SettingsRepository] now owns the canonical [BackupSettings] snapshot,
     * this function never needs to be updated when new settings are added.
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
     * Saves the backup to the backup directory and returns the absolute file path.
     * The file name includes a timestamp so multiple exports don't overwrite each other.
     */
    suspend fun exportToFile(): String {
        val backup = buildBackup()
        val jsonString = json.encodeToString(backup)
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val datePart = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}"
        val fileName = "karakept_backup_$datePart.json"
        val backupDir = FileUtils.getBackupDirectory()
        val filePath = FileUtils.saveFile(backupDir, fileName, jsonString.encodeToByteArray())
        settingsRepository.setLastAutoExportTime(Clock.System.now().toEpochMilliseconds())
        return filePath
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    /**
     * Parses [jsonContent] and atomically restores all settings it contains.
     * Unknown fields are silently ignored so that future versions remain compatible.
     * Returns a human-readable summary of what was restored.
     *
     * Because [SettingsRepository.restoreSettings] handles the full write,
     * this function never needs to be updated when new settings are added.
     *
     * @throws Exception if the JSON is invalid or the backup version is unsupported.
     */
    suspend fun importFromJson(jsonContent: String): String {
        val backup = json.decodeFromString<AppBackup>(jsonContent)

        if (backup.version > CURRENT_BACKUP_VERSION) {
            error("Backup was created with a newer version of the app (version ${backup.version}). Please update the app to restore this backup.")
        }

        settingsRepository.restoreSettings(backup.settings)

        val restoredServerCount = backup.servers.size
        return buildString {
            appendLine("Settings restored from backup (exported ${backup.exportedAt}).")
            if (restoredServerCount > 0) {
                appendLine("Note: $restoredServerCount server configuration(s) are in this backup but were NOT automatically restored for security reasons. Re-add your servers in the Server settings.")
            }
        }.trim()
    }

    // ── Scheduled Export ───────────────────────────────────────────────────────

    /**
     * Checks whether a scheduled auto-export is due and, if so, runs it.
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
                exportToFile()
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
