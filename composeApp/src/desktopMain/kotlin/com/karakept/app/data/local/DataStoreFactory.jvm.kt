package com.karakept.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import java.io.File

actual fun createDataStore(): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            val userHome = System.getProperty("user.home")
            val appDir = File(userHome, ".karakept")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            File(appDir, "karakept_settings.preferences_pb").absolutePath.toPath()
        }
    )
}
