package com.karakept.app.data.repository

import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.model.ViewerMode
import kotlinx.serialization.Serializable

/**
 * Per-category storage data classes for [SettingsRepository].
 *
 * These are **internal implementation details** — not part of the backup file format.
 * The backup file format is defined by [com.karakept.app.data.model.BackupSettings].
 *
 * Each class maps to a separate DataStore key, which provides two benefits:
 * - **Smaller writes**: only the changed category blob is re-serialized and written.
 * - **Finer-grained observers**: a theme change does not wake up reader-settings observers
 *   (the per-category Flow emits the same value → `distinctUntilChanged()` blocks propagation).
 *
 * Adding a new setting requires touching 3 files:
 * 1. [com.karakept.app.data.model.BackupSettings] — add field with default (backup format)
 * 2. The appropriate `Stored*Settings` class below — add field with default (storage format)
 * 3. [SettingsRepository] — add Flow, setter, and map the field in `currentSettings()` / `restoreSettings()`
 */

/** Theme-related settings: colors and visual mode. */
@Serializable
internal data class StoredThemeSettings(
    val themeMode: String = ThemeMode.SYSTEM.name,
    val accentColor: String = AccentColor.PURPLE.name
)

/** Bookmark-list display settings: layout, badges, and visual toggles. */
@Serializable
internal data class StoredDisplaySettings(
    val layoutType: String = LayoutType.LIST.name,
    val hideArticleThumbnails: Boolean = true,
    val showReadingTimeBadge: Boolean = true,
    val showTags: Boolean = true,
    val dimReadBookmarks: Boolean = true
)

/** Reader / viewer settings: fonts, colors, reading speed, and progress tracking. */
@Serializable
internal data class StoredReaderSettings(
    val viewerMode: String = ViewerMode.READER.name,
    val htmlTextColor: Int? = null,
    val htmlBackgroundColor: Int? = null,
    val htmlFontSize: Int = 16,
    val htmlFontFamily: String = ReaderFontFamily.SYSTEM.name,
    val readingSpeedWpm: Int = 238,
    val trackReadingProgress: Boolean = true,
    val resetProgressOnMarkUnread: Boolean = true,
    val linkOpenMode: String = LinkOpenMode.CUSTOM_TAB.name,
    val showTagsInViewer: Boolean = true,
    val scrollToTopEnabled: Boolean = true
)

/** Swipe-action settings: left/right actions and custom swipe configurations. */
@Serializable
internal data class StoredSwipeSettings(
    val swipeLeftAction: String = SwipeAction.MARK_READ.name,
    val swipeRightAction: String = SwipeAction.ARCHIVE.name,
    val customSwipeConfigsJson: String = "[]",
    val swipeLeftConfigId: String? = null,
    val swipeRightConfigId: String? = null
)

/** Content-sync settings: sync strategy and target lists. */
@Serializable
internal data class StoredSyncSettings(
    val contentSyncStrategy: String = SyncStrategy.PER_BOOKMARK.name,
    val contentSyncTargetLists: Set<String> = emptySet(),
    val contentSyncWithChildren: Set<String> = emptySet()
)

/** App-level settings: notifications, offline mode, onboarding state, auto-export schedule, export directory, and backup PIN. */
@Serializable
internal data class StoredAppSettings(
    val notificationsEnabled: Boolean = true,
    val offlineMode: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val autoExportInterval: String = AutoExportInterval.NEVER.name,
    /** null = use the platform default backup directory. On Android a SAF URI; on Desktop a file path. */
    val backupExportDirectory: String? = null,
    /**
     * PBKDF2 hash of the backup PIN (format `"<base64salt>:<base64hash>"`), or null when
     * encryption is disabled. Stored here so it can be round-tripped through [BackupSettings]
     * and re-applied when restoring a backup to a new device.
     */
    val backupPinHash: String? = null
)
