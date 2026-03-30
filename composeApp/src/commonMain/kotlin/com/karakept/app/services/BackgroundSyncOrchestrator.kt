package com.karakept.app.services

import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.first

class BackgroundSyncOrchestrator(
    private val settingsRepository: SettingsRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val serverRepository: ServerRepository,
    private val notificationProvider: NotificationProvider
) {
    suspend fun runSync(): SyncResult {
        if (!settingsRepository.backgroundSyncEnabled.first()) return SyncResult.SKIPPED

        val servers = serverRepository.servers.first()
        if (servers.isEmpty()) return SyncResult.SKIPPED

        val activeServerId = settingsRepository.activeServerId.first()
        val server = servers.find { it.id == activeServerId } ?: servers.first()

        return try {
            val newCount = bookmarkRepository.syncBookmarks(server)
            sendNotificationsIfNeeded(server.id, newCount)
            SyncResult.SUCCESS
        } catch (e: Exception) {
            AppLogger.e("BackgroundSync", "Sync failed", e)
            SyncResult.ERROR
        }
    }

    private suspend fun sendNotificationsIfNeeded(serverId: String, newCount: Int) {
        val notificationsEnabled = settingsRepository.notificationsEnabled.first()
        if (!notificationsEnabled || !notificationProvider.canSendNotifications()) return

        val digestEnabled = settingsRepository.backgroundSyncDigestNotification.first()
        if (digestEnabled) {
            val (title, message) = formatDigestMessage(newCount)
            notificationProvider.sendNotification(title, message, NotificationType.DIGEST)
        }

        if (newCount > 0) {
            val listsToNotify = bookmarkRepository.getListsNeedingNotification(serverId)
            if (listsToNotify.isNotEmpty()) {
                val (title, message) = formatListMessage(
                    listsToNotify.map { (_, name, count) -> name to count }
                )
                notificationProvider.sendNotification(title, message, NotificationType.LIST_UPDATE)
            }
        }
    }

    internal companion object {
        fun formatDigestMessage(newCount: Int): Pair<String, String> {
            val title = "Bookmarks synced"
            val message = if (newCount > 0) {
                "$newCount new bookmark${if (newCount > 1) "s" else ""} synced"
            } else {
                "Bookmarks are up to date"
            }
            return title to message
        }

        fun formatListMessage(listNamesWithCounts: List<Pair<String, Int>>): Pair<String, String> {
            val title = "List updates"
            val message = listNamesWithCounts.joinToString(", ") { (name, count) ->
                "$count new in $name"
            }
            return title to message
        }
    }
}

enum class SyncResult { SUCCESS, SKIPPED, ERROR }
