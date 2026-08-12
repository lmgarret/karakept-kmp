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
 * Root wrapper for all backup files. Every backup is AES-256-GCM encrypted with the user's PIN.
 *
 * [data] is the Base64-encoded output of `BackupCrypto.encrypt(appBackupJson.bytes, pin)`.
 */
@Serializable
data class EncryptedBackupEnvelope(
    val version: Int = 2,
    /** Base64-encoded AES-256-GCM ciphertext of the serialised [AppBackup] JSON. */
    val data: String
)

/**
 * Server connection info included in a backup.
 * This includes the API key and is therefore only stored in encrypted backups.
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
 * 1. Add a field here with a sensible default (so old backups still deserialize).
 * 2. Add a derived `Flow<T>` and setter in `SettingsRepository` using `updateSettings { copy(…) }`.
 *
 * `BackupRepository` never needs to be touched — it calls `settingsRepository.currentSettings()`
 * and `settingsRepository.restoreSettings(…)` which always capture the full schema.
 */
@Serializable
data class BackupSettings(
    // Layout / bookmark list
    val layoutType: String = LayoutType.LIST.name,
    val hideArticleThumbnails: Boolean = true,
    val showReadingTimeBadge: Boolean = true,
    val showTags: Boolean = true,
    val dimReadBookmarks: Boolean = true,
    val showScrollCursor: Boolean = true,

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
    val preferFullPageHtml: Boolean = false,
    val readerLineHeightScale: Float = 1.0f,
    val readerHorizontalMarginDp: Int = 28,
    val readerMaxWidthDp: Int = 900,
    val showReaderHeroImage: Boolean = true,

    // E-ink display mode and hardware page-turn buttons
    val einkModeEnabled: Boolean = false,
    val einkDisableAnimations: Boolean = true,
    val einkHighContrast: Boolean = true,
    val einkInstantPageScroll: Boolean = true,
    val pageTurnKeysEnabled: Boolean = true,
    val pageTurnUseVolumeKeys: Boolean = false,
    val pageTurnInvertVolumeKeys: Boolean = false,
    val pageTurnPreviousKeyCode: Int? = null,
    val pageTurnNextKeyCode: Int? = null,
    val pageTurnOverlapPercent: Int = PageTurnKeyBindings.DEFAULT_OVERLAP_PERCENT,

    // Theme
    val themeMode: String = ThemeMode.SYSTEM.name,
    val accentColor: String = AccentColor.PURPLE.name,

    // Swipe actions
    val swipeLeftAction: String = SwipeAction.MARK_READ.name,
    val swipeRightAction: String = SwipeAction.ARCHIVE.name,
    val customSwipeConfigsJson: String = "[]",
    val swipeLeftConfigId: String? = null,
    val swipeRightConfigId: String? = null,
    val rowActionMode: String = RowActionMode.SWIPE.name,

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
    val autoExportInterval: String = AutoExportInterval.NEVER.name,

    // Export directory (null = use the app default backup directory)
    // On Android this is a SAF URI (content://…); on Desktop a regular file path.
    val backupExportDirectory: String? = null,

    // Per-list settings (list ID → settings JSON). Empty map = no per-list overrides.
    val perListSettings: Map<String, ListSettings> = emptyMap(),

    // Default list shown at app startup
    val defaultListType: String = DefaultListType.ALL_BOOKMARKS.name,
    val defaultListId: String? = null,

    // Custom bookmark layouts (JSON-serialised list of BookmarkLayout objects).
    // Empty JSON array = no custom layouts.
    val customLayoutsJson: String = "[]",

    // Default layout id (null = use the built-in default for the current layoutType).
    val defaultLayoutId: String? = null,

    // Backup-PIN hash (PBKDF2, format "<base64salt>:<base64hash>").
    // null = no PIN was configured on the exporting device.
    // Restored together with all other settings so that the importing device preserves
    // the encryption-enabled state.
    val backupPinHash: String? = null
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
