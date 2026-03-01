package com.karakept.app.data.repository

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.AppBackup
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.model.BackupSettings
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.ServerBackup
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.model.ViewerMode
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
     * Collects all current settings and serialises them to a JSON [AppBackup].
     */
    suspend fun buildBackup(): AppBackup {
        val prefs = settingsRepository
        val servers = serverRepository.servers.first().map {
            ServerBackup(it.id, it.url, it.apiKey, it.label)
        }

        val settings = BackupSettings(
            layoutType = prefs.layoutType.first().name,
            hideArticleThumbnails = prefs.hideArticleThumbnails.first(),
            showReadingTimeBadge = prefs.showReadingTimeBadge.first(),
            showTags = prefs.showTags.first(),
            dimReadBookmarks = prefs.dimReadBookmarks.first(),

            viewerMode = prefs.viewerMode.first().name,
            htmlTextColor = prefs.htmlTextColor.first()?.toArgb(),
            htmlBackgroundColor = prefs.htmlBackgroundColor.first()?.toArgb(),
            htmlFontSize = prefs.htmlFontSize.first(),
            htmlFontFamily = prefs.htmlFontFamily.first().name,
            readingSpeedWpm = prefs.readingSpeedWpm.first(),
            trackReadingProgress = prefs.trackReadingProgress.first(),
            resetProgressOnMarkUnread = prefs.resetProgressOnMarkUnread.first(),
            linkOpenMode = prefs.linkOpenMode.first().name,

            themeMode = prefs.themeMode.first().name,
            accentColor = prefs.accentColor.first().name,

            swipeLeftAction = prefs.swipeLeftAction.first().name,
            swipeRightAction = prefs.swipeRightAction.first().name,
            customSwipeConfigsJson = run {
                val configs = prefs.customSwipeActionConfigs.first()
                Json.encodeToString(configs)
            },
            swipeLeftConfigId = prefs.swipeLeftConfigId.first(),
            swipeRightConfigId = prefs.swipeRightConfigId.first(),

            contentSyncStrategy = prefs.contentSyncStrategy.first().name,
            contentSyncTargetLists = prefs.contentSyncConfig.first().selectedLists,
            contentSyncWithChildren = prefs.contentSyncConfig.first().withChildrenMode,

            notificationsEnabled = prefs.notificationsEnabled.first(),
            offlineMode = prefs.offlineMode.first(),
            onboardingCompleted = prefs.onboardingCompleted.first(),
            autoExportInterval = prefs.autoExportInterval.first().name
        )

        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val timestamp = "$now".replace("T", " ").take(19) + " UTC"

        return AppBackup(
            exportedAt = timestamp,
            settings = settings,
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
        val datePart = "${now.year}-${now.monthNumber.toString().padStart(2,'0')}-${now.dayOfMonth.toString().padStart(2,'0')}"
        val fileName = "karakept_backup_$datePart.json"
        val backupDir = FileUtils.getBackupDirectory()
        val filePath = FileUtils.saveFile(backupDir, fileName, jsonString.encodeToByteArray())
        settingsRepository.setLastAutoExportTime(Clock.System.now().toEpochMilliseconds())
        return filePath
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    /**
     * Parses [jsonContent] and applies every setting it contains.
     * Unknown fields are silently ignored so that future versions remain compatible.
     * Returns a human-readable summary of what was restored.
     *
     * @throws Exception if the JSON is invalid or the backup version is unsupported.
     */
    suspend fun importFromJson(jsonContent: String): String {
        val backup = json.decodeFromString<AppBackup>(jsonContent)

        if (backup.version > CURRENT_BACKUP_VERSION) {
            error("Backup was created with a newer version of the app (version ${backup.version}). Please update the app to restore this backup.")
        }

        val s = backup.settings
        val prefs = settingsRepository

        // Layout / list
        prefs.setLayoutType(LayoutType.fromString(s.layoutType))
        prefs.setHideArticleThumbnails(s.hideArticleThumbnails)
        prefs.setShowReadingTimeBadge(s.showReadingTimeBadge)
        prefs.setShowTags(s.showTags)
        prefs.setDimReadBookmarks(s.dimReadBookmarks)

        // Viewer / reader
        prefs.setViewerMode(ViewerMode.fromString(s.viewerMode))
        prefs.setHtmlTextColor(s.htmlTextColor?.let { Color(it) })
        prefs.setHtmlBackgroundColor(s.htmlBackgroundColor?.let { Color(it) })
        prefs.setHtmlFontSize(s.htmlFontSize)
        prefs.setHtmlFontFamily(ReaderFontFamily.fromString(s.htmlFontFamily))
        prefs.setReadingSpeedWpm(s.readingSpeedWpm)
        prefs.setTrackReadingProgress(s.trackReadingProgress)
        prefs.setResetProgressOnMarkUnread(s.resetProgressOnMarkUnread)
        prefs.setLinkOpenMode(LinkOpenMode.fromString(s.linkOpenMode))

        // Theme
        prefs.setThemeMode(ThemeMode.fromString(s.themeMode))
        prefs.setAccentColor(AccentColor.fromString(s.accentColor))

        // Swipe actions
        prefs.setSwipeLeftAction(SwipeAction.fromString(s.swipeLeftAction))
        prefs.setSwipeRightAction(SwipeAction.fromString(s.swipeRightAction))
        val configs = try {
            json.decodeFromString<List<com.karakept.app.data.model.CustomSwipeActionConfig>>(s.customSwipeConfigsJson)
        } catch (e: Exception) {
            emptyList()
        }
        prefs.setCustomSwipeActionConfigs(configs)
        prefs.setSwipeLeftConfigId(s.swipeLeftConfigId)
        prefs.setSwipeRightConfigId(s.swipeRightConfigId)

        // Content sync
        prefs.setContentSyncStrategy(runCatching { SyncStrategy.valueOf(s.contentSyncStrategy) }.getOrDefault(SyncStrategy.PER_BOOKMARK))
        prefs.setContentSyncConfig(com.karakept.app.data.model.ListSyncConfig(s.contentSyncTargetLists, s.contentSyncWithChildren))

        // Notifications / offline / onboarding
        prefs.setNotificationsEnabled(s.notificationsEnabled)
        prefs.setOfflineMode(s.offlineMode)
        prefs.setOnboardingCompleted(s.onboardingCompleted)

        // Scheduled export
        prefs.setAutoExportInterval(AutoExportInterval.fromString(s.autoExportInterval))

        // Server configurations (restore if any present in backup)
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
