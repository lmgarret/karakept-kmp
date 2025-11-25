package com.karakept.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "karakept_settings")

actual fun createDataStore(): DataStore<Preferences> {
    return getApplicationContext().dataStore
}

private lateinit var applicationContext: Context

fun initializeDataStore(context: Context) {
    applicationContext = context.applicationContext
}

private fun getApplicationContext(): Context {
    return applicationContext
}
