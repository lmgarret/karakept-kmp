package com.karakept.app.data.repository

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.model.BackupSettings
import com.karakept.app.data.model.CheckboxState
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.LinkOpenMode
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.ListSyncConfig
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.model.ViewerMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DefaultListType
import com.karakept.app.utils.BackupCrypto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val settingsJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Per-category keys (primary storage) ───────────────────────────────────

    private val THEME_SETTINGS_KEY = stringPreferencesKey("settings_theme_json")
    private val DISPLAY_SETTINGS_KEY = stringPreferencesKey("settings_display_json")
    private val READER_SETTINGS_KEY = stringPreferencesKey("settings_reader_json")
    private val SWIPE_SETTINGS_KEY = stringPreferencesKey("settings_swipe_json")
    private val SYNC_SETTINGS_KEY = stringPreferencesKey("settings_sync_json")
    private val APP_SETTINGS_KEY = stringPreferencesKey("settings_app_json")

    // ── Non-backed-up individual keys (permanent, never move to a category blob) ──

    private val ACTIVE_SERVER_ID_KEY = stringPreferencesKey("active_server_id")
    private val AUTO_OFFLINE_DETECTED_KEY = booleanPreferencesKey("auto_offline_detected")
    private val LAST_AUTO_EXPORT_TIME_KEY = longPreferencesKey("last_auto_export_time")
    private val PER_LIST_SETTINGS_KEY = stringPreferencesKey("per_list_settings")

    /**
     * The actual backup PIN (4–6 digits), stored locally for use by scheduled auto-exports.
     * Never included in the backup file itself; only the derived hash is.
     */
    private val BACKUP_PIN_KEY = stringPreferencesKey("backup_pin")

    // ── Desktop layout (non-backed-up) ──────────────────────────────────────
    private val DRAWER_WIDTH_DP_KEY = floatPreferencesKey("desktop_drawer_width_dp")
    private val LIST_COLUMN_FRACTION_KEY = floatPreferencesKey("desktop_list_column_fraction")

    // ── Desktop window state (non-backed-up) ────────────────────────────────
    private val WINDOW_WIDTH_KEY = floatPreferencesKey("desktop_window_width")
    private val WINDOW_HEIGHT_KEY = floatPreferencesKey("desktop_window_height")
    private val WINDOW_X_KEY = floatPreferencesKey("desktop_window_x")
    private val WINDOW_Y_KEY = floatPreferencesKey("desktop_window_y")
    private val WINDOW_MAXIMIZED_KEY = booleanPreferencesKey("desktop_window_maximized")

    // ── Legacy keys (read-only, migration only) ───────────────────────────────
    // Two generations of legacy storage are supported:
    //
    //   Generation 2 (single blob): all settings in one "settings_json" key.
    //   Generation 1 (individual keys): each setting had its own DataStore key.
    //
    // On first read after upgrade, the category read helpers fall through these
    // generations automatically. Once a per-category key is written, the fallbacks
    // are never consulted again for that category.

    /** Generation-2 single-blob key written by the previous refactor. */
    private val LEGACY_BLOB_KEY = stringPreferencesKey("settings_json")

    /** Generation-1 individual DataStore keys (pre-single-blob). */
    private val LEGACY_LAYOUT_TYPE_KEY = stringPreferencesKey("layout_type")
    private val LEGACY_VIEWER_MODE_KEY = stringPreferencesKey("viewer_mode")
    private val LEGACY_HIDE_ARTICLE_THUMBNAILS_KEY = booleanPreferencesKey("hide_article_thumbnails")
    private val LEGACY_THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val LEGACY_ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
    private val LEGACY_HTML_TEXT_COLOR_KEY = intPreferencesKey("html_text_color")
    private val LEGACY_HTML_BACKGROUND_COLOR_KEY = intPreferencesKey("html_background_color")
    private val LEGACY_HTML_FONT_SIZE_KEY = intPreferencesKey("html_font_size")
    private val LEGACY_HTML_FONT_FAMILY_KEY = stringPreferencesKey("html_font_family")
    private val LEGACY_OFFLINE_MODE_KEY = booleanPreferencesKey("offline_mode")
    private val LEGACY_SHOW_READING_TIME_BADGE_KEY = booleanPreferencesKey("show_reading_time_badge")
    private val LEGACY_SHOW_TAGS_KEY = booleanPreferencesKey("show_tags")
    private val LEGACY_READING_SPEED_WPM_KEY = intPreferencesKey("reading_speed_wpm")
    private val LEGACY_SWIPE_LEFT_ACTION_KEY = stringPreferencesKey("swipe_left_action")
    private val LEGACY_SWIPE_RIGHT_ACTION_KEY = stringPreferencesKey("swipe_right_action")
    private val LEGACY_CUSTOM_SWIPE_CONFIGS_KEY = stringPreferencesKey("custom_swipe_configs")
    private val LEGACY_SWIPE_LEFT_CONFIG_ID_KEY = stringPreferencesKey("swipe_left_config_id")
    private val LEGACY_SWIPE_RIGHT_CONFIG_ID_KEY = stringPreferencesKey("swipe_right_config_id")
    private val LEGACY_CONTENT_SYNC_STRATEGY_KEY = stringPreferencesKey("content_sync_strategy")
    private val LEGACY_CONTENT_SYNC_TARGET_LISTS_KEY = stringSetPreferencesKey("content_sync_target_lists")
    private val LEGACY_CONTENT_SYNC_WITH_CHILDREN_KEY = stringSetPreferencesKey("content_sync_with_children")
    private val LEGACY_NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
    private val LEGACY_LINK_OPEN_MODE_KEY = stringPreferencesKey("link_open_mode")
    private val LEGACY_DIM_READ_BOOKMARKS_KEY = booleanPreferencesKey("dim_read_bookmarks")
    private val LEGACY_TRACK_READING_PROGRESS_KEY = booleanPreferencesKey("track_reading_progress")
    private val LEGACY_RESET_PROGRESS_ON_MARK_UNREAD_KEY = booleanPreferencesKey("reset_progress_on_mark_unread")
    private val LEGACY_AUTO_EXPORT_INTERVAL_KEY = stringPreferencesKey("auto_export_interval")
    private val LEGACY_ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")

    // ── Category read helpers (migration-aware) ───────────────────────────────
    //
    // Each helper tries, in order:
    //   1. The new per-category key (primary)
    //   2. The generation-2 single blob (previous refactor)
    //   3. The generation-1 individual keys (original individual-key storage)

    private fun Preferences.readThemeSettings(): StoredThemeSettings {
        this[THEME_SETTINGS_KEY]?.let { json ->
            runCatching { settingsJson.decodeFromString<StoredThemeSettings>(json) }.getOrNull()?.let { return it }
        }
        this[LEGACY_BLOB_KEY]?.let { blob ->
            runCatching { settingsJson.decodeFromString<BackupSettings>(blob) }.getOrNull()?.let { all ->
                return StoredThemeSettings(themeMode = all.themeMode, accentColor = all.accentColor)
            }
        }
        return StoredThemeSettings(
            themeMode = this[LEGACY_THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name,
            accentColor = this[LEGACY_ACCENT_COLOR_KEY] ?: AccentColor.PURPLE.name
        )
    }

    private fun Preferences.readDisplaySettings(): StoredDisplaySettings {
        this[DISPLAY_SETTINGS_KEY]?.let { json ->
            runCatching { settingsJson.decodeFromString<StoredDisplaySettings>(json) }.getOrNull()?.let { return it }
        }
        this[LEGACY_BLOB_KEY]?.let { blob ->
            runCatching { settingsJson.decodeFromString<BackupSettings>(blob) }.getOrNull()?.let { all ->
                return StoredDisplaySettings(
                    layoutType = all.layoutType,
                    hideArticleThumbnails = all.hideArticleThumbnails,
                    showReadingTimeBadge = all.showReadingTimeBadge,
                    showTags = all.showTags,
                    dimReadBookmarks = all.dimReadBookmarks
                )
            }
        }
        return StoredDisplaySettings(
            layoutType = this[LEGACY_LAYOUT_TYPE_KEY] ?: LayoutType.LIST.name,
            hideArticleThumbnails = this[LEGACY_HIDE_ARTICLE_THUMBNAILS_KEY] ?: true,
            showReadingTimeBadge = this[LEGACY_SHOW_READING_TIME_BADGE_KEY] ?: true,
            showTags = this[LEGACY_SHOW_TAGS_KEY] ?: true,
            dimReadBookmarks = this[LEGACY_DIM_READ_BOOKMARKS_KEY] ?: true
        )
    }

    private fun Preferences.readReaderSettings(): StoredReaderSettings {
        this[READER_SETTINGS_KEY]?.let { json ->
            runCatching { settingsJson.decodeFromString<StoredReaderSettings>(json) }.getOrNull()?.let { return it }
        }
        this[LEGACY_BLOB_KEY]?.let { blob ->
            runCatching { settingsJson.decodeFromString<BackupSettings>(blob) }.getOrNull()?.let { all ->
                return StoredReaderSettings(
                    viewerMode = all.viewerMode,
                    htmlTextColor = all.htmlTextColor,
                    htmlBackgroundColor = all.htmlBackgroundColor,
                    htmlFontSize = all.htmlFontSize,
                    htmlFontFamily = all.htmlFontFamily,
                    readingSpeedWpm = all.readingSpeedWpm,
                    trackReadingProgress = all.trackReadingProgress,
                    resetProgressOnMarkUnread = all.resetProgressOnMarkUnread,
                    linkOpenMode = all.linkOpenMode
                )
            }
        }
        return StoredReaderSettings(
            viewerMode = this[LEGACY_VIEWER_MODE_KEY] ?: ViewerMode.READER.name,
            htmlTextColor = this[LEGACY_HTML_TEXT_COLOR_KEY],
            htmlBackgroundColor = this[LEGACY_HTML_BACKGROUND_COLOR_KEY],
            htmlFontSize = this[LEGACY_HTML_FONT_SIZE_KEY] ?: 16,
            htmlFontFamily = this[LEGACY_HTML_FONT_FAMILY_KEY] ?: ReaderFontFamily.SYSTEM.name,
            readingSpeedWpm = this[LEGACY_READING_SPEED_WPM_KEY] ?: 238,
            trackReadingProgress = this[LEGACY_TRACK_READING_PROGRESS_KEY] ?: true,
            resetProgressOnMarkUnread = this[LEGACY_RESET_PROGRESS_ON_MARK_UNREAD_KEY] ?: true,
            linkOpenMode = this[LEGACY_LINK_OPEN_MODE_KEY] ?: LinkOpenMode.CUSTOM_TAB.name
        )
    }

    private fun Preferences.readSwipeSettings(): StoredSwipeSettings {
        this[SWIPE_SETTINGS_KEY]?.let { json ->
            runCatching { settingsJson.decodeFromString<StoredSwipeSettings>(json) }.getOrNull()?.let { return it }
        }
        this[LEGACY_BLOB_KEY]?.let { blob ->
            runCatching { settingsJson.decodeFromString<BackupSettings>(blob) }.getOrNull()?.let { all ->
                return StoredSwipeSettings(
                    swipeLeftAction = all.swipeLeftAction,
                    swipeRightAction = all.swipeRightAction,
                    customSwipeConfigsJson = all.customSwipeConfigsJson,
                    swipeLeftConfigId = all.swipeLeftConfigId,
                    swipeRightConfigId = all.swipeRightConfigId
                )
            }
        }
        return StoredSwipeSettings(
            swipeLeftAction = this[LEGACY_SWIPE_LEFT_ACTION_KEY] ?: SwipeAction.MARK_READ.name,
            swipeRightAction = this[LEGACY_SWIPE_RIGHT_ACTION_KEY] ?: SwipeAction.ARCHIVE.name,
            customSwipeConfigsJson = this[LEGACY_CUSTOM_SWIPE_CONFIGS_KEY] ?: "[]",
            swipeLeftConfigId = this[LEGACY_SWIPE_LEFT_CONFIG_ID_KEY],
            swipeRightConfigId = this[LEGACY_SWIPE_RIGHT_CONFIG_ID_KEY]
        )
    }

    private fun Preferences.readSyncSettings(): StoredSyncSettings {
        this[SYNC_SETTINGS_KEY]?.let { json ->
            runCatching { settingsJson.decodeFromString<StoredSyncSettings>(json) }.getOrNull()?.let { return it }
        }
        this[LEGACY_BLOB_KEY]?.let { blob ->
            runCatching { settingsJson.decodeFromString<BackupSettings>(blob) }.getOrNull()?.let { all ->
                return StoredSyncSettings(
                    contentSyncStrategy = all.contentSyncStrategy,
                    contentSyncTargetLists = all.contentSyncTargetLists,
                    contentSyncWithChildren = all.contentSyncWithChildren
                )
            }
        }
        return StoredSyncSettings(
            contentSyncStrategy = this[LEGACY_CONTENT_SYNC_STRATEGY_KEY] ?: SyncStrategy.PER_BOOKMARK.name,
            contentSyncTargetLists = this[LEGACY_CONTENT_SYNC_TARGET_LISTS_KEY] ?: emptySet(),
            contentSyncWithChildren = this[LEGACY_CONTENT_SYNC_WITH_CHILDREN_KEY] ?: emptySet()
        )
    }

    private fun Preferences.readAppSettings(): StoredAppSettings {
        this[APP_SETTINGS_KEY]?.let { json ->
            runCatching { settingsJson.decodeFromString<StoredAppSettings>(json) }.getOrNull()?.let { return it }
        }
        this[LEGACY_BLOB_KEY]?.let { blob ->
            runCatching { settingsJson.decodeFromString<BackupSettings>(blob) }.getOrNull()?.let { all ->
                return StoredAppSettings(
                    notificationsEnabled = all.notificationsEnabled,
                    offlineMode = all.offlineMode,
                    onboardingCompleted = all.onboardingCompleted,
                    autoExportInterval = all.autoExportInterval
                )
            }
        }
        return StoredAppSettings(
            notificationsEnabled = this[LEGACY_NOTIFICATIONS_ENABLED_KEY] ?: true,
            offlineMode = this[LEGACY_OFFLINE_MODE_KEY] ?: false,
            onboardingCompleted = this[LEGACY_ONBOARDING_COMPLETED_KEY] ?: false,
            autoExportInterval = this[LEGACY_AUTO_EXPORT_INTERVAL_KEY] ?: AutoExportInterval.NEVER.name,
            backupExportDirectory = null,
            backupPinHash = null
        )
    }

    // ── Per-category flows ────────────────────────────────────────────────────
    //
    // Each category flow uses `distinctUntilChanged()` so that a write to an
    // *unrelated* category does not propagate to this category's downstream observers.

    private val themeSettingsFlow: Flow<StoredThemeSettings> = dataStore.data
        .map { it.readThemeSettings() }
        .distinctUntilChanged()

    private val displaySettingsFlow: Flow<StoredDisplaySettings> = dataStore.data
        .map { it.readDisplaySettings() }
        .distinctUntilChanged()

    private val readerSettingsFlow: Flow<StoredReaderSettings> = dataStore.data
        .map { it.readReaderSettings() }
        .distinctUntilChanged()

    private val swipeSettingsFlow: Flow<StoredSwipeSettings> = dataStore.data
        .map { it.readSwipeSettings() }
        .distinctUntilChanged()

    private val syncSettingsFlow: Flow<StoredSyncSettings> = dataStore.data
        .map { it.readSyncSettings() }
        .distinctUntilChanged()

    private val appSettingsFlow: Flow<StoredAppSettings> = dataStore.data
        .map { it.readAppSettings() }
        .distinctUntilChanged()

    // ── Backup API ────────────────────────────────────────────────────────────

    /**
     * Returns a one-shot snapshot of all backed-up settings as a flat [BackupSettings].
     * Reads all category blobs plus the per-list-settings key in a single DataStore snapshot.
     * Used by [com.karakept.app.data.repository.BackupRepository].
     */
    suspend fun currentSettings(): BackupSettings {
        val prefs = dataStore.data.first()
        val theme = prefs.readThemeSettings()
        val display = prefs.readDisplaySettings()
        val reader = prefs.readReaderSettings()
        val swipe = prefs.readSwipeSettings()
        val sync = prefs.readSyncSettings()
        val app = prefs.readAppSettings()
        val listSettingsMap: Map<String, ListSettings> = runCatching {
            settingsJson.decodeFromString<Map<String, ListSettings>>(prefs[PER_LIST_SETTINGS_KEY] ?: "{}")
        }.getOrDefault(emptyMap())
        return BackupSettings(
            themeMode = theme.themeMode,
            accentColor = theme.accentColor,
            layoutType = display.layoutType,
            hideArticleThumbnails = display.hideArticleThumbnails,
            showReadingTimeBadge = display.showReadingTimeBadge,
            showTags = display.showTags,
            dimReadBookmarks = display.dimReadBookmarks,
            viewerMode = reader.viewerMode,
            htmlTextColor = reader.htmlTextColor,
            htmlBackgroundColor = reader.htmlBackgroundColor,
            htmlFontSize = reader.htmlFontSize,
            htmlFontFamily = reader.htmlFontFamily,
            readingSpeedWpm = reader.readingSpeedWpm,
            trackReadingProgress = reader.trackReadingProgress,
            resetProgressOnMarkUnread = reader.resetProgressOnMarkUnread,
            linkOpenMode = reader.linkOpenMode,
            swipeLeftAction = swipe.swipeLeftAction,
            swipeRightAction = swipe.swipeRightAction,
            customSwipeConfigsJson = swipe.customSwipeConfigsJson,
            swipeLeftConfigId = swipe.swipeLeftConfigId,
            swipeRightConfigId = swipe.swipeRightConfigId,
            contentSyncStrategy = sync.contentSyncStrategy,
            contentSyncTargetLists = sync.contentSyncTargetLists,
            contentSyncWithChildren = sync.contentSyncWithChildren,
            notificationsEnabled = app.notificationsEnabled,
            offlineMode = app.offlineMode,
            onboardingCompleted = app.onboardingCompleted,
            autoExportInterval = app.autoExportInterval,
            backupExportDirectory = app.backupExportDirectory,
            perListSettings = listSettingsMap,
            defaultListType = prefs[DEFAULT_LIST_TYPE_KEY] ?: DefaultListType.ALL_BOOKMARKS.name,
            defaultListId = prefs[DEFAULT_LIST_ID_KEY],
            backupPinHash = app.backupPinHash
        )
    }

    /**
     * Atomically replaces all backed-up settings with [s].
     * All six category blobs are written in a single DataStore transaction.
     * Used by [BackupRepository].
     */
    suspend fun restoreSettings(s: BackupSettings) {
        dataStore.edit { prefs ->
            prefs[THEME_SETTINGS_KEY] = settingsJson.encodeToString(
                StoredThemeSettings(themeMode = s.themeMode, accentColor = s.accentColor)
            )
            prefs[DISPLAY_SETTINGS_KEY] = settingsJson.encodeToString(
                StoredDisplaySettings(
                    layoutType = s.layoutType,
                    hideArticleThumbnails = s.hideArticleThumbnails,
                    showReadingTimeBadge = s.showReadingTimeBadge,
                    showTags = s.showTags,
                    dimReadBookmarks = s.dimReadBookmarks
                )
            )
            prefs[READER_SETTINGS_KEY] = settingsJson.encodeToString(
                StoredReaderSettings(
                    viewerMode = s.viewerMode,
                    htmlTextColor = s.htmlTextColor,
                    htmlBackgroundColor = s.htmlBackgroundColor,
                    htmlFontSize = s.htmlFontSize,
                    htmlFontFamily = s.htmlFontFamily,
                    readingSpeedWpm = s.readingSpeedWpm,
                    trackReadingProgress = s.trackReadingProgress,
                    resetProgressOnMarkUnread = s.resetProgressOnMarkUnread,
                    linkOpenMode = s.linkOpenMode
                )
            )
            prefs[SWIPE_SETTINGS_KEY] = settingsJson.encodeToString(
                StoredSwipeSettings(
                    swipeLeftAction = s.swipeLeftAction,
                    swipeRightAction = s.swipeRightAction,
                    customSwipeConfigsJson = s.customSwipeConfigsJson,
                    swipeLeftConfigId = s.swipeLeftConfigId,
                    swipeRightConfigId = s.swipeRightConfigId
                )
            )
            prefs[SYNC_SETTINGS_KEY] = settingsJson.encodeToString(
                StoredSyncSettings(
                    contentSyncStrategy = s.contentSyncStrategy,
                    contentSyncTargetLists = s.contentSyncTargetLists,
                    contentSyncWithChildren = s.contentSyncWithChildren
                )
            )
            prefs[APP_SETTINGS_KEY] = settingsJson.encodeToString(
                StoredAppSettings(
                    notificationsEnabled = s.notificationsEnabled,
                    offlineMode = s.offlineMode,
                    onboardingCompleted = s.onboardingCompleted,
                    autoExportInterval = s.autoExportInterval,
                    backupExportDirectory = s.backupExportDirectory,
                    backupPinHash = s.backupPinHash
                )
            )
            // Restore per-list settings
            prefs[PER_LIST_SETTINGS_KEY] = settingsJson.encodeToString<Map<String, ListSettings>>(s.perListSettings)
            // Restore default list
            prefs[DEFAULT_LIST_TYPE_KEY] = s.defaultListType
            if (s.defaultListId != null) {
                prefs[DEFAULT_LIST_ID_KEY] = s.defaultListId
            } else {
                prefs.remove(DEFAULT_LIST_ID_KEY)
            }
        }
    }

    // ── Derived flows (theme) ─────────────────────────────────────────────────

    val themeMode: Flow<ThemeMode> =
        themeSettingsFlow.map { ThemeMode.fromString(it.themeMode) }.distinctUntilChanged()

    val accentColor: Flow<AccentColor> =
        themeSettingsFlow.map { AccentColor.fromString(it.accentColor) }.distinctUntilChanged()

    // ── Derived flows (display) ───────────────────────────────────────────────

    val layoutType: Flow<LayoutType> =
        displaySettingsFlow.map { LayoutType.fromString(it.layoutType) }.distinctUntilChanged()

    val hideArticleThumbnails: Flow<Boolean> =
        displaySettingsFlow.map { it.hideArticleThumbnails }.distinctUntilChanged()

    val showReadingTimeBadge: Flow<Boolean> =
        displaySettingsFlow.map { it.showReadingTimeBadge }.distinctUntilChanged()

    val showTags: Flow<Boolean> =
        displaySettingsFlow.map { it.showTags }.distinctUntilChanged()

    val dimReadBookmarks: Flow<Boolean> =
        displaySettingsFlow.map { it.dimReadBookmarks }.distinctUntilChanged()

    // ── Derived flows (reader) ────────────────────────────────────────────────

    val viewerMode: Flow<ViewerMode> =
        readerSettingsFlow.map { ViewerMode.fromString(it.viewerMode) }.distinctUntilChanged()

    val htmlTextColor: Flow<Color?> =
        readerSettingsFlow.map { it.htmlTextColor?.let { c -> Color(c) } }.distinctUntilChanged()

    val htmlBackgroundColor: Flow<Color?> =
        readerSettingsFlow.map { it.htmlBackgroundColor?.let { c -> Color(c) } }.distinctUntilChanged()

    val htmlFontSize: Flow<Int> =
        readerSettingsFlow.map { it.htmlFontSize }.distinctUntilChanged()

    val htmlFontFamily: Flow<ReaderFontFamily> =
        readerSettingsFlow.map { ReaderFontFamily.fromString(it.htmlFontFamily) }.distinctUntilChanged()

    val readingSpeedWpm: Flow<Int> =
        readerSettingsFlow.map { it.readingSpeedWpm }.distinctUntilChanged()

    val trackReadingProgress: Flow<Boolean> =
        readerSettingsFlow.map { it.trackReadingProgress }.distinctUntilChanged()

    val resetProgressOnMarkUnread: Flow<Boolean> =
        readerSettingsFlow.map { it.resetProgressOnMarkUnread }.distinctUntilChanged()

    val linkOpenMode: Flow<LinkOpenMode> =
        readerSettingsFlow.map { LinkOpenMode.fromString(it.linkOpenMode) }.distinctUntilChanged()

    val showTagsInViewer: Flow<Boolean> =
        readerSettingsFlow.map { it.showTagsInViewer }.distinctUntilChanged()

    // ── Derived flows (swipe) ─────────────────────────────────────────────────

    val swipeLeftAction: Flow<SwipeAction> =
        swipeSettingsFlow.map { SwipeAction.fromString(it.swipeLeftAction) }.distinctUntilChanged()

    val swipeRightAction: Flow<SwipeAction> =
        swipeSettingsFlow.map { SwipeAction.fromString(it.swipeRightAction) }.distinctUntilChanged()

    val customSwipeActionConfigs: Flow<List<CustomSwipeActionConfig>> = swipeSettingsFlow.map { s ->
        runCatching { settingsJson.decodeFromString<List<CustomSwipeActionConfig>>(s.customSwipeConfigsJson) }
            .getOrDefault(emptyList())
    }.distinctUntilChanged()

    val swipeLeftConfigId: Flow<String?> =
        swipeSettingsFlow.map { it.swipeLeftConfigId }.distinctUntilChanged()

    val swipeRightConfigId: Flow<String?> =
        swipeSettingsFlow.map { it.swipeRightConfigId }.distinctUntilChanged()

    // ── Derived flows (sync) ──────────────────────────────────────────────────

    val contentSyncStrategy: Flow<SyncStrategy> = syncSettingsFlow.map { s ->
        runCatching { SyncStrategy.valueOf(s.contentSyncStrategy) }.getOrDefault(SyncStrategy.PER_BOOKMARK)
    }.distinctUntilChanged()

    val contentSyncTargetLists: Flow<Set<String>> =
        syncSettingsFlow.map { it.contentSyncTargetLists }.distinctUntilChanged()

    val contentSyncConfig: Flow<ListSyncConfig> = syncSettingsFlow.map { s ->
        ListSyncConfig(s.contentSyncTargetLists, s.contentSyncWithChildren)
    }.distinctUntilChanged()

    // ── Derived flows (app) ───────────────────────────────────────────────────

    val notificationsEnabled: Flow<Boolean> =
        appSettingsFlow.map { it.notificationsEnabled }.distinctUntilChanged()

    val offlineMode: Flow<Boolean> =
        appSettingsFlow.map { it.offlineMode }.distinctUntilChanged()

    val autoExportInterval: Flow<AutoExportInterval> = appSettingsFlow.map { s ->
        AutoExportInterval.fromString(s.autoExportInterval)
    }.distinctUntilChanged()

    val onboardingCompleted: Flow<Boolean> =
        appSettingsFlow.map { it.onboardingCompleted }.distinctUntilChanged()

    /** The user-configured backup export directory, or null if the app default should be used. */
    val backupExportDirectory: Flow<String?> =
        appSettingsFlow.map { it.backupExportDirectory }.distinctUntilChanged()

    /**
     * The PBKDF2 hash of the backup PIN, or null when encryption is disabled.
     * Included in backups so the importing device knows encryption was configured.
     */
    val backupPinHash: Flow<String?> =
        appSettingsFlow.map { it.backupPinHash }.distinctUntilChanged()

    /**
     * The actual backup PIN stored locally for scheduled auto-exports, or null if not set.
     * Never included in the backup file itself — only the derived hash is.
     */
    val backupPin: Flow<String?> = dataStore.data.map { it[BACKUP_PIN_KEY] }

    // ── Non-backed-up flows (individual keys, unchanged) ─────────────────────

    val activeServerId: Flow<String?> = dataStore.data.map { it[ACTIVE_SERVER_ID_KEY] }

    /**
     * Auto-detected offline state – set when network requests fail.
     */
    val autoOfflineDetected: Flow<Boolean> =
        dataStore.data.map { it[AUTO_OFFLINE_DETECTED_KEY] ?: false }

    /**
     * Effective offline mode – true if either manual offline mode OR auto-detected offline.
     * Use this for blocking network requests.
     */
    val effectiveOfflineMode: Flow<Boolean> =
        combine(offlineMode, autoOfflineDetected) { manual, auto -> manual || auto }

    val lastAutoExportTime: Flow<Long> =
        dataStore.data.map { it[LAST_AUTO_EXPORT_TIME_KEY] ?: 0L }

    // ── Desktop layout flows (non-backed-up) ─────────────────────────────────

    val drawerWidthDp: Flow<Float> =
        dataStore.data.map { it[DRAWER_WIDTH_DP_KEY] ?: 280f }

    val listColumnFraction: Flow<Float> =
        dataStore.data.map { it[LIST_COLUMN_FRACTION_KEY] ?: 0.4f }

    // ── Desktop window state flows (non-backed-up) ───────────────────────────

    val windowWidth: Flow<Float?> = dataStore.data.map { it[WINDOW_WIDTH_KEY] }
    val windowHeight: Flow<Float?> = dataStore.data.map { it[WINDOW_HEIGHT_KEY] }
    val windowX: Flow<Float?> = dataStore.data.map { it[WINDOW_X_KEY] }
    val windowY: Flow<Float?> = dataStore.data.map { it[WINDOW_Y_KEY] }
    val windowMaximized: Flow<Boolean> = dataStore.data.map { it[WINDOW_MAXIMIZED_KEY] ?: true }

    // ── Setters (theme) ───────────────────────────────────────────────────────

    suspend fun setThemeMode(mode: ThemeMode) =
        updateThemeSettings { copy(themeMode = mode.name) }

    suspend fun setAccentColor(color: AccentColor) =
        updateThemeSettings { copy(accentColor = color.name) }

    // ── Setters (display) ─────────────────────────────────────────────────────

    suspend fun setLayoutType(layoutType: LayoutType) =
        updateDisplaySettings { copy(layoutType = layoutType.name) }

    suspend fun setHideArticleThumbnails(hide: Boolean) =
        updateDisplaySettings { copy(hideArticleThumbnails = hide) }

    suspend fun setShowReadingTimeBadge(show: Boolean) =
        updateDisplaySettings { copy(showReadingTimeBadge = show) }

    suspend fun setShowTags(show: Boolean) =
        updateDisplaySettings { copy(showTags = show) }

    suspend fun setDimReadBookmarks(dim: Boolean) =
        updateDisplaySettings { copy(dimReadBookmarks = dim) }

    // ── Setters (reader) ──────────────────────────────────────────────────────

    suspend fun setViewerMode(mode: ViewerMode) =
        updateReaderSettings { copy(viewerMode = mode.name) }

    suspend fun setHtmlTextColor(color: Color?) =
        updateReaderSettings { copy(htmlTextColor = color?.toArgb()) }

    suspend fun setHtmlBackgroundColor(color: Color?) =
        updateReaderSettings { copy(htmlBackgroundColor = color?.toArgb()) }

    suspend fun setHtmlFontSize(size: Int) =
        updateReaderSettings { copy(htmlFontSize = size) }

    suspend fun setHtmlFontFamily(family: ReaderFontFamily) =
        updateReaderSettings { copy(htmlFontFamily = family.name) }

    suspend fun setReadingSpeedWpm(wpm: Int) =
        updateReaderSettings { copy(readingSpeedWpm = wpm.coerceIn(100, 500)) }

    suspend fun setTrackReadingProgress(enabled: Boolean) =
        updateReaderSettings { copy(trackReadingProgress = enabled) }

    suspend fun setResetProgressOnMarkUnread(enabled: Boolean) =
        updateReaderSettings { copy(resetProgressOnMarkUnread = enabled) }

    suspend fun setLinkOpenMode(mode: LinkOpenMode) =
        updateReaderSettings { copy(linkOpenMode = mode.name) }

    suspend fun setShowTagsInViewer(show: Boolean) =
        updateReaderSettings { copy(showTagsInViewer = show) }

    suspend fun resetReaderAppearance() = updateReaderSettings {
        copy(
            htmlTextColor = null,
            htmlBackgroundColor = null,
            htmlFontSize = 16,
            htmlFontFamily = ReaderFontFamily.SYSTEM.name
        )
    }

    // ── Setters (swipe) ───────────────────────────────────────────────────────

    suspend fun setSwipeLeftAction(action: SwipeAction) =
        updateSwipeSettings { copy(swipeLeftAction = action.name) }

    suspend fun setSwipeRightAction(action: SwipeAction) =
        updateSwipeSettings { copy(swipeRightAction = action.name) }

    suspend fun setCustomSwipeActionConfigs(configs: List<CustomSwipeActionConfig>) =
        updateSwipeSettings { copy(customSwipeConfigsJson = settingsJson.encodeToString(configs)) }

    suspend fun setSwipeLeftConfigId(id: String?) =
        updateSwipeSettings { copy(swipeLeftConfigId = id) }

    suspend fun setSwipeRightConfigId(id: String?) =
        updateSwipeSettings { copy(swipeRightConfigId = id) }

    // ── Setters (sync) ────────────────────────────────────────────────────────

    suspend fun setContentSyncStrategy(strategy: SyncStrategy) =
        updateSyncSettings { copy(contentSyncStrategy = strategy.name) }

    suspend fun setContentSyncTargetLists(listIds: Set<String>) =
        updateSyncSettings { copy(contentSyncTargetLists = listIds) }

    suspend fun toggleContentSyncTargetList(listId: String) = updateSyncSettings {
        val current = contentSyncTargetLists.toMutableSet()
        if (current.contains(listId)) current.remove(listId) else current.add(listId)
        copy(contentSyncTargetLists = current)
    }

    suspend fun updateListSyncState(listId: String, newState: CheckboxState) = updateSyncSettings {
        val currentSelected = contentSyncTargetLists.toMutableSet()
        val currentWithChildren = contentSyncWithChildren.toMutableSet()
        when (newState) {
            CheckboxState.UNCHECKED -> {
                currentSelected.remove(listId)
                currentWithChildren.remove(listId)
            }
            CheckboxState.CHECKED_PARENT_ONLY -> {
                currentSelected.add(listId)
                currentWithChildren.remove(listId)
            }
            CheckboxState.CHECKED_WITH_CHILDREN -> {
                currentSelected.add(listId)
                currentWithChildren.add(listId)
            }
        }
        copy(contentSyncTargetLists = currentSelected, contentSyncWithChildren = currentWithChildren)
    }

    suspend fun setContentSyncConfig(config: ListSyncConfig) = updateSyncSettings {
        copy(
            contentSyncTargetLists = config.selectedLists,
            contentSyncWithChildren = config.withChildrenMode
        )
    }

    // ── Setters (app) ─────────────────────────────────────────────────────────

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        updateAppSettings { copy(notificationsEnabled = enabled) }

    suspend fun setOfflineMode(enabled: Boolean) =
        updateAppSettings { copy(offlineMode = enabled) }

    suspend fun setOnboardingCompleted(completed: Boolean) =
        updateAppSettings { copy(onboardingCompleted = completed) }

    suspend fun setAutoExportInterval(interval: AutoExportInterval) =
        updateAppSettings { copy(autoExportInterval = interval.name) }

    suspend fun setBackupExportDirectory(path: String?) =
        updateAppSettings { copy(backupExportDirectory = path) }

    /**
     * Sets (or clears) the backup PIN.
     * Writes the actual PIN to [BACKUP_PIN_KEY] (for scheduled exports) and
     * stores its PBKDF2 hash in [StoredAppSettings.backupPinHash] (for verification and backup).
     */
    suspend fun setBackupPin(pin: String?) {
        dataStore.edit { prefs ->
            if (pin != null) {
                prefs[BACKUP_PIN_KEY] = pin
            } else {
                prefs.remove(BACKUP_PIN_KEY)
            }
        }
        updateAppSettings {
            copy(backupPinHash = if (pin != null) BackupCrypto.hashPin(pin) else null)
        }
    }

    // ── Setters (non-backed-up individual keys) ───────────────────────────────

    suspend fun setActiveServerId(id: String) {
        dataStore.edit { it[ACTIVE_SERVER_ID_KEY] = id }
    }

    /**
     * Set the auto-detected offline state.
     * Call this when network requests fail to automatically switch to offline mode.
     */
    suspend fun setAutoOfflineDetected(detected: Boolean) {
        dataStore.edit { it[AUTO_OFFLINE_DETECTED_KEY] = detected }
    }

    /**
     * Clear the auto-detected offline state.
     * Call this when the user manually goes online or when network is restored.
     */
    suspend fun clearAutoOfflineDetected() {
        dataStore.edit { it[AUTO_OFFLINE_DETECTED_KEY] = false }
    }

    // ── Setters (desktop layout, non-backed-up) ────────────────────────────────

    suspend fun setDrawerWidthDp(width: Float) {
        dataStore.edit { it[DRAWER_WIDTH_DP_KEY] = width }
    }

    suspend fun setListColumnFraction(fraction: Float) {
        dataStore.edit { it[LIST_COLUMN_FRACTION_KEY] = fraction }
    }

    // ── Setters (desktop window state, non-backed-up) ────────────────────────

    suspend fun setWindowState(width: Float, height: Float, x: Float, y: Float, maximized: Boolean) {
        dataStore.edit { prefs ->
            prefs[WINDOW_WIDTH_KEY] = width
            prefs[WINDOW_HEIGHT_KEY] = height
            prefs[WINDOW_X_KEY] = x
            prefs[WINDOW_Y_KEY] = y
            prefs[WINDOW_MAXIMIZED_KEY] = maximized
        }
    }

    // ── Default list settings (non-backed-up individual keys) ────────────────

    private val DEFAULT_LIST_TYPE_KEY = stringPreferencesKey("default_list_type")
    private val DEFAULT_LIST_ID_KEY = stringPreferencesKey("default_list_id")

    val defaultListType: Flow<DefaultListType> = dataStore.data.map { prefs ->
        DefaultListType.fromString(prefs[DEFAULT_LIST_TYPE_KEY] ?: DefaultListType.ALL_BOOKMARKS.name)
    }

    val defaultListId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[DEFAULT_LIST_ID_KEY]
    }

    suspend fun setDefaultListType(type: DefaultListType) {
        dataStore.edit { it[DEFAULT_LIST_TYPE_KEY] = type.name }
    }

    suspend fun setDefaultListId(id: String?) {
        dataStore.edit { prefs ->
            if (id != null) {
                prefs[DEFAULT_LIST_ID_KEY] = id
            } else {
                prefs.remove(DEFAULT_LIST_ID_KEY)
            }
        }
    }

    suspend fun setLastAutoExportTime(timestamp: Long) {
        dataStore.edit { it[LAST_AUTO_EXPORT_TIME_KEY] = timestamp }
    }

    // ── Date display settings (non-backed-up individual keys) ────────────────

    private val SHOW_DATE_IN_LIST_KEY = booleanPreferencesKey("show_date_in_list")
    private val DATE_DISPLAY_MODE_KEY = stringPreferencesKey("date_display_mode")

    val showDateInList: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_DATE_IN_LIST_KEY] ?: true
    }

    val dateDisplayMode: Flow<DateDisplayMode> = dataStore.data.map { preferences ->
        val modeString = preferences[DATE_DISPLAY_MODE_KEY] ?: DateDisplayMode.ELAPSED.name
        DateDisplayMode.fromString(modeString)
    }

    suspend fun setShowDateInList(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_DATE_IN_LIST_KEY] = show
        }
    }

    suspend fun setDateDisplayMode(mode: DateDisplayMode) {
        dataStore.edit { preferences ->
            preferences[DATE_DISPLAY_MODE_KEY] = mode.name
        }
    }

    // ── Per-list settings (separate JSON blob, not part of the backup) ────────

    val allListSettings: Flow<Map<String, ListSettings>> = dataStore.data.map { prefs ->
        runCatching {
            settingsJson.decodeFromString<Map<String, ListSettings>>(prefs[PER_LIST_SETTINGS_KEY] ?: "{}")
        }.getOrDefault(emptyMap())
    }

    fun getListSettings(listId: String): Flow<ListSettings> = dataStore.data.map { prefs ->
        runCatching {
            val map: Map<String, ListSettings> =
                settingsJson.decodeFromString(prefs[PER_LIST_SETTINGS_KEY] ?: "{}")
            map[listId] ?: ListSettings()
        }.getOrDefault(ListSettings())
    }

    suspend fun setListSettings(listId: String, settings: ListSettings) {
        dataStore.edit { prefs ->
            val current: MutableMap<String, ListSettings> = runCatching {
                settingsJson.decodeFromString<Map<String, ListSettings>>(
                    prefs[PER_LIST_SETTINGS_KEY] ?: "{}"
                ).toMutableMap()
            }.getOrDefault(mutableMapOf())
            current[listId] = settings
            prefs[PER_LIST_SETTINGS_KEY] =
                settingsJson.encodeToString<Map<String, ListSettings>>(current)
        }
    }

    // ── Per-category update helpers ───────────────────────────────────────────

    private suspend fun updateThemeSettings(transform: StoredThemeSettings.() -> StoredThemeSettings) {
        dataStore.edit { prefs ->
            prefs[THEME_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readThemeSettings().transform())
        }
    }

    private suspend fun updateDisplaySettings(transform: StoredDisplaySettings.() -> StoredDisplaySettings) {
        dataStore.edit { prefs ->
            prefs[DISPLAY_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readDisplaySettings().transform())
        }
    }

    private suspend fun updateReaderSettings(transform: StoredReaderSettings.() -> StoredReaderSettings) {
        dataStore.edit { prefs ->
            prefs[READER_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readReaderSettings().transform())
        }
    }

    private suspend fun updateSwipeSettings(transform: StoredSwipeSettings.() -> StoredSwipeSettings) {
        dataStore.edit { prefs ->
            prefs[SWIPE_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readSwipeSettings().transform())
        }
    }

    private suspend fun updateSyncSettings(transform: StoredSyncSettings.() -> StoredSyncSettings) {
        dataStore.edit { prefs ->
            prefs[SYNC_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readSyncSettings().transform())
        }
    }

    private suspend fun updateAppSettings(transform: StoredAppSettings.() -> StoredAppSettings) {
        dataStore.edit { prefs ->
            prefs[APP_SETTINGS_KEY] = settingsJson.encodeToString(prefs.readAppSettings().transform())
        }
    }

    // ── Layouts ───────────────────────────────────────────────────────────────

    private val LAYOUTS_KEY = stringPreferencesKey("display_profiles_json")
    private val DEFAULT_LAYOUT_ID_KEY = stringPreferencesKey("default_profile_id")

    val customLayouts: Flow<List<BookmarkLayout>> = dataStore.data.map { prefs ->
        runCatching {
            settingsJson.decodeFromString<List<BookmarkLayout>>(prefs[LAYOUTS_KEY] ?: "[]")
        }.getOrDefault(emptyList())
    }.distinctUntilChanged()

    val defaultLayoutId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[DEFAULT_LAYOUT_ID_KEY]
    }.distinctUntilChanged()

    suspend fun saveLayout(layout: BookmarkLayout) {
        dataStore.edit { prefs ->
            val current: MutableList<BookmarkLayout> = runCatching {
                settingsJson.decodeFromString<List<BookmarkLayout>>(
                    prefs[LAYOUTS_KEY] ?: "[]"
                ).toMutableList()
            }.getOrDefault(mutableListOf())
            val index = current.indexOfFirst { it.id == layout.id }
            if (index >= 0) {
                current[index] = layout
            } else {
                current.add(layout)
            }
            prefs[LAYOUTS_KEY] = settingsJson.encodeToString<List<BookmarkLayout>>(current)
        }
    }

    suspend fun deleteLayout(id: String) {
        dataStore.edit { prefs ->
            val current: MutableList<BookmarkLayout> = runCatching {
                settingsJson.decodeFromString<List<BookmarkLayout>>(
                    prefs[LAYOUTS_KEY] ?: "[]"
                ).toMutableList()
            }.getOrDefault(mutableListOf())
            current.removeAll { it.id == id }
            prefs[LAYOUTS_KEY] = settingsJson.encodeToString<List<BookmarkLayout>>(current)
            // If deleted layout was the default, clear default
            if (prefs[DEFAULT_LAYOUT_ID_KEY] == id) {
                prefs.remove(DEFAULT_LAYOUT_ID_KEY)
            }
        }
    }

    suspend fun setDefaultLayoutId(id: String?) {
        dataStore.edit { prefs ->
            if (id != null) {
                prefs[DEFAULT_LAYOUT_ID_KEY] = id
            } else {
                prefs.remove(DEFAULT_LAYOUT_ID_KEY)
            }
        }
    }

    suspend fun setListLayoutId(listId: String, layoutId: String?) {
        dataStore.edit { prefs ->
            val current: MutableMap<String, ListSettings> = runCatching {
                settingsJson.decodeFromString<Map<String, ListSettings>>(
                    prefs[PER_LIST_SETTINGS_KEY] ?: "{}"
                ).toMutableMap()
            }.getOrDefault(mutableMapOf())
            val existing = current[listId] ?: ListSettings()
            current[listId] = existing.copy(layoutId = layoutId)
            prefs[PER_LIST_SETTINGS_KEY] =
                settingsJson.encodeToString<Map<String, ListSettings>>(current)
        }
    }
}
