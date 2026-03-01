package com.karakept.app.data.model

import kotlinx.serialization.Serializable

/**
 * Root data class for a full app settings backup.
 * Increment [version] when the schema changes, and handle migration in BackupRepository.
 */
@Serializable
data class AppBackup(
    val version: Int = 1,
    val exportedAt: String,
    val settings: BackupSettings,
    val servers: List<ServerBackup> = emptyList()
)

/**
 * Server connection info included in a backup.
 * Note: this includes the API key – treat the backup file as sensitive.
 */
@Serializable
data class ServerBackup(
    val id: String,
    val url: String,
    val apiKey: String,
    val label: String
)

/**
 * All user-configurable settings that are backed up.
 *
 * **Adding a new setting?**
 * See CONTRIBUTING.md for the full checklist.  In short:
 * 1. Add a field here (with a sensible default so old backups still load).
 * 2. Export it in `BackupRepository.exportSettings()`.
 * 3. Restore it in `BackupRepository.importSettings()`.
 */
@Serializable
data class BackupSettings(
    // Layout / bookmark list
    val layoutType: String = LayoutType.LIST.name,
    val hideArticleThumbnails: Boolean = true,
    val showReadingTimeBadge: Boolean = true,
    val showTags: Boolean = true,
    val dimReadBookmarks: Boolean = true,

    // Viewer / reader
    val viewerMode: String = ViewerMode.READER.name,
    val htmlTextColor: Int? = null,
    val htmlBackgroundColor: Int? = null,
    val htmlFontSize: Int = 16,
    val htmlFontFamily: String = ReaderFontFamily.SYSTEM.name,
    val readingSpeedWpm: Int = 238,
    val trackReadingProgress: Boolean = true,
    val resetProgressOnMarkUnread: Boolean = true,
    val linkOpenMode: String = LinkOpenMode.CUSTOM_TAB.name,

    // Theme
    val themeMode: String = ThemeMode.SYSTEM.name,
    val accentColor: String = AccentColor.PURPLE.name,

    // Swipe actions
    val swipeLeftAction: String = SwipeAction.MARK_READ.name,
    val swipeRightAction: String = SwipeAction.ARCHIVE.name,
    val customSwipeConfigsJson: String = "[]",
    val swipeLeftConfigId: String? = null,
    val swipeRightConfigId: String? = null,

    // Content sync
    val contentSyncStrategy: String = SyncStrategy.PER_BOOKMARK.name,
    val contentSyncTargetLists: Set<String> = emptySet(),
    val contentSyncWithChildren: Set<String> = emptySet(),

    // Notifications
    val notificationsEnabled: Boolean = true,

    // Offline mode
    val offlineMode: Boolean = false,

    // Onboarding
    val onboardingCompleted: Boolean = false,

    // Scheduled auto-export
    val autoExportInterval: String = AutoExportInterval.NEVER.name
)

/**
 * How often the app automatically exports a backup in the background.
 */
enum class AutoExportInterval {
    NEVER,
    DAILY,
    WEEKLY,
    MONTHLY;

    companion object {
        fun fromString(value: String): AutoExportInterval =
            entries.firstOrNull { it.name == value.uppercase() } ?: NEVER
    }
}
