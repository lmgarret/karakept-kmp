package com.karakept.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.karakept.app.data.model.LayoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val LAYOUT_TYPE_KEY = stringPreferencesKey("layout_type")

    private val ACTIVE_SERVER_ID_KEY = stringPreferencesKey("active_server_id")

    val layoutType: Flow<LayoutType> = dataStore.data.map { preferences ->
        val layoutString = preferences[LAYOUT_TYPE_KEY] ?: LayoutType.CARD.name
        LayoutType.fromString(layoutString)
    }

    val activeServerId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_SERVER_ID_KEY]
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
}
