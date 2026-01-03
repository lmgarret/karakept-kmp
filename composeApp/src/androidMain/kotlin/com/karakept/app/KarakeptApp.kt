package com.karakept.app

import android.app.Application
import com.karakept.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KarakeptApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@KarakeptApp)
            modules(appModule)
        }

        // Initialize platform-specific database context
        com.karakept.app.data.local.AndroidContext.context = applicationContext
        com.karakept.app.data.local.initializeDataStore(applicationContext)
    }
}
