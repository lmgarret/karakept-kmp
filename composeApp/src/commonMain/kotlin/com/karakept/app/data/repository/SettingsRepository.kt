package com.karakept.app.data.repository

import androidx.compose.ui.graphics.Color
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
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DefaultListType
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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SettingsRepository(internal val dataStore: DataStore<Preferences>) {

    internal val settingsJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Per-category keys (primary storage) ───────────────────────────────────

    internal val THEME_SETTINGS_KEY = stringPreferencesKey("settings_theme_json")
    internal val DISPLAY_SETTINGS_KEY = stringPreferencesKey("settings_display_json")
    internal val READER_SETTINGS_KEY = stringPreferencesKey("settings_reader_json")
    internal val SWIPE_SETTINGS_KEY = stringPreferencesKey("settings_swipe_json")
    internal val SYNC_SETTINGS_KEY = stringPreferencesKey("settings_sync_json")
    internal val APP_SETTINGS_KEY = stringPreferencesKey("settings_app_json")

    // ── Non-backed-up individual keys (permanent, never move to a category blob) ──

    internal val ACTIVE_SERVER_ID_KEY = stringPreferencesKey("active_server_id")
    internal val AUTO_OFFLINE_DETECTED_KEY = booleanPreferencesKey("auto_offline_detected")
    internal val LAST_AUTO_EXPORT_TIME_KEY = longPreferencesKey("last_auto_export_time")
    internal val PER_LIST_SETTINGS_KEY = stringPreferencesKey("per_list_settings")

    /**
     * The actual backup PIN (4–6 digits), stored locally for use by scheduled auto-exports.
     * Never included in the backup file itself; only the derived hash is.
     */
    internal val BACKUP_PIN_KEY = stringPreferencesKey("backup_pin")

    // ── Desktop layout (non-backed-up) ──────────────────────────────────────
    internal val DRAWER_WIDTH_DP_KEY = floatPreferencesKey("desktop_drawer_width_dp")
    internal val LIST_COLUMN_FRACTION_KEY = floatPreferencesKey("desktop_list_column_fraction")

    // ── Desktop window state (non-backed-up) ────────────────────────────────
    internal val WINDOW_WIDTH_KEY = floatPreferencesKey("desktop_window_width")
    internal val WINDOW_HEIGHT_KEY = floatPreferencesKey("desktop_window_height")
    internal val WINDOW_X_KEY = floatPreferencesKey("desktop_window_x")
    internal val WINDOW_Y_KEY = floatPreferencesKey("desktop_window_y")
    internal val WINDOW_MAXIMIZED_KEY = booleanPreferencesKey("desktop_window_maximized")

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

    internal fun Preferences.readThemeSettings(): StoredThemeSettings {
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

    internal fun Preferences.readDisplaySettings(): StoredDisplaySettings {
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

    internal fun Preferences.readReaderSettings(): StoredReaderSettings {
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

    internal fun Preferences.readSwipeSettings(): StoredSwipeSettings {
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

    internal fun Preferences.readSyncSettings(): StoredSyncSettings {
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

    internal fun Preferences.readAppSettings(): StoredAppSettings {
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

    // ── Derived flows (theme) ─────────────────────────────────────────────────

    val themeMode: Flow<ThemeMode> = themeSettingsFlow.map { ThemeMode.fromString(it.themeMode) }.distinctUntilChanged()
    val accentColor: Flow<AccentColor> = themeSettingsFlow.map { AccentColor.fromString(it.accentColor) }.distinctUntilChanged()

    // ── Derived flows (display) ───────────────────────────────────────────────

    val layoutType: Flow<LayoutType> = displaySettingsFlow.map { LayoutType.fromString(it.layoutType) }.distinctUntilChanged()
    val hideArticleThumbnails: Flow<Boolean> = displaySettingsFlow.map { it.hideArticleThumbnails }.distinctUntilChanged()
    val showReadingTimeBadge: Flow<Boolean> = displaySettingsFlow.map { it.showReadingTimeBadge }.distinctUntilChanged()
    val showTags: Flow<Boolean> = displaySettingsFlow.map { it.showTags }.distinctUntilChanged()
    val dimReadBookmarks: Flow<Boolean> = displaySettingsFlow.map { it.dimReadBookmarks }.distinctUntilChanged()

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

    val scrollToTopEnabled: Flow<Boolean> =
        readerSettingsFlow.map { it.scrollToTopEnabled }.distinctUntilChanged()

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

    // ── Desktop layout & window state flows (non-backed-up) ─────────────────

    val drawerWidthDp: Flow<Float> = dataStore.data.map { it[DRAWER_WIDTH_DP_KEY] ?: 280f }
    val listColumnFraction: Flow<Float> = dataStore.data.map { it[LIST_COLUMN_FRACTION_KEY] ?: 0.4f }
    val windowWidth: Flow<Float?> = dataStore.data.map { it[WINDOW_WIDTH_KEY] }
    val windowHeight: Flow<Float?> = dataStore.data.map { it[WINDOW_HEIGHT_KEY] }
    val windowX: Flow<Float?> = dataStore.data.map { it[WINDOW_X_KEY] }
    val windowY: Flow<Float?> = dataStore.data.map { it[WINDOW_Y_KEY] }
    val windowMaximized: Flow<Boolean> = dataStore.data.map { it[WINDOW_MAXIMIZED_KEY] ?: true }

    // ── Setters, update helpers, backup API, and layout CRUD are in SettingsRepositoryMutations.kt ──

    // ── Default list settings (non-backed-up individual keys) ────────────────

    internal val DEFAULT_LIST_TYPE_KEY = stringPreferencesKey("default_list_type")
    internal val DEFAULT_LIST_ID_KEY = stringPreferencesKey("default_list_id")

    val defaultListType: Flow<DefaultListType> = dataStore.data.map { prefs ->
        DefaultListType.fromString(prefs[DEFAULT_LIST_TYPE_KEY] ?: DefaultListType.ALL_BOOKMARKS.name)
    }

    val defaultListId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[DEFAULT_LIST_ID_KEY]
    }

    // ── Date display settings (non-backed-up individual keys) ────────────────

    internal val SHOW_DATE_IN_LIST_KEY = booleanPreferencesKey("show_date_in_list")
    internal val DATE_DISPLAY_MODE_KEY = stringPreferencesKey("date_display_mode")

    val showDateInList: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_DATE_IN_LIST_KEY] ?: true
    }

    val dateDisplayMode: Flow<DateDisplayMode> = dataStore.data.map { preferences ->
        val modeString = preferences[DATE_DISPLAY_MODE_KEY] ?: DateDisplayMode.ELAPSED.name
        DateDisplayMode.fromString(modeString)
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

    // ── Layouts (flows only — CRUD in SettingsRepositoryMutations.kt) ────────

    internal val LAYOUTS_KEY = stringPreferencesKey("display_profiles_json")
    internal val DEFAULT_LAYOUT_ID_KEY = stringPreferencesKey("default_profile_id")

    val customLayouts: Flow<List<BookmarkLayout>> = dataStore.data.map { prefs ->
        runCatching {
            settingsJson.decodeFromString<List<BookmarkLayout>>(prefs[LAYOUTS_KEY] ?: "[]")
        }.getOrDefault(emptyList())
    }.distinctUntilChanged()

    val defaultLayoutId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[DEFAULT_LAYOUT_ID_KEY]
    }.distinctUntilChanged()

    // ── Background sync (non-backed-up individual keys) ────────────────────────

    private val BACKGROUND_SYNC_ENABLED_KEY = booleanPreferencesKey("background_sync_enabled")
    private val BACKGROUND_SYNC_FREQUENCY_KEY = intPreferencesKey("background_sync_frequency_minutes")
    private val BACKGROUND_SYNC_DIGEST_KEY = booleanPreferencesKey("background_sync_digest_notification")

    val backgroundSyncEnabled: Flow<Boolean> =
        dataStore.data.map { it[BACKGROUND_SYNC_ENABLED_KEY] ?: false }

    val backgroundSyncFrequencyMinutes: Flow<Int> =
        dataStore.data.map { it[BACKGROUND_SYNC_FREQUENCY_KEY] ?: 60 }

    val backgroundSyncDigestNotification: Flow<Boolean> =
        dataStore.data.map { it[BACKGROUND_SYNC_DIGEST_KEY] ?: false }

    suspend fun setBackgroundSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[BACKGROUND_SYNC_ENABLED_KEY] = enabled }
    }

    suspend fun setBackgroundSyncFrequencyMinutes(minutes: Int) {
        dataStore.edit { it[BACKGROUND_SYNC_FREQUENCY_KEY] = minutes }
    }

    suspend fun setBackgroundSyncDigestNotification(enabled: Boolean) {
        dataStore.edit { it[BACKGROUND_SYNC_DIGEST_KEY] = enabled }
    }
}
