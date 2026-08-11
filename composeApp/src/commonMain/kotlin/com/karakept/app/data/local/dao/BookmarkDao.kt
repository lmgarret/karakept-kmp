package com.karakept.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Update
import com.karakept.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId
        ORDER BY createdAt DESC
    """)
    fun getBookmarksForServer(serverId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE localId = :id")
    suspend fun getBookmarkById(id: Long): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE localId = :id")
    fun observeBookmarkById(id: Long): Flow<BookmarkEntity?>
    
    @Query("SELECT * FROM bookmarks WHERE remoteId = :remoteId AND serverId = :serverId LIMIT 1")
    suspend fun getBookmarkByRemoteId(remoteId: Long, serverId: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE originalRemoteId = :originalRemoteId AND serverId = :serverId LIMIT 1")
    suspend fun getBookmarkByOriginalRemoteId(originalRemoteId: String, serverId: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    // Returns the generated localIds, in the order of [bookmarks]. Sync relies on this
    // instead of re-reading the whole table to recover the ids it just inserted.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>): List<Long>

    @Update
    suspend fun updateBookmarks(bookmarks: List<BookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmarks(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE serverId = :serverId")
    suspend fun deleteAllBookmarksForServer(serverId: String)

    @Query("UPDATE bookmarks SET content = :content, readingTimeMinutes = :readingTime WHERE localId = :localId")
    suspend fun updateContent(localId: Long, content: String, readingTime: Int)

    @Query("""
        UPDATE bookmarks SET
            readingProgress = :progress,
            readingScrollIndex = :scrollIndex,
            readingScrollOffset = :scrollOffset,
            isRead = CASE WHEN :progress >= 1.0 THEN 1 ELSE isRead END
        WHERE localId = :localId
    """)
    suspend fun updateReadingProgress(
        localId: Long,
        progress: Float,
        scrollIndex: Int,
        scrollOffset: Int
    )

    // Update only metadata fields, preserving content
    @Query("""
        UPDATE bookmarks SET
            title = :title,
            url = :url,
            description = :description,
            imageUrl = :imageUrl,
            bannerImageAssetId = :bannerImageAssetId,
            screenshotAssetId = :screenshotAssetId,
            tags = :tags,
            listIds = :listIds,
            isStarred = :isStarred,
            isArchived = :isArchived,
            isRead = :isRead,
            readingTimeMinutes = :readingTimeMinutes,
            modifiedAt = :modifiedAt,
            crawlStatus = :crawlStatus,
            crawledAt = :crawledAt
        WHERE localId = :localId
    """)
    suspend fun updateBookmarkMetadata(
        localId: Long,
        title: String,
        url: String,
        description: String?,
        imageUrl: String?,
        bannerImageAssetId: String?,
        screenshotAssetId: String?,
        tags: String,
        listIds: String,
        isStarred: Boolean,
        isArchived: Boolean,
        isRead: Boolean,
        readingTimeMinutes: Int,
        modifiedAt: Long?,
        crawlStatus: String?,
        crawledAt: Long?
    )

    // Stamp the reading-progress rotating cursor after a pull (G3).
    @Query("UPDATE bookmarks SET progressSyncedAt = :syncedAt WHERE localId = :localId")
    suspend fun updateProgressSyncedAt(localId: Long, syncedAt: Long)

    // Reading-progress pull candidates: least-recently-pulled first, then most recently
    // modified, so large libraries converge across successive syncs (G3).
    @Query("""
        SELECT * FROM bookmarks
        WHERE serverId = :serverId
        ORDER BY progressSyncedAt ASC, modifiedAt DESC
        LIMIT :limit
    """)
    suspend fun getReadingProgressPullCandidates(serverId: String, limit: Int): List<BookmarkEntity>

    // Bookmarks whose progress has never been pulled. Drained in full on a freshly connected
    // server so the library does not converge 50 rows per sync. A projection rather than
    // SELECT *: the pull only needs the two ids, and the row carries article content.
    @Query("""
        SELECT localId, remoteId FROM bookmarks
        WHERE serverId = :serverId AND progressSyncedAt = 0
        ORDER BY modifiedAt DESC
        LIMIT :limit
    """)
    suspend fun getNeverProgressSyncedTargets(serverId: String, limit: Int): List<ProgressPullTarget>

    // Same projection for an explicit set of bookmarks whose progress is missing or stale —
    // the rows the user is actually looking at, refreshed ahead of the rotating cursor
    // reaching them. Staleness rather than "never pulled" so that progress changed on
    // another device shows up on the list being looked at, not one rotation later.
    @Query("""
        SELECT localId, remoteId FROM bookmarks
        WHERE serverId = :serverId AND progressSyncedAt < :staleBefore AND remoteId IN (:remoteIds)
    """)
    suspend fun getStaleProgressTargetsIn(
        serverId: String,
        remoteIds: List<Long>,
        staleBefore: Long
    ): List<ProgressPullTarget>

    /**
     * Writes progress that came from the server. Unlike [updateReadingProgress] this also
     * *clears* the read flag below 100%: read state is local-only in this app, and the
     * percentage is the only thing that carries it between devices, so a bookmark marked
     * unread elsewhere has to be able to come back as unread here.
     */
    @Query("""
        UPDATE bookmarks SET
            readingProgress = :progress,
            readingScrollIndex = :scrollIndex,
            readingScrollOffset = :scrollOffset,
            isRead = CASE WHEN :progress >= 1.0 THEN 1 ELSE 0 END
        WHERE localId = :localId
    """)
    suspend fun applyServerReadingProgress(
        localId: Long,
        progress: Float,
        scrollIndex: Int,
        scrollOffset: Int
    )

    // Paginated query — ORDER BY is injected dynamically via RoomRawQuery so the
    // sort option from FilterConfig is applied at the DB level rather than in memory.
    @RawQuery
    suspend fun getBookmarksPaged(query: RoomRawQuery): List<BookmarkEntity>

    // Count queries for pagination
    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId")
    suspend fun getTotalBookmarkCount(serverId: String): Int

    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId AND isStarred = 1")
    suspend fun getFavoritesCount(serverId: String): Int

    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId AND isArchived = 1")
    suspend fun getArchivedCount(serverId: String): Int

    // List-filtered queries
    @Query("""
        SELECT COUNT(*)
        FROM bookmarks
        WHERE serverId = :serverId
        AND (listIds = :listId
             OR listIds LIKE :listId || ',%'
             OR listIds LIKE '%,' || :listId
             OR listIds LIKE '%,' || :listId || ',%')
    """)
    suspend fun getBookmarksForListCount(
        serverId: String,
        listId: String
    ): Int

    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId AND isArchived = 0")
    suspend fun getNotArchivedCount(serverId: String): Int

    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND content IS NOT NULL AND length(content) > 0
        ORDER BY createdAt DESC
    """)
    suspend fun getAllOfflineForServer(serverId: String): List<BookmarkEntity>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId AND content IS NOT NULL AND length(content) > 0")
    fun getOfflineCountFlow(serverId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE serverId = :serverId AND content IS NOT NULL AND length(content) > 0")
    suspend fun getOfflineCount(serverId: String): Int

    // Query for sync that includes content length and reading time to determine if content exists
    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               CASE WHEN length(content) > 0 THEN 'HAS_CONTENT' ELSE '' END as content
        FROM bookmarks
        WHERE serverId = :serverId
    """)
    suspend fun getBookmarksForServerWithContentInfo(serverId: String): List<BookmarkEntity>

    // Unpaged queries for select-all (no LIMIT/OFFSET)
    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND isArchived = 0
        ORDER BY createdAt DESC
    """)
    suspend fun getAllNotArchivedForServer(serverId: String): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND isStarred = 1
        ORDER BY createdAt DESC
    """)
    suspend fun getAllFavoritesForServer(serverId: String): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId AND isArchived = 1
        ORDER BY createdAt DESC
    """)
    suspend fun getAllArchivedForServer(serverId: String): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId
        ORDER BY createdAt DESC
    """)
    suspend fun getAllBookmarksForServerSuspend(serverId: String): List<BookmarkEntity>

    @Query("""
        SELECT localId, remoteId, originalRemoteId, serverId, title, url,
               description, imageUrl, bannerImageAssetId, screenshotAssetId, tags, listIds, isStarred, isArchived,
               isRead, createdAt, readingTimeMinutes, readingProgress, readingScrollIndex, readingScrollOffset,
               modifiedAt, progressSyncedAt,
               '' as content
        FROM bookmarks
        WHERE serverId = :serverId
        AND (listIds = :listId
             OR listIds LIKE :listId || ',%'
             OR listIds LIKE '%,' || :listId
             OR listIds LIKE '%,' || :listId || ',%')
        ORDER BY createdAt DESC
    """)
    suspend fun getAllBookmarksForList(serverId: String, listId: String): List<BookmarkEntity>
}
