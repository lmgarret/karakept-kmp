package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.ListSettings
import kotlinx.coroutines.flow.first

/** Works out which notifying lists the last sync added to. */
internal class BookmarkListMembership(
    private val settingsRepository: SettingsRepository,
    private val listDao: ListDao,
    private val lastSyncNewBookmarks: () -> List<BookmarkEntity>,
) {
    /**
     * After sync, finds lists with notifyOnNewBookmarks=true that received new bookmarks.
     * Returns list of (listId, listName, count) triples for notification dispatch.
     */
    suspend fun getListsNeedingNotification(serverId: String): List<Triple<String, String, Int>> {
        val allSettings = settingsRepository.allListSettings.first()
        val notifyListIds = allSettings.filter { it.value.notifyOnNewBookmarks }.keys
        if (notifyListIds.isEmpty()) return emptyList()

        val newBookmarks = lastSyncNewBookmarks()
        if (newBookmarks.isEmpty()) return emptyList()

        val allLists = listDao.getListsForServer(serverId).first()

        return findListsWithNewBookmarks(allSettings, newBookmarks, allLists)
    }
}

/**
 * Determines which lists with notifyOnNewBookmarks=true received new bookmarks.
 * Returns list of (listId, listName) pairs for lists that should trigger a notification.
 *
 * Extracted as internal top-level function for testability in commonTest.
 */
internal fun findListsWithNewBookmarks(
    allListSettings: Map<String, ListSettings>,
    newBookmarks: List<BookmarkEntity>,
    allLists: List<ListEntity>
): List<Triple<String, String, Int>> {
    val notifyListIds = allListSettings
        .filter { it.value.notifyOnNewBookmarks }
        .keys
    if (notifyListIds.isEmpty()) return emptyList()

    val listNameMap = allLists.associate { it.remoteId to it.name }

    return notifyListIds.mapNotNull { listId ->
        val count = newBookmarks.count { bookmark ->
            bookmark.listIds.split(",").map { it.trim() }.contains(listId)
        }
        if (count > 0) {
            listNameMap[listId]?.let { name -> Triple(listId, name, count) }
        } else null
    }
}
