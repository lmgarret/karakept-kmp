package com.karakept.app

import android.app.Application
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.di.appModule
import com.karakept.app.services.AndroidNotificationProvider
import com.karakept.app.services.BackgroundSyncScheduler
import com.karakept.app.services.NotificationProvider
import com.karakept.app.utils.AppIconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.module

class KarakeptApp : Application(), KoinComponent {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@KarakeptApp)
            modules(appModule, module {
                single<NotificationProvider> { AndroidNotificationProvider(androidContext()) }
            })
        }

        // Initialize platform-specific database context
        com.karakept.app.data.local.AndroidContext.context = applicationContext
        com.karakept.app.data.local.initializeDataStore(applicationContext)

        AppIconManager.init(applicationContext)

        initializeBackgroundSync()
        initializeAppIcon()
    }

    /**
     * Keeps the launcher aliases in step with the stored preference. Driven from the flow rather
     * than from the settings screen so a restored backup also lands, and so a component the
     * system reset (a "clear data", a restore-from-cloud) is put back on the next start.
     */
    private fun initializeAppIcon() {
        val settingsRepository: SettingsRepository by inject()
        appScope.launch {
            settingsRepository.einkMonochromeIcon.collect { AppIconManager.setMonochrome(it) }
        }
    }

    private fun initializeBackgroundSync() {
        val settingsRepository: SettingsRepository by inject()
        appScope.launch {
            combine(
                settingsRepository.backgroundSyncEnabled,
                settingsRepository.backgroundSyncFrequencyMinutes
            ) { enabled, frequency -> enabled to frequency }
                .distinctUntilChanged()
                .collect { (enabled, frequency) ->
                    if (enabled) {
                        BackgroundSyncScheduler.schedule(applicationContext, frequency)
                    } else {
                        BackgroundSyncScheduler.cancel(applicationContext)
                    }
                }
        }
    }
}
