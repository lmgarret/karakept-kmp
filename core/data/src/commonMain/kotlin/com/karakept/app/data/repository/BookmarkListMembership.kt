package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.ListSettings
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.first

/**
 * List membership after the fact: reconciling a bookmark's smart lists with the server once a
 * quick action has changed it, and working out which notifying lists the last sync added to.
 */
internal class BookmarkListMembership(
    private val bookmarkDao: BookmarkDao,
    private val remoteDataSource: RemoteDataSource,
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

    /**
     * Reconciles a bookmark's list membership after a quick action.
     * Uses GET /bookmarks/{id}/lists — one API call.
     *
     * Smart list membership is server-computed and fully replaced with the server's answer.
     * Manual list membership is kept from the local DB — the optimistic update applied by
     * the action is already correct, and the queued server action may not have been
     * processed yet when this call is made.
     *
     * @param smartListIds the IDs of all known smart lists — only these are overwritten
     *                     by the server response; manual list IDs are preserved locally
     * @return true if the server returned smart list membership (fresh data),
     *         false if potentially stale (no smart lists returned by the server).
     */
    suspend fun reconcileBookmarkSmartListMembership(
        server: Server,
        bookmarkLocalId: Long,
        smartListIds: Set<String>
    ): Boolean {
        val entity = bookmarkDao.getBookmarkById(bookmarkLocalId) ?: return true
        try {
            val serverListIds = remoteDataSource.fetchListsForBookmark(server, entity.remoteId)
                .mapNotNull { it.id }.toSet()

            val currentIds = entity.listIds.split(",").filter { it.isNotEmpty() }.toSet()
            // Smart lists: server is authoritative (criteria are server-computed)
            // Manual lists: local DB is authoritative (optimistic update already applied)
            val updatedIds = ((currentIds - smartListIds) + (serverListIds intersect smartListIds))
                .joinToString(",")

            if (updatedIds != entity.listIds) {
                bookmarkDao.updateBookmarkMetadata(
                    localId = bookmarkLocalId,
                    title = entity.title,
                    url = entity.url,
                    description = entity.description,
                    imageUrl = entity.imageUrl,
                    bannerImageAssetId = entity.bannerImageAssetId,
                    screenshotAssetId = entity.screenshotAssetId,
                    tags = entity.tags,
                    listIds = updatedIds,
                    isStarred = entity.isStarred,
                    isArchived = entity.isArchived,
                    isRead = entity.isRead,
                    readingTimeMinutes = entity.readingTimeMinutes,
                    modifiedAt = entity.modifiedAt,
                    crawlStatus = entity.crawlStatus,
                    crawledAt = entity.crawledAt,
                    summary = entity.summary,
                    summarizationStatus = entity.summarizationStatus
                )
                AppLogger.d("BookmarkRepository", "Reconciled list membership for bookmark $bookmarkLocalId: $updatedIds")
            }

            // If the server returned no smart list membership, the response may be stale
            // (server's async smart list recalculation hasn't completed yet).
            val serverHasSmartLists = (serverListIds intersect smartListIds).isNotEmpty()
            return serverHasSmartLists
        } catch (e: Exception) {
            AppLogger.e("BookmarkRepository", "Failed to reconcile list membership for bookmark $bookmarkLocalId: ${e.message}", e)
            return false
        }
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
