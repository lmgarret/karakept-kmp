package com.karakept.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "karakept_settings")

actual fun createDataStore(): DataStore<Preferences> {
    return ContextHolder.get().dataStore
}

private object ContextHolder {
    private lateinit var context: Context
    
    fun init(ctx: Context) {
        context = ctx.applicationContext
    }
    
    fun get(): Context = context
}

fun initializeDataStore(context: Context) {
    ContextHolder.init(context)
}
