package com.karakept.app.data.repository

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.model.ViewerMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val LAYOUT_TYPE_KEY = stringPreferencesKey("layout_type")
    private val ACTIVE_SERVER_ID_KEY = stringPreferencesKey("active_server_id")
    private val VIEWER_MODE_KEY = stringPreferencesKey("viewer_mode")
    private val HIDE_ARTICLE_THUMBNAILS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("hide_article_thumbnails")
    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
    private val HTML_TEXT_COLOR_KEY = intPreferencesKey("html_text_color") // Store as ARGB int, null means use theme default

    val layoutType: Flow<LayoutType> = dataStore.data.map { preferences ->
        val layoutString = preferences[LAYOUT_TYPE_KEY] ?: LayoutType.CARD.name
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
}
