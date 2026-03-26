package com.karakept.app.services

import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import io.github.kdroidfilter.knotify.builder.ExperimentalNotificationsApi
import io.github.kdroidfilter.knotify.builder.notification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object BackgroundSyncScheduler {

    private var syncJob: Job? = null

    fun schedule(
        scope: CoroutineScope,
        settingsRepository: SettingsRepository,
        bookmarkRepository: BookmarkRepository,
        serverRepository: ServerRepository,
        frequencyMinutes: Int
    ) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(frequencyMinutes * 60_000L)
                runSync(settingsRepository, bookmarkRepository, serverRepository)
            }
        }
    }

    fun cancel() {
        syncJob?.cancel()
        syncJob = null
    }

    private suspend fun runSync(
        settingsRepository: SettingsRepository,
        bookmarkRepository: BookmarkRepository,
        serverRepository: ServerRepository
    ) {
        val enabled = settingsRepository.backgroundSyncEnabled.first()
        if (!enabled) return

        val servers = serverRepository.servers.first()
        if (servers.isEmpty()) return

        val activeServerId = settingsRepository.activeServerId.first()
        val server = servers.find { it.id == activeServerId } ?: servers.first()

        try {
            bookmarkRepository.syncBookmarks(server)

            val digestEnabled = settingsRepository.backgroundSyncDigestNotification.first()
            if (digestEnabled) {
                showDigestNotification()
            }
        } catch (_: Exception) {
            // Silently ignore; will retry on next interval
        }
    }

    @OptIn(ExperimentalNotificationsApi::class)
    private fun showDigestNotification() {
        // UNUserNotificationCenter requires a valid macOS .app bundle. When running via
        // `gradlew run`, the JVM process has no real bundle and throws an uncatchable
        // NSInternalInconsistencyException that crashes the process. Guard against this.
        if (!isMacAppBundleAvailable()) return
        try {
            notification(
                title = "Bookmarks synced",
                message = "Your bookmarks have been synced in the background."
            ).send()
        } catch (_: Exception) {
            // Notifications not available on this platform
        }
    }

    /**
     * Returns false on macOS when running outside a proper .app bundle (e.g. via `gradlew run`).
     * UNUserNotificationCenter crashes with NSInternalInconsistencyException in that case.
     */
    private fun isMacAppBundleAvailable(): Boolean {
        if (!System.getProperty("os.name", "").lowercase().contains("mac")) return true
        val command = ProcessHandle.current().info().command().orElse("")
        return command.contains(".app/Contents/MacOS/")
    }
}
