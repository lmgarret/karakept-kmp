package com.karakept.app.data.repository

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.CheckboxState
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.ListSyncConfig
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.model.ViewerMode
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.data.model.LinkOpenMode

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val LAYOUT_TYPE_KEY = stringPreferencesKey("layout_type")
    private val ACTIVE_SERVER_ID_KEY = stringPreferencesKey("active_server_id")
    private val VIEWER_MODE_KEY = stringPreferencesKey("viewer_mode")
    private val HIDE_ARTICLE_THUMBNAILS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("hide_article_thumbnails")
    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
    private val HTML_TEXT_COLOR_KEY = intPreferencesKey("html_text_color") // Store as ARGB int, null means use theme default
    private val HTML_BACKGROUND_COLOR_KEY = intPreferencesKey("html_background_color") // Store as ARGB int, null means transparent
    private val HTML_FONT_SIZE_KEY = intPreferencesKey("html_font_size") // Default: 16px
    private val HTML_FONT_FAMILY_KEY = stringPreferencesKey("html_font_family") // Enum name
    private val OFFLINE_MODE_KEY = booleanPreferencesKey("offline_mode")
    private val AUTO_OFFLINE_DETECTED_KEY = booleanPreferencesKey("auto_offline_detected")
    private val SHOW_READING_TIME_BADGE_KEY = booleanPreferencesKey("show_reading_time_badge")
    private val SHOW_TAGS_KEY = booleanPreferencesKey("show_tags")
    private val READING_SPEED_WPM_KEY = intPreferencesKey("reading_speed_wpm")
    private val SWIPE_LEFT_ACTION_KEY = stringPreferencesKey("swipe_left_action")
    private val SWIPE_RIGHT_ACTION_KEY = stringPreferencesKey("swipe_right_action")
    
    private val CONTENT_SYNC_STRATEGY_KEY = stringPreferencesKey("content_sync_strategy")
    private val CONTENT_SYNC_TARGET_LISTS_KEY = stringSetPreferencesKey("content_sync_target_lists")
    private val CONTENT_SYNC_WITH_CHILDREN_KEY = stringSetPreferencesKey("content_sync_with_children")

    private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
    private val LINK_OPEN_MODE_KEY = stringPreferencesKey("link_open_mode")

    val layoutType: Flow<LayoutType> = dataStore.data.map { preferences ->
        val layoutString = preferences[LAYOUT_TYPE_KEY] ?: LayoutType.LIST.name
        LayoutType.fromString(layoutString)
    }

    val activeServerId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_SERVER_ID_KEY]
    }

    val viewerMode: Flow<ViewerMode> = dataStore.data.map { preferences ->
        val modeString = preferences[VIEWER_MODE_KEY] ?: ViewerMode.READER.name
        ViewerMode.fromString(modeString)
    }

    val hideArticleThumbnails: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HIDE_ARTICLE_THUMBNAILS_KEY] ?: true // Default to true to avoid duplicates
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        val modeString = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        ThemeMode.fromString(modeString)
    }

    val accentColor: Flow<AccentColor> = dataStore.data.map { preferences ->
        val colorString = preferences[ACCENT_COLOR_KEY] ?: AccentColor.PURPLE.name
        AccentColor.fromString(colorString)
    }

    val htmlTextColor: Flow<Color?> = dataStore.data.map { preferences ->
        preferences[HTML_TEXT_COLOR_KEY]?.let { Color(it) }
    }

    val htmlBackgroundColor: Flow<Color?> = dataStore.data.map { preferences ->
        preferences[HTML_BACKGROUND_COLOR_KEY]?.let { Color(it) }
    }

    val htmlFontSize: Flow<Int> = dataStore.data.map { preferences ->
        preferences[HTML_FONT_SIZE_KEY] ?: 16
    }

    val htmlFontFamily: Flow<ReaderFontFamily> = dataStore.data.map { preferences ->
        val familyString = preferences[HTML_FONT_FAMILY_KEY] ?: ReaderFontFamily.SYSTEM.name
        ReaderFontFamily.fromString(familyString)
    }
    
    /**
     * Manual offline mode setting - user-controlled via settings toggle.
     */
    val offlineMode: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[OFFLINE_MODE_KEY] ?: false
    }

    /**
     * Auto-detected offline state - set when network requests fail.
     */
    val autoOfflineDetected: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_OFFLINE_DETECTED_KEY] ?: false
    }

    /**
     * Effective offline mode - true if either manual offline mode OR auto-detected offline.
     * Use this for blocking network requests.
     */
    val effectiveOfflineMode: Flow<Boolean> = combine(offlineMode, autoOfflineDetected) { manual, auto ->
        manual || auto
    }

    val showReadingTimeBadge: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_READING_TIME_BADGE_KEY] ?: true // Default: show badge
    }

    val showTags: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_TAGS_KEY] ?: true // Default: show tags
    }

    val readingSpeedWpm: Flow<Int> = dataStore.data.map { preferences ->
        preferences[READING_SPEED_WPM_KEY] ?: 238 // Default: 238 WPM (Medium standard)
    }

    val swipeLeftAction: Flow<com.karakept.app.data.model.SwipeAction> = dataStore.data.map { preferences ->
        val actionString = preferences[SWIPE_LEFT_ACTION_KEY] ?: com.karakept.app.data.model.SwipeAction.MARK_READ.name
        com.karakept.app.data.model.SwipeAction.fromString(actionString)
    }

    val swipeRightAction: Flow<com.karakept.app.data.model.SwipeAction> = dataStore.data.map { preferences ->
        val actionString = preferences[SWIPE_RIGHT_ACTION_KEY] ?: com.karakept.app.data.model.SwipeAction.ARCHIVE.name
        com.karakept.app.data.model.SwipeAction.fromString(actionString)
    }

    suspend fun setLayoutType(layoutType: LayoutType) {
        dataStore.edit { preferences ->
            preferences[LAYOUT_TYPE_KEY] = layoutType.name
        }
    }

    suspend fun setActiveServerId(id: String) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_SERVER_ID_KEY] = id
        }
    }

    suspend fun setViewerMode(mode: ViewerMode) {
        dataStore.edit { preferences ->
            preferences[VIEWER_MODE_KEY] = mode.name
        }
    }

    suspend fun setHideArticleThumbnails(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[HIDE_ARTICLE_THUMBNAILS_KEY] = hide
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setAccentColor(color: AccentColor) {
        dataStore.edit { preferences ->
            preferences[ACCENT_COLOR_KEY] = color.name
        }
    }

    suspend fun setHtmlTextColor(color: Color?) {
        dataStore.edit { preferences ->
            if (color != null) {
                preferences[HTML_TEXT_COLOR_KEY] = color.toArgb()
            } else {
                preferences.remove(HTML_TEXT_COLOR_KEY)
            }
        }
    }

    suspend fun setHtmlBackgroundColor(color: Color?) {
        dataStore.edit { preferences ->
            if (color != null) {
                preferences[HTML_BACKGROUND_COLOR_KEY] = color.toArgb()
            } else {
                preferences.remove(HTML_BACKGROUND_COLOR_KEY)
            }
        }
    }

    suspend fun setHtmlFontSize(size: Int) {
        dataStore.edit { preferences ->
            preferences[HTML_FONT_SIZE_KEY] = size
        }
    }

    suspend fun setHtmlFontFamily(family: ReaderFontFamily) {
        dataStore.edit { preferences ->
            preferences[HTML_FONT_FAMILY_KEY] = family.name
        }
    }

    suspend fun resetReaderAppearance() {
        dataStore.edit { preferences ->
            preferences.remove(HTML_TEXT_COLOR_KEY)
            preferences.remove(HTML_BACKGROUND_COLOR_KEY)
            preferences.remove(HTML_FONT_SIZE_KEY)
            preferences.remove(HTML_FONT_FAMILY_KEY)
        }
    }
    
    suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[OFFLINE_MODE_KEY] = enabled
        }
    }

    /**
     * Set the auto-detected offline state.
     * Call this when network requests fail to automatically switch to offline mode.
     */
    suspend fun setAutoOfflineDetected(detected: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_OFFLINE_DETECTED_KEY] = detected
        }
    }

    /**
     * Clear the auto-detected offline state.
     * Call this when the user manually goes online or when network is restored.
     */
    suspend fun clearAutoOfflineDetected() {
        dataStore.edit { preferences ->
            preferences[AUTO_OFFLINE_DETECTED_KEY] = false
        }
    }

    suspend fun setShowReadingTimeBadge(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_READING_TIME_BADGE_KEY] = show
        }
    }

    suspend fun setShowTags(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_TAGS_KEY] = show
        }
    }

    suspend fun setReadingSpeedWpm(wpm: Int) {
        dataStore.edit { preferences ->
            // Clamp to reasonable range: 100-500 WPM
            val clampedWpm = wpm.coerceIn(100, 500)
            preferences[READING_SPEED_WPM_KEY] = clampedWpm
        }
    }

    suspend fun setSwipeLeftAction(action: com.karakept.app.data.model.SwipeAction) {
        dataStore.edit { preferences ->
            preferences[SWIPE_LEFT_ACTION_KEY] = action.name
        }
    }

    suspend fun setSwipeRightAction(action: com.karakept.app.data.model.SwipeAction) {
        dataStore.edit { preferences ->
            preferences[SWIPE_RIGHT_ACTION_KEY] = action.name
        }
    }

    val contentSyncStrategy: Flow<SyncStrategy> = dataStore.data.map { preferences ->
        val strategyString = preferences[CONTENT_SYNC_STRATEGY_KEY] ?: SyncStrategy.PER_BOOKMARK.name
        try {
            SyncStrategy.valueOf(strategyString)
        } catch (e: Exception) {
            SyncStrategy.PER_BOOKMARK
        }
    }

    val contentSyncTargetLists: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[CONTENT_SYNC_TARGET_LISTS_KEY] ?: emptySet()
    }

    val contentSyncConfig: Flow<ListSyncConfig> = dataStore.data.map { preferences ->
        val targetLists = preferences[CONTENT_SYNC_TARGET_LISTS_KEY] ?: emptySet()
        val withChildren = preferences[CONTENT_SYNC_WITH_CHILDREN_KEY] ?: emptySet()
        ListSyncConfig(targetLists, withChildren)
    }

    suspend fun setContentSyncStrategy(strategy: SyncStrategy) {
        dataStore.edit { preferences ->
            preferences[CONTENT_SYNC_STRATEGY_KEY] = strategy.name
        }
    }

    suspend fun setContentSyncTargetLists(listIds: Set<String>) {
        dataStore.edit { preferences ->
            preferences[CONTENT_SYNC_TARGET_LISTS_KEY] = listIds
        }
    }

    suspend fun toggleContentSyncTargetList(listId: String) {
        dataStore.edit { preferences ->
            val current = (preferences[CONTENT_SYNC_TARGET_LISTS_KEY] ?: emptySet()).toMutableSet()
            if (current.contains(listId)) {
                current.remove(listId)
            } else {
                current.add(listId)
            }
            preferences[CONTENT_SYNC_TARGET_LISTS_KEY] = current
        }
    }

    suspend fun updateListSyncState(listId: String, newState: CheckboxState) {
        dataStore.edit { preferences ->
            val currentSelected = (preferences[CONTENT_SYNC_TARGET_LISTS_KEY] ?: emptySet()).toMutableSet()
            val currentWithChildren = (preferences[CONTENT_SYNC_WITH_CHILDREN_KEY] ?: emptySet()).toMutableSet()

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

            preferences[CONTENT_SYNC_TARGET_LISTS_KEY] = currentSelected
            preferences[CONTENT_SYNC_WITH_CHILDREN_KEY] = currentWithChildren
        }
    }

    suspend fun setContentSyncConfig(config: ListSyncConfig) {
        dataStore.edit { preferences ->
            preferences[CONTENT_SYNC_TARGET_LISTS_KEY] = config.selectedLists
            preferences[CONTENT_SYNC_WITH_CHILDREN_KEY] = config.withChildrenMode
        }
    }

    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED_KEY] ?: true // Default: enabled
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }

    val linkOpenMode: Flow<LinkOpenMode> = dataStore.data.map { preferences ->
        val modeString = preferences[LINK_OPEN_MODE_KEY] ?: LinkOpenMode.CUSTOM_TAB.name
        LinkOpenMode.fromString(modeString)
    }

    suspend fun setLinkOpenMode(mode: LinkOpenMode) {
        dataStore.edit { preferences ->
            preferences[LINK_OPEN_MODE_KEY] = mode.name
        }
    }

    private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")

    private val DIM_READ_BOOKMARKS_KEY = booleanPreferencesKey("dim_read_bookmarks")
    private val AUTO_MARK_READ_ON_SCROLL_KEY = booleanPreferencesKey("auto_mark_read_on_scroll")

    val dimReadBookmarks: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DIM_READ_BOOKMARKS_KEY] ?: true
    }

    val autoMarkReadOnScroll: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_MARK_READ_ON_SCROLL_KEY] ?: false
    }

    suspend fun setDimReadBookmarks(dim: Boolean) {
        dataStore.edit { preferences ->
            preferences[DIM_READ_BOOKMARKS_KEY] = dim
        }
    }

    suspend fun setAutoMarkReadOnScroll(autoMark: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_MARK_READ_ON_SCROLL_KEY] = autoMark
        }
    }

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }
}
